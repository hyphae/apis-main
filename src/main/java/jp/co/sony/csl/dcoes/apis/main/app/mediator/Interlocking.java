package jp.co.sony.csl.dcoes.apis.main.app.mediator;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.Promise;
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

public class Interlocking extends AbstractVerticle {
	private static final Logger log = LoggerFactory.getLogger(Interlocking.class);

	@Override
	public void start(Promise<Void> startPromise) throws Exception {
		startGridMasterInterlockingService_(resGridMasterInterlocking -> {
			if (resGridMasterInterlocking.succeeded()) {
				startDealInterlockingService_(resDealInterlocking -> {
					if (resDealInterlocking.succeeded()) {
						startResetLocalService_(resResetLocal -> {
							if (resResetLocal.succeeded()) {
								startResetAllService_(resResetAll -> {
									if (resResetAll.succeeded()) {
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
						startPromise.fail(resDealInterlocking.cause());
					}
				});
			} else {
				startPromise.fail(resGridMasterInterlocking.cause());
			}
		});
	}

	@Override
	public void stop() throws Exception {
		if (log.isTraceEnabled())
			log.trace("stopped : " + deploymentID());
	}

	public static int dealInterlockCapacity(Vertx vertx) {
		Float gridCurrentCapacityA = HwConfigKeeping.gridCurrentCapacityA();
		Float dealGridCurrentA = PolicyKeeping.cache().getFloat("mediator", "deal", "gridCurrentA");
		if (gridCurrentCapacityA != null && dealGridCurrentA != null) {
			return (int) (gridCurrentCapacityA / dealGridCurrentA);
		} else {
			ErrorUtil.report(vertx, Error.Category.USER, Error.Extent.LOCAL, Error.Level.ERROR,
					"data deficiency; HWCONFIG.gridCurrentCapacityA : " + gridCurrentCapacityA
							+ ", POLICY.mediator.deal.gridCurrentA : " + dealGridCurrentA);
			return 0;
		}
	}

	private void startGridMasterInterlockingService_(Handler<AsyncResult<Void>> onComplete) {
		vertx.eventBus().<String>localConsumer(ServiceAddress.Mediator.gridMasterInterlocking(), req -> {
			String command = req.headers().get("command");
			String value = req.body();
			if (value != null) {
				if ("acquire".equalsIgnoreCase(command)) {
					InterlockUtil.lockGridMasterUnitId(vertx, value, false, resAcquire -> {
						if (resAcquire.succeeded()) {
							if (log.isInfoEnabled())
								log.info("locked; gridMasterUnitId : " + value);
							req.reply(ApisConfig.unitId());
						} else {
							ErrorExceptionUtil.reportIfNeedAndFail(vertx, resAcquire.cause(), req);
						}
					});
				} else if ("release".equalsIgnoreCase(command)) {
					InterlockUtil.unlockGridMasterUnitId(vertx, value, resRelease -> {
						if (resRelease.succeeded()) {
							if (log.isInfoEnabled())
								log.info("unlocked; gridMasterUnitId : " + value);
							req.reply(ApisConfig.unitId());
						} else {
							ErrorExceptionUtil.reportIfNeedAndFail(vertx, resRelease.cause(), req);
						}
					});
				} else {
					ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN,
							"unknown command : " + command, req);
				}
			} else {
				ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN,
						"invalid request : " + req, req);
			}
		}).completionHandler(onComplete);
	}

	private void startDealInterlockingService_(Handler<AsyncResult<Void>> onComplete) {
		vertx.eventBus().<JsonObject>consumer(ServiceAddress.Mediator.dealInterlocking(ApisConfig.unitId()), req -> {
			String command = req.headers().get("command");
			JsonObject deal = req.body();
			if (deal != null) {
				String dealId = Deal.dealId(deal);
				if ("acquire".equalsIgnoreCase(command)) {
					InterlockUtil.lockDealId(vertx, dealId, dealInterlockCapacity(vertx), true, resAcquire -> {
						if (resAcquire.succeeded()) {
							DeliveryOptions options = new DeliveryOptions().addHeader("command", command);
							vertx.eventBus().<Boolean>request(ServiceAddress.Controller.batteryCapacityManaging(), deal,
									options, repBatteryCapacityAcquire -> {
										if (repBatteryCapacityAcquire.succeeded()
												&& repBatteryCapacityAcquire.result().body()) {
											if (log.isInfoEnabled())
												log.info("locked; dealId : " + dealId);
											req.reply(ApisConfig.unitId());
										} else {
											InterlockUtil.unlockDealId(vertx, dealId, resRelease -> {
												if (resRelease.succeeded()) {
													if (repBatteryCapacityAcquire.succeeded()) {
														req.fail(-1, "batteryCapacityManaging acquire failed");
													} else {
														ErrorExceptionUtil.reportIfNeedAndFail(vertx,
																repBatteryCapacityAcquire.cause(), req);
													}
												} else {
													ErrorExceptionUtil.reportIfNeedAndFail(vertx, resRelease.cause(),
															req);
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
									if (0 < resGet.result().size()) {
										if (log.isInfoEnabled())
											log.info("unlocked; dealId : " + dealId);
										req.reply(ApisConfig.unitId());
									} else {
										DeliveryOptions options = new DeliveryOptions().addHeader("command", command);
										vertx.eventBus().<Boolean>request(
												ServiceAddress.Controller.batteryCapacityManaging(), deal, options,
												repBatteryCapacityRelease -> {
													if (repBatteryCapacityRelease.succeeded()) {
														if (log.isInfoEnabled())
															log.info("unlocked; dealId : " + dealId);
														req.reply(ApisConfig.unitId());
													} else {
														ErrorExceptionUtil.reportIfNeedAndFail(vertx,
																repBatteryCapacityRelease.cause(), req);
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
					ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN,
							"unknown command : " + command, req);
				}
			} else {
				ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN,
						"invalid request : " + req, req);
			}
		}).completionHandler(onComplete);
	}

	private void startResetLocalService_(Handler<AsyncResult<Void>> onComplete) {
		vertx.eventBus().<Void>localConsumer(ServiceAddress.resetLocal(), req -> {
			InterlockUtil.resetExclusiveLock(vertx);
			Promise<Void> resetDealIdPromise = Promise.promise();
			Promise<Void> resetGridMasterUnitIdPromise = Promise.promise();
			doResetLocalDealId_(resetDealIdPromise);
			doResetLocalGridMasterUnitId_(resetGridMasterUnitIdPromise);
			Future.all(resetDealIdPromise.future(), resetGridMasterUnitIdPromise.future()).onComplete(ar -> {
				if (ar.succeeded()) {
					req.reply(ApisConfig.unitId());
				} else {
					ErrorExceptionUtil.reportIfNeedAndFail(vertx, ar.cause(), req);
				}
			});
		}).completionHandler(onComplete);
	}

	private void startResetAllService_(Handler<AsyncResult<Void>> onComplete) {
		vertx.eventBus().<Void>consumer(ServiceAddress.resetAll(), req -> {
			InterlockUtil.resetExclusiveLock(vertx);
			Promise<Void> resetDealIdPromise = Promise.promise();
			Promise<Void> resetGridMasterUnitIdPromise = Promise.promise();
			InterlockUtil.resetDealId(vertx, resetDealIdPromise);
			InterlockUtil.resetGridMasterUnitId(vertx, resetGridMasterUnitIdPromise);
			Future.all(resetDealIdPromise.future(), resetGridMasterUnitIdPromise.future()).onComplete(ar -> {
				if (ar.succeeded()) {
					req.reply(ApisConfig.unitId());
				} else {
					ErrorExceptionUtil.reportIfNeedAndFail(vertx, ar.cause(), req);
				}
			});
		}).completionHandler(onComplete);
	}

	private void doResetLocalDealId_(Handler<AsyncResult<Void>> onComplete) {
		DealUtil.withUnitId(vertx, ApisConfig.unitId(), resDeals -> {
			if (resDeals.succeeded()) {
				if (resDeals.result().isEmpty()) {
					InterlockUtil.resetDealId(vertx, resReset -> {
						if (resReset.succeeded()) {
							onComplete.handle(Future.succeededFuture());
						} else {
							ErrorExceptionUtil.reportIfNeedAndFail(vertx, resReset.cause(), onComplete);
						}
					});
				} else {
					onComplete.handle(Future.succeededFuture());
				}
			} else {
				ErrorExceptionUtil.reportIfNeed(vertx, resDeals.cause());
			}
		});
	}

	private void doResetLocalGridMasterUnitId_(Handler<AsyncResult<Void>> onComplete) {
		InterlockUtil.getGridMasterUnitId(vertx, resGet -> {
			if (resGet.succeeded()) {
				String value = resGet.result();
				if (ApisConfig.unitId().equals(value)) {
					InterlockUtil.unlockGridMasterUnitId(vertx, value, resRelease -> {
						if (resRelease.succeeded()) {
							onComplete.handle(Future.succeededFuture());
						} else {
							ErrorExceptionUtil.reportIfNeedAndFail(vertx, resRelease.cause(), onComplete);
						}
					});
				} else {
					vertx.eventBus().<String>request(ServiceAddress.GridMaster.helo(), null, repHeloGridMaster -> {
						if (repHeloGridMaster.succeeded()) {
							onComplete.handle(Future.succeededFuture());
						} else if (ReplyFailureUtil.isNoHandlers(repHeloGridMaster)) {
							InterlockUtil.resetGridMasterUnitId(vertx, resReset -> {
								if (resReset.succeeded()) {
									onComplete.handle(Future.succeededFuture());
								} else {
									ErrorExceptionUtil.reportIfNeedAndFail(vertx, resReset.cause(), onComplete);
								}
							});
						} else {
							onComplete.handle(Future.succeededFuture());
						}
					});
				}
			} else {
				ErrorExceptionUtil.reportIfNeedAndFail(vertx, resGet.cause(), onComplete);
			}
		});
	}

}
