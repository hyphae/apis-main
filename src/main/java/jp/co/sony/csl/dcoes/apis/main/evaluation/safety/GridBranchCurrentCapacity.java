package jp.co.sony.csl.dcoes.apis.main.evaluation.safety;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import jp.co.sony.csl.dcoes.apis.common.Deal;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

/**
 * Judge whether or not an interchange is possible based on grid currents, and perform safety checks.
 * If {@code POLICY.safety.gridTopologyBasedEvaluation.enabled} is {@code true}, perform calculations taking topology into consideration.
 * @author OES Project
 *          
 * グリッド電流に基づく融通可否判定および安全性チェック処理.
 * {@code POLICY.safety.gridTopologyBasedEvaluation.enabled} が {@code true} ならトポロジを考慮した計算を実施する.
 * @author OES Project
 */
public class GridBranchCurrentCapacity {

	private static final Logger log = LoggerFactory.getLogger(GridBranchCurrentCapacity.class);

	private GridBranchCurrentCapacity() { }

	/**
	 * Check the safety of the grid current capacity.
	 * Raise a GLOBAL ERROR if exceeded capacity is detected.
	 * @param vertx a vertx object
	 * @param policy a POLICY object
	 * @param unitData unit data of all units
	 * @param completionHandler the completion handler
	 *          
	 * グリッド電流容量の安全性をチェックする.
	 * 容量オーバーを検知したら GLOBAL:ERROR を発する.
	 * @param vertx vertx オブジェクト
	 * @param policy POLICY オブジェクト
	 * @param unitData 全ユニットのユニットデータ
	 * @param completionHandler the completion handler
	 */
	public static void check(Vertx vertx, JsonObject policy, JsonObject unitData, Handler<AsyncResult<Void>> completionHandler) {
		if (JsonObjectUtil.getBoolean(policy, Boolean.FALSE, "safety", "gridTopologyBasedEvaluation", "enabled")) {
			check_topologyBased_(vertx, policy, unitData, completionHandler);
		} else {
			// If not using topology functions (POLICY.safety.gridTopologyBasedEvaluation.enabled == false), do nothing
			// トポロジ機能を使わない ( POLICY.safety.gridTopologyBasedEvaluation.enabled == false ) なら何もしない
			completionHandler.handle(Future.succeededFuture());
		}
	}
	/**
	 * Perform current capacity checks that take the grid topology into consideration.
	 * @param vertx a vertx object
	 * @param policy a POLICY object
	 * @param unitData unit data of all units
	 * @param completionHandler the completion handler
	 * TODO: Checks are ended if one deviation is found, but should check everything
	 *          
	 * グリッドのトポロジを考慮した電流容量チェックを実行する.
	 * @param vertx vertx オブジェクト
	 * @param policy POLICY オブジェクト
	 * @param unitData 全ユニットのユニットデータ
	 * @param completionHandler the completion handler
	 * TODO : 逸脱が一つ見つかったら終わらせてるが全部チェックすべき
	 */
	private static void check_topologyBased_(Vertx vertx, JsonObject policy, JsonObject unitData, Handler<AsyncResult<Void>> completionHandler) {
		JsonObject config = JsonObjectUtil.getJsonObject(policy, "safety", "gridTopologyBasedEvaluation");
		List<String> branchIds = JsonObjectUtil.getStringList(config, "branchIds");
		if (branchIds != null) {
			for (String aBranchId : branchIds) {
				if (log.isInfoEnabled()) log.info(aBranchId);
				Float capacity = JsonObjectUtil.getFloat(config, "branchCurrentCapacityA", aBranchId);
				// Check only in the forward direction
				// 順方向だけチェックする
				List<String> forwardUnitIds = JsonObjectUtil.getStringList(config, "branchAssociation", aBranchId, "forwardUnitIds");
				if (capacity != null && forwardUnitIds != null) {
					if (log.isInfoEnabled()) log.info("  currentCapacityA : " + capacity);
					float sum = 0F;
					for (String aUnitId : forwardUnitIds) {
						// Add the unit's ig
						// ユニットの ig を加算する
						Float ig = JsonObjectUtil.getFloat(unitData, aUnitId, "dcdc", "meter", "ig");
						if (ig != null) {
							sum += ig;
						} else {
							ErrorUtil.report(vertx, Error.Category.HARDWARE, Error.Extent.GLOBAL, Error.Level.WARN, "no " + aUnitId + ".dcdc.meter.ig");
						}
					}
					if (log.isInfoEnabled()) log.info("  forward sum of dcdc.meter.ig : " + sum);
					if (Math.abs(capacity) < Math.abs(sum)) {
						ErrorUtil.reportAndFail(vertx, Error.Category.HARDWARE, Error.Extent.GLOBAL, Error.Level.ERROR, "branch : " + aBranchId + ", forward sum of dcdc.meter.ig : " + sum + ", exceeds capacity : " + capacity, completionHandler);
						return;
					}
				} else {
					ErrorUtil.report(vertx, Error.Category.USER, Error.Extent.GLOBAL, Error.Level.WARN, "data deficiency ; POLICY.safety.gridTopologyBasedEvaluation.branchCurrentCapacityA." + aBranchId + " : " + capacity + ", POLICY.safety.gridTopologyBasedEvaluation.branchAssociation." + aBranchId + ".forwardUnitIds : " + forwardUnitIds);
				}
			}
			completionHandler.handle(Future.succeededFuture());
		} else {
			ErrorUtil.reportAndFail(vertx, Error.Category.USER, Error.Extent.GLOBAL, Error.Level.WARN, "no POLICY.safety.gridTopologyBasedEvaluation.branchIds", completionHandler);
		}
	}

	/**
	 * Judge whether or not a new interchange is possible based on the grid current capacity.
	 * @param vertx a vertx object
	 * @param policy a POLICY object
	 * @param deal a new DEAL object
	 * @param otherDeals a list of other DEAL objects
	 * @return {@code null} if possible, or the reason for failure otherwise
	 *          
	 * グリッド電流容量から新しい融通の可否を判定する.
	 * @param vertx vertx オブジェクト
	 * @param policy POLICY オブジェクト
	 * @param deal 新しい DEAL オブジェクト
	 * @param otherDeals その他の DEAL オブジェクトのリスト
	 * @return 可なら {@code null}, 不可なら理由
	 */
	public static String checkNewDeal(Vertx vertx, JsonObject policy, JsonObject deal, List<JsonObject> otherDeals) {
		// Start by making a list containing only interchanges that are operating
		// まず動いている融通だけをリストし
		List<JsonObject> activeDeals = activeDeals_(otherDeals);
		// Check the effect of adding the new interchange. If the result is out, then this addition is impossible
		// それに新しく始める融通を加えてチェックした結果アウトなら不可ということ
		activeDeals.add(deal);
		if (JsonObjectUtil.getBoolean(policy, Boolean.FALSE, "safety", "gridTopologyBasedEvaluation", "enabled")) {
			return checkNewDeal_topologyBased_(vertx, policy, activeDeals);
		} else {
			return checkNewDeal_gridTotal_(vertx, policy, activeDeals);
		}
	}
	/**
	 * Judge the possibility of adding a new interchange by taking the grid topology into consideration.
	 * @param vertx a vertx object
	 * @param policy a POLICY object
	 * @param activeDeals a list of DEAL objects
	 * @return {@code true} if possible
	 *          
	 * グリッドのトポロジを考慮した新規融通可否判定を実行する.
	 * @param vertx vertx オブジェクト
	 * @param policy POLICY オブジェクト
	 * @param activeDeals DEAL オブジェクトのリスト
	 * @return 可なら {@code true}
	 */
	private static String checkNewDeal_topologyBased_(Vertx vertx, JsonObject policy, List<JsonObject> activeDeals) {
		JsonObject config = JsonObjectUtil.getJsonObject(policy, "safety", "gridTopologyBasedEvaluation");
		List<String> branchIds = JsonObjectUtil.getStringList(config, "branchIds");
		if (branchIds != null) {
			for (String aBranchId : branchIds) {
				if (log.isInfoEnabled()) log.info(aBranchId);
				Float capacity = JsonObjectUtil.getFloat(config, "branchCurrentCapacityA", aBranchId);
				List<String> forwardUnitIds = JsonObjectUtil.getStringList(config, "branchAssociation", aBranchId, "forwardUnitIds");
				List<String> backwardUnitIds = JsonObjectUtil.getStringList(config, "branchAssociation", aBranchId, "backwardUnitIds");
				if (capacity != null && forwardUnitIds != null && backwardUnitIds != null) {
					if (log.isInfoEnabled()) log.info("  currentCapacityA : " + capacity);
					// Check in the forward direction
					// 順方向チェック
					float forwardDischargeSum = 0F;
					float forwardChargeSum = 0F;
					for (String aUnitId : forwardUnitIds) {
						for (JsonObject aDeal : activeDeals) {
							Float dealGridCurrentA = Deal.dealGridCurrentA(aDeal);
							if (dealGridCurrentA != null) {
								// Add the interchange currents separately for the discharge and charging sides
								// 送電側と受電側とに分けて融通電流を加算する
								if (Deal.isDischargeUnit(aDeal, aUnitId)) {
									forwardDischargeSum += dealGridCurrentA;
								} else if (Deal.isChargeUnit(aDeal, aUnitId)) {
									forwardChargeSum += dealGridCurrentA;
								}
							} else {
								String msg = "no dealGridCurrentA in deal : " + aDeal;
								ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.ERROR, msg);
								return msg;
							}
						}
					}
					if (log.isInfoEnabled()) log.info("  forward sum of discharge dealGridCurrentA : " + forwardDischargeSum);
					if (log.isInfoEnabled()) log.info("  forward sum of charge dealGridCurrentA : " + forwardChargeSum);
					if (Math.abs(capacity) < Math.abs(forwardDischargeSum)) {
						return "branch : " + aBranchId + ", forward sum of discharge dealGridCurrentA : " + forwardDischargeSum + ", exceeds capacity : " + capacity;
					} else if (Math.abs(capacity) < Math.abs(forwardChargeSum)) {
						return "branch : " + aBranchId + ", forward sum of charge dealGridCurrentA : " + forwardChargeSum + ", exceeds capacity : " + capacity;
					}
					// Check in the reverse direction
					// 逆方向チェック
					float backwardDischargeSum = 0F;
					float backwardChargeSum = 0F;
					for (String aUnitId : backwardUnitIds) {
						for (JsonObject aDeal : activeDeals) {
							Float dealGridCurrentA = Deal.dealGridCurrentA(aDeal);
							if (dealGridCurrentA != null) {
								// Add the interchange currents separately for the discharge and charging sides
								// 送電側と受電側とに分けて融通電流を加算する
								if (Deal.isDischargeUnit(aDeal, aUnitId)) {
									backwardDischargeSum += dealGridCurrentA;
								} else if (Deal.isChargeUnit(aDeal, aUnitId)) {
									backwardChargeSum += dealGridCurrentA;
								}
							} else {
								String msg = "no dealGridCurrentA in deal : " + aDeal;
								ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.ERROR, msg);
								return msg;
							}
						}
					}
					if (log.isInfoEnabled()) log.info("  backward sum of discharge dealGridCurrentA : " + backwardDischargeSum);
					if (log.isInfoEnabled()) log.info("  backward sum of charge dealGridCurrentA : " + backwardChargeSum);
					if (Math.abs(capacity) < Math.abs(backwardDischargeSum)) {
						return "branch : " + aBranchId + ", backward sum of discharge dealGridCurrentA : " + backwardDischargeSum + ", exceeds capacity : " + capacity;
					} else if (Math.abs(capacity) < Math.abs(backwardChargeSum)) {
						return "branch : " + aBranchId + ", backward sum of charge dealGridCurrentA : " + backwardChargeSum + ", exceeds capacity : " + capacity;
					}
				} else {
					String msg = "data deficiency ; POLICY.safety.gridTopologyBasedEvaluation.branchCurrentCapacityA." + aBranchId + " : " + capacity + ", POLICY.safety.gridTopologyBasedEvaluation.branchAssociation." + aBranchId + ".forwardUnitIds : " + forwardUnitIds + ", POLICY.safety.gridTopologyBasedEvaluation.branchAssociation." + aBranchId + ".backwardUnitIds : " + backwardUnitIds;
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
	/**
	 * Judge whether or not a new interchange is possible based only on the total value of the interchange currents.
	 * @param vertx a vertx object
	 * @param policy a POLICY object
	 * @param activeDeals a list of DEAL objects
	 * @return {@code true} if possible
	 *          
	 * 融通電流の合計値だけで新規融通可否判定を実行する.
	 * @param vertx vertx オブジェクト
	 * @param policy POLICY オブジェクト
	 * @param activeDeals DEAL オブジェクトのリスト
	 * @return 可なら {@code true}
	 */
	private static String checkNewDeal_gridTotal_(Vertx vertx, JsonObject policy, List<JsonObject> activeDeals) {
		Float sumOfDealGridCurrentMaxA = JsonObjectUtil.getFloat(policy, "safety", "sumOfDealGridCurrentMaxA");
		if (sumOfDealGridCurrentMaxA != null) {
			if (log.isInfoEnabled()) log.info("sumOfDealGridCurrentMaxA : " + sumOfDealGridCurrentMaxA);
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
			if (log.isInfoEnabled()) log.info("sumOfDealGridCurrentA : " + sumOfDealGridCurrentA);
			if (sumOfDealGridCurrentMaxA < sumOfDealGridCurrentA) {
				return "sum of dealGridCurrentA of all deals : " + sumOfDealGridCurrentA + " ; exceeds limit : " + sumOfDealGridCurrentMaxA;
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
