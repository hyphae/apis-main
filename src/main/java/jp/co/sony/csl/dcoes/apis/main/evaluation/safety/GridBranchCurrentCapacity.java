package jp.co.sony.csl.dcoes.apis.main.evaluation.safety;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import jp.co.sony.csl.dcoes.apis.common.Deal;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

public class GridBranchCurrentCapacity {
	private static final Logger log = LoggerFactory.getLogger(GridBranchCurrentCapacity.class);

	private GridBranchCurrentCapacity() {
	}

	public static void check(Vertx vertx, JsonObject policy, JsonObject unitData,
			Handler<AsyncResult<Void>> completionHandler) {
		if (JsonObjectUtil.getBoolean(policy, Boolean.FALSE, "safety", "gridTopologyBasedEvaluation", "enabled")) {
			check_topologyBased_(vertx, policy, unitData, completionHandler);
		} else {
			completionHandler.handle(Future.succeededFuture());
		}
	}

	private static void check_topologyBased_(Vertx vertx, JsonObject policy, JsonObject unitData,
			Handler<AsyncResult<Void>> completionHandler) {
		JsonObject config = JsonObjectUtil.getJsonObject(policy, "safety", "gridTopologyBasedEvaluation");
		List<String> branchIds = JsonObjectUtil.getStringList(config, "branchIds");
		if (branchIds != null) {
			for (String aBranchId : branchIds) {
				Float capacity = JsonObjectUtil.getFloat(config, "branchCurrentCapacityA", aBranchId);
				List<String> forwardUnitIds = JsonObjectUtil.getStringList(config, "branchAssociation", aBranchId,
						"forwardUnitIds");
				if (capacity != null && forwardUnitIds != null) {
					float sum = 0F;
					for (String aUnitId : forwardUnitIds) {
						Float ig = JsonObjectUtil.getFloat(unitData, aUnitId, "dcdc", "meter", "ig");
						if (ig != null) {
							sum += ig;
						} else {
							ErrorUtil.report(vertx, Error.Category.HARDWARE, Error.Extent.GLOBAL, Error.Level.WARN,
									"no " + aUnitId + ".dcdc.meter.ig");
						}
					}
					if (Math.abs(capacity) < Math.abs(sum)) {
						ErrorUtil.reportAndFail(vertx, Error.Category.HARDWARE, Error.Extent.GLOBAL, Error.Level.ERROR,
								"branch : " + aBranchId + ", forward sum of dcdc.meter.ig : " + sum
										+ ", exceeds capacity : " + capacity,
								completionHandler);
						return;
					}
				} else {
					ErrorUtil.report(vertx, Error.Category.USER, Error.Extent.GLOBAL, Error.Level.WARN,
							"data deficiency ; POLICY.safety.gridTopologyBasedEvaluation.branchCurrentCapacityA."
									+ aBranchId + " : " + capacity
									+ ", POLICY.safety.gridTopologyBasedEvaluation.branchAssociation." + aBranchId
									+ ".forwardUnitIds : " + forwardUnitIds);
				}
			}
			completionHandler.handle(Future.succeededFuture());
		} else {
			ErrorUtil.reportAndFail(vertx, Error.Category.USER, Error.Extent.GLOBAL, Error.Level.WARN,
					"no POLICY.safety.gridTopologyBasedEvaluation.branchIds", completionHandler);
		}
	}

	public static String checkNewDeal(Vertx vertx, JsonObject policy, JsonObject deal, List<JsonObject> otherDeals) {
		List<JsonObject> activeDeals = activeDeals_(otherDeals);
		activeDeals.add(deal);
		if (JsonObjectUtil.getBoolean(policy, Boolean.FALSE, "safety", "gridTopologyBasedEvaluation", "enabled")) {
			return checkNewDeal_topologyBased_(vertx, policy, activeDeals);
		} else {
			return checkNewDeal_gridTotal_(vertx, policy, activeDeals);
		}
	}

	private static String checkNewDeal_topologyBased_(Vertx vertx, JsonObject policy, List<JsonObject> activeDeals) {
		JsonObject config = JsonObjectUtil.getJsonObject(policy, "safety", "gridTopologyBasedEvaluation");
		List<String> branchIds = JsonObjectUtil.getStringList(config, "branchIds");
		if (branchIds != null) {
			for (String aBranchId : branchIds) {
				Float capacity = JsonObjectUtil.getFloat(config, "branchCurrentCapacityA", aBranchId);
				List<String> forwardUnitIds = JsonObjectUtil.getStringList(config, "branchAssociation", aBranchId,
						"forwardUnitIds");
				List<String> backwardUnitIds = JsonObjectUtil.getStringList(config, "branchAssociation", aBranchId,
						"backwardUnitIds");
				if (capacity != null && forwardUnitIds != null && backwardUnitIds != null) {
					float forwardDischargeSum = 0F;
					float forwardChargeSum = 0F;
					for (String aUnitId : forwardUnitIds) {
						for (JsonObject aDeal : activeDeals) {
							Float dealGridCurrentA = Deal.dealGridCurrentA(aDeal);
							if (dealGridCurrentA != null) {
								if (Deal.isDischargeUnit(aDeal, aUnitId)) {
									forwardDischargeSum += dealGridCurrentA;
								} else if (Deal.isChargeUnit(aDeal, aUnitId)) {
									forwardChargeSum += dealGridCurrentA;
								}
							} else {
								String msg = "no dealGridCurrentA in deal : " + aDeal;
								ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.ERROR,
										msg);
								return msg;
							}
						}
					}
					if (Math.abs(capacity) < Math.abs(forwardDischargeSum)) {
						return "branch : " + aBranchId + ", forward sum of discharge dealGridCurrentA : "
								+ forwardDischargeSum + ", exceeds capacity : " + capacity;
					} else if (Math.abs(capacity) < Math.abs(forwardChargeSum)) {
						return "branch : " + aBranchId + ", forward sum of charge dealGridCurrentA : "
								+ forwardChargeSum + ", exceeds capacity : " + capacity;
					}
					float backwardDischargeSum = 0F;
					float backwardChargeSum = 0F;
					for (String aUnitId : backwardUnitIds) {
						for (JsonObject aDeal : activeDeals) {
							Float dealGridCurrentA = Deal.dealGridCurrentA(aDeal);
							if (dealGridCurrentA != null) {
								if (Deal.isDischargeUnit(aDeal, aUnitId)) {
									backwardDischargeSum += dealGridCurrentA;
								} else if (Deal.isChargeUnit(aDeal, aUnitId)) {
									backwardChargeSum += dealGridCurrentA;
								}
							} else {
								String msg = "no dealGridCurrentA in deal : " + aDeal;
								ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.ERROR,
										msg);
								return msg;
							}
						}
					}
					if (Math.abs(capacity) < Math.abs(backwardDischargeSum)) {
						return "branch : " + aBranchId + ", backward sum of discharge dealGridCurrentA : "
								+ backwardDischargeSum + ", exceeds capacity : " + capacity;
					} else if (Math.abs(capacity) < Math.abs(backwardChargeSum)) {
						return "branch : " + aBranchId + ", backward sum of charge dealGridCurrentA : "
								+ backwardChargeSum + ", exceeds capacity : " + capacity;
					}
				} else {
					String msg = "data deficiency ; POLICY.safety.gridTopologyBasedEvaluation.branchCurrentCapacityA."
							+ aBranchId + " : " + capacity
							+ ", POLICY.safety.gridTopologyBasedEvaluation.branchAssociation." + aBranchId
							+ ".forwardUnitIds : " + forwardUnitIds
							+ ", POLICY.safety.gridTopologyBasedEvaluation.branchAssociation." + aBranchId
							+ ".backwardUnitIds : " + backwardUnitIds;
					ErrorUtil.report(vertx, Error.Category.USER, Error.Extent.GLOBAL, Error.Level.ERROR, msg);
					return msg;
				}
			}
		} else {
			String msg = "no POLICY.safety.gridTopologyBasedEvaluation.branchIds";
			ErrorUtil.report(vertx, Error.Category.USER, Error.Extent.GLOBAL, Error.Level.ERROR, msg);
			return msg;
		}
		return null;
	}

	private static String checkNewDeal_gridTotal_(Vertx vertx, JsonObject policy, List<JsonObject> activeDeals) {
		Float sumOfDealGridCurrentMaxA = JsonObjectUtil.getFloat(policy, "safety", "sumOfDealGridCurrentMaxA");
		if (sumOfDealGridCurrentMaxA != null) {
			float sumOfDealGridCurrentA = 0F;
			for (JsonObject aDeal : activeDeals) {
				Float dealGridCurrentA = Deal.dealGridCurrentA(aDeal);
				if (dealGridCurrentA != null) {
					sumOfDealGridCurrentA += dealGridCurrentA;
				} else {
					String msg = "no dealGridCurrentA in deal : " + aDeal;
					ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.ERROR, msg);
					return msg;
				}
			}
			if (sumOfDealGridCurrentMaxA < sumOfDealGridCurrentA) {
				return "sum of dealGridCurrentA of all deals : " + sumOfDealGridCurrentA + " ; exceeds limit : "
						+ sumOfDealGridCurrentMaxA;
			}
		} else {
			String msg = "no POLICY.safety.sumOfDealGridCurrentMaxA";
			ErrorUtil.report(vertx, Error.Category.USER, Error.Extent.GLOBAL, Error.Level.ERROR, msg);
			return msg;
		}
		return null;
	}

	private static List<JsonObject> activeDeals_(List<JsonObject> deals) {
		List<JsonObject> result = new ArrayList<>();
		for (JsonObject aDeal : deals) {
			if (Deal.masterSideUnitMustBeActive(aDeal)) {
				result.add(aDeal);
			}
		}
		return result;
	}

}
