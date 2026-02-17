package jp.co.sony.csl.dcoes.apis.main.app.mediator;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.core.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.ServiceAddress;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.ReplyFailureUtil;
import jp.co.sony.csl.dcoes.apis.main.app.PolicyKeeping;
import jp.co.sony.csl.dcoes.apis.main.app.StateHandling;
import jp.co.sony.csl.dcoes.apis.main.util.ApisConfig;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

public class ExternalRequestHandling extends AbstractVerticle {
	private static final Logger log = LoggerFactory.getLogger(ExternalRequestHandling.class);

	@Override
	public void start(Promise<Void> startPromise) throws Exception {
		startExternalRequestHandlingService_(resExternalRequestHandling -> {
			if (resExternalRequestHandling.succeeded()) {
				if (log.isTraceEnabled())
					log.trace("started : " + deploymentID());
				startPromise.complete();
			} else {
				log.error(resExternalRequestHandling.cause().getMessage());
				startPromise.fail(resExternalRequestHandling.cause());
			}
		});
	}

	@Override
	public void stop() throws Exception {
		if (log.isTraceEnabled())
			log.trace("stopped : " + deploymentID());
	}

	private void startExternalRequestHandlingService_(Handler<AsyncResult<Void>> onComplete) {
		vertx.eventBus().<JsonObject>consumer(ServiceAddress.Mediator.externalRequest(), req -> {
			if (!StateHandling.isInOperation())
				return;
			String replyAddress = req.headers().get("replyAddress");
			if (replyAddress != null) {
				JsonObject request = req.body();
				if (log.isDebugEnabled())
					log.debug("request received : " + request);
				if (request != null) {
					String requestUnitId = request.getString("unitId");
					if (requestUnitId != null) {
						if (!ApisConfig.unitId().equals(requestUnitId)) {
							if (PolicyKeeping.isMember(requestUnitId)) {
								vertx.eventBus().<JsonObject>request(ServiceAddress.User.mediatorRequest(), request,
										rep -> {
											if (rep.succeeded()) {
												JsonObject accept = rep.result().body();
												if (accept != null) {
													Integer dealAmountMinWh = PolicyKeeping.cache().getInteger(0,
															"mediator", "deal", "amountMinWh");
													Integer amountWh = accept.getInteger("amountWh", 0);
													if (dealAmountMinWh < amountWh) {
														accept.put("dealGridCurrentA", PolicyKeeping.cache()
																.getFloat(0F, "mediator", "deal", "gridCurrentA"));
														accept.put("unitId", ApisConfig.unitId());
														vertx.eventBus().send(replyAddress, accept);
														if (log.isDebugEnabled())
															log.debug("accept sent back to " + requestUnitId + " : "
																	+ accept);
													} else {
														if (log.isDebugEnabled())
															log.debug("accept amount : " + amountWh
																	+ " ; less than dealAmountMinWh : "
																	+ dealAmountMinWh);
													}
												}
											} else {
												if (ReplyFailureUtil.isRecipientFailure(rep)) {
												} else if (ReplyFailureUtil.isTimeout(rep)) {
													ErrorUtil.report(vertx, Error.Category.FRAMEWORK,
															Error.Extent.LOCAL, Error.Level.WARN,
															"Communication failed on EventBus", rep.cause());
												} else {
													ErrorUtil.report(vertx, Error.Category.FRAMEWORK,
															Error.Extent.LOCAL, Error.Level.ERROR,
															"Communication failed on EventBus", rep.cause());
												}
											}
										});
							} else {
								ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN,
										"request received from illegal unit : " + requestUnitId + "; request : "
												+ request);
							}
						} else {
							if (log.isTraceEnabled())
								log.trace("this is my request");
						}
					} else {
						ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN,
								"no unitId in request : " + request);
					}
				} else {
					ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN,
							"request is null");
				}
			} else {
				ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN,
						"no replyAddress in request header : " + req.headers());
			}
		}).completionHandler(onComplete);
	}

}
