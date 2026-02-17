package jp.co.sony.csl.dcoes.apis.main.app.gridmaster.main_loop;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jp.co.sony.csl.dcoes.apis.common.Deal;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.ServiceAddress;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectUtil;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectWrapper;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.ReplyFailureUtil;
import jp.co.sony.csl.dcoes.apis.main.app.PolicyKeeping;
import jp.co.sony.csl.dcoes.apis.main.app.StateHandling;
import jp.co.sony.csl.dcoes.apis.main.app.controller.util.DDCon;
import jp.co.sony.csl.dcoes.apis.main.app.gridmaster.ErrorCollection;
import jp.co.sony.csl.dcoes.apis.main.app.gridmaster.deal_execution.AbstractDealExecution;
import jp.co.sony.csl.dcoes.apis.main.app.gridmaster.deal_execution.DealAbortion;
import jp.co.sony.csl.dcoes.apis.main.app.gridmaster.deal_execution.DealActivation;
import jp.co.sony.csl.dcoes.apis.main.app.gridmaster.deal_execution.DealCompensation;
import jp.co.sony.csl.dcoes.apis.main.app.gridmaster.deal_execution.DealCumulation;
import jp.co.sony.csl.dcoes.apis.main.app.gridmaster.deal_execution.DealDeactivation;
import jp.co.sony.csl.dcoes.apis.main.app.gridmaster.deal_execution.DealDisposition;
import jp.co.sony.csl.dcoes.apis.main.app.gridmaster.deal_execution.DealMasterAuthorization;
import jp.co.sony.csl.dcoes.apis.main.app.gridmaster.deal_execution.DealRampingUp;
import jp.co.sony.csl.dcoes.apis.main.app.mediator.util.DealNeedToStopUtil;
import jp.co.sony.csl.dcoes.apis.main.app.mediator.util.DealUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorExceptionUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;
import jp.co.sony.csl.dcoes.apis.main.util.Policy;

public class DealExecution {
	private static final Logger log = LoggerFactory.getLogger(DealExecution.class);

	public static final JsonObjectWrapper unitDataCache = new JsonObjectWrapper();

	private static long lastDealExecutionMillis_ = 0L;

	private DealExecution() {
	}

	public static void execute(Vertx vertx, Handler<AsyncResult<Void>> onComplete) {
		execute_(vertx, res -> {
			lastDealExecutionMillis_ = System.currentTimeMillis();
			onComplete.handle(res);
		});
	}

	private static void execute_(Vertx vertx, Handler<AsyncResult<Void>> onComplete) {
		getUnitData_(vertx, resGetUnitData -> {
			if (resGetUnitData.succeeded()) {
				DealUtil.all(vertx, resAll -> {
					if (resAll.succeeded()) {
						List<JsonObject> deals = resAll.result();
						if (log.isDebugEnabled())
							log.debug("deals : " + deals);
						if (!deals.isEmpty()) {
							DealNeedToStopUtil.copyToDeals(vertx, deals, resCopyNeedToStop -> {
								if (resCopyNeedToStop.succeeded()) {
									StateHandling.globalOperationMode(vertx, resOperationMode -> {
										if (resOperationMode.succeeded()) {
											String result = resOperationMode.result();
											if ("stop".equals(result) || "manual".equals(result)) {
												ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC,
														Error.Extent.GLOBAL, Error.Level.ERROR,
														"operationMode is '" + result + "' but deal exists",
														onComplete);
											} else {
												if (checkMasterDealExistence_(deals)) {
													JsonObject policy = PolicyKeeping.cache().jsonObject();
													sortDeals_(vertx, policy, deals);
													if (log.isDebugEnabled())
														log.debug("sorted deals : " + deals);
													new DealExecution_(vertx, policy, deals).doLoop_(onComplete);
												} else {
													ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC,
															Error.Extent.GLOBAL, Error.Level.ERROR,
															"no master deal found", onComplete);
												}
											}
										} else {
											onComplete.handle(Future.failedFuture(resOperationMode.cause()));
										}
									});
								} else {
									ErrorExceptionUtil.reportIfNeedAndFail(vertx, resCopyNeedToStop.cause(),
											onComplete);
								}
							});
						} else {
							onComplete.handle(Future.succeededFuture());
						}
					} else {
						ErrorExceptionUtil.reportIfNeedAndFail(vertx, resAll.cause(), onComplete);
					}
				});
			} else {
				onComplete.handle(resGetUnitData);
			}
		});
	}

	private static void getUnitData_(Vertx vertx, Handler<AsyncResult<Void>> onComplete) {
		vertx.eventBus().<JsonObject>request(ServiceAddress.GridMaster.urgentUnitDatas(), lastDealExecutionMillis_,
				rep -> {
					if (rep.succeeded()) {
						unitDataCache.setJsonObject(rep.result().body());
						onComplete.handle(Future.succeededFuture());
					} else {
						if (ReplyFailureUtil.isRecipientFailure(rep)) {
							onComplete.handle(Future.failedFuture(rep.cause()));
						} else {
							ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.GLOBAL,
									Error.Level.ERROR, "Communication failed on EventBus", rep.cause(), onComplete);
						}
					}
				});
	}

	private static boolean checkMasterDealExistence_(List<JsonObject> deals) {
		boolean noActiveDeal = true;
		for (JsonObject aDeal : deals) {
			if (Deal.masterSideUnitMustBeActive(aDeal)) {
				noActiveDeal = false;
				break;
			}
		}
		if (noActiveDeal) {
			return true;
		}
		for (JsonObject aDeal : deals) {
			if (Deal.isMaster(aDeal)) {
				return true;
			}
		}
		return false;
	}

	private static void sortDeals_(Vertx vertx, JsonObject policy, List<JsonObject> deals) {
		if (1 < deals.size()) {
			List<JsonObject> sorted = new ArrayList<>(deals);
			List<String> largeCapacityUnitIds = Policy.largeCapacityUnitIds(policy);
			if (largeCapacityUnitIds != null && !largeCapacityUnitIds.isEmpty()) {
				String masterSidePolicy = masterSide(vertx, policy, deals);
				for (JsonObject aDeal : deals) {
					if (!Deal.isMaster(aDeal)) {
						if (largeCapacityUnitIds.contains(Deal.slaveSideUnitId(aDeal, masterSidePolicy))) {
							if (log.isDebugEnabled())
								log.debug("move non-master deal with large capacity slave side unit to first ; deal : "
										+ Deal.dealId(aDeal));
							sorted.remove(aDeal);
							sorted.add(0, aDeal);
						}
					}
				}
				for (JsonObject aDeal : deals) {
					if (!Deal.isMaster(aDeal)) {
						if (largeCapacityUnitIds.contains(Deal.masterSideUnitId(aDeal, masterSidePolicy))) {
							if (log.isDebugEnabled())
								log.debug("move non-master deal with large capacity master side unit to first ; deal : "
										+ Deal.dealId(aDeal));
							sorted.remove(aDeal);
							sorted.add(0, aDeal);
						}
					}
				}
			}
			for (JsonObject aDeal : deals) {
				if (Deal.isMaster(aDeal)) {
					if (Deal.isActivated(aDeal) && !Deal.isStarted(aDeal)) {
						if (log.isDebugEnabled())
							log.debug("move ramping up master deal to first ; deal : " + Deal.dealId(aDeal));
						sorted.remove(aDeal);
						sorted.add(0, aDeal);
					} else {
						if (log.isDebugEnabled())
							log.debug("move master deal to last ; deal : " + Deal.dealId(aDeal));
						sorted.remove(aDeal);
						sorted.add(aDeal);
					}
					break;
				}
			}
			for (JsonObject aDeal : deals) {
				if (Deal.isNeedToStop(aDeal)) {
					if (log.isDebugEnabled())
						log.debug("move need-to-stop deal to first ; deal : " + Deal.dealId(aDeal));
					sorted.remove(aDeal);
					sorted.add(0, aDeal);
				}
			}
			deals.clear();
			deals.addAll(sorted);
		}
	}

	public static String voltageReferenceUnitId() {
		JsonObject unitData = unitDataCache.jsonObject();
		if (unitData != null) {
			for (String aUnitId : unitData.fieldNames()) {
				JsonObject aUnitData = unitData.getJsonObject(aUnitId);
				DDCon.Mode aMode = DDCon.modeFromCode(JsonObjectUtil.getString(aUnitData, "dcdc", "status", "status"));
				if (DDCon.Mode.VOLTAGE_REFERENCE == aMode) {
					if (log.isInfoEnabled())
						log.info("voltage reference unit : " + aUnitId);
					return aUnitId;
				}
			}
		} else {
			if (log.isWarnEnabled())
				log.warn("no unit data");
		}
		if (log.isInfoEnabled())
			log.info("no voltage reference unit found");
		return null;
	}

	public static String masterSide(Vertx vertx, JsonObject policy, List<JsonObject> deals) {
		for (JsonObject aDeal : deals) {
			if (Deal.isMaster(aDeal)) {
				String voltageReferenceUnitId = voltageReferenceUnitId();
				if (voltageReferenceUnitId != null) {
					if (Deal.isDischargeUnit(aDeal, voltageReferenceUnitId)) {
						return "dischargeUnit";
					} else if (Deal.isChargeUnit(aDeal, voltageReferenceUnitId)) {
						return "chargeUnit";
					} else {
						ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.ERROR,
								"voltage reference unit found : " + voltageReferenceUnitId
										+ " ; but not in master deal : " + aDeal);
					}
				} else {
					ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.ERROR,
							"no voltage reference unit found ; master deal : " + aDeal);
				}
				break;
			}
		}
		return Policy.masterSide(policy);
	}

	private static class DealExecution_ {
		private Vertx vertx_;
		private JsonObject policy_;
		private List<JsonObject> deals_;
		private List<JsonObject> dealsForLoop_;
		private List<String> activeDealIdsBeforeLoop_;

		private DealExecution_(Vertx vertx, JsonObject policy, List<JsonObject> deals) {
			vertx_ = vertx;
			policy_ = policy;
			deals_ = deals;
			dealsForLoop_ = new ArrayList<JsonObject>(deals_);
			activeDealIdsBeforeLoop_ = activeDealIds_();
		}

		private void doLoop_(Handler<AsyncResult<Void>> onComplete) {
			if (ErrorCollection.hasErrors()) {
				String msg = "global error exists";
				if (log.isInfoEnabled())
					log.info(msg);
				onComplete.handle(Future.failedFuture(msg));
			} else if (dealsForLoop_.isEmpty()) {
				if (Policy.gridVoltageOptimizationEnabled(policy_)) {
					doGridVoltageOptimization_(vertx_, onComplete);
				} else {
					onComplete.handle(Future.succeededFuture());
				}
			} else {
				JsonObject aDeal = dealsForLoop_.remove(0);
				List<JsonObject> otherDeals = new ArrayList<JsonObject>(deals_);
				otherDeals.remove(aDeal);
				final AbstractDealExecution exec;
				if (Deal.isDeactivated(aDeal)) {
					exec = new DealDisposition(vertx_, policy_, aDeal, otherDeals);
				} else if (Deal.isStopped(aDeal)) {
					exec = new DealDeactivation(vertx_, policy_, aDeal, otherDeals);
				} else if (Deal.isNeedToStop(aDeal)) {
					exec = new DealAbortion(vertx_, policy_, aDeal, otherDeals, Deal.needToStopReasons(aDeal).encode());
				} else if (Deal.isStarted(aDeal)) {
					exec = new DealCumulation(vertx_, policy_, aDeal, otherDeals);
				} else if (Deal.isActivated(aDeal)) {
					if (Deal.isMaster(aDeal)) {
						if (Deal.isRampedUp(aDeal)) {
							exec = new DealMasterAuthorization(vertx_, policy_, aDeal, otherDeals);
						} else {
							exec = new DealRampingUp(vertx_, policy_, aDeal, otherDeals);
						}
					} else {
						exec = new DealCompensation(vertx_, policy_, aDeal, otherDeals);
					}
				} else {
					exec = new DealActivation(vertx_, policy_, aDeal, otherDeals);
				}
				exec.execute(resExec -> {
					if (resExec.succeeded()) {
						doLoop_(onComplete);
					} else {
						new DealAbortion(exec, resExec.cause().getMessage()).execute(resAbort -> {
							if (!resAbort.succeeded()) {
								String msg = "deal abortion failed";
								ErrorUtil.report(vertx_, Deal.chargeUnitId(aDeal), Error.Category.HARDWARE,
										Error.Extent.LOCAL, Error.Level.ERROR, msg);
								ErrorUtil.report(vertx_, Deal.dischargeUnitId(aDeal), Error.Category.HARDWARE,
										Error.Extent.LOCAL, Error.Level.ERROR, msg);
							}
							doLoop_(onComplete);
						});
					}
				});
			}
		}

		private List<String> activeDealIds_() {
			List<String> result = new ArrayList<>();
			for (JsonObject aDeal : deals_) {
				if (Deal.bothSideUnitsMustBeActive(aDeal)) {
					result.add(Deal.dealId(aDeal));
				}
			}
			return result;
		}

		private void doGridVoltageOptimization_(Vertx vertx, Handler<AsyncResult<Void>> onComplete) {
			boolean shouldDoGridVoltageOptimization = true;
			List<String> activeDealIdsAfterLoop = activeDealIds_();
			if (activeDealIdsBeforeLoop_.size() == activeDealIdsAfterLoop.size()) {
				Collections.sort(activeDealIdsBeforeLoop_);
				Collections.sort(activeDealIdsAfterLoop);
				if (activeDealIdsBeforeLoop_.equals(activeDealIdsAfterLoop)) {
					shouldDoGridVoltageOptimization = false;
				}
			}
			if (shouldDoGridVoltageOptimization) {
				GridVoltageOptimization.execute(vertx, deals_, onComplete);
			} else {
				onComplete.handle(Future.succeededFuture());
			}
		}
	}

}
