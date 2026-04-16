package jp.co.sony.csl.dcoes.apis.main.app.controller;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import jp.co.sony.csl.dcoes.apis.common.Deal;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.ServiceAddress;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.FileSystemExclusiveLockUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ApisConfig;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

/**
 * Battery capacity management Verticle.
 * Launched from the {@link Controller} Verticle.
 * When multiple apis-mains share a single battery, as in gateway operation or the like, perform coordinated management of battery current capacity with the other apis-mains.
 * At present, all the apis-mains to be coordinated must exist on the same computer (because coordination management is implemented using the file system).
 * @author OES Project
 *          
 * バッテリ容量管理 Verticle.
 * {@link Controller} Verticle から起動される.
 * Gateway 運用など一つのバッテリを複数の apis-main で共有している場合に他の apis-main とバッテリ電流容量を協調管理する.
 * 現状は協調管理する apis-main が全て同一コンピュータ上にある必要がある ( ファイルシステムを用いて協調管理するため ).
 * @author OES Project
 */
public class BatteryCapacityManagement extends AbstractVerticle {
	private static final Logger log = LoggerFactory.getLogger(BatteryCapacityManagement.class);

	/**
	 * Called at startup.
	 * Launches the {@link io.vertx.core.eventbus.EventBus} service.
	 * @param startFuture {@inheritDoc}
	 * @throws Exception {@inheritDoc}
	 *          
	 * 起動時に呼び出される.
	 * {@link io.vertx.core.eventbus.EventBus} サービスを起動する.
	 * @param startFuture {@inheritDoc}
	 * @throws Exception {@inheritDoc}
	 */
	@Override public void start(Future<Void> startFuture) throws Exception {
		if (log.isInfoEnabled()) log.info("batteryCapacityManagement : " + ApisConfig.isBatteryCapacityManagementEnabled());
		startBatteryCapacityTestingService_(resBatteryCapacityTesting -> {
			if (resBatteryCapacityTesting.succeeded()) {
				startBatteryCapacityManagingService_(resBatteryCapacityManaging -> {
					if (resBatteryCapacityManaging.succeeded()) {
						startResetLocalService_(resResetLocal -> {
							if (resResetLocal.succeeded()) {
								startResetAllService_(resResetAll -> {
									if (resResetAll.succeeded()) {
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
						startFuture.fail(resBatteryCapacityManaging.cause());
					}
				});
			} else {
				startFuture.fail(resBatteryCapacityTesting.cause());
			}
		});
	}

	/**
	 * Called when stopped.
	 * @throws Exception {@inheritDoc}
	 *          
	 * 停止時に呼び出される.
	 * @throws Exception {@inheritDoc}
	 */
	@Override public void stop() throws Exception {
		if (log.isTraceEnabled()) log.trace("stopped : " + deploymentID());
	}

	////

	private String lockName_(Deal.Direction direction) {
		return "batteryCapacity." + direction.name();
	}

	/**
	 * Launch the {@link io.vertx.core.eventbus.EventBus} service.
	 * Address: {@link ServiceAddress.Controller#batteryCapacityTesting()}
	 * Scope: local
	 * Function: Check the battery capacity and the present interchange status to determine if a new interchange is possible.
	 * 　　   When multiple apis-mains share a single battery, as in gateway operation or the like, perform coordinated management of battery current capacity with the other apis-mains.
	 * 　　   At present, all the apis-mains to be coordinated must exist on the same computer (because coordination management is implemented using the file system).
	 * Message body: interchange direction [{@link String}]
	 * 　　　　　　　　   - {@code "DISCHARGE"}: discharge
	 * 　　　　　　　　   - {@code "CHARGE"}: charge
	 * Message header: none
	 * Response: {@link Boolean#TRUE} if possible, or if battery capacity management is disabled
	 * 　　　　　   {@link Boolean#FALSE} if not possible
	 * 　　　　　   Fails if an error occurs.
	 * @param completionHandler the completion handler
	 *          
	 * {@link io.vertx.core.eventbus.EventBus} サービス起動.
	 * アドレス : {@link ServiceAddress.Controller#batteryCapacityTesting()}
	 * 範囲 : ローカル
	 * 処理 : バッテリ容量と現在の融通状態を確認し新しい融通が可能か否か判定する.
	 * 　　   Gateway 運用など一つのバッテリを複数の apis-main で共有している場合に他の apis-main とバッテリ電流容量を協調管理する.
	 * 　　   現状は協調管理する apis-main が全て同一コンピュータ上にある必要がある ( ファイルシステムを用いて協調管理するため ).
	 * メッセージボディ : 融通方向 [{@link String}]
	 * 　　　　　　　　   - {@code "DISCHARGE"} : 送電
	 * 　　　　　　　　   - {@code "CHARGE"} : 受電
	 * メッセージヘッダ : なし
	 * レスポンス : 可またはバッテリ容量管理機能が無効なら {@link Boolean#TRUE}
	 * 　　　　　   不可なら {@link Boolean#FALSE}
	 * 　　　　　   エラーが起きたら fail.
	 * @param completionHandler the completion handler
	 */
	private void startBatteryCapacityTestingService_(Handler<AsyncResult<Void>> completionHandler) {
		vertx.eventBus().<String>localConsumer(ServiceAddress.Controller.batteryCapacityTesting(), req -> {
			if (ApisConfig.isBatteryCapacityManagementEnabled()) {
				Deal.Direction direction = Deal.direction(req.body());
				if (direction != null) {
					doBatteryCapacityTesting_(direction, req);
				} else {
					ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, "invalid request body : " + req.body(), req);
				}
			} else {
				req.reply(Boolean.TRUE);
			}
		}).completionHandler(completionHandler);
	}
	/**
	 * Check the battery capacity and the present interchange status to determine if a new interchange is possible.
	 * At present, we only consider the interchange direction.
	 * - To avoid having multiple apis-mains each running interchanges in the same direction, exclusion control is performed by creating a separate file for each direction.
	 * Uses a {@link Message#reply(Object)} to send back results in response to a {@code message}.
	 * @param direction a direction object
	 * @param message the message requiring a reply
	 *          
	 * バッテリ容量と現在の融通状態を確認し新しい融通が可能か否か判定する.
	 * 現状は融通の方向だけを見ている.
	 * - 複数の apis-main がそれぞれ同一方向の融通を実行しないように方向ごとのファイルを作り排他制御している.
	 * {@code message} に対して {@link Message#reply(Object)} で結果を返す.
	 * @param direction direction オブジェクト
	 * @param message : 返信対象メッセージ
	 */
	private void doBatteryCapacityTesting_(Deal.Direction direction, Message<?> message) {
		String lockName = lockName_(direction);
		FileSystemExclusiveLockUtil.check(vertx, lockName, resCheck -> {
			if (resCheck.succeeded()) {
				if (resCheck.result()) {
					// Success if acquired
					// 獲得済なら成功
					message.reply(Boolean.TRUE);
				} else {
					// Try acquiring
					// 獲得してみる
					FileSystemExclusiveLockUtil.lock(vertx, lockName, true, resLock -> {
						if (resLock.succeeded()) {
							if (resLock.result()) {
								// If the acquisition is successful
								// 獲得成功なら
								FileSystemExclusiveLockUtil.unlock(vertx, lockName, true, resUnlock -> {
									if (resUnlock.succeeded()) {
										// Successfully opened
										// 開放して成功
										message.reply(Boolean.TRUE);
									} else {
										ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.FATAL, resUnlock.cause(), message);
									}
								});
							} else {
								// Fail if acquisition fails
								// 獲得失敗なら失敗
								if (log.isInfoEnabled()) log.info("battery over-capacity ; direction : " + direction);
								message.reply(Boolean.FALSE);
							}
						} else {
							ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.FATAL, resLock.cause(), message);
						}
					});
				}
			} else {
				ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.FATAL, resCheck.cause(), message);
			}
		});
	}

	/**
	 * Launch the {@link io.vertx.core.eventbus.EventBus} service.
	 * Address: {@link ServiceAddress.Controller#batteryCapacityManaging()}
	 * Scope: local
	 * Function: Acquire/release an interchange frame.
	 * 　　   When multiple apis-mains share a single battery, as in gateway operation or the like, perform coordinated management of battery current capacity with the other apis-mains.
	 * 　　   At present, all the apis-mains to be coordinated must exist on the same computer (because coordination management is implemented using the file system).
	 * Message body: Interchange information [{@link JsonObject}]
	 * Message header: {@code "command"}
	 * 　　　　　　　　   - {@code "acquire"}: acquire an interchange frame
	 * 　　　　　　　　   - {@code "release"}: release an interchange frame
	 * Response: {@link Boolean#TRUE} if successful or if battery capacity management is disabled
	 * 　　　　　   {@link Boolean#FALSE} if failed
	 * 　　　　　   Fails if an error occurs.
	 * @param completionHandler the completion handler
	 *          
	 * {@link io.vertx.core.eventbus.EventBus} サービス起動.
	 * アドレス : {@link ServiceAddress.Controller#batteryCapacityManaging()}
	 * 範囲 : ローカル
	 * 処理 : 融通枠を確保/解放する.
	 * 　　   Gateway 運用など一つのバッテリを複数の apis-main で共有している場合に他の apis-main とバッテリ電流容量を協調管理する.
	 * 　　   現状は協調管理する apis-main が全て同一コンピュータ上にある必要がある ( ファイルシステムを用いて協調管理するため ).
	 * メッセージボディ : 融通情報 [{@link JsonObject}]
	 * メッセージヘッダ : {@code "command"}
	 * 　　　　　　　　   - {@code "acquire"} : 融通枠を確保する
	 * 　　　　　　　　   - {@code "release"} : 融通枠を解放する
	 * レスポンス : 成功またはバッテリ容量管理機能が無効なら {@link Boolean#TRUE}
	 * 　　　　　   失敗なら {@link Boolean#FALSE}
	 * 　　　　　   エラーが起きたら fail.
	 * @param completionHandler the completion handler
	 */
	private void startBatteryCapacityManagingService_(Handler<AsyncResult<Void>> completionHandler) {
		vertx.eventBus().<JsonObject>localConsumer(ServiceAddress.Controller.batteryCapacityManaging(), req -> {
			if (ApisConfig.isBatteryCapacityManagementEnabled()) {
				JsonObject deal = req.body();
				if (deal != null) {
					Deal.Direction direction = Deal.direction(deal, ApisConfig.unitId());
					if (direction != null) {
						String command = req.headers().get("command");
						if ("acquire".equalsIgnoreCase(command)) {
							doBatteryCapacityAcquiring_(direction, req);
						} else if ("release".equalsIgnoreCase(command)) {
							doBatteryCapacityReleasing_(direction, req);
						} else {
							ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, "unknown command : " + command, req);
						}
					} else {
						ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, "invalid deal : " + deal, req);
					}
				} else {
					ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, "invalid request body : " + deal, req);
				}
			} else {
				req.reply(Boolean.TRUE);
			}
		}).completionHandler(completionHandler);
	}
	/**
	 * Acquire an interchange frame.
	 * Uses a {@link Message#reply(Object)} to send back results in response to a {@code message}.
	 * @param direction a direction object
	 * @param message the message requiring a reply
	 *          
	 * 融通枠を確保する.
	 * {@code message} に対して {@link Message#reply(Object)} で結果を返す.
	 * @param direction direction オブジェクト
	 * @param message : 返信対象メッセージ
	 */
	private void doBatteryCapacityAcquiring_(Deal.Direction direction, Message<?> message) {
		FileSystemExclusiveLockUtil.lock(vertx, lockName_(direction), true, resLock -> {
			if (resLock.succeeded()) {
				if (! resLock.result()) {
					if (log.isInfoEnabled()) log.info("battery over-capacity ; direction : " + direction);
				}
				message.reply(resLock.result());
			} else {
				ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.FATAL, resLock.cause(), message);
			}
		});
	}
	/**
	 * Release an interchange frame.
	 * Uses a {@link Message#reply(Object)} to send back results in response to a {@code message}.
	 * @param direction a direction object
	 * @param message the message requiring a reply
	 *          
	 * 融通枠を開放する.
	 * {@code message} に対して {@link Message#reply(Object)} で結果を返す.
	 * @param direction direction オブジェクト
	 * @param message : 返信対象メッセージ
	 */
	private void doBatteryCapacityReleasing_(Deal.Direction direction, Message<?> message) {
		FileSystemExclusiveLockUtil.unlock(vertx, lockName_(direction), true, resUnlock -> {
			if (resUnlock.succeeded()) {
				message.reply(resUnlock.result());
			} else {
				ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.FATAL, resUnlock.cause(), message);
			}
		});
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
		if (ApisConfig.isBatteryCapacityManagementEnabled()) {
			doBatteryCapacityResetting_(message);
		} else {
			message.reply(ApisConfig.unitId());
		}
	}
	/**
	 * Reset battery capacity management.
	 * Release the interchange locks in both directions.
	 * @param message the message requiring a reply
	 *          
	 * バッテリ容量管理をリセットする.
	 * 融通の両方向のロックを開放する.
	 * @param message : 返信対象メッセージ
	 */
	private void doBatteryCapacityResetting_(Message<?> message) {
		Future<Boolean> unlockDischargeFuture = Future.future();
		Future<Boolean> unlockChargeFuture = Future.future();
		FileSystemExclusiveLockUtil.unlock(vertx, lockName_(Deal.Direction.DISCHARGE), true, unlockDischargeFuture);
		FileSystemExclusiveLockUtil.unlock(vertx, lockName_(Deal.Direction.CHARGE), true, unlockChargeFuture);
		CompositeFuture.<Boolean, Boolean>all(unlockDischargeFuture, unlockChargeFuture).setHandler(ar -> {
			FileSystemExclusiveLockUtil.resetExclusiveLock(vertx);
			if (ar.succeeded()) {
				message.reply(ApisConfig.unitId());
			} else {
				ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.FATAL, ar.cause(), message);
			}
		});
	}

}
