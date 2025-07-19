package jp.co.sony.csl.dcoes.apis.main.app.controller;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collection;
import java.util.Enumeration;

import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.ServiceAddress;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.AbstractStarter;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectUtil;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectWrapper;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.LocalExclusiveLock;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.VertxConfig;
import jp.co.sony.csl.dcoes.apis.main.app.HwConfigKeeping;
import jp.co.sony.csl.dcoes.apis.main.app.PolicyKeeping;
import jp.co.sony.csl.dcoes.apis.main.app.StateHandling;
import jp.co.sony.csl.dcoes.apis.main.app.mediator.Interlocking;
import jp.co.sony.csl.dcoes.apis.main.app.mediator.util.InterlockUtil;
import jp.co.sony.csl.dcoes.apis.main.evaluation.safety.LocalSafetyEvaluation;
import jp.co.sony.csl.dcoes.apis.main.util.ApisConfig;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorExceptionUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

/**
 * Data acquisition service object Verticle.
 * Launched from the {@link Controller} Verticle.
 * The following types are available, depending on the type of system.
 * - {@link jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.emulator.DcdcEmulatorDataAcquisition}
 * - {@link jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.v1.DcdcV1DataAcquisition}
 * - {@link jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.v2.DcdcV2DataAcquisition}
 * @author OES Project
 *          
 * データ取得サービスの親玉 Verticle.
 * {@link Controller} Verticle から起動される.
 * システムの種類に応じて以下の種類がある.
 * - {@link jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.emulator.DcdcEmulatorDataAcquisition}
 * - {@link jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.v1.DcdcV1DataAcquisition}
 * - {@link jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.v2.DcdcV2DataAcquisition}
 * @author OES Project
 */
public abstract class DataAcquisition extends AbstractVerticle {
	private static final Logger log = LoggerFactory.getLogger(DataAcquisition.class);

	/**
	 * Default duration of data acquisition cycle [ms].
	 * Value: {@value}.
	 *          
	 * データ取得周期のデフォルト値 [ms].
	 * 値は {@value}.
	 */
	private static final Long DEFAULT_DATA_ACQUISITION_PERIOD_MSEC = 5000L;
	/**
	 * Default HTTP request timeout duration [ms].
	 * Value: {@value}.
	 *          
	 * HTTP リクエストのタイムアウト時間のデフォルト値 [ms].
	 * 値は {@value}.
	 */
	private static final Long DEFAULT_REQUEST_TIMEOUT_MSEC = 5000L;
	/**
	 * Default HTTP request retry count.
	 * Value: {@value}.
	 *          
	 * HTTP リクエストのリトライ回数のデフォルト値.
	 * 値は {@value}.
	 */
	private static final Integer DEFAULT_RETRY_LIMIT = 3;

	private static final LocalExclusiveLock exclusiveLock_ = new LocalExclusiveLock(DataAcquisition.class.getName());
	/**
	 * Acquire an exclusive lock.
	 * Results are received with the {@link AsyncResult#result()} method of completionHandler.
	 * @param vertx a vertx object
	 * @param completionHandler the completion handler
	 *          
	 * 排他ロックを獲得する.
	 * completionHandler の {@link AsyncResult#result()} で受け取る.
	 * @param vertx vertx オブジェクト
	 * @param completionHandler the completion handler
	 */
	public static void acquireExclusiveLock(Vertx vertx, Handler<AsyncResult<LocalExclusiveLock.Lock>> completionHandler) {
		exclusiveLock_.acquire(vertx, completionHandler);
	}
	/**
	 * Reset an exclusive lock.
	 * @param vertx a vertx object
	 *          
	 * 排他ロックをリセットする.
	 * @param vertx vertx オブジェクト
	 */
	public static void resetExclusiveLock(Vertx vertx) {
		exclusiveLock_.reset(vertx);
	}

	/**
	 * A cache that retains unit data.
	 *          
	 * ユニットデータを保持しておくキャッシュ.
	 */
	public static final JsonObjectWrapper cache = new JsonObjectWrapper();

	private long dataAcquisitionTimerId_ = 0L;
	private long lastDataAcquisitionMillis_ = 0L;
	private boolean stopped_ = false;

	/**
	 * Called at startup.
	 * Perform various initialization processes.
	 * Launches the {@link io.vertx.core.eventbus.EventBus} service.
	 * Start a timer for periodic acquisition of unit data and cache updates.
	 * @param startFuture {@inheritDoc}
	 * @throws Exception {@inheritDoc}
	 *          
	 * 起動時に呼び出される.
	 * 各種初期化処理を実行する.
	 * {@link io.vertx.core.eventbus.EventBus} サービスを起動する.
	 * 定期的にユニットデータを取得しキャッシュを更新するタイマを起動する.
	 * @param startFuture {@inheritDoc}
	 * @throws Exception {@inheritDoc}
	 */
	@Override public void start(Future<Void> startFuture) throws Exception {
		init(resInit -> {
			if (resInit.succeeded()) {
				startInternalUrgentUnitDataService_(resInternalUrgentUnitData -> {
					if (resInternalUrgentUnitData.succeeded()) {
						startInternalUrgentUnitDeviceStatusService_(resInternalUrgentUnitDeviceStatus -> {
							if (resInternalUrgentUnitDeviceStatus.succeeded()) {
								startResetLocalService_(resResetLocal -> {
									if (resResetLocal.succeeded()) {
										startResetAllService_(resResetAll -> {
											if (resResetAll.succeeded()) {
												dataAcquisitionTimerHandler_(0L);
												if (log.isTraceEnabled()) log.trace("started : " + deploymentID());
												startFuture.complete();
											} else {
												startFuture.fail(resResetAll.cause());
											}
										});
									} else {
										startFuture.fail(resResetLocal.cause());
									}
								});
							} else {
								startFuture.fail(resInternalUrgentUnitDeviceStatus.cause());
							}
						});
					} else {
						startFuture.fail(resInternalUrgentUnitData.cause());
					}
				});
			} else {
				startFuture.fail(resInit.cause());
			}
		});
	}

	/**
	 * Called when stopped.
	 * Set a flag to stop the timer.
	 * @throws Exception {@inheritDoc}
	 *          
	 * 停止時に呼び出される.
	 * タイマを止めるためのフラグを立てる.
	 * @throws Exception {@inheritDoc}
	 */
	@Override public void stop() throws Exception {
		stopped_ = true;
		if (log.isTraceEnabled()) log.trace("stopped : " + deploymentID());
	}

	////

	/**
	 * Initialization.
	 * @param completionHandler the completion handler
	 *          
	 * 初期化.
	 * @param completionHandler the completion handler
	 */
	protected abstract void init(Handler<AsyncResult<Void>> completionHandler);
	/**
	 * Fetch this unit's hardware information.
	 * Receive the results with the {@link AsyncResult#result()} method of completionHandler.
	 * @param completionHandler the completion handler
	 *          
	 * 自ユニットのハードウェア情報を取得する.
	 * completionHandler の {@link AsyncResult#result()} で結果を受け取る.
	 * @param completionHandler the completion handler
	 */
	protected abstract void getData(Handler<AsyncResult<JsonObject>> completionHandler);
	/**
	 * Fetch this unit's device control state.
	 * Receive the results with the {@link AsyncResult#result()} method of completionHandler.
	 * @param completionHandler the completion handler
	 *          
	 * 自ユニットのデバイス制御状態を取得する.
	 * completionHandler の {@link AsyncResult#result()} で結果を受け取る.
	 * @param completionHandler the completion handler
	 */
	protected abstract void getDeviceStatus(Handler<AsyncResult<JsonObject>> completionHandler);
	/**
	 * The device control state is bidirectionally merged with the cached unit data to get the result.
	 * If only the device control state is acquired, then there may be less content than in cases where all the unit data is acquired (depending on the driver).
	 * For this reason, the unit data cache is kept up to date to compensate for missing elements.
	 * @param value control state of merge source device
	 * @return control state of merged devices
	 *          
	 * デバイス制御状態をユニットデータのキャッシュと双方向マージし結果を取得する.
	 * デバイス制御状態のみを取得すると全ユニットデータを取得する場合より内容が少ない場合がある ( ドライバによる ).
	 * なのでユニットデータのキャッシュを最新にしつつ不足要素を補ってもらう.
	 * @param value マージ元デバイス制御状態
	 * @return マージ済デバイス制御状態
	 */
	protected abstract JsonObject mergeDeviceStatus(JsonObject value);

	////

	/**
	 * Launch the {@link io.vertx.core.eventbus.EventBus} service.
	 * Address: {@link ServiceAddress.Controller#urgentUnitData()}
	 * Scope: local
	 * Function: Acquire the unit data of this unit.
	 * 　　   Query the device directly and return fresh data instead of relying on the cache.
	 * Message body: none
	 * Message header: none
	 * Response: unit data [{@link JsonObject}].
	 * 　　　　　   Fails if an error occurs.
	 * @param completionHandler the completion handler
	 *          
	 * {@link io.vertx.core.eventbus.EventBus} サービス起動.
	 * アドレス : {@link ServiceAddress.Controller#urgentUnitData()}
	 * 範囲 : ローカル
	 * 処理 : 自ユニットのユニットデータを取得する.
	 * 　　   キャッシュではなくその場でデバイスに問い合せフレッシュなデータを返す.
	 * メッセージボディ : なし
	 * メッセージヘッダ : なし
	 * レスポンス : ユニットデータ [{@link JsonObject}].
	 * 　　　　　   エラーが起きたら fail.
	 * @param completionHandler the completion handler
	 */
	private void startInternalUrgentUnitDataService_(Handler<AsyncResult<Void>> completionHandler) {
		vertx.eventBus().<Void>localConsumer(ServiceAddress.Controller.urgentUnitData(), req -> {
			getData_(res -> {
				if (res.succeeded()) {
					req.reply(res.result());
				} else {
					req.fail(-1, res.cause().getMessage());
				}
			});
		}).completionHandler(completionHandler);
	}

	/**
	 * Launch the {@link io.vertx.core.eventbus.EventBus} service.
	 * Address: {@link ServiceAddress.Controller#urgentUnitDeviceStatus()}
	 * Scope: local
	 * Function: Fetch this unit's device control state.
	 * 　　   Query the device directly and return fresh data instead of relying on the cache.
	 * Message body: none
	 * Message header: none
	 * Response: device control state [{@link JsonObject}].
	 * 　　　　　   Fails if an error occurs.
	 * @param completionHandler the completion handler
	 *          
	 * {@link io.vertx.core.eventbus.EventBus} サービス起動.
	 * アドレス : {@link ServiceAddress.Controller#urgentUnitDeviceStatus()}
	 * 範囲 : ローカル
	 * 処理 : 自ユニットのデバイス制御状態を取得する.
	 * 　　   キャッシュではなくその場でデバイスに問い合せフレッシュなデータを返す.
	 * メッセージボディ : なし
	 * メッセージヘッダ : なし
	 * レスポンス : デバイス制御状態 [{@link JsonObject}].
	 * 　　　　　   エラーが起きたら fail.
	 * @param completionHandler the completion handler
	 */
	private void startInternalUrgentUnitDeviceStatusService_(Handler<AsyncResult<Void>> completionHandler) {
		vertx.eventBus().<Void>localConsumer(ServiceAddress.Controller.urgentUnitDeviceStatus(), req -> {
			getDeviceStatus_(res -> {
				if (res.succeeded()) {
					req.reply(res.result());
				} else {
					req.fail(-1, res.cause().getMessage());
				}
			});
		}).completionHandler(completionHandler);
	}

	/**
	 * Launch the {@link io.vertx.core.eventbus.EventBus} service.
	 * Address: {@link ServiceAddress#resetLocal()}
	 * Scope: local
	 * Function: reset this unit.
	 * Message body: none
	 * Message header: none
	 * Response: this unit's ID [{@link String}].
	 * 　　　　　Fails if an error occurs.
	 * @param completionHandler the completion handler
	 *          
	 * {@link io.vertx.core.eventbus.EventBus} サービス起動.
	 * アドレス : {@link ServiceAddress#resetLocal()}
	 * 範囲 : ローカル
	 * 処理 : 自ユニットをリセットする.
	 * メッセージボディ : なし
	 * メッセージヘッダ : なし
	 * レスポンス : 自ユニットの ID [{@link String}].
	 * 　　　　　   エラーが起きたら fail.
	 * @param completionHandler the completion handler
	 */
	private void startResetLocalService_(Handler<AsyncResult<Void>> completionHandler) {
		vertx.eventBus().<Void>localConsumer(ServiceAddress.resetLocal(), req -> {
			doReset_(req);
		}).completionHandler(completionHandler);
	}
	/**
	 * Launch the {@link io.vertx.core.eventbus.EventBus} service.
	 * Address: {@link ServiceAddress#resetAll()}
	 * Scope: global
	 * Function: Reset all units and all programs that participate in a cluster.
	 * Message body: none
	 * Message header: none
	 * Response: this unit's ID [{@link String}].
	 * 　　　　　Fails if an error occurs.
	 * @param completionHandler the completion handler
	 *          
	 * {@link io.vertx.core.eventbus.EventBus} サービス起動.
	 * アドレス : {@link ServiceAddress#resetAll()}
	 * 範囲 : グローバル
	 * 処理 : 全ユニットおよびクラスタに参加する全プログラムをリセットする.
	 * メッセージボディ : なし
	 * メッセージヘッダ : なし
	 * レスポンス : 自ユニットの ID [{@link String}].
	 * 　　　　　   エラーが起きたら fail.
	 * @param completionHandler the completion handler
	 */
	private void startResetAllService_(Handler<AsyncResult<Void>> completionHandler) {
		vertx.eventBus().<Void>consumer(ServiceAddress.resetAll(), req -> {
			doReset_(req);
		}).completionHandler(completionHandler);
	}
	/**
	 * Actual implementation of reset process.
	 * @param message the message requiring a reply
	 *          
	 * リセット処理の実実装.
	 * @param message : 返信対象メッセージ
	 */
	private void doReset_(Message<?> message) {
		resetExclusiveLock(vertx);
		message.reply(ApisConfig.unitId());
	}

	/**
	 * Data acquisition timer setting.
	 * The timeout duration is {@code POLICY.controller.dataAcquisitionPeriodMsec} (default: {@link #DEFAULT_DATA_ACQUISITION_PERIOD_MSEC}).
	 *          
	 * データ取得タイマ設定.
	 * 待ち時間は {@code POLICY.controller.dataAcquisitionPeriodMsec} ( デフォルト値 {@link #DEFAULT_DATA_ACQUISITION_PERIOD_MSEC} ).
	 */
	private void setDataAcquisitionTimer_() {
		Long delay = PolicyKeeping.cache().getLong(DEFAULT_DATA_ACQUISITION_PERIOD_MSEC, "controller", "dataAcquisitionPeriodMsec");
		setDataAcquisitionTimer_(delay);
	}
	/**
	 * Data acquisition timer setting.
	 * @param delay cycle duration [ms]
	 *          
	 * データ取得タイマ設定.
	 * @param delay 周期 [ms]
	 */
	private void setDataAcquisitionTimer_(long delay) {
		dataAcquisitionTimerId_ = vertx.setTimer(delay, this::dataAcquisitionTimerHandler_);
	}
	/**
	 * Data acquisition timer processing.
	 * @param timerId timer ID
	 *          
	 * データ取得タイマ処理.
	 * @param timerId タイマ ID
	 */
	private void dataAcquisitionTimerHandler_(Long timerId) {
		if (stopped_) return;
		if (null == timerId || timerId.longValue() != dataAcquisitionTimerId_) {
			ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, "illegal timerId : " + timerId + ", dataAcquisitionTimerId_ : " + dataAcquisitionTimerId_);
			return;
		}
		// I guess periodic data acquisition is OK, so...
		// 定期的なデータ取得はまあ適当でよいので...
		if (lastDataAcquisitionMillis_ != 0L) {
			long millisAfterLastDataAcquisition = (System.currentTimeMillis() - lastDataAcquisitionMillis_);
			Long period = PolicyKeeping.cache().getLong(DEFAULT_DATA_ACQUISITION_PERIOD_MSEC, "controller", "dataAcquisitionPeriodMsec");
			if (millisAfterLastDataAcquisition < period) {
				// If the timer cycle time has not elapsed since the last data acquisition, reset the timer to the time difference and finish without doing anything
				// 前回のデータ取得からタイマ周期時間経過していない場合は差分時間でタイマを再セットし何もせず終わる
				setDataAcquisitionTimer_(period - millisAfterLastDataAcquisition);
				return;
			}
		}
		// Perform device control and exclusion control
		// デバイス制御と排他制御する
		acquireExclusiveLock(vertx, resExclusiveLock -> {
			if (resExclusiveLock.succeeded()) {
				LocalExclusiveLock.Lock lock = resExclusiveLock.result();
				doTimerWithExclusiveLock_(resDoTimerWithExclusiveLock -> {
					lock.release();
					setDataAcquisitionTimer_();
				});
			} else {
				setDataAcquisitionTimer_();
				ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, resExclusiveLock.cause());
			}
		});
	}
	private void doTimerWithExclusiveLock_(Handler<AsyncResult<JsonObject>> completionHandler) {
		getData_(completionHandler);
	}

	////

	////

	/**
	 * Actual implementation of unit data acquisition process.
	 * Receive the results with the {@link AsyncResult#result()} method of completionHandler.
	 * @param completionHandler the completion handler
	 *          
	 * ユニットデータ取得処理の実実装.
	 * completionHandler の {@link AsyncResult#result()} で結果を受け取る.
	 * @param completionHandler the completion handler
	 */
	private void getData_(Handler<AsyncResult<JsonObject>> completionHandler) {
		                                      // Record the start time of the data acquisition process
		long ts = System.currentTimeMillis(); // データ取得処理開始時刻を記録しておく
		Future<JsonObject> getDataFuture = Future.future();
		Future<JsonObject> getOesunitFuture = Future.future();
		                        // Call the actual implementation of the subclass for each driver
		getData(getDataFuture); // ドライバごとのサブクラスの実実装を呼ぶ
		getOesunit_(getOesunitFuture);
		CompositeFuture.<JsonObject, JsonObject>all(getDataFuture, getOesunitFuture).setHandler(ar -> {
			if (ar.succeeded()) {
				                                 // Update the data acquisition process start time
				lastDataAcquisitionMillis_ = ts; // データ取得処理開始時刻を更新する
				JsonObject result = ar.result().resultAt(0);
				JsonObject oesunit = ar.result().resultAt(1);
				result.put("oesunit", oesunit);
				getApisData_(result, res -> {
					if (res.succeeded()) {
						JsonObject apis = res.result();
						result.put("apis", apis);
					}
					// Attributes for the old BUDO system
					// いにしえの BUDO システム用の属性
					if ("autonomous".equals(JsonObjectUtil.getString(result, "apis", "operation_mode", "effective"))) {
						JsonObjectUtil.put(result, "1", "oesunit", "budo");
					} else {
						JsonObjectUtil.put(result, "0", "oesunit", "budo");
					}
					                             // Update the cache
					cache.setJsonObject(result); // キャッシュを更新
					LocalSafetyEvaluation.check(vertx, PolicyKeeping.cache().jsonObject(), result, resSafetyEvaluation -> {
						completionHandler.handle(Future.succeededFuture(result));
					});
				});
			} else {
				completionHandler.handle(Future.failedFuture(ar.cause()));
			}
		});
	}

	/**
	 * This information is also mostly for the old BUDO system.
	 * Receive the results with the {@link AsyncResult#result()} method of completionHandler.
	 * @param completionHandler the completion handler
	 *          
	 * このへんもいにしえの BUDO システム用の情報がメイン.
	 * completionHandler の {@link AsyncResult#result()} で結果を受け取る.
	 * @param completionHandler the completion handler
	 */
	private void getOesunit_(Handler<AsyncResult<JsonObject>> completionHandler) {
		JsonObject result = new JsonObject();
		doConfig_(result);
		doNetwork_(result);
		completionHandler.handle(Future.succeededFuture(result));
	}
	private void doConfig_(JsonObject oesunit) {
		oesunit.put("communityId", VertxConfig.communityId());
		oesunit.put("clusterId", VertxConfig.clusterId());
		oesunit.put("id", ApisConfig.unitId());
		oesunit.put("display", ApisConfig.unitName());
		oesunit.put("sn", ApisConfig.serialNumber());
		oesunit.put("budo", "0");
		if (oesunit.getValue("communityId") == null) oesunit.put("communityId", "NA");
		if (oesunit.getValue("clusterId") == null) oesunit.put("clusterId", "NA");
		if (oesunit.getValue("display") == null) oesunit.put("display", "auto" + ApisConfig.unitId());
		if (oesunit.getValue("sn") == null) oesunit.put("sn", "NA");
	}
	private void doNetwork_(JsonObject oesunit) {
		oesunit.put("ip", "NA");
		oesunit.put("ipv6_ll", "NA");
		oesunit.put("ipv6_g", "NA");
		oesunit.put("mac", "NA");
		try {
			Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
			if (nis != null) {
				while (nis.hasMoreElements()) {
					NetworkInterface ni = nis.nextElement();
					if (!ni.isLoopback() && ni.isUp()) {
						if (ni.getName() != null && (ni.getName().startsWith("e") || ni.getName().startsWith("w"))) {
							byte[] ha = ni.getHardwareAddress();
							if (ha != null) {
								String ipv4 = null;
								String ipv6 = null;
								String ipv6LinkLocal = null;
								Enumeration<InetAddress> ias = ni.getInetAddresses();
								if (ias != null) {
									while (ias.hasMoreElements()) {
										InetAddress ia = ias.nextElement();
										if (ia instanceof Inet4Address) {
											ipv4 = ia.getHostAddress();
										} else if (ia instanceof Inet6Address) {
											if (ia.isLinkLocalAddress()) {
												ipv6LinkLocal = ia.getHostAddress();
											} else {
												ipv6 = ia.getHostAddress();
											}
										}
									}
								}
								if (ipv4 != null || ipv6 != null || ipv6LinkLocal != null) {
									if (ipv4 != null) oesunit.put("ip", ipv4);
									if (ipv6LinkLocal != null) oesunit.put("ipv6_ll", ipv6LinkLocal);
									if (ipv6 != null) oesunit.put("ipv6_g", ipv6);
									StringBuilder mac = new StringBuilder();
									for (int i = 0; i < ha.length; i++) {
										if (0 < i) mac.append(':');
										mac.append(String.format("%02x", ha[i]));
									}
									oesunit.put("mac", String.valueOf(mac));
								}
							}
						}
					}
				}
			}
		} catch (SocketException e) {
			ErrorUtil.report(vertx, Error.Category.HARDWARE, Error.Extent.LOCAL, Error.Level.ERROR, e);
		}
	}

	/**
	 * Various attributes associated with APIS.
	 * Receive the results with the {@link AsyncResult#result()} method of completionHandler.
	 * @param data unit data. Needs {@code rsoc} in order to calculate {@code remaining_capacity_wh}.
	 * @param completionHandler the completion handler
	 *          
	 * APIS まわりの諸々の属性.
	 * completionHandler の {@link AsyncResult#result()} で結果を受け取る.
	 * @param data ユニットデータ. {@code remaining_capacity_wh} 算出のために {@code rsoc} が必要.
	 * @param completionHandler the completion handler
	 */
	private void getApisData_(JsonObject data, Handler<AsyncResult<JsonObject>> completionHandler) {
		JsonObject result = new JsonObject();
		result.put("version", AbstractStarter.APIS_VERSION);
		Float batteryNominalCapacityWh = HwConfigKeeping.batteryNominalCapacityWh();
		if (batteryNominalCapacityWh != null) {
			Float rsoc = JsonObjectUtil.getFloat(data, "battery", "rsoc");
			if (rsoc != null) {
				int remainingWh = (int) (batteryNominalCapacityWh.floatValue() * rsoc.floatValue() / 100.0);
				result.put("remaining_capacity_wh", remainingWh);
			} else {
				ErrorUtil.report(vertx, Error.Category.HARDWARE, Error.Extent.LOCAL, Error.Level.WARN, "no battery.rsoc value in unit data : " + cache.jsonObject());
			}
		} else {
			ErrorUtil.report(vertx, Error.Category.USER, Error.Extent.LOCAL, Error.Level.WARN, "no batteryNominalCapacityWh value in hwConfig : " + HwConfigKeeping.cache.jsonObject());
		}
		int dealInterlockCapacity = Interlocking.dealInterlockCapacity(vertx);
		result.put("deal_interlock_capacity", dealInterlockCapacity);
		Future<String> getGridMasterUnitIdFuture = Future.future();
		Future<Collection<String>> getDealIdsFuture = Future.future();
		Future<JsonObject> getOperationModesFuture = Future.future();
		InterlockUtil.getGridMasterUnitId(vertx, getGridMasterUnitIdFuture);
		InterlockUtil.getDealIds(vertx, getDealIdsFuture);
		StateHandling.operationModes(vertx, getOperationModesFuture);
		CompositeFuture.<String, Collection<String>, JsonObject>all(getGridMasterUnitIdFuture, getDealIdsFuture, getOperationModesFuture).setHandler(ar -> {
			if (ar.succeeded()) {
				String gridMasterUnitId = ar.result().resultAt(0);
				Collection<String> dealIds = ar.result().resultAt(1);
				JsonObject operationModes = ar.result().resultAt(2);
				if (ApisConfig.unitId().equals(gridMasterUnitId)) {
					result.put("is_grid_master", Boolean.TRUE);
				}
				if (dealIds != null && !dealIds.isEmpty()) {
					result.put("deal_id_list", dealIds);
				}
				result.put("operation_mode", operationModes);
				completionHandler.handle(Future.succeededFuture(result));
			} else {
				ErrorExceptionUtil.reportIfNeedAndFail(vertx, ar.cause(), completionHandler);
			}
		});
	}

	/**
	 * Actual implementation of device control state acquisition process.
	 * Receive the results with the {@link AsyncResult#result()} method of completionHandler.
	 * @param completionHandler the completion handler
	 *          
	 * デバイス制御状態の取得処理の実実装.
	 * completionHandler の {@link AsyncResult#result()} で結果を受け取る.
	 * @param completionHandler the completion handler
	 */
	private void getDeviceStatus_(Handler<AsyncResult<JsonObject>> completionHandler) {
		// Call the actual implementation of the subclass for each driver
		// ドライバごとのサブクラスの実実装を呼ぶ
		getDeviceStatus(resGetdDeviceStatus -> {
			if (resGetdDeviceStatus.succeeded()) {
				JsonObject result = resGetdDeviceStatus.result();
				                                    // Bidirectionally merge with unit data cache
				result = mergeDeviceStatus(result); // ユニットデータのキャッシュと双方向マージする
				completionHandler.handle(Future.succeededFuture(result));
			} else {
				completionHandler.handle(resGetdDeviceStatus);
			}
		});
	}

	/**
	 * Send out an external HTTP GET request and return the response.
	 * Receive the results with the {@link AsyncResult#result()} method of completionHandler.
	 * @param client an httpclient object
	 * @param uri the request URI
	 * @param completionHandler the completion handler
	 *          
	 * HTTP GET で外部にリクエストを送信しレスポンスを返す.
	 * completionHandler の {@link AsyncResult#result()} で結果を受け取る.
	 * @param client httpclient オブジェクト
	 * @param uri リクエストの URI
	 * @param completionHandler the completion handler
	 */
	protected void send(HttpClient client, String uri, Handler<AsyncResult<JsonObject>> completionHandler) {
		// Number of retries: POLICY.controller.retryLimit [{@link Integer}]
		// リトライ回数 : POLICY.controller.retryLimit [{@link Integer}]
		Integer retryLimit = PolicyKeeping.cache().getInteger(DEFAULT_RETRY_LIMIT, "controller", "retryLimit");
		new Sender_(retryLimit, client, uri).execute_(completionHandler);
	}

	////

	/**
	 * Issues an HTTP GET request to the specified URL.
	 * If the request fails, retry up to the specified number of times.
	 * @author OES Project
	 *          
	 * 指定された URL に対し HTTP GET する.
	 * 失敗しても指定回数リトライする.
	 * @author OES Project
	 */
	private class Sender_ {
		private int retryLimit_;
		private HttpClient client_;
		private String uri_;
		private boolean completed_ = false;
		private Sender_(Integer retryLimit, HttpClient client, String uri) {
			retryLimit_ = retryLimit;
			client_ = client;
			uri_ = uri;
		}
		/**
		 * Perform HTTP GET processing.
		 * Duplicate results are sometimes returned (maybe due to poor implementation). Block duplicates here.
		 * Receive the results with the {@link AsyncResult#result()} method of completionHandler.
		 * @param completionHandler the completion handler
		 *          
		 * HTTP GET 処理実行.
		 * ( 実装がまずいのか ) 二度結果が返ってくることがあるためここでブロックする.
		 * completionHandler の {@link AsyncResult#result()} で結果を受け取る.
		 * @param completionHandler the completion handler
		 */
		private void execute_(Handler<AsyncResult<JsonObject>> completionHandler) {
			executeWithRetry_(r -> {
				if (!completed_) {
					completed_ = true;
					completionHandler.handle(r);
				} else {
					ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, "executeWithRetry_() result returned more than once : " + r);
				}
			});
		}
		/**
		 * Perform HTTP GET processing.
		 * Retry if fails.
		 * @param completionHandler the completion handler
		 *          
		 * HTTP GET 処理実行.
		 * 失敗してもリトライする.
		 * @param completionHandler the completion handler
		 */
		private void executeWithRetry_(Handler<AsyncResult<JsonObject>> completionHandler) {
			send_(client_, uri_, r -> {
				if (r.succeeded()) {
					completionHandler.handle(r);
				} else {
					if (0 < --retryLimit_) {
						ErrorUtil.report(vertx, Error.Category.HARDWARE, Error.Extent.LOCAL, Error.Level.WARN, "Communication failed with Device Driver", r.cause());
						executeWithRetry_(completionHandler);
					} else {
						ErrorUtil.reportAndFail(vertx, Error.Category.HARDWARE, Error.Extent.LOCAL, Error.Level.ERROR, "Communication failed with Device Driver", r.cause(), completionHandler);
					}
				}
			});
		}
		/**
		 * Perform HTTP GET processing.
		 * HTTP timeout is {@code POLICY.controller.requestTimeoutMsec} (default: {@link #DEFAULT_REQUEST_TIMEOUT_MSEC}).
		 * Receive the results with the {@link AsyncResult#result()} method of completionHandler.
		 * @param client an httpclient object
		 * @param uri the URI to access
		 * @param completionHandler the completion handler
		 *          
		 * HTTP GET 処理実行.
		 * HTTP タイムアウト は {@code POLICY.controller.requestTimeoutMsec} ( デフォルト値 {@link #DEFAULT_REQUEST_TIMEOUT_MSEC} ).
		 * completionHandler の {@link AsyncResult#result()} で結果を受け取る.
		 * @param client httpclient オブジェクト
		 * @param uri アクセス URI
		 * @param completionHandler the completion handler

		 * NOTE: The method {@code HttpClient.get(String, Handler<HttpClientResponse>)} used below is deprecated
 		 * in newer versions of Vert.x. However, this project currently targets Vert.x 3.5.3, where this method is 
 		 * the supported and appropriate way to make simple HTTP GET requests.
 		 *
  		 * It is acceptable to continue using this method for now because:
 		 * - The alternative (WebClient) was only introduced in Vert.x 3.5.4+
 		 * - Upgrading Vert.x is not feasible at this time due to compatibility constraints
 		 *
 		 * Refactoring should be planned once the project is upgraded to a newer Vert.x version (3.6+ or 4.x)
 		 * where modern APIs are available.
 		 */
		@SuppressWarnings("deprecation")
		private void send_(HttpClient client, String uri, Handler<AsyncResult<JsonObject>> completionHandler) {
			if (log.isInfoEnabled()) log.info("uri : " + uri);
			Long requestTimeoutMsec = PolicyKeeping.cache().getLong(DEFAULT_REQUEST_TIMEOUT_MSEC, "controller", "requestTimeoutMsec");
			client.get(uri, resGet -> {
				if (200 == resGet.statusCode()) {
					resGet.bodyHandler(body -> {
						JsonObjectUtil.toJsonObject(body, completionHandler);
					}).exceptionHandler(t -> {
						completionHandler.handle(Future.failedFuture(t));
					});
				} else {
					resGet.bodyHandler(error -> {
						completionHandler.handle(Future.failedFuture("http request failed : " + resGet.statusCode() + " : " + resGet.statusMessage() + " : " + error));
					}).exceptionHandler(t -> {
						completionHandler.handle(Future.failedFuture("http request failed : " + resGet.statusCode() + " : " + resGet.statusMessage() + " : " + t));
					});
				}
			}).setTimeout(requestTimeoutMsec).exceptionHandler(t -> {
				completionHandler.handle(Future.failedFuture(t));
			}).end();
		}
	}

}
