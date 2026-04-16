package jp.co.sony.csl.dcoes.apis.main.app.mediator;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.ServiceAddress;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectWrapper;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.LocalExclusiveLock;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.ReplyFailureUtil;
import jp.co.sony.csl.dcoes.apis.main.app.PolicyKeeping;
import jp.co.sony.csl.dcoes.apis.main.app.StateHandling;
import jp.co.sony.csl.dcoes.apis.main.app.gridmaster.GridMaster;
import jp.co.sony.csl.dcoes.apis.main.app.gridmaster.MainLoop;
import jp.co.sony.csl.dcoes.apis.main.app.gridmaster.main_loop.DealExecution;
import jp.co.sony.csl.dcoes.apis.main.app.mediator.util.InterlockUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ApisConfig;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorExceptionUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;
import jp.co.sony.csl.dcoes.apis.main.util.Policy;

/**
 * A Verticle that manages a GridMaster.
 * Launched from the {@link Mediator} Verticle.
 * Launches, stop and move a GridMaster.
 * Periodically confirms its existence, and responds when absent.
 * @author OES Project
 *          
 * GridMaster を管理する Verticle.
 * {@link Mediator} Verticle から起動される.
 * GridMaster の起動と停止および移動.
 * 定期的な存在確認と不在時の対応.
 * @author OES Project
 */
public class GridMasterManagement extends AbstractVerticle {
	private static final Logger log = LoggerFactory.getLogger(GridMasterManagement.class);

	/**
	 * Default duration of the time to wait until reconfirmation if a GridMaster interlock is inconsistent [ms].
	 * Value: {@value}.
	 *          
	 * GridMaster インタロックが不整合の場合に再確認するまでの待ち時間のデフォルト値 [ms].
	 * 値は {@value}.
	 */
	private static final Long DEFAULT_INTERLOCK_INCONSISTENCY_RETRY_WAIT_MSEC = 2000L;
	/**
	 * Default duration of the time to wait until reconfirmation if a GridMaster is absent [ms].
	 * Value: {@value}.
	 *          
	 * GridMaster が不在の場合に再確認するまでの待ち時間のデフォルト値 [ms].
	 * 値は {@value}.
	 */
	private static final Long DEFAULT_ABSENCE_ENSURE_WAIT_MSEC = 5000L;

	/**
	 * A cache for passing through just one error.
	 *          
	 * 一発だけのエラーはスルーするためのキャッシュ.
	 */
	private static final JsonObjectWrapper errors = new JsonObjectWrapper();

	private long gridMasterWatchingTimerId_ = 0L;
	private boolean stopped_ = false;

	/**
	 * Called at startup.
	 * Launches the {@link io.vertx.core.eventbus.EventBus} service.
	 * Start a timer that periodically checks for the presence of a GridMaster.
	 * @param startFuture {@inheritDoc}
	 * @throws Exception {@inheritDoc}
	 *          
	 * 起動時に呼び出される.
	 * {@link io.vertx.core.eventbus.EventBus} サービスを起動する.
	 * 定期的に GridMaster の存在を確認するタイマを起動する.
	 * @param startFuture {@inheritDoc}
	 * @throws Exception {@inheritDoc}
	 */
	@Override public void start(Future<Void> startFuture) throws Exception {
		startGridMasterActivationService_(resGridMasterActivation -> {
			if (resGridMasterActivation.succeeded()) {
				startGridMasterDeactivationService_(resGridMasterDeactivation -> {
					if (resGridMasterDeactivation.succeeded()) {
						startGridMasterEnsuringService_(resGridMasterEnsuring -> {
							if (resGridMasterEnsuring.succeeded()) {
								startResetLocalService_(resResetLocal -> {
									if (resResetLocal.succeeded()) {
										startResetAllService_(resResetAll -> {
											if (resResetAll.succeeded()) {
												gridMasterWatchingTimerHandler_(0L);
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
								startFuture.fail(resGridMasterEnsuring.cause());
							}
						});
					} else {
						startFuture.fail(resGridMasterDeactivation.cause());
					}
				});
			} else {
				startFuture.fail(resGridMasterActivation.cause());
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
	 * Launch the {@link io.vertx.core.eventbus.EventBus} service.
	 * Address: {@link ServiceAddress.Mediator#gridMasterActivation(String)}
	 * Scope: global
	 * Function: Launch a GridMaster.
	 * Message body: none
	 * Message header: none
	 * Response: this unit's ID [{@link String}].
	 * 　　　　　Fails if an error occurs.
	 * @param completionHandler the completion handler
	 *          
	 * {@link io.vertx.core.eventbus.EventBus} サービス起動.
	 * アドレス : {@link ServiceAddress.Mediator#gridMasterActivation(String)}
	 * 範囲 : グローバル
	 * 処理 : GridMaster を起動する.
	 * メッセージボディ : なし
	 * メッセージヘッダ : なし
	 * レスポンス : 自ユニットの ID [{@link String}].
	 * 　　　　　   エラーが起きたら fail.
	 * @param completionHandler the completion handler
	 */
	private void startGridMasterActivationService_(Handler<AsyncResult<Void>> completionHandler) {
		vertx.eventBus().<Void>consumer(ServiceAddress.Mediator.gridMasterActivation(ApisConfig.unitId()), req -> {
			if (!StateHandling.isStopping()) {
				// GridMaster main loop execution and exclusion control
				// GridMaster メインループの実行と排他制御
				MainLoop.acquirePrivilegedExclusiveLock(vertx, resExclusiveLock1 -> {
					if (resExclusiveLock1.succeeded()) {
						LocalExclusiveLock.Lock lock1 = resExclusiveLock1.result();
						// Exclusion control with processing around the management of interchange information is no longer necessary
						// 融通情報の管理まわりの処理との排他制御は不要になった
//						DealManagement.acquirePrivilegedExclusiveLock(vertx, resExclusiveLock2 -> {
//							if (resExclusiveLock2.succeeded()) {
//								LocalExclusiveLock.Lock lock2 = resExclusiveLock2.result();
								doGridMasterActivationWithExclusiveLock_(resDoGridMasterActivationWithExclusiveLock -> {
//									lock2.release();
									lock1.release();
									if (resDoGridMasterActivationWithExclusiveLock.succeeded()) {
										req.reply(ApisConfig.unitId());
									} else {
										req.fail(-1, resDoGridMasterActivationWithExclusiveLock.cause().getMessage());
									}
								});
//							} else {
//								lock1.release();
//								ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, resExclusiveLock2.cause(), req);
//							}
//						});
					} else {
						ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, resExclusiveLock1.cause(), req);
					}
				});
			} else {
				ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, "this unit is stopping ...", req);
			}
		}).completionHandler(completionHandler);
	}
	private void doGridMasterActivationWithExclusiveLock_(Handler<AsyncResult<Void>> completionHandler) {
		DeliveryOptions acquireOptions = new DeliveryOptions().addHeader("command", "acquire");
		vertx.eventBus().send(ServiceAddress.Mediator.gridMasterInterlocking(), ApisConfig.unitId(), acquireOptions, repAcquire -> {
			if (repAcquire.succeeded()) {
				vertx.deployVerticle(new GridMaster(), resDeployGridMaster -> {
					if (resDeployGridMaster.succeeded()) {
						completionHandler.handle(Future.succeededFuture());
					} else {
						log.error(resDeployGridMaster.cause());
						System.err.println(resDeployGridMaster.cause());
						DeliveryOptions releaseOptions = new DeliveryOptions().addHeader("command", "release");
						vertx.eventBus().send(ServiceAddress.Mediator.gridMasterInterlocking(), ApisConfig.unitId(), releaseOptions, repRelease -> {
							if (repRelease.succeeded()) {
								// nop
							} else {
								if (ReplyFailureUtil.isRecipientFailure(repRelease)) {
									// nop
								} else {
									ErrorUtil.report(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR, "Communication failed on EventBus", repRelease.cause());
								}
							}
							ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR, resDeployGridMaster.cause(), completionHandler);
						});
					}
				});
			} else {
				if (ReplyFailureUtil.isRecipientFailure(repAcquire)) {
					completionHandler.handle(Future.failedFuture(repAcquire.cause()));
				} else {
					ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR, "Communication failed on EventBus", repAcquire.cause(), completionHandler);
				}
			}
		});
	}

	/**
	 * Launch the {@link io.vertx.core.eventbus.EventBus} service.
	 * Address: {@link ServiceAddress.Mediator#gridMasterDeactivation(String)}
	 * Scope: global
	 * Function: Stop a GridMaster.
	 * Message body: none
	 * Message header: none
	 * Response: this unit's ID [{@link String}].
	 * 　　　　　Fails if an error occurs.
	 * @param completionHandler the completion handler
	 *          
	 * {@link io.vertx.core.eventbus.EventBus} サービス起動.
	 * アドレス : {@link ServiceAddress.Mediator#gridMasterDeactivation(String)}
	 * 範囲 : グローバル
	 * 処理 : GridMaster を停止する.
	 * メッセージボディ : なし
	 * メッセージヘッダ : なし
	 * レスポンス : 自ユニットの ID [{@link String}].
	 * 　　　　　   エラーが起きたら fail.
	 * @param completionHandler the completion handler
	 */
	private void startGridMasterDeactivationService_(Handler<AsyncResult<Void>> completionHandler) {
		vertx.eventBus().<Void>consumer(ServiceAddress.Mediator.gridMasterDeactivation(ApisConfig.unitId()), req -> {
			// GridMaster main loop execution and exclusion control
			// GridMaster メインループの実行と排他制御
			MainLoop.acquirePrivilegedExclusiveLock(vertx, resExclusiveLock1 -> {
				if (resExclusiveLock1.succeeded()) {
					LocalExclusiveLock.Lock lock1 = resExclusiveLock1.result();
					// Exclusion control with processing around the management of interchange information is no longer necessary
					// 融通情報の管理まわりの処理との排他制御は不要になった
//					DealManagement.acquirePrivilegedExclusiveLock(vertx, resExclusiveLock2 -> {
//						if (resExclusiveLock2.succeeded()) {
//							LocalExclusiveLock.Lock lock2 = resExclusiveLock2.result();
							doGridMasterDeactivationWithExclusiveLock_(resDoGridMasterDeactivationWithExclusiveLock -> {
//								lock2.release();
								lock1.release();
								if (resDoGridMasterDeactivationWithExclusiveLock.succeeded()) {
									req.reply(ApisConfig.unitId());
								} else {
									req.fail(-1, resDoGridMasterDeactivationWithExclusiveLock.cause().getMessage());
								}
							});
//						} else {
//							lock1.release();
//							ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, resExclusiveLock2.cause(), req);
//						}
//					});
				} else {
					ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, resExclusiveLock1.cause(), req);
				}
			});
		}).completionHandler(completionHandler);
	}
	private void doGridMasterDeactivationWithExclusiveLock_(Handler<AsyncResult<Void>> completionHandler) {
		vertx.eventBus().send(ServiceAddress.GridMaster.undeploymentLocal(), null, repUndeploy -> {
			if (repUndeploy.succeeded()) {
				DeliveryOptions releaseOptions = new DeliveryOptions().addHeader("command", "release");
				vertx.eventBus().send(ServiceAddress.Mediator.gridMasterInterlocking(), ApisConfig.unitId(), releaseOptions, repRelease -> {
					if (repRelease.succeeded()) {
						completionHandler.handle(Future.succeededFuture());
					} else {
						if (ReplyFailureUtil.isRecipientFailure(repRelease)) {
							completionHandler.handle(Future.failedFuture(repRelease.cause()));
						} else {
							ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR, "Communication failed on EventBus", repRelease.cause(), completionHandler);
						}
					}
				});
			} else {
				if (ReplyFailureUtil.isNoHandlers(repUndeploy)) {
					completionHandler.handle(Future.succeededFuture());
				} else if (ReplyFailureUtil.isRecipientFailure(repUndeploy)) {
					completionHandler.handle(Future.failedFuture(repUndeploy.cause()));
				} else {
					ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR, "Communication failed on EventBus", repUndeploy.cause(), completionHandler);
				}
			}
		});
	}

	/**
	 * Launch the {@link io.vertx.core.eventbus.EventBus} service.
	 * Address: {@link ServiceAddress.Mediator#gridMasterEnsuring()}
	 * Scope: local
	 * Function: Maintaining the existence of a GridMaster.
	 *           Confirming its existence.
	 *           Confirming its location.
	 *           Moving it to a suitable unit.
	 * Message body: none
	 * Message header: none
	 * Response: ID of the unit where the GridMaster exists [{@link String}].
	 *           Fails if an error occurs.
	 * @param completionHandler the completion handler
	 *          
	 * {@link io.vertx.core.eventbus.EventBus} サービス起動.
	 * アドレス : {@link ServiceAddress.Mediator#gridMasterEnsuring()}
	 * 範囲 : ローカル
	 * 処理 : GridMaster の存在メンテナンス.
	 * 　　   存在の確認.
	 * 　　   存在場所の確認.
	 * 　　   適切なユニットへの移動.
	 * メッセージボディ : なし
	 * メッセージヘッダ : なし
	 * レスポンス : GridMaster が存在すユニットの ID [{@link String}].
	 * 　　　　　   エラーが起きたら fail.
	 * @param completionHandler the completion handler
	 */
	private void startGridMasterEnsuringService_(Handler<AsyncResult<Void>> completionHandler) {
		vertx.eventBus().<Void>localConsumer(ServiceAddress.Mediator.gridMasterEnsuring(), req -> {
			String properGridMasterUnitId = properGridMasterUnitId_();
			// First check that a GridMaster exists, and if so, where
			// まず GridMaster がいるか, いるならどこかを確認する
			vertx.eventBus().<String>send(ServiceAddress.GridMaster.helo(), null, repHeloGridMaster -> {
				if (repHeloGridMaster.succeeded()) {
					// A GridMaster exists somewhere
					// どこかに GridMaster が存在する
					String gridMasterUnitId = repHeloGridMaster.result().body();
					if (log.isInfoEnabled()) log.info("gridMasterUnitId : " + gridMasterUnitId);
					if (properGridMasterUnitId == null || properGridMasterUnitId.equals(gridMasterUnitId)) {
						// Either the GridMaster has not yet been allocated to a unit (anywhere will do), or it is already in the unit where it belongs
						// GridMaster がいるべきユニットが決まっていない ( どこでもよい ) か既にいるべきユニットにいる
						// → OK as it is
						// → そのままで OK
						if (log.isInfoEnabled()) log.info("no need to move");
						// Check that the GridMaster interlock is correct
						// GridMaster インタロックが正しいか確認する
						InterlockUtil.getGridMasterUnitId(vertx, resInterlockGridMasterUnitId -> {
							if (resInterlockGridMasterUnitId.succeeded()) {
								String interlockGridMasterUnitId = resInterlockGridMasterUnitId.result();
								if (gridMasterUnitId.equals(interlockGridMasterUnitId)) {
									// OK, so clear the first inconsistency
									// OK なので一回目の不整合をクリア
									errors.remove("gridMasterEnsuring");
									req.reply(gridMasterUnitId);
								} else {
									ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.ERROR, "gridMaster interlock inconsistency; gridMasterUnitId : " + gridMasterUnitId + ", interlockGridMasterUnitId : " + interlockGridMasterUnitId, req);
								}
							} else {
								ErrorExceptionUtil.reportIfNeedAndFail(vertx, resInterlockGridMasterUnitId.cause(), req);
							}
						});
					} else {
						// It is not in the unit where it belongs
						// いるべきユニットではないところにいる
						if (log.isInfoEnabled()) log.info("should move GridMaster");
						// First drop the current one
						// まず今のを落とす
						vertx.eventBus().<String>send(ServiceAddress.Mediator.gridMasterDeactivation(gridMasterUnitId), null, repGridMasterDeactivation -> {
							if (repGridMasterDeactivation.succeeded()) {
								// Set in the right place
								// 正しいところに立てる
								activateGridMaster_(properGridMasterUnitId, resActivateGridMaster -> {
									if (resActivateGridMaster.succeeded()) {
										// OK, so clear the first inconsistency
										// OK なので一回目の不整合をクリア
										errors.remove("gridMasterEnsuring");
										req.reply(properGridMasterUnitId);
									} else {
										req.fail(-1, resActivateGridMaster.cause().getMessage());
									}
								});
							} else {
								if (ReplyFailureUtil.isRecipientFailure(repGridMasterDeactivation)) {
									req.fail(-1, repGridMasterDeactivation.cause().getMessage());
								} else if (ReplyFailureUtil.isNoHandlers(repGridMasterDeactivation)) {
									ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, "Communication failed on EventBus", repGridMasterDeactivation.cause(), req);
								} else {
									ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.WARN, "Communication failed on EventBus", repGridMasterDeactivation.cause(), req);
								}
							}
						});
					}
				} else if (ReplyFailureUtil.isNoHandlers(repHeloGridMaster)) {
					// No GridMaster anywhere
					// どこにもいない
					if (log.isInfoEnabled()) log.info("no GridMaster exists");
					// Check the GridMaster interlock
					// GridMaster インタロックを確認する
					InterlockUtil.getGridMasterUnitId(vertx, resInterlockGridMasterUnitId -> {
						if (resInterlockGridMasterUnitId.succeeded()) {
							String interlockGridMasterUnitId = resInterlockGridMasterUnitId.result();
							if (null == interlockGridMasterUnitId) {
								// If there is no inconsistency in the interlock
								// インタロックに不整合がなければ
								String properGridMasterUnitId2 = (properGridMasterUnitId != null) ? properGridMasterUnitId : ApisConfig.unitId();
								// Set in the right place
								// 正しいところに立てる
								activateGridMaster_(properGridMasterUnitId2, resActivateGridMaster -> {
									if (resActivateGridMaster.succeeded()) {
										// OK, so clear the first inconsistency
										// OK なので一回目の不整合をクリア
										errors.remove("gridMasterEnsuring");
										req.reply(properGridMasterUnitId2);
									} else {
										req.fail(-1, resActivateGridMaster.cause().getMessage());
									}
								});
							} else {
								// The interlock is inconsistent
								// インタロックに不整合あり
								// Record the error occurrence for the time being
								// エラー発生をひとまず記録する
								errors.add(Boolean.FALSE, "gridMasterEnsuring", "ERROR_1");
								if (1 < errors.getJsonArray("gridMasterEnsuring", "ERROR_1").size()) {
									// If it is not the first occurrence, raise an error
									// 一発目じゃないならエラーにする
									ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, "gridMaster interlock inconsistency; no GridMaster exists, interlockGridMasterUnitId : " + interlockGridMasterUnitId, req);
								} else {
									// Pass through if it is the first occurrence...
									// 一発目ならスルーして...
									ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, "gridMaster interlock inconsistency; no GridMaster exists, interlockGridMasterUnitId : " + interlockGridMasterUnitId);
									Long retryWaitMsec = PolicyKeeping.cache().getLong(DEFAULT_INTERLOCK_INCONSISTENCY_RETRY_WAIT_MSEC, "gridMaster", "gridMasterEnsuring", "interlockInconsistency", "retryWaitMsec");
									// Wait a little and call this process again
									// ちょっと待ってもう一度この処理をよぶ
									vertx.setTimer(retryWaitMsec, timerId -> {
										vertx.eventBus().<String>send(ServiceAddress.Mediator.gridMasterEnsuring(), null, repAgain -> {
											if (repAgain.succeeded()) {
												// Clear the first inconsistency if successful
												// 成功したら一回目の不整合をクリア
												// TODO: Isn't this cleared in a process that is called once more? Doesn't that make it unnecessary?
												// TODO : もう一度呼んだ処理の中でクリアされるのでは？だから不要では？
												errors.remove("gridMasterEnsuring");
												req.reply(repAgain.result().body());
											} else {
												req.fail(-1, repAgain.cause().getMessage());
											}
										});
									});
								}
							}
						} else {
							req.fail(-1, resInterlockGridMasterUnitId.cause().getMessage());
						}
					});
				} else if (ReplyFailureUtil.isRecipientFailure(repHeloGridMaster)) {
					req.fail(-1, repHeloGridMaster.cause().getMessage());
				} else {
					ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.WARN, "Communication failed on EventBus", repHeloGridMaster.cause(), req);
				}
			});
		}).completionHandler(completionHandler);
	}
	private void activateGridMaster_(String unitId, Handler<AsyncResult<Void>> completionHandler) {
		vertx.eventBus().<String>send(ServiceAddress.Mediator.gridMasterActivation(unitId), null, repGridMasterActivation -> {
			if (repGridMasterActivation.succeeded()) {
				String newGridMasterUnitId = repGridMasterActivation.result().body();
				if (unitId.equals(newGridMasterUnitId)) {
					if (log.isInfoEnabled()) log.info("newGridMasterUnitId : " + newGridMasterUnitId);
					completionHandler.handle(Future.succeededFuture());
				} else {
					ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.ERROR, "invalid newGridMasterUnitId : " + newGridMasterUnitId + ", should be : " + unitId, completionHandler);
				}
			} else {
				if (ReplyFailureUtil.isRecipientFailure(repGridMasterActivation)) {
					completionHandler.handle(Future.failedFuture(repGridMasterActivation.cause()));
				} else if (ReplyFailureUtil.isNoHandlers(repGridMasterActivation)) {
					ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, "Communication failed on EventBus", repGridMasterActivation.cause(), completionHandler);
				} else {
					ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.WARN, "Communication failed on EventBus", repGridMasterActivation.cause(), completionHandler);
				}
			}
		});
	}
	/**
	 * Get the ID of the unit where the GridMaster should be situated.
	 * @return the ID of the unit where the GridMaster ought to be situated.
	 *         If {@code null}, anywhere will do.
	 *          
	 * GridMaster が立っているべきユニットの ID を取得する.
	 * @return GridMaster が立っているべきユニットの ID.
	 *         {@code null} ならどこでもよい.
	 */
	private String properGridMasterUnitId_() {
		JsonObject policy = PolicyKeeping.cache().jsonObject();
		String gridMasterSelectionStrategy = Policy.gridMasterSelectionStrategy(policy);
		if ("anywhere".equals(gridMasterSelectionStrategy)) {
			return null;
		} else if ("fixed".equals(gridMasterSelectionStrategy)) {
			return Policy.gridMasterSelectionFixedUnitId(policy);
		} else {
			// The remaining gridMasterSelectionStrategy is "voltageReferenceUnit", so search for the ID of the voltage reference unit
			// 残る gridMasterSelectionStrategy は "voltageReferenceUnit" なので電圧リファレンスユニットの ID を探す
			String voltageReferenceUnitId = DealExecution.voltageReferenceUnitId();
			if (voltageReferenceUnitId == null) {
				if (log.isInfoEnabled()) log.info("no voltage reference unit");
			}
			return voltageReferenceUnitId;
		}
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
	private void doReset_(Message<?> message) {
		vertx.eventBus().publish(ServiceAddress.GridMaster.undeploymentLocal(), null);
		MainLoop.resetExclusiveLock(vertx);
		message.reply(ApisConfig.unitId());
	}

	/**
	 * Set a timer to confirm the existence of a GridMaster.
	 * The timeout duration is calculated randomly, taking the number of units into consideration, based on {@code POLICY.gridMaster.mainLoopPeriodMsec} (default: {@link MainLoop#DEFAULT_MAIN_LOOP_PERIOD_MSEC}).
	 *          
	 * GridMaster の存在確認タイマ設定.
	 * 待ち時間は {@code POLICY.gridMaster.mainLoopPeriodMsec} ( デフォルト値 {@link MainLoop#DEFAULT_MAIN_LOOP_PERIOD_MSEC} ) をベースにユニット数を考慮しランダムに算出.
	 */
	private void setGridMasterWatchingTimer_() {
		Long mainLoopPeriodMsec = PolicyKeeping.cache().getLong(MainLoop.DEFAULT_MAIN_LOOP_PERIOD_MSEC, "gridMaster", "mainLoopPeriodMsec");
		int numberOfMembers = PolicyKeeping.numberOfMembers();
		long delay = (long) (mainLoopPeriodMsec * numberOfMembers * 2L * Math.random());
		// Timer wait time = {base period} * {number of units} * 2 * {random number from 0 to 1}
		// タイマの待ち時間 = ベースの周期 x ユニット数 x 2 x 乱数 ( 0 〜 1 )
		// → In other words, when all units are running, the frequency is roughly the same as the base cycle for the entire cluster.
		// → つまり全ユニットが起動している状況でクラスタ全体でベース周期くらいの頻度になる計算
		if (delay == 0L) delay = mainLoopPeriodMsec;
		setGridMasterWatchingTimer_(delay);
	}
	/**
	 * Set a timer to confirm the existence of a GridMaster.
	 * @param delay the wait time [ms]
	 *          
	 * GridMaster の存在確認タイマ設定.
	 * @param delay 待ち時間 [ms]
	 */
	private void setGridMasterWatchingTimer_(long delay) {
		gridMasterWatchingTimerId_ = vertx.setTimer(delay, this::gridMasterWatchingTimerHandler_);
	}
	/**
	 * GridMaster existence confirmation timer process.
	 * @param timerId timer ID
	 *          
	 * GridMaster の存在確認タイマ処理.
	 * @param timerId タイマ ID
	 */
	private void gridMasterWatchingTimerHandler_(Long timerId) {
		if (stopped_) return;
		if (null == timerId || timerId.longValue() != gridMasterWatchingTimerId_) {
			ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, "illegal timerId : " + timerId + ", gridMasterWatchingTimerId_ : " + gridMasterWatchingTimerId_);
			return;
		}
		if (!StateHandling.isInOperation()) {
			// Pass through if not in full operation
			// 本稼働中でなければスルー
			setGridMasterWatchingTimer_();
		} else {
			vertx.eventBus().<String>send(ServiceAddress.GridMaster.helo(), null, repHeloGridMaster -> {
				if (repHeloGridMaster.succeeded()) {
					// If there is a GridMaster somewhere, then this is OK
					// どこかに GridMaster がいたらそれで OK
				} else if (ReplyFailureUtil.isNoHandlers(repHeloGridMaster)) {
					// If there isn't any GridMaster
					// GridMaster がいなかったら
					Long ensureWaitMsec = PolicyKeeping.cache().getLong(DEFAULT_ABSENCE_ENSURE_WAIT_MSEC, "gridMaster", "gridMasterWatching", "absence", "ensureWaitMsec");
					long delay = (long) (ensureWaitMsec + ensureWaitMsec * Math.random());
					// Timer wait time = {base period} * 2 * {random number from 0 to 1}
					// タイマの待ち時間 = ベースの時間 x 2 x 乱数 ( 0 〜 1 )
					if (delay == 0L) delay = ensureWaitMsec;
					if (log.isInfoEnabled()) log.info("no GridMaster exists, wait " + delay + "ms and check again ...");
					vertx.setTimer(delay, v -> {
						// Call the GridMaster existence management process after waiting for a while
						// しばらく待ったあと GridMaster 存在メンテナンス処理を叩く
						vertx.eventBus().send(ServiceAddress.Mediator.gridMasterEnsuring(), null);
					});
				} else if (ReplyFailureUtil.isRecipientFailure(repHeloGridMaster)) {
					// nop
				} else {
					ErrorUtil.report(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.WARN, "Communication failed on EventBus", repHeloGridMaster.cause());
				}
				setGridMasterWatchingTimer_();
			});
		}
	}

}
