package jp.co.sony.csl.dcoes.apis.main.app.user;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.ServiceAddress;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectUtil;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.ReplyFailureUtil;
import jp.co.sony.csl.dcoes.apis.main.app.PolicyKeeping;
import jp.co.sony.csl.dcoes.apis.main.app.StateHandling;
import jp.co.sony.csl.dcoes.apis.main.app.user.util.Misc;
import jp.co.sony.csl.dcoes.apis.main.evaluation.scenario.ScenarioEvaluation;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

public class HouseKeeping extends AbstractVerticle {
	private static final Logger log = LoggerFactory.getLogger(HouseKeeping.class);

	private static final Long DEFAULT_HOUSE_KEEPING_PERIOD_MSEC = 60000L;

	private long houseKeepingTimerId_ = 0L;
	private boolean stopped_ = false;

	@Override
	public void start(Promise<Void> startPromise) throws Exception {
		houseKeepingTimerHandler_(0L);
		if (log.isTraceEnabled())
			log.trace("started : " + deploymentID());
		startPromise.complete();
	}

	@Override
	public void stop() throws Exception {
		stopped_ = true;
		if (log.isTraceEnabled())
			log.trace("stopped : " + deploymentID());
	}

	private void setHouseKeepingTimer_() {
		Long delay = PolicyKeeping.cache().getLong(DEFAULT_HOUSE_KEEPING_PERIOD_MSEC, "user", "houseKeepingPeriodMsec");
		setHouseKeepingTimer_(delay);
	}

	private void setHouseKeepingTimer_(long delay) {
		houseKeepingTimerId_ = vertx.setTimer(delay, this::houseKeepingTimerHandler_);
	}

	private void houseKeepingTimerHandler_(Long timerId) {
		if (stopped_)
			return;
		if (null == timerId || timerId.longValue() != houseKeepingTimerId_) {
			ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN,
					"illegal timerId : " + timerId + ", houseKeepingTimerId_ : " + houseKeepingTimerId_);
			return;
		}
		if (!StateHandling.isInOperation()) {
			setHouseKeepingTimer_();
		} else {
			doHouseKeeping_(res -> {
				setHouseKeepingTimer_();
			});
		}
	}

	private void doHouseKeeping_(Handler<AsyncResult<Void>> onComplete) {
		if (ErrorCollection.hasErrors()) {
			if (log.isInfoEnabled())
				log.info("this unit has errors : " + ErrorCollection.cache.jsonObject());
			onComplete.handle(Future.succeededFuture());
		} else {
			vertx.eventBus().<Boolean>request(ServiceAddress.GridMaster.errorTesting(), null, repGlobalErrors -> {
				if (repGlobalErrors.succeeded()) {
					Boolean hasGlobalErrors = repGlobalErrors.result().body();
					if (hasGlobalErrors != null && hasGlobalErrors) {
						if (log.isInfoEnabled())
							log.info("global error exists");
						onComplete.handle(Future.succeededFuture());
					} else {
						StateHandling.operationMode(vertx, resOperationMode -> {
							if (resOperationMode.succeeded()) {
								String operationMode = resOperationMode.result();
								if ("autonomous".equals(operationMode)) {
									doHouseKeeping__(onComplete);
								} else {
									if (log.isInfoEnabled())
										log.info("operationMode is not autonomous : " + operationMode);
									onComplete.handle(Future.succeededFuture());
								}
							} else {
								onComplete.handle(Future.failedFuture(resOperationMode.cause()));
							}
						});
					}
				} else {
					if (ReplyFailureUtil.isRecipientFailure(repGlobalErrors)) {
						onComplete.handle(Future.failedFuture(repGlobalErrors.cause()));
					} else if (ReplyFailureUtil.isNoHandlers(repGlobalErrors)) {
						ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN,
								"Communication failed on EventBus", repGlobalErrors.cause(), onComplete);
					} else if (ReplyFailureUtil.isTimeout(repGlobalErrors)) {
						ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.WARN,
								"Communication failed on EventBus", repGlobalErrors.cause(), onComplete);
					} else {
						ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR,
								"Communication failed on EventBus", repGlobalErrors.cause(), onComplete);
					}
				}
			});
		}
	}

	private void doHouseKeeping__(Handler<AsyncResult<Void>> onComplete) {
		vertx.eventBus().<JsonObject>request(ServiceAddress.Controller.unitData(), null, repData -> {
			if (repData.succeeded()) {
				JsonObject data = repData.result().body();
				Integer dealInterlockCapacity = JsonObjectUtil.getInteger(data, "apis", "deal_interlock_capacity");
				JsonArray dealIds = JsonObjectUtil.getJsonArray(data, "apis", "deal_id_list");
				if (dealInterlockCapacity != null && 0 < dealInterlockCapacity
						&& (dealIds == null || dealIds.size() < dealInterlockCapacity)) {
					String dateTime = data.getString("time");
					vertx.eventBus().<JsonObject>request(ServiceAddress.User.scenario(), dateTime, repScenario -> {
						if (repScenario.succeeded()) {
							JsonObject scenario = repScenario.result().body();
							ScenarioEvaluation.checkStatus(vertx, scenario, data, resEvaluation -> {
								if (resEvaluation.succeeded()) {
									JsonObject request = resEvaluation.result();
									if (request != null) {
										String direction = request.getString("type");
										vertx.eventBus().<Boolean>request(
												ServiceAddress.Controller.batteryCapacityTesting(), direction,
												repBatteryCapacityTest -> {
													if (repBatteryCapacityTest.succeeded()) {
														if (repBatteryCapacityTest.result().body()) {
															Float efficientGridVoltageV = Misc
																	.efficientGridVoltageV_(data);
															if (efficientGridVoltageV != null) {
																request.put("efficientGridVoltageV",
																		efficientGridVoltageV);
															}
															request.put("dateTime", dateTime);
															vertx.eventBus().send(
																	ServiceAddress.Mediator.internalRequest(), request);
														}
														onComplete.handle(Future.succeededFuture());
													} else {
														onComplete.handle(
																Future.failedFuture(repBatteryCapacityTest.cause()));
													}
												});
									} else {
										onComplete.handle(Future.succeededFuture());
									}
								} else {
									onComplete.handle(Future.failedFuture(resEvaluation.cause()));
								}
							});
						} else {
							if (ReplyFailureUtil.isRecipientFailure(repScenario)) {
								onComplete.handle(Future.failedFuture(repScenario.cause()));
							} else {
								ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL,
										Error.Level.ERROR, "Communication failed on EventBus", repScenario.cause(),
										onComplete);
							}
						}
					});
				} else {
					if (log.isInfoEnabled())
						log.info("unit is busy; dealInterlockCapacity : " + dealInterlockCapacity + ", dealIds : "
								+ dealIds);
					onComplete.handle(Future.succeededFuture());
				}
			} else {
				if (ReplyFailureUtil.isRecipientFailure(repData)) {
					onComplete.handle(Future.failedFuture(repData.cause()));
				} else {
					ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR,
							"Communication failed on EventBus", repData.cause(), onComplete);
				}
			}
		});
	}

}
