package jp.co.sony.csl.dcoes.apis.main.app.gridmaster;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.UUID;

import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.ServiceAddress;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectUtil;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectWrapper;
import jp.co.sony.csl.dcoes.apis.main.app.PolicyKeeping;
import jp.co.sony.csl.dcoes.apis.main.app.controller.util.DDCon;
import jp.co.sony.csl.dcoes.apis.main.util.ApisConfig;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

/**
 * A Verticle that collects the unit data of all units.
 * Launched from {@link GridMaster}.
 * Periodically broadcast a date collection request to all units.
 * Receive and retain the returned unit data.
 * @author OES Project
 *          
 * 全ユニットのユニットデータを収集する Verticle.
 * {@link GridMaster} から起動される.
 * 定期的に全ユニットに対しデータ収集要求をブロードキャストする.
 * 返ってきたユニットデータを受信し保持する.
 * @author OES Project
 */
public class DataCollection extends AbstractVerticle {
	private static final Logger log = LoggerFactory.getLogger(DataCollection.class);

	/**
	 * Default duration of data collection cycle [ms].
	 * Value: {@value}.
	 *          
	 * データ収集周期のデフォルト値 [ms].
	 * 値は {@value}.
	 */
	private static final Long DEFAULT_DATA_COLLECTION_PERIOD_MSEC = 5000L;
	/**
	 * Default HTTP request timeout duration [ms].
	 * Value: {@value}.
	 *          
	 * HTTP リクエストのタイムアウト時間のデフォルト値 [ms].
	 * 値は {@value}.
	 */
	private static final Long DEFAULT_DATA_COLLECTION_TIMEOUT_MSEC = 2000L;

	/**
	 * A cache that retains unit data for all units.
	 * Independent of {@link jp.co.sony.csl.dcoes.apis.main.app.gridmaster.main_loop.DealExecution#unitDataCache}
	 *          
	 * 全ユニットのユニットデータを保持しておくキャッシュ.
	 * {@link jp.co.sony.csl.dcoes.apis.main.app.gridmaster.main_loop.DealExecution#unitDataCache} とは独立.
	 */
	public static final JsonObjectWrapper cache = new JsonObjectWrapper();

	private long dataCollectionTimerId_ = 0L;
	private long lastDataCollectionMillis_ = 0L;
	private boolean stopped_ = false;
	private Future<Void> stopFuture_ = Future.succeededFuture();

	/**
	 * Called at startup.
	 * Launches the {@link io.vertx.core.eventbus.EventBus} service.
	 * Launches a timer that periodically updates the cache by collecting unit data from all units.
	 * @param startFuture {@inheritDoc}
	 * @throws Exception {@inheritDoc}
	 *          
	 * 起動時に呼び出される.
	 * {@link io.vertx.core.eventbus.EventBus} サービスを起動する.
	 * 定期的に全ユニットのユニットデータを収集しキャッシュを更新するタイマを起動する.
	 * @param startFuture {@inheritDoc}
	 * @throws Exception {@inheritDoc}
	 */
	@Override public void start(Future<Void> startFuture) throws Exception {
		startInternalUnitDatasService_(resInternalUnitDatas -> {
			if (resInternalUnitDatas.succeeded()) {
				dataCollectionTimerHandler_(0L);
				if (log.isTraceEnabled()) log.trace("started : " + deploymentID());
				startFuture.complete();
			} else {
				startFuture.fail(resInternalUnitDatas.cause());
			}
		});
	}

	/**
	 * Called when stopped.
	 * Set a flag to stop the timer.
	 * Use {@link #stopFuture_} to perform exclusion control so that {@link #stop(Future)} does not complete during propagation of the {@link io.vertx.core.eventbus.EventBus} service created during the process of periodically collecting unit data from all units.
	 * @param stopFuture {@inheritDoc}
	 * @throws Exception {@inheritDoc}
	 *          
	 * 停止時に呼び出される.
	 * タイマを止めるためのフラグを立てる.
	 * 定期的に全ユニットのユニットデータを収集する処理中に作成する {@link io.vertx.core.eventbus.EventBus} サービスの伝搬中に {@link #stop(Future)} が完了しないよう {@link #stopFuture_} を使って排他制御する.
	 * @param stopFuture {@inheritDoc}
	 * @throws Exception {@inheritDoc}
	 */
	@Override public void stop(Future<Void> stopFuture) throws Exception {
		stopped_ = true;
		/**
		 * We use the following trick to avoid conflicts with the data collection process.
		 * If an undeploy occurs while propagating the data collection address, a Vert.x error will occur.
		 * So don't complete() stopFuture while propagation is in progress.
		 *          
		 * 以下データ収集処理との競合を避けるための細工.
		 * データ収集用アドレスの伝搬中に undeploy が起きると Vert.x 的にエラーになる.
		 * なので伝搬中は stopFuture を complete() しないようにする.
		 */
		// The stopFuture_ object is updated each time data is collected, and is referenced by the variable f
		// データ収集の都度 stopFuture_ オブジェクトが新しくなるため f で参照しておく
		Future<Void> f = this.stopFuture_;
		// Set a timeout
		// タイムアウトを仕込む
		long timerId = vertx.setTimer(DEFAULT_DATA_COLLECTION_PERIOD_MSEC, t -> {
			if (!f.isComplete()) {
				// If it is still not complete, raise an error and fail()
				// まだ complete でなければエラーを出して fail() させる
				ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, "stopFuture_ timed out", f);
			}
		});
		/**
		 * Called immediately if the data collection address is not being propagated.
		 * Otherwise wait until propagation is complete.
		 * Or call with a timeout.
		 *          
		 * データ収集用アドレスの伝搬中でなければ即座に呼ばれる.
		 * 伝搬中なら伝搬が終わったところで呼ばれる.
		 * あるいはタイムアウトで呼ばれる.
		*/
		f.setHandler(ar -> {
			// Cancel the timeout timer that was set above
			// 上で仕込んだタイムアウト用タイマをキャンセルする
			vertx.cancelTimer(timerId);
			if (log.isTraceEnabled()) log.trace("stopped : " + deploymentID());
			stopFuture.complete();
		});
	}

	////

	/**
	 * Launch the {@link io.vertx.core.eventbus.EventBus} service.
	 * Address: {@link ServiceAddress.GridMaster#urgentUnitDatas()}
	 * Scope: local
	 * Function: Acquire the unit data of all units.
	 *           Return the results collected by querying all the units instead of using the cached values that are periodically refreshed by GridMaster.
	 *           If a timestamp was sent in the message body and the most recent data collection took place after this time, skip the data collection and return the cached value instead.
	 *           Since "urgent" was not specified in the header at the time of collection, collect the cached data that has been periodically refreshed for each unit.
	 * Message body: A timestamp indicating the time after which the collection of fresh data is not required [{@link Number}]
	 * Message header: none
	 * Response: Unit data for all units [{@link JsonObject}].
	 *           Fails if an error occurs.
	 * @param completionHandler the completion handler
	 *          
	 * {@link io.vertx.core.eventbus.EventBus} サービス起動.
	 * アドレス : {@link ServiceAddress.GridMaster#urgentUnitDatas()}
	 * 範囲 : ローカル
	 * 処理 : 全ユニットのユニットデータを取得する.
	 * 　　   GridMaster が定期的にリフレッシュしているキャッシュ値ではなく全ユニットに問合せ収集した結果を返す.
	 * 　　   メッセージボディでタイムスタンプが送られその値より直近のデータ収集が新しい場合はデータ収集を実行せずキャッシュを返す.
	 * 　　   収集時ヘッダに urgent 指定をしないため各ユニットで定期的にリフレッシュしてあるキャッシュデータが集まる.
	 * メッセージボディ : これより新しければ収集不要タイムスタンプ [{@link Number}]
	 * メッセージヘッダ : なし
	 * レスポンス : 全ユニットのユニットデータ [{@link JsonObject}].
	 * 　　　　　   エラーが起きたら fail.
	 * @param completionHandler the completion handler
	 */
	private void startInternalUnitDatasService_(Handler<AsyncResult<Void>> completionHandler) {
		vertx.eventBus().<Number>localConsumer(ServiceAddress.GridMaster.urgentUnitDatas(), req -> {
			Number noNeedToRefreshIfNewerThan = req.body();
			if (!cache.isEmpty() && noNeedToRefreshIfNewerThan != null && noNeedToRefreshIfNewerThan.longValue() < lastDataCollectionMillis_) {
				// When some sort of timestamp is provided
				// 何やら時刻が送られてきた場合
				// If the cache is newer than this timestamp, return the cache without collecting new data.
				// その時刻よりキャッシュ時刻の方が新しければ新たにデータ収集することなくキャッシュを返す
				if (log.isDebugEnabled()) log.debug("no need to refresh ; noNeedToRefreshIfNewerThan : " + noNeedToRefreshIfNewerThan + " , lastDataCollectionMillis_ : " + lastDataCollectionMillis_);
				req.reply(cache.jsonObject());
			} else {
				// Actually collect the data and return the results
				// 実際にデータ収集してその結果を返す
				getDatas_(res -> {
					if (res.succeeded()) {
						req.reply(res.result());
					} else {
						req.fail(-1, res.cause().getMessage());
					}
				});
			}
		}).completionHandler(completionHandler);
	}

	/**
	 * Set a data collection timer.
	 * The timeout duration is {@code POLICY.gridMaster.dataCollectionPeriodMsec} (default: {@link #DEFAULT_DATA_COLLECTION_PERIOD_MSEC}).
	 *          
	 * データ収集タイマ設定.
	 * 待ち時間は {@code POLICY.gridMaster.dataCollectionPeriodMsec} ( デフォルト値 {@link #DEFAULT_DATA_COLLECTION_PERIOD_MSEC} ).
	 */
	private void setDataCollectionTimer_() {
		Long delay = PolicyKeeping.cache().getLong(DEFAULT_DATA_COLLECTION_PERIOD_MSEC, "gridMaster", "dataCollectionPeriodMsec");
		setDataCollectionTimer_(delay);
	}
	/**
	 * Set a data collection timer.
	 * @param delay cycle duration [ms]
	 *          
	 * データ収集タイマ設定.
	 * @param delay 周期 [ms]
	 */
	private void setDataCollectionTimer_(long delay) {
		dataCollectionTimerId_ = vertx.setTimer(delay, this::dataCollectionTimerHandler_);
	}
	/**
	 * Data collection timer processing.
	 * @param timerId timer ID
	 *          
	 * データ収集タイマ処理.
	 * @param timerId タイマ ID
	 */
	private void dataCollectionTimerHandler_(Long timerId) {
		if (stopped_) return;
		if (null == timerId || timerId.longValue() != dataCollectionTimerId_) {
			ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, "illegal timerId : " + timerId + ", dataCollectionTimerId_ : " + dataCollectionTimerId_);
			return;
		}
		// I guess periodic data collection is OK, so...
		// 定期的なデータ収集はまあ適当でよいので...
		if (lastDataCollectionMillis_ != 0L) {
			long millisAfterLastDataCollection = (System.currentTimeMillis() - lastDataCollectionMillis_);
			Long period = PolicyKeeping.cache().getLong(DEFAULT_DATA_COLLECTION_PERIOD_MSEC, "gridMaster", "dataCollectionPeriodMsec");
			if (millisAfterLastDataCollection < period) {
				// If the timer cycle time has not elapsed since the last data collection, reset the timer to the time difference and finish without doing anything
				// 前回のデータ収集からタイマ周期時間経過していない場合は差分時間でタイマを再セットし何もせず終わる
				setDataCollectionTimer_(period - millisAfterLastDataCollection);
				return;
			}
		}
		// Collect data
		// データ収集し
		getDatas_(res -> {
			// Also set the next timer
			// また次のタイマをセットする
			setDataCollectionTimer_();
		});
	}
	private void getDatas_(Handler<AsyncResult<JsonObject>> completionHandler) {
		// Temporarily remember the time at which data collection started
		// データ収集を始めた時刻を仮に覚えておく
		                                      // TODO: This method could do with some improvement
		long ts = System.currentTimeMillis(); // TODO : このやり方ちょっといまいちだなぁ
		new DataCollection_().execute_(res -> {
			if (res.succeeded()) {
				// When data collection is finished, formally remember the "starting time" that was temporarily stored earlier
				// データ収集が終わったら仮に覚えてある "始めた時刻" を正式に覚える
				lastDataCollectionMillis_ = ts;
				// Keep in cache
				// キャッシュしておく
				cache.setJsonObject(res.result());
				if (log.isInfoEnabled()) log.info(res.result().size() + " unit data collected");
			}
			completionHandler.handle(res);
		});
	}

	////

	/**
	 * A data collection processing class.
	 * @author OES Project
	 *          
	 * データ収集処理クラス.
	 * @author OES Project
	 */
	private class DataCollection_ {
		private long timeoutTimerId_ = 0L;
		/**
		 * Make an instance.
		 *          
		 * インスタンス作成.
		 */
		private DataCollection_() {
		}
		private void execute_(Handler<AsyncResult<JsonObject>> completionHandler) {
			// Generate a disposable reply address
			// 使い捨ての返信用アドレスを生成する
			String replyAddress = UUID.randomUUID().toString();
			JsonObject result = new JsonObject();
			int numberOfMembers = PolicyKeeping.numberOfMembers();
			// Set up a minor fix (stopFuture_) to ensure that the Verticle body does not stop during address expansion.
			// アドレス展開中に Verticle 本体が stop するのを防ぐための小細工 ( stopFuture_ ) を仕込む
			Future<Void> registerFuture = Future.future();
			stopFuture_ = registerFuture;
//			if (log.isInfoEnabled()) log.info("DataCollection:" + replyAddress + " begin");
			// Open up an EventBus connection with a disposable address
			// 使い捨てのアドレスで EventBus の口を開く
			MessageConsumer<JsonObject> dataConsumer = vertx.eventBus().<JsonObject>consumer(replyAddress);
			// Register a receiver process
			// 受信処理を登録する
			dataConsumer.handler(rep -> {
				// When a message is received
				// メッセージを受け取ったら
				JsonObject aData = rep.body();
				// Check the unit ID to see whether or not it is a member defined in POLICY
				// ユニット ID を確認して POLICY で定義されているメンバかどうか確認する
				String unitId = JsonObjectUtil.getString(aData, "oesunit", "id");
				boolean isMember = PolicyKeeping.isMember(unitId);
				if (!isMember) {
					ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, "unit data received from illegal unit : " + unitId + " ; unit data : " + aData);
				} else {
//					if (log.isInfoEnabled()) log.info("DataCollection:" + replyAddress + " received from : " + unitId);
					result.put(unitId, aData);
					if (numberOfMembers <= result.size()) {
						// When the number of members defined in POLICY has been collected, end immediately
						// POLICY で定義されているメンバの数だけ集まったら即座に終了する
						// Cancel the timer for the timeout
						// タイムアウト用のタイマをキャンセルし
						if (vertx.cancelTimer(timeoutTimerId_)) {
							// Close the connection
							// 開いた口を閉じる
							dataConsumer.unregister(resUnregister -> {
								if (resUnregister.succeeded()) {
//									if (log.isInfoEnabled()) log.info("DataCollection:" + replyAddress + " end");
									// Return the results
									// 結果を返す
									completionHandler.handle(Future.succeededFuture(result));
								} else {
									ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR, resUnregister.cause(), completionHandler);
								}
							});
						}
					}
				}
			}).exceptionHandler(t -> {
				dataConsumer.unregister(resUnregister -> {
					if (resUnregister.succeeded()) {
						// nop
					} else {
						ErrorUtil.report(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR, resUnregister.cause());
					}
					ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR, t, completionHandler);
				});
			}).completionHandler(res -> {
				// When finished opening the connection (eventBus address has finished propagating)
				// 口を開け終わった ( EventBus アドレスの伝搬が終わった ) ら
				// Once the propagation of the EventBus address has finished, release the block with Verticle#stop().
				// EventBus アドレスの伝搬が終わったのでもう Verticle#stop() の阻止を解除する
				registerFuture.complete();
				if (res.succeeded()) {
					// Prepare a disposable address for the reply
					// 返信用の使い捨てアドレスを仕込んで
					DeliveryOptions options = new DeliveryOptions().addHeader("replyAddress", replyAddress).addHeader("gridMasterUnitId", ApisConfig.unitId());
//					if (log.isInfoEnabled()) log.info("DataCollection:" + replyAddress + " requested");
					// Publish a unit data collection request
					// ユニットデータ収集要求を publish する
					vertx.eventBus().publish(ServiceAddress.Controller.unitDatas(), null, options);
					Long dataCollectionTimeoutMsec = PolicyKeeping.cache().getLong(DEFAULT_DATA_COLLECTION_TIMEOUT_MSEC, "gridMaster", "dataCollectionTimeoutMsec");
					// Set a timeout
					// タイムアウトを仕込む
					// The timeout duration is {@code POLICY.gridMaster.dataCollectionTimeoutMsec} (default: {@link #DEFAULT_DATA_COLLECTION_TIMEOUT_MSEC}).
					// 待ち時間は {@code POLICY.gridMaster.dataCollectionTimeoutMsec} ( デフォルト値 {@link #DEFAULT_DATA_COLLECTION_TIMEOUT_MSEC} ).
					timeoutTimerId_ = vertx.setTimer(dataCollectionTimeoutMsec, t -> {
						// If a timeout occurs
						// タイムアウトしたら
						// Close the connection
						// 開いた口を閉じる
						dataConsumer.unregister(resUnregister -> {
							if (resUnregister.succeeded()) {
								if (!result.isEmpty()) {
									// Assume no problem if at least one item of data is returned
									// データが一件でも返ってきていたらまあ問題なし
//									log.error("DataCollection:" + replyAddress + " end ( " + result.size() + " )");
									// Return the results
									// 結果を返す
									completionHandler.handle(Future.succeededFuture(result));
								} else {
									// If no data is returned at all, then this is a major problem
									// 一件も返ってこなければ一大事
//									log.error("DataCollection:" + replyAddress + " end ( 0 )");
									String msg = "no unit data collected";
									if (stopped_) {
										// In fact, the Verticle has stopped
										// 実は Verticle が stop していた
										// → No problem
										// → 問題なし
										ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, msg, completionHandler);
									} else {
										// This is a GLOBAL ERROR
										// これは GLOBAL ERROR !
										ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.ERROR, msg, completionHandler);
									}
								}
							} else {
								ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR, resUnregister.cause(), completionHandler);
							}
						});
					});
				} else {
					ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR, res.cause(), completionHandler);
				}
			});
		}
	}

	////

	/**
	 * Get the ID of the unit responsible for the voltage reference.
	 * The decision is based on data that is periodically collected and updated.
	 * This uses a different set of data to that used in {@link jp.co.sony.csl.dcoes.apis.main.app.gridmaster.main_loop.DealExecution#voltageReferenceUnitId()}.
	 * @return the ID of the voltage reference unit, if it exists.
	 *         If not, return {@code null}.
	 *         Also return {@code null} if the data has not yet been cached.
	 *          
	 * 電圧リファレンスを担っているユニットの ID を取得する.
	 * 定期的に収集して更新しているデータを元に決定する.
	 * {@link jp.co.sony.csl.dcoes.apis.main.app.gridmaster.main_loop.DealExecution#voltageReferenceUnitId()} とは別のデータで判定.
	 * @return 電圧リファレンスがある場合そのユニットの ID.
	 *         なければ {@code null}.
	 *         データがまだキャッシュされていない場合も {@code null}.
	 */
	public static String voltageReferenceUnitId() {
		JsonObject unitData = cache.jsonObject();
		if (unitData != null) {
			for (String aUnitId : unitData.fieldNames()) {
				JsonObject aUnitData = unitData.getJsonObject(aUnitId);
				DDCon.Mode aMode = DDCon.modeFromCode(JsonObjectUtil.getString(aUnitData, "dcdc", "status", "status"));
				if (DDCon.Mode.VOLTAGE_REFERENCE == aMode) {
					if (log.isInfoEnabled()) log.info("voltage reference unit : " + aUnitId);
					return aUnitId;
				}
			}
		} else {
			if (log.isInfoEnabled()) log.info("no unit data");
		}
		if (log.isInfoEnabled()) log.info("no voltage reference unit found");
		return null;
	}

}
