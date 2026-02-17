package jp.co.sony.csl.dcoes.apis.main.evaluation.safety;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jp.co.sony.csl.dcoes.apis.common.Deal;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectUtil;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectWrapper;
import jp.co.sony.csl.dcoes.apis.main.app.mediator.util.DealUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorExceptionUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

public class GlobalSafetyEvaluation {

	public static final JsonObjectWrapper errors = new JsonObjectWrapper();
	private static final String ERROR_SUM_OF_METER_IG_EXCEEDS_ALLOWANCE = "ERROR_SUM_OF_METER_IG_EXCEEDS_ALLOWANCE";

	private GlobalSafetyEvaluation() {
	}

	public static void check(Vertx vertx, JsonObject policy, JsonObject unitData,
			Handler<AsyncResult<Void>> completionHandler) {
		if (unitData != null) {
			checkMemberUnitIds_(vertx, policy, unitData, resCheckMemberUnitIds -> {
				boolean failed = false;
				if (resCheckMemberUnitIds.succeeded()) {
				} else {
					failed = true;
				}
				boolean failed_ = failed;
				checkSumOfUnitAndDealCurrent_(vertx, policy, unitData, resCheckSumOfUnitAndDealCurrent -> {
					boolean failed__ = failed_;
					if (resCheckSumOfUnitAndDealCurrent.succeeded()) {
					} else {
						failed__ = true;
					}
					boolean failed___ = failed__;
					GridBranchCurrentCapacity.check(vertx, policy, unitData, resCheckGridBranchCurrentCapacity -> {
						boolean failed____ = failed___;
						if (resCheckGridBranchCurrentCapacity.succeeded()) {
						} else {
							failed____ = true;
						}
						if (failed____) {
							completionHandler.handle(Future.failedFuture("global safety evaluation failed"));
						} else {
							completionHandler.handle(Future.succeededFuture());
						}
					});
				});
			});
		} else {
			ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.WARN, "no unitData",
					completionHandler);
		}
	}

	private static void checkMemberUnitIds_(Vertx vertx, JsonObject policy, JsonObject unitData,
			Handler<AsyncResult<Void>> completionHandler) {
		boolean failed = false;
		List<String> memberUnitIds = JsonObjectUtil.getStringList(policy, "memberUnitIds");
		if (memberUnitIds == null) {
			ErrorUtil.reportAndFail(vertx, Error.Category.USER, Error.Extent.GLOBAL, Error.Level.WARN,
					"no memberUnitIds values in POLICY : " + policy, completionHandler);
		} else {
			if (memberUnitIds.size() != unitData.size()) {
				failed = true;
			} else {
				for (String aUnitId : memberUnitIds) {
					if (!unitData.containsKey(aUnitId)) {
						failed = true;
						break;
					}
				}
			}
			if (failed) {
				String[] unitIds = unitData.fieldNames().toArray(new String[unitData.size()]);
				Arrays.sort(unitIds);
				ErrorUtil.reportAndFail(
						vertx, Error.Category.HARDWARE, Error.Extent.GLOBAL, Error.Level.WARN, "illegal member list : "
								+ new JsonArray(Arrays.asList(unitIds)) + ", POLICY.memberUnitIds : " + memberUnitIds,
						completionHandler);
			} else {
				completionHandler.handle(Future.succeededFuture());
			}
		}
	}

	private static void checkSumOfUnitAndDealCurrent_(Vertx vertx, JsonObject policy, JsonObject unitData,
			Handler<AsyncResult<Void>> completionHandler) {
		Float sumOfDealingUnitGridCurrentAllowancePerUnitA = JsonObjectUtil.getFloat(policy, "safety",
				"sumOfDealingUnitGridCurrentAllowancePerUnitA");
		if (sumOfDealingUnitGridCurrentAllowancePerUnitA != null) {
			DealUtil.all(vertx, resAll -> {
				if (resAll.succeeded()) {
					boolean failed = false;
					int numberOfRunningDeals = 0;
					float sumOfDealingUnitGridCurrentA = 0;
					Set<String> dealingUnitIds = new HashSet<>();
					for (JsonObject aDeal : resAll.result()) {
						if (Deal.bothSideUnitsMustBeActive(aDeal)) {
							numberOfRunningDeals++;
							String dischargeUnitId = Deal.dischargeUnitId(aDeal);
							String chargeUnitId = Deal.chargeUnitId(aDeal);
							if (dischargeUnitId == null || chargeUnitId == null) {
								ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.ERROR,
										"no dischargeUnitId and/or chargeUnitId in deal : " + aDeal);
								failed = true;
							} else {
								if (dealingUnitIds.add(dischargeUnitId)) {
									Float dischargeUnitIg = JsonObjectUtil.getFloat(unitData, dischargeUnitId, "dcdc",
											"meter", "ig");
									if (dischargeUnitIg == null) {
										ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.GLOBAL,
												Error.Level.ERROR, "no dcdc.meter.ig value in discharging unit data : "
														+ JsonObjectUtil.getJsonObject(unitData, dischargeUnitId));
										failed = true;
									} else {
										sumOfDealingUnitGridCurrentA += dischargeUnitIg;
									}
								}
								if (dealingUnitIds.add(chargeUnitId)) {
									Float chargeUnitIg = JsonObjectUtil.getFloat(unitData, chargeUnitId, "dcdc",
											"meter", "ig");
									if (chargeUnitIg == null) {
										ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.GLOBAL,
												Error.Level.ERROR, "no dcdc.meter.ig value in charging unit data : "
														+ JsonObjectUtil.getJsonObject(unitData, chargeUnitId));
										failed = true;
									} else {
										sumOfDealingUnitGridCurrentA += chargeUnitIg;
									}
								}
							}
						}
					}
					float sumOfDealingUnitGridCurrentAllowanceA = sumOfDealingUnitGridCurrentAllowancePerUnitA
							* dealingUnitIds.size();
					if (sumOfDealingUnitGridCurrentAllowanceA < Math.abs(sumOfDealingUnitGridCurrentA)) {
						errors.add(Boolean.FALSE, ERROR_SUM_OF_METER_IG_EXCEEDS_ALLOWANCE);
						String msg = "sum of dcdc.meter.ig of all dealing units : " + sumOfDealingUnitGridCurrentA
								+ " ; exceeds allowance : " + sumOfDealingUnitGridCurrentAllowanceA
								+ " ( number of running deals : " + numberOfRunningDeals
								+ ", number of dealing units : " + dealingUnitIds.size() + " )";
						if (1 < errors.getJsonArray(ERROR_SUM_OF_METER_IG_EXCEEDS_ALLOWANCE).size()) {
							ErrorUtil.report(vertx, Error.Category.HARDWARE, Error.Extent.GLOBAL, Error.Level.ERROR,
									msg);
						} else {
							ErrorUtil.report(vertx, Error.Category.HARDWARE, Error.Extent.GLOBAL, Error.Level.WARN,
									msg);
						}
						failed = true;
					} else {
						errors.remove(ERROR_SUM_OF_METER_IG_EXCEEDS_ALLOWANCE);
					}
					if (failed) {
						completionHandler.handle(Future.failedFuture("global safety evaluation failed"));
					} else {
						completionHandler.handle(Future.succeededFuture());
					}
				} else {
					ErrorExceptionUtil.reportIfNeedAndFail(vertx, resAll.cause(), completionHandler);
				}
			});
		} else {
			ErrorUtil.reportAndFail(vertx, Error.Category.USER, Error.Extent.GLOBAL, Error.Level.ERROR,
					"no sumOfDealingUnitGridCurrentAllowancePerUnitA value in POLICY.safety : "
							+ JsonObjectUtil.getJsonObject(policy, "safety"),
					completionHandler);
		}
	}

}
