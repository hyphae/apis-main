package jp.co.sony.csl.dcoes.apis.main.app.mediator;

import java.util.List;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jp.co.sony.csl.dcoes.apis.common.Deal;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.ServiceAddress;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.ReplyFailureUtil;
import jp.co.sony.csl.dcoes.apis.main.app.PolicyKeeping;
import jp.co.sony.csl.dcoes.apis.main.app.controller.DataAcquisition;
import jp.co.sony.csl.dcoes.apis.main.app.mediator.util.DealNeedToStopUtil;
import jp.co.sony.csl.dcoes.apis.main.app.mediator.util.DealUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ApisConfig;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorExceptionUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

public class DealManagement extends AbstractVerticle {
	private static final Logger log = LoggerFactory.getLogger(DealManagement.class);

	@Override
	public void start(Promise<Void> startPromise) throws Exception {
		startDealsService_(resDeals -> {
			if (resDeals.succeeded()) {
				startDealCreationService_(resDealCreation -> {
					if (resDealCreation.succeeded()) {
						startDealDispositionService_(resDealDisposition -> {
							if (resDealDisposition.succeeded()) {
								startDealNeedToStopService_(resDealNeedToStop -> {
									if (resDealNeedToStop.succeeded()) {
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
										startPromise.fail(resDealNeedToStop.cause());
									}
								});
							} else {
								startPromise.fail(resDealDisposition.cause());
							}
						});
					} else {
						startPromise.fail(resDealCreation.cause());
					}
				});
			} else {
				startPromise.fail(resDeals.cause());
			}
		});
	}

	@Override
	public void stop() throws Exception {
		if (log.isTraceEnabled())
			log.trace("stopped : " + deploymentID());
	}

	private void startDealsService_(Handler<AsyncResult<Void>> onComplete) {
		vertx.eventBus().<Void>consumer(ServiceAddress.Mediator.deals(), req -> {
			DealUtil.all(vertx, resAll -> {
				if (resAll.succeeded()) {
					List<JsonObject> deals = resAll.result();
					req.reply(new JsonArray(deals));
				} else {
					ErrorExceptionUtil.reportIfNeedAndFail(vertx, resAll.cause(), req);
				}
			});
		}).completionHandler(onComplete);
	}

	private void startDealCreationService_(Handler<AsyncResult<Void>> onComplete) {
		vertx.eventBus().<JsonObject>consumer(ServiceAddress.Mediator.dealCreation(), req -> {
			JsonObject deal = req.body();
			if (deal != null) {
				String requestUnitId = Deal.requestUnitId(deal);
				String acceptUnitId = Deal.acceptUnitId(deal);
				if (requestUnitId != null && acceptUnitId != null) {
					if (PolicyKeeping.isMember(requestUnitId) && PolicyKeeping.isMember(acceptUnitId)) {
						vertx.eventBus().<Boolean>request(ServiceAddress.GridMaster.errorTesting(), null,
								repGlobalErrors -> {
									if (repGlobalErrors.succeeded()) {
										Boolean hasGlobalErrors = repGlobalErrors.result().body();
										if (hasGlobalErrors != null && hasGlobalErrors) {
											String msg = "global error exists";
											if (log.isInfoEnabled())
												log.info(msg);
											req.fail(-1, msg);
										} else {
											Promise<Message<Boolean>> requestUnitPromise = Promise.promise();
											Promise<Message<Boolean>> acceptUnitPromise = Promise.promise();
											vertx.eventBus().<Boolean>request(
													ServiceAddress.User.errorTesting(requestUnitId), null,
													requestUnitPromise);
											vertx.eventBus().<Boolean>request(
													ServiceAddress.User.errorTesting(acceptUnitId), null,
													acceptUnitPromise);
											Future.all(requestUnitPromise.future(), acceptUnitPromise.future())
													.onComplete(ar -> {
														if (ar.succeeded()) {
															Boolean hasRequestUnitErrors = ar.result()
																	.<Message<Boolean>>resultAt(0).body();
															Boolean hasAcceptUnitErrors = ar.result()
																	.<Message<Boolean>>resultAt(1).body();
															JsonArray errors = new JsonArray();
															if (hasRequestUnitErrors != null && hasRequestUnitErrors) {
																String msg = "local error exists on request unit : "
																		+ requestUnitId;
																if (log.isInfoEnabled())
																	log.info(msg);
																errors.add(msg);
															}
															if (hasAcceptUnitErrors != null && hasAcceptUnitErrors) {
																String msg = "local error exists on accept unit : "
																		+ acceptUnitId;
																if (log.isInfoEnabled())
																	log.info(msg);
																errors.add(msg);
															}
															if (errors.isEmpty()) {
																createDeal_(deal, resCreate -> {
																	if (resCreate.succeeded()) {
																		req.reply(resCreate.result());
																	} else {
																		req.fail(-1, resCreate.cause().getMessage());
																	}
																});
															} else {
																req.fail(-1, errors.encode());
															}
														} else {
															if (ReplyFailureUtil.isRecipientFailure(ar.cause())) {
																req.fail(-1, ar.cause().getMessage());
															} else {
																ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK,
																		Error.Extent.LOCAL, Error.Level.ERROR,
																		"Communication failed on EventBus", ar.cause(),
																		req);
															}
														}
													});
										}
									} else {
										if (ReplyFailureUtil.isRecipientFailure(repGlobalErrors)) {
											req.fail(-1, repGlobalErrors.cause().getMessage());
										} else {
											ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL,
													Error.Level.ERROR, "Communication failed on EventBus",
													repGlobalErrors.cause(), req);
										}
									}
								});
					} else {
						StringBuilder msg = new StringBuilder();
						if (!PolicyKeeping.isMember(requestUnitId)) {
							msg.append(requestUnitId);
						}
						if (!PolicyKeeping.isMember(acceptUnitId)) {
							if (0 < msg.length())
								msg.append(" & ");
							msg.append(acceptUnitId);
						}
						msg.insert(0, "deal received with illegal unit(s) : ").append(" ; deal : ").append(deal);
						ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN,
								msg.toString(), req);
					}
				} else {
					ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN,
							"no requestUnitId and/or acceptUnitId in deal : " + deal, req);
				}
			} else {
				ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN,
						"deal is null", req);
			}
		}).completionHandler(onComplete);
	}

	private void createDeal_(JsonObject deal, Handler<AsyncResult<JsonObject>> onComplete) {
		String requestUnitId = Deal.requestUnitId(deal);
		String acceptUnitId = Deal.acceptUnitId(deal);
		String unitId1_, unitId2_;
		if (requestUnitId != null && acceptUnitId != null) {
			if (requestUnitId.compareTo(acceptUnitId) < 0) {
				unitId1_ = requestUnitId;
				unitId2_ = acceptUnitId;
			} else {
				unitId2_ = requestUnitId;
				unitId1_ = acceptUnitId;
			}
			acquireInterlock_(unitId1_, deal, resAcquire1 -> {
				if (resAcquire1.succeeded()) {
					acquireInterlock_(unitId2_, deal, resAcquire2 -> {
						if (resAcquire2.succeeded()) {
							deal.put("createDateTime", DataAcquisition.cache.getString("time"));
							DealUtil.add(vertx, deal, resAdd -> {
								if (resAdd.succeeded()) {
									onComplete.handle(Future.succeededFuture(deal));
								} else {
									ErrorExceptionUtil.reportIfNeed(vertx, resAdd.cause());
									releaseInterlock_(unitId2_, deal, resRelease2 -> {
										releaseInterlock_(unitId1_, deal, resRelease1 -> {
											if (resRelease2.succeeded() && resRelease1.succeeded()) {
												onComplete.handle(Future.failedFuture(resAdd.cause()));
											} else if (resRelease1.failed()) {
												onComplete.handle(Future.failedFuture(resRelease1.cause()));
											} else {
												onComplete.handle(Future.failedFuture(resRelease2.cause()));
											}
										});
									});
								}
							});
						} else {
							releaseInterlock_(unitId1_, deal, resRelease1 -> {
								if (resRelease1.succeeded()) {
									onComplete.handle(Future.failedFuture(resAcquire2.cause()));
								} else {
									onComplete.handle(Future.failedFuture(resRelease1.cause()));
								}
							});
						}
					});
				} else {
					onComplete.handle(Future.failedFuture(resAcquire1.cause()));
				}
			});
		} else {
			ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN,
					"no requestUnitId and/or acceptUnitId in deal : " + deal, onComplete);
		}
	}

	private void acquireInterlock_(String unitId, JsonObject deal, Handler<AsyncResult<Void>> onComplete) {
		if (unitId != null) {
			DeliveryOptions acquireOptions = new DeliveryOptions().addHeader("command", "acquire");
			vertx.eventBus().<Void>request(ServiceAddress.Mediator.dealInterlocking(unitId), deal, acquireOptions,
					rep -> {
						if (rep.succeeded()) {
							onComplete.handle(Future.succeededFuture());
						} else {
							if (ReplyFailureUtil.isRecipientFailure(rep)) {
								onComplete.handle(Future.failedFuture(rep.cause()));
							} else {
								ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL,
										Error.Level.ERROR, "Communication failed on EventBus", rep.cause(), onComplete);
							}
						}
					});
		} else {
			onComplete.handle(Future.failedFuture("no unitId"));
		}
	}

	private void releaseInterlock_(String unitId, JsonObject deal, Handler<AsyncResult<Void>> onComplete) {
		if (unitId != null) {
			DeliveryOptions releaseOptions = new DeliveryOptions().addHeader("command", "release");
			vertx.eventBus().<Void>request(ServiceAddress.Mediator.dealInterlocking(unitId), deal, releaseOptions,
					rep -> {
						if (rep.succeeded()) {
							onComplete.handle(Future.succeededFuture());
						} else {
							if (ReplyFailureUtil.isRecipientFailure(rep)) {
								onComplete.handle(Future.failedFuture(rep.cause()));
							} else {
								ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL,
										Error.Level.ERROR, "Communication failed on EventBus", rep.cause(), onComplete);
							}
						}
					});
		} else {
			onComplete.handle(Future.succeededFuture());
		}
	}

	private void startDealDispositionService_(Handler<AsyncResult<Void>> onComplete) {
		vertx.eventBus().<String>localConsumer(ServiceAddress.Mediator.dealDisposition(), req -> {
			String dealId = req.body();
			if (dealId != null) {
				disposeDeal_(dealId, resDispose -> {
					if (resDispose.succeeded()) {
						req.reply(resDispose.result());
					} else {
						req.fail(-1, resDispose.cause().getMessage());
					}
				});
			} else {
				ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR,
						"dealId is null", req);
			}
		}).completionHandler(onComplete);
	}

	private void disposeDeal_(String dealId, Handler<AsyncResult<JsonObject>> onComplete) {
		DealNeedToStopUtil.remove(vertx, dealId, resRemoveNeedToStop -> {
			if (resRemoveNeedToStop.succeeded()) {
				// nop
			} else {
				ErrorExceptionUtil.reportIfNeed(vertx, resRemoveNeedToStop.cause());
			}
		});
		DealUtil.get(vertx, dealId, resGet -> {
			if (resGet.succeeded()) {
				JsonObject deal = resGet.result();
				vertx.eventBus().publish(ServiceAddress.Mediator.dealLogging(), deal);
				String requestUnitId = Deal.requestUnitId(deal);
				String acceptUnitId = Deal.acceptUnitId(deal);
				String unitId1_, unitId2_;
				if (requestUnitId != null && acceptUnitId != null) {
					if (requestUnitId.compareTo(acceptUnitId) < 0) {
						unitId1_ = requestUnitId;
						unitId2_ = acceptUnitId;
					} else {
						unitId2_ = requestUnitId;
						unitId1_ = acceptUnitId;
					}
					DealUtil.remove(vertx, dealId, resRemove -> {
						if (resRemove.succeeded()) {
							releaseInterlock_(unitId2_, deal, resRelease2 -> {
								releaseInterlock_(unitId1_, deal, resRelease1 -> {
									if (resRelease2.succeeded() && resRelease1.succeeded()) {
										onComplete.handle(Future.succeededFuture(deal));
									} else if (resRelease1.failed()) {
										onComplete.handle(Future.failedFuture(resRelease1.cause()));
									} else {
										onComplete.handle(Future.failedFuture(resRelease2.cause()));
									}
								});
							});
						} else {
							ErrorExceptionUtil.reportIfNeedAndFail(vertx, resRemove.cause(), onComplete);
						}
					});
				} else {
					onComplete.handle(Future.failedFuture("no requestUnitId and/or acceptUnitId in deal : " + deal));
				}
			} else {
				ErrorExceptionUtil.reportIfNeedAndFail(vertx, resGet.cause(), onComplete);
			}
		});
	}

	private void startDealNeedToStopService_(Handler<AsyncResult<Void>> onComplete) {
		vertx.eventBus().<JsonObject>consumer(ServiceAddress.Mediator.dealNeedToStop(), req -> {
			JsonObject message = req.body();
			String dealId = message.getString("dealId");
			if (dealId != null) {
				JsonArray reasons = message.getJsonArray("reasons");
				needToStopDeal_(dealId, reasons, resNeedToStop -> {
					if (resNeedToStop.succeeded()) {
						req.reply(dealId);
					} else {
						req.fail(-1, resNeedToStop.cause().getMessage());
					}
				});
			} else {
				ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN,
						"dealId is null", req);
			}
		}).completionHandler(onComplete);
	}

	private void needToStopDeal_(String dealId, JsonArray reasons, Handler<AsyncResult<Void>> onComplete) {
		DealUtil.get(vertx, dealId, true, resGet -> {
			if (resGet.succeeded()) {
				JsonObject deal = resGet.result();
				if (deal != null) {
					DealNeedToStopUtil.add(vertx, dealId, reasons, resNeedToStop -> ErrorExceptionUtil
							.reportIfNeedAndHandle(vertx, resNeedToStop, onComplete));
				} else {
					onComplete.handle(Future.succeededFuture());
				}
			} else {
				ErrorExceptionUtil.reportIfNeedAndFail(vertx, resGet.cause(), onComplete);
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
		DealUtil.resetExclusiveLock(vertx);
		message.reply(ApisConfig.unitId());
	}

}
