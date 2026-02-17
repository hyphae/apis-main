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

import java.util.ArrayList;
import java.util.List;

import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.ServiceAddress;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectUtil;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.ReplyFailureUtil;
import jp.co.sony.csl.dcoes.apis.main.app.StateHandling;
import jp.co.sony.csl.dcoes.apis.main.evaluation.scenario.ScenarioEvaluation;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

public class MediatorAcceptsHandling extends AbstractVerticle {
	private static final Logger log = LoggerFactory.getLogger(MediatorAcceptsHandling.class);

	@Override
	public void start(Promise<Void> startPromise) throws Exception {
		startMediatorAcceptsHandlingService_(resMediatorAcceptsHandling -> {
			if (resMediatorAcceptsHandling.succeeded()) {
				if (log.isTraceEnabled())
					log.trace("started : " + deploymentID());
				startPromise.complete();
			} else {
				startPromise.fail(resMediatorAcceptsHandling.cause());
			}
		});
	}

	@Override
	public void stop() throws Exception {
		if (log.isTraceEnabled())
			log.trace("stopped : " + deploymentID());
	}

	private void startMediatorAcceptsHandlingService_(Handler<AsyncResult<Void>> onComplete) {
		vertx.eventBus().<JsonObject>localConsumer(ServiceAddress.User.mediatorAccepts(), req -> {
			JsonObject values = req.body();
			JsonObject request = values.getJsonObject("request");
			JsonArray accepts = values.getJsonArray("accepts");
			if (log.isDebugEnabled())
				log.debug("accepts received : " + accepts);
			if (request != null && accepts != null && !accepts.isEmpty()) {
				doHandleMediatorAccepts_(request, accepts, resHandleMediatorAccepts -> {
					if (resHandleMediatorAccepts.succeeded()) {
						req.reply(resHandleMediatorAccepts.result());
					} else {
						req.fail(-1, resHandleMediatorAccepts.cause().getMessage());
					}
				});
			} else {
				ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN,
						"request is null and/or accepts is null or empty; request : " + request + ", accepts : "
								+ accepts,
						req);
			}
		}).completionHandler(onComplete);
	}

	private void doHandleMediatorAccepts_(JsonObject request, JsonArray accepts,
			Handler<AsyncResult<JsonObject>> onComplete) {
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
									doHandleMediatorAccepts__(request, accepts, onComplete);
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

	private void doHandleMediatorAccepts__(JsonObject request, JsonArray accepts_,
			Handler<AsyncResult<JsonObject>> onComplete) {
		List<JsonObject> accepts = new ArrayList<>();
		for (Object obj : accepts_) {
			if (obj instanceof JsonObject) {
				accepts.add((JsonObject) obj);
			}
		}
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
							ScenarioEvaluation.chooseAccept(vertx, scenario, data, request, accepts, resEvaluation -> {
								if (resEvaluation.succeeded()) {
									JsonObject accept = resEvaluation.result();
									if (accept != null) {
										String direction = request.getString("type");
										vertx.eventBus().<Boolean>request(
												ServiceAddress.Controller.batteryCapacityTesting(), direction,
												repBatteryCapacityTest -> {
													if (repBatteryCapacityTest.succeeded()) {
														if (repBatteryCapacityTest.result().body()) {
															onComplete.handle(Future.succeededFuture(accept));
														} else {
															onComplete.handle(Future.succeededFuture());
														}
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
