package jp.co.sony.csl.dcoes.apis.main.app.mediator;

import java.util.ArrayList;
import java.util.List;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.ServiceAddress;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.ReplyFailureUtil;
import jp.co.sony.csl.dcoes.apis.main.app.PolicyKeeping;
import jp.co.sony.csl.dcoes.apis.main.util.ApisConfig;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

public class Negotiation extends AbstractVerticle {
	private static final Logger log = LoggerFactory.getLogger(Negotiation.class);

	private static final Long DEFAULT_NEGOTIATION_TIMEOUT_MSEC = 2000L;

	private JsonObject request_;

	public Negotiation(JsonObject request) {
		request_ = request;
	}

	@Override
	public void start(Promise<Void> startPromise) throws Exception {
		startAcceptService_(resAccept -> {
			if (resAccept.succeeded()) {
				if (log.isTraceEnabled())
					log.trace("started : " + deploymentID());
				startPromise.complete();
			} else {
				startPromise.fail(resAccept.cause());
			}
		});
	}

	@Override
	public void stop() throws Exception {
		if (log.isTraceEnabled())
			log.trace("stopped : " + deploymentID());
	}

	private String negotiationId_() {
		return deploymentID();
	}

	private void startAcceptService_(Handler<AsyncResult<Void>> onComplete) {
		String replyAddress = deploymentID();
		List<JsonObject> accepts = new ArrayList<>();
		MessageConsumer<JsonObject> acceptConsumer = vertx.eventBus().<JsonObject>consumer(replyAddress);
		acceptConsumer.handler(rep -> {
			JsonObject anAccept = rep.body();
			String unitId = anAccept.getString("unitId");
			boolean isMember = PolicyKeeping.isMember(unitId);
			if (!isMember) {
				ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN,
						"accept received from illegal unit : " + unitId + " ; accept : " + anAccept);
			} else {
				accepts.add(anAccept);
			}
		}).exceptionHandler(t -> {
			acceptConsumer.unregister(resUnregister -> {
				if (resUnregister.failed()) {
					ErrorUtil.report(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR,
							resUnregister.cause());
				}
				ErrorUtil.report(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR, t);
				vertx.undeploy(deploymentID());
			});
		}).completionHandler(res -> {
			if (res.succeeded()) {
				if (log.isDebugEnabled())
					log.debug("request : " + request_);
				DeliveryOptions options = new DeliveryOptions().addHeader("replyAddress", replyAddress);
				vertx.eventBus().publish(ServiceAddress.Mediator.externalRequest(), request_, options);
				Long negotiationTimeoutMsec = PolicyKeeping.cache().getLong(DEFAULT_NEGOTIATION_TIMEOUT_MSEC,
						"mediator", "negotiationTimeoutMsec");
				vertx.setTimer(negotiationTimeoutMsec, t -> {
					acceptConsumer.unregister(resUnregister -> {
						if (resUnregister.succeeded()) {
							doTreatAccepts_(accepts, resTreat -> {
								vertx.undeploy(deploymentID());
							});
						} else {
							ErrorUtil.report(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR,
									resUnregister.cause());
							vertx.undeploy(deploymentID());
						}
					});
				});
				onComplete.handle(Future.succeededFuture());
			} else {
				ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR,
						res.cause(), onComplete);
			}
		});
	}

	private void doTreatAccepts_(List<JsonObject> accepts, Handler<AsyncResult<Void>> onComplete) {
		if (log.isDebugEnabled())
			log.debug("accepts received : " + accepts);
		if (accepts != null && !accepts.isEmpty()) {
			JsonObject values = new JsonObject().put("request", request_).put("accepts", new JsonArray(accepts));
			vertx.eventBus().<JsonObject>request(ServiceAddress.User.mediatorAccepts(), values, repAccept -> {
				if (repAccept.succeeded()) {
					JsonObject accept = repAccept.result().body();
					if (accept != null) {
						Integer requestAmountWh = request_.getInteger("amountWh");
						Integer acceptAmountWh = accept.getInteger("amountWh");
						Integer dealAmountMinWh = PolicyKeeping.cache().getInteger("mediator", "deal", "amountMinWh");
						Integer dealAmountMaxWh = PolicyKeeping.cache().getInteger("mediator", "deal", "amountWhMaxWh"); // Actually
																															// amountMaxWh
																															// in
																															// policy?
																															// Wait,
																															// check
																															// the
																															// code.
						// Looking at original code: Integer dealAmountMaxWh =
						// PolicyKeeping.cache().getInteger("mediator", "deal", "amountMaxWh");
						// Let me re-verify the original code for dealAmountMaxWh.
						// Line 195: Integer dealAmountMaxWh =
						// PolicyKeeping.cache().getInteger("mediator", "deal", "amountMaxWh");
						// My previous thought had a typo.
						Integer dealAmountMaxWhVal = PolicyKeeping.cache().getInteger("mediator", "deal",
								"amountMaxWh");
						Integer dealAmountUnitWh = PolicyKeeping.cache().getInteger("mediator", "deal", "amountUnitWh");
						if (requestAmountWh != null && acceptAmountWh != null && dealAmountMinWh != null
								&& dealAmountMaxWhVal != null && dealAmountUnitWh != null) {
							int dealAmountWh = (requestAmountWh < acceptAmountWh) ? requestAmountWh : acceptAmountWh;
							dealAmountWh = (dealAmountMaxWhVal < dealAmountWh) ? dealAmountMaxWhVal : dealAmountWh;
							dealAmountWh = (dealAmountWh / dealAmountUnitWh) * dealAmountUnitWh;
							dealAmountWh = (dealAmountWh < dealAmountMinWh) ? 0 : dealAmountWh;
							if (0 < dealAmountWh) {
								Float requestDealGridCurrentA = request_.getFloat("dealGridCurrentA");
								Float acceptDealGridCurrentA = accept.getFloat("dealGridCurrentA");
								if (requestDealGridCurrentA != null && acceptDealGridCurrentA != null
										&& 0 < requestDealGridCurrentA && 0 < acceptDealGridCurrentA) {
									String acceptUnitId = accept.getString("unitId");
									JsonObject deal = new JsonObject();
									deal.put("unitId", ApisConfig.unitId());
									deal.put("negotiationId", negotiationId_());
									deal.put("requestUnitId", ApisConfig.unitId());
									deal.put("acceptUnitId", acceptUnitId);
									deal.put("requestDateTime", request_.getString("dateTime"));
									deal.put("acceptDateTime", accept.getString("dateTime"));
									deal.put("requestPointPerWh", request_.getFloat("pointPerWh"));
									deal.put("acceptPointPerWh", accept.getFloat("pointPerWh"));
									deal.put("requestDealGridCurrentA", requestDealGridCurrentA);
									deal.put("acceptDealGridCurrentA", acceptDealGridCurrentA);
									String type = request_.getString("type");
									deal.put("type", type);
									if ("charge".equals(type)) {
										deal.put("chargeUnitId", ApisConfig.unitId());
										deal.put("dischargeUnitId", acceptUnitId);
										deal.put("pointPerWh", request_.getFloat("pointPerWh"));
										deal.put("chargeUnitEfficientGridVoltageV",
												request_.getFloat("efficientGridVoltageV"));
										deal.put("dischargeUnitEfficientGridVoltageV",
												accept.getFloat("efficientGridVoltageV"));
									} else if ("discharge".equals(type)) {
										deal.put("chargeUnitId", acceptUnitId);
										deal.put("dischargeUnitId", ApisConfig.unitId());
										deal.put("pointPerWh", accept.getFloat("pointPerWh"));
										deal.put("chargeUnitEfficientGridVoltageV",
												accept.getFloat("efficientGridVoltageV"));
										deal.put("dischargeUnitEfficientGridVoltageV",
												request_.getFloat("efficientGridVoltageV"));
									} else {
										ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.LOCAL,
												Error.Level.WARN, "unknown type : " + type);
									}
									Float dealGridCurrentA = (requestDealGridCurrentA < acceptDealGridCurrentA)
											? requestDealGridCurrentA
											: acceptDealGridCurrentA;
									deal.put("dealGridCurrentA", dealGridCurrentA);
									deal.put("requestAmountWh", requestAmountWh);
									deal.put("acceptAmountWh", acceptAmountWh);
									deal.put("dealAmountWh", dealAmountWh);
									if (request_.getString("pairUnitId") != null)
										deal.put("requestPairUnitId", request_.getString("pairUnitId"));
									if (accept.getString("pairUnitId") != null)
										deal.put("acceptPairUnitId", accept.getString("pairUnitId"));
									vertx.eventBus().send(ServiceAddress.Mediator.dealCreation(), deal);
								} else {
									ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN,
											"illegal values; requestDealGridCurrentA : " + requestDealGridCurrentA
													+ ", acceptDealGridCurrentA : " + acceptDealGridCurrentA);
								}
							} else {
								if (log.isDebugEnabled())
									log.debug("negotiated amount : " + acceptAmountWh
											+ " ; less than dealAmountMinWh : " + dealAmountMinWh);
							}
						} else {
							ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN,
									"data deficiency; requestAmountWh : " + requestAmountWh + ", acceptAmountWh : "
											+ acceptAmountWh + ", dealAmountMinWh : " + dealAmountMinWh
											+ ", dealAmountMaxWh : "
											+ (dealAmountMaxWhVal != null ? dealAmountMaxWhVal : "null")
											+ ", dealAmountUnitWh : " + dealAmountUnitWh);
						}
					} else {
						if (log.isInfoEnabled())
							log.info("no accept chosen");
					}
					onComplete.handle(Future.succeededFuture());
				} else {
					if (ReplyFailureUtil.isRecipientFailure(repAccept)) {
						onComplete.handle(Future.failedFuture(repAccept.cause()));
					} else if (ReplyFailureUtil.isTimeout(repAccept)) {
						ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.WARN,
								"Communication failed on EventBus", repAccept.cause(), onComplete);
					} else {
						ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR,
								"Communication failed on EventBus", repAccept.cause(), onComplete);
					}
				}
			});
		} else {
			onComplete.handle(Future.succeededFuture());
		}
	}

}
