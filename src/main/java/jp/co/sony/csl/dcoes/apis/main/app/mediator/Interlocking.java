package jp.co.sony.csl.dcoes.apis.main.app.mediator;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jp.co.sony.csl.dcoes.apis.common.Deal;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.ServiceAddress;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.ReplyFailureUtil;
import jp.co.sony.csl.dcoes.apis.main.app.HwConfigKeeping;
import jp.co.sony.csl.dcoes.apis.main.app.PolicyKeeping;
import jp.co.sony.csl.dcoes.apis.main.app.mediator.util.DealUtil;
import jp.co.sony.csl.dcoes.apis.main.app.mediator.util.InterlockUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ApisConfig;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorExceptionUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

/**
 * A Verticle that manages interlocks.
 * Launched from the {@link Mediator} Verticle.
 * Acquires, releases and resets GridMaster interlocks and interchange interlocks.
 * @author OES Project
 *          
 * インタロックを管理する Verticle.
 * {@link Mediator} Verticle から起動される.
 * GridMaster インタロックおよび融通インタロックの獲得と開放とリセット.
 * @author OES Project
 */
public class Interlocking extends AbstractVerticle {
	private static final Logger log = LoggerFactory.getLogger(Interlocking.class);

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
	@Override public void start(Promise<Void> startPromise) throws Exception {
		startGridMasterInterlockingService_(resGridMasterInterlocking -> {
			if (resGridMasterInterlocking.succeeded()) {
				startDealInterlockingService_(resDealInterlocking -> {
					if (resDealInterlocking.succeeded()) {
						startResetLocalService_(resResetLocal -> {
							if (resResetLocal.succeeded()) {
								startResetAllService_(resResetAll -> {
									if (resResetAll.succeeded()) {
										if (log.isTraceEnabled()) log.trace("started : " + deploymentID());
										startPromise.complete();
									} else {
										startPromise.fail(resResetAll.cause());
									}
								});
							} else {
								startPromise.fail(resResetLocal.cause());
							}
						});
					} else {
						startPromise.fail(resDealInterlocking.cause());
					}
				});
			} else {
				startPromise.fail(resGridMasterInterlocking.cause());
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
	@Override public void stop(Promise<Void> stopPromise) throws Exception {
		if (log.isTraceEnabled()) log.trace("stopped : " + deploymentID());
		stopPromise.complete();
	}

	////

	/**
	 * Get the possible number of simultaneous interchanges.
	 * This is calculated from the grid current capacity and the grid current for each interchange.
	 * @param vertx a vertx object
	 * @return the number of possible simultaneous interchanges
	 *          
	 * 同時融通可能数を取得する.
	 * グリッド電流容量と融通ごとのグリッド電流から算出する.
	 * @param vertx vertx オブジェクト
	 * @return 同時融通可能数
	 */
	public static int dealInterlockCapacity(Vertx vertx) {
		Float gridCurrentCapacityA = HwConfigKeeping.gridCurrentCapacityA();
		Float dealGridCurrentA = PolicyKeeping.cache().getFloat("mediator", "deal", "gridCurrentA");
		if (gridCurrentCapacityA != null && dealGridCurrentA != null) {
			// {HWCONFIG.gridCurrentCapacityA} / {POLICY.mediator.deal.gridCurrentA}
			// HWCONFIG.gridCurrentCapacityA ÷ POLICY.mediator.deal.gridCurrentA
			return (int) (gridCurrentCapacityA / dealGridCurrentA);
		} else {
			ErrorUtil.report(vertx, Error.Category.USER, Error.Extent.LOCAL, Error.Level.ERROR, "data deficiency; HWCONFIG.gridCurrentCapacityA : " + gridCurrentCapacityA + ", POLICY.mediator.deal.gridCurrentA : " + dealGridCurrentA);
			return 0;
		}
	}

	////

	/**
	 * Launch the {@link io.vertx.core.eventbus.EventBus} service.
	 * Address: {@link ServiceAddress.Mediator#gridMasterInterlocking()}
	 * Scope: local
	 * Function: Acquire/release a GridMaster interlock.
	 * Message body: GridMaster unit ID [{@link String}]
	 * Message header:
	 * 　　　　   - {@code "command"}
	 * 　　　　     - {@code "acquire"}: acquire an interlock
	 * 　　　　     - {@code "release"}: release an interlock
	 * Response: this unit's ID [{@link String}]
	 * 　　　　　   Fails if an error occurs.
	 * @param completionHandler the completion handler
	 *          
	 * {@link io.vertx.core.eventbus.EventBus} サービス起動.
	 * アドレス : {@link ServiceAddress.Mediator#gridMasterInterlocking()}
	 * 範囲 : ローカル
	 * 処理 : GridMaster インタロックを確保/解放する.
	 * メッセージボディ : GridMaster ユニット ID [{@link String}]
	 * メッセージヘッダ :
	 * 　　　　　　　　   - {@code "command"}
	 * 　　　　　　　　     - {@code "acquire"} : インタロックを確保する
	 * 　　　　　　　　     - {@code "release"} : インタロックを解放する
	 * レスポンス : 自ユニットの ID [{@link String}]
	 * 　　　　　   エラーが起きたら fail.
	 * @param completionHandler the completion handler
	 */
	private void startGridMasterInterlockingService_(Handler<AsyncResult<Void>> completionHandler) {
		vertx.eventBus().<String>localConsumer(ServiceAddress.Mediator.gridMasterInterlocking(), req -> {
			String command = req.headers().get("command");
			String value = req.body();
			if (value != null) {
				if ("acquire".equalsIgnoreCase(command)) {
					InterlockUtil.lockGridMasterUnitId(vertx, value, false, resAcquire -> {
						if (resAcquire.succeeded()) {
							if (log.isInfoEnabled()) log.info("locked; gridMasterUnitId : " + value);
							req.reply(ApisConfig.unitId());
						} else {
							ErrorExceptionUtil.reportIfNeedAndFail(vertx, resAcquire.cause(), req);
						}
					});
				} else if ("release".equalsIgnoreCase(command)) {
					InterlockUtil.unlockGridMasterUnitId(vertx, value, resRelease -> {
						if (resRelease.succeeded()) {
							if (log.isInfoEnabled()) log.info("unlocked; gridMasterUnitId : " + value);
							req.reply(ApisConfig.unitId());
						} else {
							ErrorExceptionUtil.reportIfNeedAndFail(vertx, resRelease.cause(), req);
						}
					});
				} else {
					ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, "unknown command : " + command, req);
				}
			} else {
				ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, "invalid request : " + req, req);
			}
		}).completionHandler(completionHandler);
	}

	/**
	 * Launch the {@link io.vertx.core.eventbus.EventBus} service.
	 * Address: {@link ServiceAddress.Mediator#dealInterlocking(String)}
	 * Scope: global
	 * Function: Acquire/release an interchange interlock.
	 * Message body: Interchange information [{@link JsonObject}]
	 * Message header:
	 * 　　　　   - {@code "command"}
	 * 　　　　     - {@code "acquire"}: acquire an interlock
	 * 　　　　     - {@code "release"}: release an interlock
	 * Response: this unit's ID [{@link String}]
	 *           Fails if an error occurs.
	 * @param completionHandler the completion handler
	 *          
	 * {@link io.vertx.core.eventbus.EventBus} サービス起動.
	 * アドレス : {@link ServiceAddress.Mediator#dealInterlocking(String)}
	 * 範囲 : グローバル
	 * 処理 : 融通インタロックを確保/解放する.
	 * メッセージボディ : 融通情報 [{@link JsonObject}]
	 * メッセージヘッダ :
	 * 　　　　　　　　   - {@code "command"}
	 * 　　　　　　　　     - {@code "acquire"} : インタロックを確保する
	 * 　　　　　　　　     - {@code "release"} : インタロックを解放する
	 * レスポンス : 自ユニットの ID [{@link String}]
	 * 　　　　　   エラーが起きたら fail.
	 * @param completionHandler the completion handler
	 */
	private void startDealInterlockingService_(Handler<AsyncResult<Void>> completionHandler) {
		vertx.eventBus().<JsonObject>consumer(ServiceAddress.Mediator.dealInterlocking(ApisConfig.unitId()), req -> {
			String command = req.headers().get("command");
			JsonObject deal = req.body();
			if (deal != null) {
				String dealId = Deal.dealId(deal);
				if ("acquire".equalsIgnoreCase(command)) {
					// Since an interchange interlock was acquired earlier
					// 先に融通インタロックを獲得してから
					InterlockUtil.lockDealId(vertx, dealId, dealInterlockCapacity(vertx), true, resAcquire -> {
						if (resAcquire.succeeded()) {
							// Fetch the battery capacity
							// バッテリ容量を確保する
							DeliveryOptions options = new DeliveryOptions().addHeader("command", command);
							vertx.eventBus().<Boolean>request(ServiceAddress.Controller.batteryCapacityManaging(), deal, options, repBatteryCapacityAcquire -> {
								if (repBatteryCapacityAcquire.succeeded() && repBatteryCapacityAcquire.result().body()) {
									if (log.isInfoEnabled()) log.info("locked; dealId : " + dealId);
									req.reply(ApisConfig.unitId());
								} else {
									InterlockUtil.unlockDealId(vertx, dealId, resRelease -> {
										if (resRelease.succeeded()) {
											if (repBatteryCapacityAcquire.succeeded()) {
												req.fail(-1, "batteryCapacityManaging acquire failed");
											} else {
												ErrorExceptionUtil.reportIfNeedAndFail(vertx, repBatteryCapacityAcquire.cause(), req);
											}
										} else {
											ErrorExceptionUtil.reportIfNeedAndFail(vertx, resRelease.cause(), req);
										}
									});
								}
							});
						} else {
							ErrorExceptionUtil.reportIfNeedAndFail(vertx, resAcquire.cause(), req);
						}
					});
				} else if ("release".equalsIgnoreCase(command)) {
					InterlockUtil.unlockDealId(vertx, dealId, resRelease -> {
						if (resRelease.succeeded()) {
							InterlockUtil.getDealIds(vertx, resGet -> {
								if (resGet.succeeded()) {
									// TODO: It's not good to branch here depending on whether or not battery capacity management is called
									// TODO : ここでバッテリ容量管理を呼ぶか呼ばないか分岐があるとかイマイチ
									if (0 < resGet.result().size()) {
										// There is still an interchange left, so don't release the battery capacity acquisition
										// まだ融通が残っているのでバッテリ容量の確保を開放しない
										if (log.isInfoEnabled()) log.info("unlocked; dealId : " + dealId);
										req.reply(ApisConfig.unitId());
									} else {
										// The interchange has gone, so release the battery capacity acquisition
										// 融通がなくなったのでバッテリ容量の確保を開放する
										DeliveryOptions options = new DeliveryOptions().addHeader("command", command);
										vertx.eventBus().<Boolean>request(ServiceAddress.Controller.batteryCapacityManaging(), deal, options, repBatteryCapacityRelease -> {
											if (repBatteryCapacityRelease.succeeded()) {
												if (log.isInfoEnabled()) log.info("unlocked; dealId : " + dealId);
												req.reply(ApisConfig.unitId());
											} else {
												ErrorExceptionUtil.reportIfNeedAndFail(vertx, repBatteryCapacityRelease.cause(), req);
											}
										});
									}
								} else {
									ErrorExceptionUtil.reportIfNeedAndFail(vertx, resGet.cause(), req);
								}
							});
						} else {
							ErrorExceptionUtil.reportIfNeedAndFail(vertx, resRelease.cause(), req);
						}
					});
				} else {
					ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, "unknown command : " + command, req);
				}
			} else {
				ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, "invalid request : " + req, req);
			}
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
			InterlockUtil.resetExclusiveLock(vertx);
			Promise<Void> resetDealIdPromise = Promise.promise();
			Future<Void> resetDealIdFuture = resetDealIdPromise.future();
			Promise<Void> resetGridMasterUnitIdPromise = Promise.promise();
			Future<Void> resetGridMasterUnitIdFuture = resetGridMasterUnitIdPromise.future();
			doResetLocalDealId_(ar -> {
				if (ar.succeeded()) {
					resetDealIdPromise.complete();
				} else {
					resetDealIdPromise.fail(ar.cause());
				}
			});
			doResetLocalGridMasterUnitId_(ar -> {
				if (ar.succeeded()) {
					resetGridMasterUnitIdPromise.complete();
				} else {
					resetGridMasterUnitIdPromise.fail(ar.cause());
				}
			});
			CompositeFuture.all(resetDealIdFuture, resetGridMasterUnitIdFuture).onComplete(ar -> {
				if (ar.succeeded()) {
					req.reply(ApisConfig.unitId());
				} else {
					ErrorExceptionUtil.reportIfNeedAndFail(vertx, ar.cause(), req);
				}
			});
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
			InterlockUtil.resetExclusiveLock(vertx);
			Promise<Void> resetDealIdPromise = Promise.promise();
			Future<Void> resetDealIdFuture = resetDealIdPromise.future();
			Promise<Void> resetGridMasterUnitIdPromise = Promise.promise();
			Future<Void> resetGridMasterUnitIdFuture = resetGridMasterUnitIdPromise.future();
			InterlockUtil.resetDealId(vertx, ar -> {
				if (ar.succeeded()) {
					resetDealIdPromise.complete();
				} else {
					resetDealIdPromise.fail(ar.cause());
				}
			});
			InterlockUtil.resetGridMasterUnitId(vertx, ar -> {
				if (ar.succeeded()) {
					resetGridMasterUnitIdPromise.complete();
				} else {
					resetGridMasterUnitIdPromise.fail(ar.cause());
				}
			});
			CompositeFuture.all(resetDealIdFuture, resetGridMasterUnitIdFuture).onComplete(ar -> {
				if (ar.succeeded()) {
					req.reply(ApisConfig.unitId());
				} else {
					ErrorExceptionUtil.reportIfNeedAndFail(vertx, ar.cause(), req);
				}
			});
		}).completionHandler(completionHandler);
	}
	private void doResetLocalDealId_(Handler<AsyncResult<Void>> completionHandler) {
		DealUtil.withUnitId(vertx, ApisConfig.unitId(), resDeals -> {
			if (resDeals.succeeded()) {
				if (resDeals.result().isEmpty()) {
					// Reset if there is no participating interchange
					// 参加している融通がなければリセット
					InterlockUtil.resetDealId(vertx, resReset -> {
						if (resReset.succeeded()) {
							completionHandler.handle(Future.succeededFuture());
						} else {
							ErrorExceptionUtil.reportIfNeedAndFail(vertx, resReset.cause(), completionHandler);
						}
					});
				} else {
					// Do nothing if there is a participating interchange
					// 参加している融通があったら何もしない
					completionHandler.handle(Future.succeededFuture());
				}
			} else {
				ErrorExceptionUtil.reportIfNeed(vertx, resDeals.cause());
			}
		});
	}
	private void doResetLocalGridMasterUnitId_(Handler<AsyncResult<Void>> completionHandler) {
		InterlockUtil.getGridMasterUnitId(vertx, resGet -> {
			if (resGet.succeeded()) {
				String value = resGet.result();
				if (ApisConfig.unitId().equals(value)) {
					// Unlock if the GridMaster interlock value matches this unit's ID
					// GridMaster インタロックの値が自ユニットの ID だったらアンロック
					InterlockUtil.unlockGridMasterUnitId(vertx, value, resRelease -> {
						if (resRelease.succeeded()) {
							completionHandler.handle(Future.succeededFuture());
						} else {
							ErrorExceptionUtil.reportIfNeedAndFail(vertx, resRelease.cause(), completionHandler);
						}
					});
				} else {
					// If the GridMaster interlock value does not match this unit's ID
					// GridMaster インタロックの値が自ユニットの ID じゃなかったら
					vertx.eventBus().<String>request(ServiceAddress.GridMaster.helo(), null, repHeloGridMaster -> {
						if (repHeloGridMaster.succeeded()) {
							// Do nothing if a GridMaster exists somewhere
							// どこかに GridMaster が存在したら何もしない
							completionHandler.handle(Future.succeededFuture());
						} else if (ReplyFailureUtil.isNoHandlers(repHeloGridMaster)) {
							// Reset if no GridMaster exists anywhere
							// どこにも GridMaster が存在しなかったらリセット
							InterlockUtil.resetGridMasterUnitId(vertx, resReset -> {
								if (resReset.succeeded()) {
									completionHandler.handle(Future.succeededFuture());
								} else {
									ErrorExceptionUtil.reportIfNeedAndFail(vertx, resReset.cause(), completionHandler);
								}
							});
						} else {
							completionHandler.handle(Future.succeededFuture());
						}
					});
				}
			} else {
				ErrorExceptionUtil.reportIfNeedAndFail(vertx, resGet.cause(), completionHandler);
			}
		});
	}

}
