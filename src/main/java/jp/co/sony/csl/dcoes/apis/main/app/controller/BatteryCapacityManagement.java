package jp.co.sony.csl.dcoes.apis.main.app.controller;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jp.co.sony.csl.dcoes.apis.common.Deal;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.ServiceAddress;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.FileSystemExclusiveLockUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ApisConfig;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

public class BatteryCapacityManagement extends AbstractVerticle {
	private static final Logger log = LoggerFactory.getLogger(BatteryCapacityManagement.class);

	private static final Long DEFAULT_HELO_PERIOD_MSEC = 5000L;

	@Override
	public void start(Promise<Void> startPromise) throws Exception {
		if (log.isInfoEnabled())
			log.info("batteryCapacityManagement : " + ApisConfig.isBatteryCapacityManagementEnabled());
		startBatteryCapacityTestingService_(resBatteryCapacityTesting -> {
			if (resBatteryCapacityTesting.succeeded()) {
				startBatteryCapacityManagingService_(resBatteryCapacityManaging -> {
					if (resBatteryCapacityManaging.succeeded()) {
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
						startPromise.fail(resBatteryCapacityManaging.cause());
					}
				});
			} else {
				startPromise.fail(resBatteryCapacityTesting.cause());
			}
		});
	}

	@Override
	public void stop() throws Exception {
		if (log.isTraceEnabled())
			log.trace("stopped : " + deploymentID());
	}

	private String lockName_(Deal.Direction direction) {
		return "batteryCapacity." + direction.name();
	}

	private void startBatteryCapacityTestingService_(Handler<AsyncResult<Void>> onComplete) {
		vertx.eventBus().<String>localConsumer(ServiceAddress.Controller.batteryCapacityTesting(), req -> {
			if (ApisConfig.isBatteryCapacityManagementEnabled()) {
				Deal.Direction direction = Deal.direction(req.body());
				if (direction != null) {
					doBatteryCapacityTesting_(direction, req);
				} else {
					ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR,
							"invalid request body : " + req.body(), req);
				}
			} else {
				req.reply(Boolean.TRUE);
			}
		}).completionHandler(onComplete);
	}

	private void doBatteryCapacityTesting_(Deal.Direction direction, Message<?> message) {
		String lockName = lockName_(direction);
		FileSystemExclusiveLockUtil.check(vertx, lockName, resCheck -> {
			if (resCheck.succeeded()) {
				if (resCheck.result()) {
					message.reply(Boolean.TRUE);
				} else {
					FileSystemExclusiveLockUtil.lock(vertx, lockName, true, resLock -> {
						if (resLock.succeeded()) {
							if (resLock.result()) {
								FileSystemExclusiveLockUtil.unlock(vertx, lockName, true, resUnlock -> {
									if (resUnlock.succeeded()) {
										message.reply(Boolean.TRUE);
									} else {
										ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL,
												Error.Level.FATAL, resUnlock.cause(), message);
									}
								});
							} else {
								if (log.isInfoEnabled())
									log.info("battery over-capacity ; direction : " + direction);
								message.reply(Boolean.FALSE);
							}
						} else {
							ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL,
									Error.Level.FATAL, resLock.cause(), message);
						}
					});
				}
			} else {
				ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.FATAL,
						resCheck.cause(), message);
			}
		});
	}

	private void startBatteryCapacityManagingService_(Handler<AsyncResult<Void>> onComplete) {
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
							ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR,
									"unknown command : " + command, req);
						}
					} else {
						ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR,
								"invalid deal : " + deal, req);
					}
				} else {
					ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR,
							"invalid request body : " + deal, req);
				}
			} else {
				req.reply(Boolean.TRUE);
			}
		}).completionHandler(onComplete);
	}

	private void doBatteryCapacityAcquiring_(Deal.Direction direction, Message<?> message) {
		FileSystemExclusiveLockUtil.lock(vertx, lockName_(direction), true, resLock -> {
			if (resLock.succeeded()) {
				if (!resLock.result()) {
					if (log.isInfoEnabled())
						log.info("battery over-capacity ; direction : " + direction);
				}
				message.reply(resLock.result());
			} else {
				ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.FATAL,
						resLock.cause(), message);
			}
		});
	}

	private void doBatteryCapacityReleasing_(Deal.Direction direction, Message<?> message) {
		FileSystemExclusiveLockUtil.unlock(vertx, lockName_(direction), true, resUnlock -> {
			if (resUnlock.succeeded()) {
				message.reply(resUnlock.result());
			} else {
				ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.FATAL,
						resUnlock.cause(), message);
			}
		});
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
		if (ApisConfig.isBatteryCapacityManagementEnabled()) {
			doBatteryCapacityResetting_(message);
		} else {
			message.reply(ApisConfig.unitId());
		}
	}

	private void doBatteryCapacityResetting_(Message<?> message) {
		Promise<Boolean> unlockDischargePromise = Promise.promise();
		Promise<Boolean> unlockChargePromise = Promise.promise();
		FileSystemExclusiveLockUtil.unlock(vertx, lockName_(Deal.Direction.DISCHARGE), true, unlockDischargePromise);
		FileSystemExclusiveLockUtil.unlock(vertx, lockName_(Deal.Direction.CHARGE), true, unlockChargePromise);
		Future.all(unlockDischargePromise.future(), unlockChargePromise.future()).onComplete(ar -> {
			FileSystemExclusiveLockUtil.resetExclusiveLock(vertx);
			if (ar.succeeded()) {
				message.reply(ApisConfig.unitId());
			} else {
				ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.FATAL,
						ar.cause(), message);
			}
		});
	}

}
