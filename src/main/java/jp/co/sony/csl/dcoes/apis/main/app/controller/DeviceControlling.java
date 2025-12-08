package jp.co.sony.csl.dcoes.apis.main.app.controller;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.ServiceAddress;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectUtil;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.LocalExclusiveLock;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.ReplyFailureUtil;
import jp.co.sony.csl.dcoes.apis.main.app.PolicyKeeping;
import jp.co.sony.csl.dcoes.apis.main.app.mediator.util.InterlockUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ApisConfig;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorExceptionUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

/**
 * Device control service object Verticle.
 * Launched from the {@link Controller} Verticle.
 * The following types are available, depending on the type of system.
 * - {@link jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.emulator.DcdcEmulatorDeviceControlling}
 * - {@link jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.v1.DcdcV1DeviceControlling}
 * - {@link jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.v2.DcdcV2DeviceControlling}
 * @author OES Project
 *          
 * デバイス制御サービスの親玉 Verticle.
 * {@link Controller} Verticle から起動される.
 * システムの種類に応じて以下の種類がある.
 * - {@link jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.emulator.DcdcEmulatorDeviceControlling}
 * - {@link jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.v1.DcdcV1DeviceControlling}
 * - {@link jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.v2.DcdcV2DeviceControlling}
 * @author OES Project
 */
public abstract class DeviceControlling extends AbstractVerticle {
	private static final Logger log = LoggerFactory.getLogger(DeviceControlling.class);

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

	private static boolean ignoreDynamicSafetyCheck_ = false;
	/**
	 * Find out whether or not dynamic safety checks should be skipped.
	 * TODO: Shouldn't this be "skip" instead of "ignore"?
	 * @return True if dynamic safety checks are skipped
	 *          
	 * 動的安全性チェックの実行をスキップするか否かを取得する.
	 * TODO : ignore じゃなくて skip ではないだろうか...
	 * @return 動的安全性チェックをスキップするなら true
	 */
	public static boolean ignoreDynamicSafetyCheck() { return ignoreDynamicSafetyCheck_; }
	/**
	 * Specifies whether or not to skip the dynamic safety checks.
	 * @param value skip if true; don't skip if false
	 *          
	 * 動的安全性チェックの実行をスキップするか否かを指定する.
	 * @param value true ならスキップ, false なら実行する
	 */
	public static void ignoreDynamicSafetyCheck(boolean value) {
		if (log.isInfoEnabled()) {
			if (ignoreDynamicSafetyCheck_ != value) {
				if (value) {
					log.info("begin ignoring dynamic safety check");
				} else {
					log.info("end ignoring dynamic safety check");
				}
			}
		}
		ignoreDynamicSafetyCheck_ = value;
	}

	/**
	 * Called at startup.
	 * Performs initialization processing.
	 * Launches the {@link io.vertx.core.eventbus.EventBus} service.
	 * @param startFuture {@inheritDoc}
	 * @throws Exception {@inheritDoc}
	 *          
	 * 起動時に呼び出される.
	 * 初期化処理を実行する.
	 * {@link io.vertx.core.eventbus.EventBus} サービスを起動する.
	 * @param startFuture {@inheritDoc}
	 * @throws Exception {@inheritDoc}
	 */
	@Override public void start(Promise<Void> startPromise) throws Exception {
		init(resInit -> {
			if (resInit.succeeded()) {
				startLocalStopService_(resLocalStop -> {
					if (resLocalStop.succeeded()) {
						startScramService_(resScram -> {
							if (resScram.succeeded()) {
								startDeviceControllingService_(resDeviceControlling -> {
									if (resDeviceControlling.succeeded()) {
										if (log.isTraceEnabled()) log.trace("started : " + deploymentID());
										startPromise.complete();
									} else {
										startPromise.fail(resDeviceControlling.cause());
									}
								});
							} else {
								startPromise.fail(resScram.cause());
							}
						});
					} else {
						startPromise.fail(resLocalStop.cause());
					}
				});
			} else {
				startPromise.fail(resInit.cause());
			}
		});
	}

	/**
	 * Called when stopped.
	 * Stop this unit's device.
	 * @throws Exception {@inheritDoc}
	 *          
	 * 停止時に呼び出される.
	 * 自ユニットのデバイスを停止する.
	 * @throws Exception {@inheritDoc}
	 */
	@Override public void stop(Promise<Void> stopPromise) throws Exception {
		vertx.eventBus().request(ServiceAddress.Controller.stopLocal(), null, repStopLocal -> {
			if (repStopLocal.succeeded()) {
				// nop
			} else {
				if (ReplyFailureUtil.isRecipientFailure(repStopLocal)) {
					// nop
				} else {
					ErrorUtil.report(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR, "Communication failed on EventBus", repStopLocal.cause());
				}
			}
			if (log.isTraceEnabled()) log.trace("stopped : " + deploymentID());
			stopPromise.complete();
		});
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
	 * Stop this unit's device.
	 * Receive the device control state after running the {@link AsyncResult#result()} method of completionHandler.
	 * @param completionHandler the completion handler
	 *          
	 * 自ユニットのデバイスを停止する.
	 * completionHandler の {@link AsyncResult#result()} で操作後のデバイス制御状態を受け取る.
	 * @param completionHandler the completion handler
	 */
	protected abstract void doLocalStopWithExclusiveLock(Handler<AsyncResult<JsonObject>> completionHandler);
	/**
	 * Stop this unit's device when the SCRAM command is received.
	 * Does nothing if the present mode is voltage reference and {@code excludeVoltageReference} is {@code true}.
	 * Receive the device control state after running the {@link AsyncResult#result()} method of completionHandler.
	 * @param excludeVoltageReference a flag that prevents stopping when currently in the voltage reference mode
	 * @param completionHandler the completion handler
	 *          
	 * SCRAM 命令受信時に自ユニットのデバイスを停止する.
	 * 現在のモードが電圧リファレンスで {@code excludeVoltageReference} が {@code true} なら何もしない.
	 * completionHandler の {@link AsyncResult#result()} で操作後のデバイス制御状態を受け取る.
	 * @param excludeVoltageReference 現在のモードが電圧リファレンスモードの場合に停止しないフラグ
	 * @param completionHandler the completion handler
	 */
	protected abstract void doScramWithExclusiveLock(boolean excludeVoltageReference, Handler<AsyncResult<JsonObject>> completionHandler);
	/**
	 * Control this unit's devices.
	 * Receive the device control state after running the {@link AsyncResult#result()} method of completionHandler.
	 * @param operation processing details
	 * @param completionHandler the completion handler
	 *          
	 * 自ユニットのデバイスを制御する.
	 * completionHandler の {@link AsyncResult#result()} で操作後のデバイス制御状態を受け取る.
	 * @param operation 処理内容
	 * @param completionHandler the completion handler
	 */
	protected abstract void doDeviceControllingWithExclusiveLock(JsonObject operation, Handler<AsyncResult<JsonObject>> completionHandler);
	/**
	 * The device control state is bidirectionally merged with the cached unit data to get the result.
	 * The device control processing response may sometimes contain less than the entire unit data (depending on the driver).
	 * For this reason, the unit data cache is kept up to date to compensate for missing elements.
	 * @param value control state of merge source device
	 * @return control state of merged devices
	 *          
	 * デバイス制御状態をユニットデータのキャッシュと双方向マージし結果を取得する.
	 * デバイス制御処理のレスポンスはユニットデータ中のそれより内容が少ない場合がある ( ドライバによる ).
	 * なのでユニットデータのキャッシュを最新にしつつ不足要素を補ってもらう.
	 * @param value マージ元デバイス制御状態
	 * @return マージ済デバイス制御状態
	 */
	protected abstract JsonObject mergeDeviceStatus(JsonObject value);

	////

	/**
	 * Launch the {@link io.vertx.core.eventbus.EventBus} service.
	 * Address: {@link ServiceAddress.Controller#stopLocal()}
	 * Scope: local
	 * Function: Stop this unit's devices.
	 * Message body: none
	 * Message header: none
	 * Response: device control state [{@link JsonObject}].
	 * 　　　　　   Fails if an error occurs.
	 * @param completionHandler the completion handler
	 *          
	 * {@link io.vertx.core.eventbus.EventBus} サービス起動.
	 * アドレス : {@link ServiceAddress.Controller#stopLocal()}
	 * 範囲 : ローカル
	 * 処理 : 自ユニットのデバイスを停止する.
	 * メッセージボディ : なし
	 * メッセージヘッダ : なし
	 * レスポンス : デバイス制御状態 [{@link JsonObject}].
	 * 　　　　　   エラーが起きたら fail.
	 * @param completionHandler the completion handler
	 */
	private void startLocalStopService_(Handler<AsyncResult<Void>> completionHandler) {
		vertx.eventBus().<Void>localConsumer(ServiceAddress.Controller.stopLocal(), req -> {
			if (log.isInfoEnabled()) log.info("STOP LOCAL command received");
			if (log.isInfoEnabled()) log.info("( do without exclusive lock !!! )");
			// Since this is mainly called for error handling, complex exclusion control is passed through
			// 主にエラー処理で呼ばれるためややこしい排他制御はスルーする
//			DataAcquisition.acquireExclusiveLock(vertx, resExclusiveLock -> {
//				if (resExclusiveLock.succeeded()) {
//					LocalExclusiveLock.Lock lock = resExclusiveLock.result();
					doLocalStopWithExclusiveLock(resDoLocalStopWithExclusiveLock -> {
//						lock.release();
						if (resDoLocalStopWithExclusiveLock.succeeded()) {
							req.reply(resDoLocalStopWithExclusiveLock.result());
						} else {
							req.fail(-1, resDoLocalStopWithExclusiveLock.cause().getMessage());
						}
					});
//				} else {
//					ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, resExclusiveLock.cause(), req);
//				}
//			});
		}).completionHandler(completionHandler);
	}

	/**
	 * Launch the {@link io.vertx.core.eventbus.EventBus} service.
	 * Address: {@link ServiceAddress.Controller#stopLocal()}
	 * Scope: global
	 * Function: Emergency stop the devices of all units.
	 * 　　   GridMaster interlock not required for emergency processing.
	 * Message body: none
	 * Message header:
	 * 　　　　　　　　   - {@code "excludeVoltageReference"}: Voltage reference exclusion flag
	 * 　　　　　　　　     - {@code "true"}: Pass through if this unit is a voltage reference
	 * 　　　　　　　　     - {@code "false"}: Send a stop command even if this unit is a voltage reference
	 * Response: device control state [{@link JsonObject}].
	 * 　　　　　   Fails if an error occurs.
	 * @param completionHandler the completion handler
	 *          
	 * {@link io.vertx.core.eventbus.EventBus} サービス起動.
	 * アドレス : {@link ServiceAddress.Controller#stopLocal()}
	 * 範囲 : グローバル
	 * 処理 : 全ユニットのデバイスを緊急停止する.
	 * 　　   緊急処理のため GridMaster インタロックは不要.
	 * メッセージボディ : なし
	 * メッセージヘッダ :
	 * 　　　　　　　　   - {@code "excludeVoltageReference"} : 電圧リファレンス除外フラグ
	 * 　　　　　　　　     - {@code "true"} : 自ユニットが電圧リファレンスならスルーする
	 * 　　　　　　　　     - {@code "false"} : 自ユニットが電圧リファレンスでも停止命令を送信する
	 * レスポンス : デバイス制御状態 [{@link JsonObject}].
	 * 　　　　　   エラーが起きたら fail.
	 * @param completionHandler the completion handler
	 */
	private void startScramService_(Handler<AsyncResult<Void>> completionHandler) {
		vertx.eventBus().<Void>consumer(ServiceAddress.Controller.scram(), req -> {
			// Since this is called during error handling, do not check the GridMaster interlock
			// エラー処理で呼ばれるため GridMaster インタロックを確認しない
//			checkGridMasterInterlock_(req, resCheckGridMasterInterlock -> {
//				if (resCheckGridMasterInterlock.succeeded()) {
					if (log.isInfoEnabled()) log.info("SCRAM command received");
					if (log.isInfoEnabled()) log.info("( do without exclusive lock !!! )");
					// Since this is called for error handling, complex exclusion control is passed through
					// エラー処理で呼ばれるためややこしい排他制御はスルーする
//					DataAcquisition.acquireExclusiveLock(vertx, resExclusiveLock -> {
//						if (resExclusiveLock.succeeded()) {
//							LocalExclusiveLock.Lock lock = resExclusiveLock.result();
							boolean excludeVoltageReference = Boolean.valueOf(req.headers().get("excludeVoltageReference"));
							doScramWithExclusiveLock(excludeVoltageReference, resDoScramWithExclusiveLock -> {
//								lock.release();
								if (resDoScramWithExclusiveLock.succeeded()) {
									req.reply(resDoScramWithExclusiveLock.result());
								} else {
									req.fail(-1, resDoScramWithExclusiveLock.cause().getMessage());
								}
							});
//						} else {
//							ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, resExclusiveLock.cause(), req);
//						}
//					});
//				} else {
//					req.fail(-1, resCheckGridMasterInterlock.cause().getMessage());
//				}
//			});
		}).completionHandler(completionHandler);
	}

	/**
	 * Launch the {@link io.vertx.core.eventbus.EventBus} service.
	 * Address: {@link ServiceAddress.Controller#deviceControlling(String)}
	 * Scope: global
	 * Function: Control the devices of a unit specified by ID.
	 * 　　   The control details are sent in the message body.
	 * 　　   A gridMasterUnitId must be specified in the header, and must match the GridMaster interlock value.
	 * 　　   Specific processing is implemented in child classes.
	 * Message body: commands and parameters [{@link JsonObject}]
	 * Message header:
	 * 　　　　　　　　   - {@code "gridMasterUnitId"}: GridMaster unit ID
	 * Response: device control state [{@link JsonObject}].
	 * 　　　　　   Fails if an error occurs.
	 * @param completionHandler the completion handler
	 *          
	 * {@link io.vertx.core.eventbus.EventBus} サービス起動.
	 * アドレス : {@link ServiceAddress.Controller#deviceControlling(String)}
	 * 範囲 : グローバル
	 * 処理 : ID で指定したユニットのデバイスを制御する.
	 * 　　   制御の内容はメッセージボディで送る.
	 * 　　   ヘッダに gridMasterUnitId 指定が必要であり GridMaster インタロック値と一致する必要がある.
	 * 　　   具体的な処理は子クラスで実装する.
	 * メッセージボディ : コマンドおよびパラメタ [{@link JsonObject}]
	 * メッセージヘッダ :
	 * 　　　　　　　　   - {@code "gridMasterUnitId"} : GridMaster ユニット ID
	 * レスポンス : デバイス制御状態 [{@link JsonObject}].
	 * 　　　　　   エラーが起きたら fail.
	 * @param completionHandler the completion handler
	 */
	private void startDeviceControllingService_(Handler<AsyncResult<Void>> completionHandler) {
		vertx.eventBus().<JsonObject>consumer(ServiceAddress.Controller.deviceControlling(ApisConfig.unitId()), req -> {
			// Check the GridMaster interlock
			// GridMaster インタロックを確認する
			checkGridMasterInterlock_(req, resCheckGridMasterInterlock -> {
				if (resCheckGridMasterInterlock.succeeded()) {
					DataAcquisition.acquireExclusiveLock(vertx, resExclusiveLock -> {
						if (resExclusiveLock.succeeded()) {
							LocalExclusiveLock.Lock lock = resExclusiveLock.result();
							doDeviceControllingWithExclusiveLock(req.body(), resDoDeviceControllingWithExclusiveLock -> {
								lock.release();
								if (resDoDeviceControllingWithExclusiveLock.succeeded()) {
									req.reply(resDoDeviceControllingWithExclusiveLock.result());
								} else {
									req.fail(-1, resDoDeviceControllingWithExclusiveLock.cause().getMessage());
								}
							});
						} else {
							ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, resExclusiveLock.cause(), req);
						}
					});
				} else {
					req.fail(-1, resCheckGridMasterInterlock.cause().getMessage());
				}
			});
		}).completionHandler(completionHandler);
	}

	/**
	 * Check the GridMaster interlock.
	 * Before that, also check for inclusion in the cluster members listed in POLICY.
	 * Results are received with the {@link AsyncResult#result()} method of completionHandler.
	 * @param <T> type of {@link Message#body()} of message object
	 * @param req message object
	 * @param completionHandler the completion handler
	 *          
	 * GridMaster インタロックを確認する.
	 * POLICY に記載されたクラスタメンバに含まれているかも先に確認する.
	 * completionHandler の {@link AsyncResult#result()} で受け取る.
	 * @param <T> message オブジェクトの {@link Message#body()} の型
	 * @param req message オブジェクト
	 * @param completionHandler the completion handler
	 */
	private <T> void checkGridMasterInterlock_(Message<T> req, Handler<AsyncResult<Void>> completionHandler) {
		String reqGridMasterUnitId = req.headers().get("gridMasterUnitId");
		if (reqGridMasterUnitId != null) {
			if (PolicyKeeping.isMember(reqGridMasterUnitId)) {
				InterlockUtil.getGridMasterUnitId(vertx, resGridMasterUnitId -> {
					if (resGridMasterUnitId.succeeded()) {
						String interlockedGridMasterUnitId = resGridMasterUnitId.result();
						if (interlockedGridMasterUnitId != null) {
							if (reqGridMasterUnitId.equals(interlockedGridMasterUnitId)) {
								completionHandler.handle(Future.succeededFuture());
							} else {
								ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, "access from illegal gridMaster; interlocked gridMasterUnitId: " + interlockedGridMasterUnitId + ", gridMasterUnitId in request: " + reqGridMasterUnitId, completionHandler);
							}
						} else {
							ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, "illegal access; no interlocked gridMasterUnitId", completionHandler);
						}
					} else {
						ErrorExceptionUtil.reportIfNeedAndFail(vertx, resGridMasterUnitId.cause(), completionHandler);
					}
				});
			} else {
				ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, "request received from illegal unit : " + reqGridMasterUnitId, completionHandler);
			}
		} else {
			ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, "illegal access; no gridMasterUnitId in request header", completionHandler);
		}
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
					JsonObject data = r.result();
					                                // Bidirectionally merge with unit data cache
					data = mergeDeviceStatus(data); // ユニットデータのキャッシュと双方向マージする
					completionHandler.handle(Future.succeededFuture(data));
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
		 */
		/**
		 * NOTE: The method {@code HttpClient.get(String, Handler<HttpClientResponse>)}
		 * used below is deprecated
		 * in newer versions of Vert.x. However, due to compatibility requirements with
		 * Vert.x 3.5.3,
		 * which does not support the WebClient API or newer alternatives, we are
		 * retaining this usage.
		 *
		 * The usage is suppressed with {@code @SuppressWarnings("deprecation")} and may
		 * be refactored
		 * once the project upgrades to a newer Vert.x version (3.6+ or 4.x) where
		 * WebClient is available.
		 */
		private void send_(HttpClient client, String uri, Handler<AsyncResult<JsonObject>> completionHandler) {
			if (log.isInfoEnabled()) log.info("uri : " + uri);
			Long requestTimeoutMsec = PolicyKeeping.cache().getLong(DEFAULT_REQUEST_TIMEOUT_MSEC, "controller", "requestTimeoutMsec");
			client.request(HttpMethod.GET, uri)
				.compose(request -> request.send())
				.onSuccess(response -> {
					if (response.statusCode() == 200) {
						response.body()
							.onSuccess(body -> {
								JsonObjectUtil.toJsonObject(body, completionHandler);
							})
							.onFailure(t -> completionHandler.handle(Future.failedFuture(t)));
					} else {
						response.body()
							.onSuccess(error -> {
								completionHandler.handle(Future.failedFuture("http request failed : " + response.statusCode() + " : " + response.statusMessage() + " : " + error));
							})
							.onFailure(t -> completionHandler.handle(Future.failedFuture("http request failed : " + response.statusCode() + " : " + response.statusMessage() + " : " + t)));
					}
				})
				.onFailure(t -> completionHandler.handle(Future.failedFuture(t)));
		}
	}

}
