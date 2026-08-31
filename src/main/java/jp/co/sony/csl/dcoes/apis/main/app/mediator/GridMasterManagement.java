package jp.co.sony.csl.dcoes.apis.main.app.mediator;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.core.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

public class GridMasterManagement extends AbstractVerticle {
	private static final Logger log = LoggerFactory.getLogger(GridMasterManagement.class);

	private static final Long DEFAULT_INTERLOCK_INCONSISTENCY_RETRY_WAIT_MSEC = 2000L;
	private static final Long DEFAULT_ABSENCE_ENSURE_WAIT_MSEC = 5000L;

	private static final JsonObjectWrapper errors = new JsonObjectWrapper();

	private long gridMasterWatchingTimerId_ = 0L;
	private boolean stopped_ = false;

	@Override
	public void start(Promise<Void> startPromise) throws Exception {
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
												if (log.isTraceEnabled())
													log.trace("started : " + deploymentID());
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
								startPromise.fail(resGridMasterEnsuring.cause());
							}
						});
					} else {
						startPromise.fail(resGridMasterDeactivation.cause());
					}
				});
			} else {
				startPromise.fail(resGridMasterActivation.cause());
			}
		});
	}

	@Override
	public void stop() throws Exception {
		stopped_ = true;
		if (log.isTraceEnabled())
			log.trace("stopped : " + deploymentID());
	}

	private void startGridMasterActivationService_(Handler<AsyncResult<Void>> onComplete) {
		vertx.eventBus().<Void>consumer(ServiceAddress.Mediator.gridMasterActivation(ApisConfig.unitId()), req -> {
			if (!StateHandling.isStopping()) {
				MainLoop.acquirePrivilegedExclusiveLock(vertx, resExclusiveLock1 -> {
					if (resExclusiveLock1.succeeded()) {
						LocalExclusiveLock.Lock lock1 = resExclusiveLock1.result();
						doGridMasterActivationWithExclusiveLock_(resDoGridMasterActivationWithExclusiveLock -> {
							lock1.release();
							if (resDoGridMasterActivationWithExclusiveLock.succeeded()) {
								req.reply(ApisConfig.unitId());
							} else {
								req.fail(-1, resDoGridMasterActivationWithExclusiveLock.cause().getMessage());
							}
						});
					} else {
						ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN,
								resExclusiveLock1.cause(), req);
					}
				});
			} else {
				ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN,
						"this unit is stopping ...", req);
			}
		}).completionHandler(onComplete);
	}

	private void doGridMasterActivationWithExclusiveLock_(Handler<AsyncResult<Void>> onComplete) {
		DeliveryOptions acquireOptions = new DeliveryOptions().addHeader("command", "acquire");
		vertx.eventBus().request(ServiceAddress.Mediator.gridMasterInterlocking(), ApisConfig.unitId(), acquireOptions,
				repAcquire -> {
					if (repAcquire.succeeded()) {
						vertx.deployVerticle(new GridMaster(), resDeployGridMaster -> {
							if (resDeployGridMaster.succeeded()) {
								onComplete.handle(Future.succeededFuture());
							} else {
								log.error(resDeployGridMaster.cause().getMessage());
								DeliveryOptions releaseOptions = new DeliveryOptions().addHeader("command", "release");
								vertx.eventBus().request(ServiceAddress.Mediator.gridMasterInterlocking(),
										ApisConfig.unitId(), releaseOptions, repRelease -> {
											if (repRelease.failed()) {
												if (!ReplyFailureUtil.isRecipientFailure(repRelease)) {
													ErrorUtil.report(vertx, Error.Category.FRAMEWORK,
															Error.Extent.LOCAL, Error.Level.ERROR,
															"Communication failed on EventBus", repRelease.cause());
												}
											}
											ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL,
													Error.Level.ERROR, resDeployGridMaster.cause(), onComplete);
										});
							}
						});
					} else {
						if (ReplyFailureUtil.isRecipientFailure(repAcquire)) {
							onComplete.handle(Future.failedFuture(repAcquire.cause()));
						} else {
							ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL,
									Error.Level.ERROR, "Communication failed on EventBus", repAcquire.cause(),
									onComplete);
						}
					}
				});
	}

	private void startGridMasterDeactivationService_(Handler<AsyncResult<Void>> onComplete) {
		vertx.eventBus().<Void>consumer(ServiceAddress.Mediator.gridMasterDeactivation(ApisConfig.unitId()), req -> {
			MainLoop.acquirePrivilegedExclusiveLock(vertx, resExclusiveLock1 -> {
				if (resExclusiveLock1.succeeded()) {
					LocalExclusiveLock.Lock lock1 = resExclusiveLock1.result();
					doGridMasterDeactivationWithExclusiveLock_(resDoGridMasterDeactivationWithExclusiveLock -> {
						lock1.release();
						if (resDoGridMasterDeactivationWithExclusiveLock.succeeded()) {
							req.reply(ApisConfig.unitId());
						} else {
							req.fail(-1, resDoGridMasterDeactivationWithExclusiveLock.cause().getMessage());
						}
					});
				} else {
					ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN,
							resExclusiveLock1.cause(), req);
				}
			});
		}).completionHandler(onComplete);
	}

	private void doGridMasterDeactivationWithExclusiveLock_(Handler<AsyncResult<Void>> onComplete) {
		vertx.eventBus().request(ServiceAddress.GridMaster.undeploymentLocal(), null, repUndeploy -> {
			if (repUndeploy.succeeded()) {
				DeliveryOptions releaseOptions = new DeliveryOptions().addHeader("command", "release");
				vertx.eventBus().request(ServiceAddress.Mediator.gridMasterInterlocking(), ApisConfig.unitId(),
						releaseOptions, repRelease -> {
							if (repRelease.succeeded()) {
								onComplete.handle(Future.succeededFuture());
							} else {
								if (ReplyFailureUtil.isRecipientFailure(repRelease)) {
									onComplete.handle(Future.failedFuture(repRelease.cause()));
								} else {
									ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL,
											Error.Level.ERROR, "Communication failed on EventBus", repRelease.cause(),
											onComplete);
								}
							}
						});
			} else {
				if (ReplyFailureUtil.isNoHandlers(repUndeploy)) {
					onComplete.handle(Future.succeededFuture());
				} else if (ReplyFailureUtil.isRecipientFailure(repUndeploy)) {
					onComplete.handle(Future.failedFuture(repUndeploy.cause()));
				} else {
					ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR,
							"Communication failed on EventBus", repUndeploy.cause(), onComplete);
				}
			}
		});
	}

	private void startGridMasterEnsuringService_(Handler<AsyncResult<Void>> onComplete) {
		vertx.eventBus().<Void>localConsumer(ServiceAddress.Mediator.gridMasterEnsuring(), req -> {
			String properGridMasterUnitId = properGridMasterUnitId_();
			vertx.eventBus().<String>request(ServiceAddress.GridMaster.helo(), null, repHeloGridMaster -> {
				if (repHeloGridMaster.succeeded()) {
					String gridMasterUnitId = repHeloGridMaster.result().body();
					if (log.isInfoEnabled())
						log.info("gridMasterUnitId : " + gridMasterUnitId);
					if (properGridMasterUnitId == null || properGridMasterUnitId.equals(gridMasterUnitId)) {
						if (log.isInfoEnabled())
							log.info("no need to move");
						InterlockUtil.getGridMasterUnitId(vertx, resInterlockGridMasterUnitId -> {
							if (resInterlockGridMasterUnitId.succeeded()) {
								String interlockGridMasterUnitId = resInterlockGridMasterUnitId.result();
								if (gridMasterUnitId.equals(interlockGridMasterUnitId)) {
									errors.remove("gridMasterEnsuring");
									req.reply(gridMasterUnitId);
								} else {
									ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.GLOBAL,
											Error.Level.ERROR,
											"gridMaster interlock inconsistency; gridMasterUnitId : " + gridMasterUnitId
													+ ", interlockGridMasterUnitId : " + interlockGridMasterUnitId,
											req);
								}
							} else {
								ErrorExceptionUtil.reportIfNeedAndFail(vertx, resInterlockGridMasterUnitId.cause(),
										req);
							}
						});
					} else {
						if (log.isInfoEnabled())
							log.info("should move GridMaster");
						vertx.eventBus().<String>request(
								ServiceAddress.Mediator.gridMasterDeactivation(gridMasterUnitId), null,
								repGridMasterDeactivation -> {
									if (repGridMasterDeactivation.succeeded()) {
										activateGridMaster_(properGridMasterUnitId, resActivateGridMaster -> {
											if (resActivateGridMaster.succeeded()) {
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
											ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL,
													Error.Level.ERROR, "Communication failed on EventBus",
													repGridMasterDeactivation.cause(), req);
										} else {
											ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL,
													Error.Level.WARN, "Communication failed on EventBus",
													repGridMasterDeactivation.cause(), req);
										}
									}
								});
					}
				} else if (ReplyFailureUtil.isNoHandlers(repHeloGridMaster)) {
					if (log.isInfoEnabled())
						log.info("no GridMaster exists");
					InterlockUtil.getGridMasterUnitId(vertx, resInterlockGridMasterUnitId -> {
						if (resInterlockGridMasterUnitId.succeeded()) {
							String interlockGridMasterUnitId = resInterlockGridMasterUnitId.result();
							if (null == interlockGridMasterUnitId) {
								String properGridMasterUnitId2 = (properGridMasterUnitId != null)
										? properGridMasterUnitId
										: ApisConfig.unitId();
								activateGridMaster_(properGridMasterUnitId2, resActivateGridMaster -> {
									if (resActivateGridMaster.succeeded()) {
										errors.remove("gridMasterEnsuring");
										req.reply(properGridMasterUnitId2);
									} else {
										req.fail(-1, resActivateGridMaster.cause().getMessage());
									}
								});
							} else {
								errors.add(Boolean.FALSE, "gridMasterEnsuring", "ERROR_1");
								if (1 < errors.getJsonArray("gridMasterEnsuring", "ERROR_1").size()) {
									ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL,
											Error.Level.ERROR,
											"gridMaster interlock inconsistency; no GridMaster exists, interlockGridMasterUnitId : "
													+ interlockGridMasterUnitId,
											req);
								} else {
									ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN,
											"gridMaster interlock inconsistency; no GridMaster exists, interlockGridMasterUnitId : "
													+ interlockGridMasterUnitId);
									Long retryWaitMsec = PolicyKeeping.cache().getLong(
											DEFAULT_INTERLOCK_INCONSISTENCY_RETRY_WAIT_MSEC, "gridMaster",
											"gridMasterEnsuring", "interlockInconsistency", "retryWaitMsec");
									vertx.setTimer(retryWaitMsec, timerId -> {
										vertx.eventBus().<String>request(ServiceAddress.Mediator.gridMasterEnsuring(),
												null, repAgain -> {
													if (repAgain.succeeded()) {
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
					ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.WARN,
							"Communication failed on EventBus", repHeloGridMaster.cause(), req);
				}
			});
		}).completionHandler(onComplete);
	}

	private void activateGridMaster_(String unitId, Handler<AsyncResult<Void>> onComplete) {
		vertx.eventBus().<String>request(ServiceAddress.Mediator.gridMasterActivation(unitId), null,
				repGridMasterActivation -> {
					if (repGridMasterActivation.succeeded()) {
						String newGridMasterUnitId = repGridMasterActivation.result().body();
						if (unitId.equals(newGridMasterUnitId)) {
							if (log.isInfoEnabled())
								log.info("newGridMasterUnitId : " + newGridMasterUnitId);
							onComplete.handle(Future.succeededFuture());
						} else {
							ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.ERROR,
									"invalid newGridMasterUnitId : " + newGridMasterUnitId + ", should be : " + unitId,
									onComplete);
						}
					} else {
						if (ReplyFailureUtil.isRecipientFailure(repGridMasterActivation)) {
							onComplete.handle(Future.failedFuture(repGridMasterActivation.cause()));
						} else if (ReplyFailureUtil.isNoHandlers(repGridMasterActivation)) {
							ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR,
									"Communication failed on EventBus", repGridMasterActivation.cause(), onComplete);
						} else {
							ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL,
									Error.Level.WARN, "Communication failed on EventBus",
									repGridMasterActivation.cause(), onComplete);
						}
					}
				});
	}

	private String properGridMasterUnitId_() {
		JsonObject policy = PolicyKeeping.cache().jsonObject();
		String gridMasterSelectionStrategy = Policy.gridMasterSelectionStrategy(policy);
		if ("anywhere".equals(gridMasterSelectionStrategy)) {
			return null;
		} else if ("fixed".equals(gridMasterSelectionStrategy)) {
			return Policy.gridMasterSelectionFixedUnitId(policy);
		} else {
			String voltageReferenceUnitId = DealExecution.voltageReferenceUnitId();
			if (voltageReferenceUnitId == null) {
				if (log.isInfoEnabled())
					log.info("no voltage reference unit");
			}
			return voltageReferenceUnitId;
		}
	}

	private void startResetLocalService_(Handler<AsyncResult<Void>> onComplete) {
		vertx.eventBus().<Void>localConsumer(ServiceAddress.resetLocal(), req -> {
			doReset_(req);
		}).completionHandler(onComplete);
	}

	private void startResetAllService_(Handler<AsyncResult<Void>> onComplete) {
		vertx.eventBus().<Void>consumer(ServiceAddress.resetAll(), req -> {
			doReset_(req);
		}).completionHandler(onComplete);
	}

	private void doReset_(Message<?> message) {
		vertx.eventBus().publish(ServiceAddress.GridMaster.undeploymentLocal(), null);
		MainLoop.resetExclusiveLock(vertx);
		message.reply(ApisConfig.unitId());
	}

	private void setGridMasterWatchingTimer_() {
		Long mainLoopPeriodMsec = PolicyKeeping.cache().getLong(MainLoop.DEFAULT_MAIN_LOOP_PERIOD_MSEC, "gridMaster",
				"mainLoopPeriodMsec");
		int numberOfMembers = PolicyKeeping.numberOfMembers();
		long delay = (long) (mainLoopPeriodMsec * numberOfMembers * 2L * Math.random());
		if (delay == 0L)
			delay = mainLoopPeriodMsec;
		setGridMasterWatchingTimer_(delay);
	}

	private void setGridMasterWatchingTimer_(long delay) {
		gridMasterWatchingTimerId_ = vertx.setTimer(delay, this::gridMasterWatchingTimerHandler_);
	}

	private void gridMasterWatchingTimerHandler_(Long timerId) {
		if (stopped_)
			return;
		if (null == timerId || timerId.longValue() != gridMasterWatchingTimerId_) {
			ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN,
					"illegal timerId : " + timerId + ", gridMasterWatchingTimerId_ : " + gridMasterWatchingTimerId_);
			return;
		}
		if (!StateHandling.isInOperation()) {
			setGridMasterWatchingTimer_();
		} else {
			vertx.eventBus().<String>request(ServiceAddress.GridMaster.helo(), null, repHeloGridMaster -> {
				if (repHeloGridMaster.succeeded()) {
				} else if (ReplyFailureUtil.isNoHandlers(repHeloGridMaster)) {
					Long ensureWaitMsec = PolicyKeeping.cache().getLong(DEFAULT_ABSENCE_ENSURE_WAIT_MSEC, "gridMaster",
							"gridMasterWatching", "absence", "ensureWaitMsec");
					long delay = (long) (ensureWaitMsec + ensureWaitMsec * Math.random());
					if (delay == 0L)
						delay = ensureWaitMsec;
					if (log.isInfoEnabled())
						log.info("no GridMaster exists, wait " + delay + "ms and check again ...");
					vertx.setTimer(delay, v -> {
						vertx.eventBus().request(ServiceAddress.Mediator.gridMasterEnsuring(), null);
					});
				} else if (!ReplyFailureUtil.isRecipientFailure(repHeloGridMaster)) {
					ErrorUtil.report(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.WARN,
							"Communication failed on EventBus", repHeloGridMaster.cause());
				}
				setGridMasterWatchingTimer_();
			});
		}
	}

}
