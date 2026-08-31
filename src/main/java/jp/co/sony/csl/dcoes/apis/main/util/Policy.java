package jp.co.sony.csl.dcoes.apis.main.util;

import io.vertx.core.json.JsonObject;
import java.util.List;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectUtil;

public class Policy {

	private Policy() {
	}

	public static String masterSide(JsonObject policy) {
		String result = JsonObjectUtil.getString(policy, "gridMaster", "voltageReferenceSide");
		if (!"dischargeUnit".equals(result) && !"chargeUnit".equals(result)) {
			ErrorExceptionUtil.log(Error.Category.USER, Error.Extent.LOCAL, Error.Level.WARN,
					"gridMaster.voltageReferenceSide '" + result + "' not supported, use 'chargeUnit'");
			result = "chargeUnit";
		}
		return result;
	}

	public static String dealReferenceSide(JsonObject policy) {
		String result = JsonObjectUtil.getString(policy, "gridMaster", "deal", "referenceSide");
		if (!"chargeUnit".equals(result) && !"dischargeUnit".equals(result)) {
			ErrorExceptionUtil.log(Error.Category.USER, Error.Extent.LOCAL, Error.Level.WARN,
					"gridMaster.deal.referenceSide '" + result + "' not supported, use 'chargeUnit'");
			result = "chargeUnit";
		}
		return result;
	}

	public static String gridMasterSelectionStrategy(JsonObject policy) {
		String result = JsonObjectUtil.getString(policy, "gridMaster", "gridMasterSelection", "strategy");
		if (!"anywhere".equals(result) && !"fixed".equals(result) && !"voltageReferenceUnit".equals(result)) {
			ErrorExceptionUtil.log(Error.Category.USER, Error.Extent.LOCAL, Error.Level.WARN,
					"gridMaster.gridMasterSelection.strategy '" + result
							+ "' not supported, use 'voltageReferenceUnit'");
			result = "voltageReferenceUnit";
		}
		if ("fixed".equals(result) && gridMasterSelectionFixedUnitId(policy) == null) {
			ErrorExceptionUtil.log(Error.Category.USER, Error.Extent.LOCAL, Error.Level.WARN,
					"gridMaster.gridMasterSelection.strategy is '" + result
							+ "' but no fixedUnitId specified, use 'voltageReferenceUnit'");
			result = "voltageReferenceUnit";
		}
		return result;
	}

	public static String gridMasterSelectionFixedUnitId(JsonObject policy) {
		return JsonObjectUtil.getString(policy, "gridMaster", "gridMasterSelection", "fixedUnitId");
	}

	public static String masterDealSelectionStrategy(JsonObject policy) {
		String result = JsonObjectUtil.getString(policy, "gridMaster", "masterDealSelection", "strategy");
		if (!"newestDeal".equals(result)) {
			ErrorExceptionUtil.log(Error.Category.USER, Error.Extent.LOCAL, Error.Level.WARN,
					"gridMaster.masterDealSelection.strategy '" + result + "' not supported, use 'newestDeal'");
			result = "newestDeal";
		}
		return result;
	}

	public static List<String> largeCapacityUnitIds(JsonObject policy) {
		return JsonObjectUtil.getStringList(policy, "largeCapacityUnitIds");
	}

	public static Boolean gridVoltageOptimizationEnabled(JsonObject policy) {
		return JsonObjectUtil.getBoolean(policy, Boolean.FALSE, "gridMaster", "gridVoltageOptimization", "enabled");
	}

	public static String voltageReferenceTakeOverDvg(JsonObject policy) {
		String result = JsonObjectUtil.getString(policy, "gridMaster", "voltageReferenceTakeOverDvg");
		if (!"theoretical".equals(result) && !"actual".equals(result)) {
			ErrorExceptionUtil.log(Error.Category.USER, Error.Extent.LOCAL, Error.Level.WARN,
					"gridMaster.voltageReferenceTakeOverDvg '" + result + "' not supported, use 'theoretical'");
			result = "theoretical";
		}
		return result;
	}

}
