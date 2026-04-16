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
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectWrapper;
import jp.co.sony.csl.dcoes.apis.main.app.HwConfigKeeping;
import jp.co.sony.csl.dcoes.apis.main.app.StateHandling;
import jp.co.sony.csl.dcoes.apis.main.app.controller.DeviceControlling;
import jp.co.sony.csl.dcoes.apis.main.app.controller.util.DDCon;
import jp.co.sony.csl.dcoes.apis.main.app.mediator.util.DealUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ApisConfig;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorExceptionUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

/**
 * Local safety check processing.
 * @author OES Project
 *          
 * ローカル安全性チェック処理.
 * @author OES Project
 */
public class LocalSafetyEvaluation {
	private static final Logger log = LoggerFactory.getLogger(LocalSafetyEvaluation.class);

	/**
	 * A cache for passing through just one error.
	 *          
	 * 一発だけのエラーはスルーするためのキャッシュ.
	 */
	public static final JsonObjectWrapper errors = new JsonObjectWrapper();
	private static final String ERROR_DDCON_ACTIVE_BUT_NO_DEAL = "ERROR_DDCON_ACTIVE_BUT_NO_DEAL";
	private static final String ERROR_INVALID_DEALS_DIRECTION = "ERROR_INVALID_DEALS_DIRECTION";
	private static final String ERROR_INVALID_DDCON_MODE = "ERROR_INVALID_DDCON_MODE";
	private static final String ERROR_NO_DDCON_DIG_DVG = "ERROR_NO_DDCON_DIG_DVG";
	private static final String ERROR_WAIT_INVALID_METER_IG = "ERROR_WAIT_INVALID_METER_IG";
	private static final String ERROR_VR_INVALID_METER_VG = "ERROR_VR_INVALID_METER_VG";
	private static final String ERROR_CHARGE_INVALID_METER_IG = "ERROR_CHARGE_INVALID_METER_IG";
	private static final String ERROR_CHARGE_INVALID_METER_VG = "ERROR_CHARGE_INVALID_METER_VG";
	private static final String ERROR_DISCHARGE_INVALID_METER_IG = "ERROR_DISCHARGE_INVALID_METER_IG";
	private static final String ERROR_DISCHARGE_INVALID_METER_VG = "ERROR_DISCHARGE_INVALID_METER_VG";

	private LocalSafetyEvaluation() { }

	/**
	 * Check the local stability (in this unit).
	 * @param vertx a vertx object
	 * @param policy a POLICY object
	 * @param unitData the unit data of this unit
	 * @param completionHandler the completion handler
	 *          
	 * ローカルな ( ユニット内の ) 安全性をチェックする.
	 * @param vertx vertx オブジェクト
	 * @param policy POLICY オブジェクト
	 * @param unitData 自ユニットのユニットデータ
	 * @param completionHandler the completion handler
	 */
	public static void check(Vertx vertx, JsonObject policy, JsonObject unitData, Handler<AsyncResult<Void>> completionHandler) {
		StateHandling.operationMode(vertx, resOperationMode -> {
			if (resOperationMode.succeeded()) {
				boolean failed = false;
				String apisOperationMode = resOperationMode.result();
				if (!checkAlarmState_(vertx, apisOperationMode, unitData)) {
					failed = true;
				}
				if (!checkBatteryOperationStatus(vertx, apisOperationMode, unitData)) {
					failed = true;
				}
				if (!checkModeAndDeal_(vertx, apisOperationMode, unitData, policy)) {
					failed = true;
				}
				if (!checkFloatRange_(vertx, apisOperationMode, unitData, new String[] {"dcdc", "meter", "tmp"}, Error.Level.ERROR, true)) {
					failed = true;
				}
				if (!checkFloatRange_(vertx, apisOperationMode, unitData, new String[] {"dcdc", "meter", "vg"}, Error.Level.ERROR, true)) {
					failed = true;
				}
				if (!checkFloatRange_(vertx, apisOperationMode, unitData, new String[] {"dcdc", "meter", "vb"}, Error.Level.ERROR, true)) {
					failed = true;
				}
				if (!checkFloatRange_(vertx, apisOperationMode, unitData, new String[] {"dcdc", "meter", "ig"}, Error.Level.ERROR, true)) {
					failed = true;
				}
				if (!checkFloatRange_(vertx, apisOperationMode, unitData, new String[] {"dcdc", "meter", "ib"}, Error.Level.ERROR, true)) {
					failed = true;
				}
				if (!checkDynamic_(vertx, apisOperationMode, unitData, policy)) {
					failed = true;
				}
				if (failed) {
					completionHandler.handle(Future.failedFuture("local safety evaluation failed"));
				} else {
					completionHandler.handle(Future.succeededFuture());
				}
			} else {
				completionHandler.handle(Future.failedFuture(resOperationMode.cause()));
			}
		});
	}

	private static boolean checkAlarmState_(Vertx vertx, String apisOperationMode, JsonObject unitData) {
		String alarmState = JsonObjectUtil.getString(unitData, "dcdc", "status", "alarmState");
		if (DDCon.AlarmState.HEAVY_ALARM == DDCon.alarmStateFromCode(alarmState)) {
			ErrorUtil.report(vertx, Error.Category.HARDWARE, Error.Extent.LOCAL, Error.Level.ERROR, "invalid dcdc.status.alarmState value : " + alarmState);
			return false;
		}
		return true;
	}

	private static boolean checkBatteryOperationStatus(Vertx vertx, String apisOperationMode, JsonObject unitData) {
		if (JsonObjectUtil.getValue(unitData, "battery", "battery_operation_status") != null) {
			Integer value = JsonObjectUtil.getInteger(unitData, "battery", "battery_operation_status");
			if (value == null || value != 3) {
				ErrorUtil.report(vertx, Error.Category.HARDWARE, Error.Extent.LOCAL, Error.Level.ERROR, "invalid battery.battery_operation_status value : " + JsonObjectUtil.getValue(unitData, "battery", "battery_operation_status"));
				return false;
			}
		}
		return true;
	}

	private static boolean checkModeAndDeal_(Vertx vertx, String apisOperationMode, JsonObject unitData, JsonObject policy) {
		boolean result = true;
		String mode_ = JsonObjectUtil.getString(unitData, "dcdc", "status", "status");
		String operationMode_ = JsonObjectUtil.getString(unitData, "dcdc", "status", "operationMode");
		DDCon.Mode mode = DDCon.modeFromCode(mode_);
		if (mode == null) {
			ErrorUtil.report(vertx, Error.Category.HARDWARE, Error.Extent.LOCAL, Error.Level.ERROR, "invalid dcdc.status.status value : " + mode_);
			result = false;
		} else {
			DDCon.OperationMode operationMode = DDCon.operationModeFromCode(operationMode_);
			if (DDCon.operationModeForMode(mode) != operationMode) {
				ErrorUtil.report(vertx, Error.Category.HARDWARE, Error.Extent.LOCAL, Error.Level.ERROR, "invalid dcdc.status.operationMode value : " + operationMode_ + " ; dcdc.status.status : " + mode_);
				result = false;
			}
			List<Boolean> failed = new ArrayList<>();
			DealUtil.withUnitId(vertx, ApisConfig.unitId(), resWithUnitId -> {
				if (resWithUnitId.succeeded()) {
					List<JsonObject> deals = resWithUnitId.result();
					if (deals.isEmpty()) {
						if ("manual".equals(apisOperationMode)) {
							// skip DDCon mode check
							errors.remove(ERROR_DDCON_ACTIVE_BUT_NO_DEAL);
						} else {
							if (DDCon.Mode.WAIT != mode) {
								errors.add(Boolean.FALSE, ERROR_DDCON_ACTIVE_BUT_NO_DEAL);
								String msg = "invalid dcdc.status.status value : " + mode_ + " ; but no deal exists";
								if (1 < errors.getJsonArray(ERROR_DDCON_ACTIVE_BUT_NO_DEAL).size()) {
									ErrorUtil.report(vertx, Error.Category.HARDWARE, Error.Extent.LOCAL, Error.Level.ERROR, msg);
								} else {
									ErrorUtil.report(vertx, Error.Category.HARDWARE, Error.Extent.LOCAL, Error.Level.WARN, msg);
								}
								failed.add(Boolean.TRUE);
							} else {
								errors.remove(ERROR_DDCON_ACTIVE_BUT_NO_DEAL);
							}
						}
						errors.remove(ERROR_INVALID_DEALS_DIRECTION);
						errors.remove(ERROR_INVALID_DDCON_MODE);
					} else {
						if ("stop".equals(apisOperationMode) || "manual".equals(apisOperationMode)) {
							ErrorUtil.report(vertx, Error.Category.USER, Error.Extent.LOCAL, Error.Level.ERROR, "operationMode : " + apisOperationMode + " ; but deal exists");
							failed.add(Boolean.TRUE);
						} else {
							if (!isCorrectDealsDirection_(vertx, deals)) {
								errors.add(Boolean.FALSE, ERROR_INVALID_DEALS_DIRECTION);
								String msg = "invalid deals direction ; deals : " + deals;
								if (1 < errors.getJsonArray(ERROR_INVALID_DEALS_DIRECTION).size()) {
									ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, msg);
								} else {
									ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, msg);
								}
								failed.add(Boolean.TRUE);
							} else {
								errors.remove(ERROR_INVALID_DEALS_DIRECTION);
							}
							if (!isCorrectDDConMode_(vertx, deals, mode)) {
								errors.add(Boolean.FALSE, ERROR_INVALID_DDCON_MODE);
								String msg = "invalid dcdc.status.status value : " + mode_ + " ; deals : " + deals;
								if (1 < errors.getJsonArray(ERROR_INVALID_DDCON_MODE).size()) {
									ErrorUtil.report(vertx, Error.Category.HARDWARE, Error.Extent.LOCAL, Error.Level.ERROR, msg);
								} else {
									ErrorUtil.report(vertx, Error.Category.HARDWARE, Error.Extent.LOCAL, Error.Level.WARN, msg);
								}
								failed.add(Boolean.TRUE);
							} else {
								errors.remove(ERROR_INVALID_DDCON_MODE);
							}
						}
						errors.remove(ERROR_DDCON_ACTIVE_BUT_NO_DEAL);
					}
				} else {
					ErrorExceptionUtil.reportIfNeed(vertx, resWithUnitId.cause());
					failed.add(Boolean.TRUE);
				}
			});
			if (!failed.isEmpty()) {
				result = false;
			}
		}
		return result;
	}
	private static boolean isCorrectDealsDirection_(Vertx vertx, List<JsonObject> deals) {
		Boolean isDischargeUnit = null;
		for (JsonObject aDeal : deals) {
			if (Deal.masterSideUnitMustBeActive(aDeal)) {
				boolean aDealIsDischargeUnit = Deal.isDischargeUnit(aDeal, ApisConfig.unitId());
				if (isDischargeUnit == null) {
					isDischargeUnit = aDealIsDischargeUnit;
				} else {
					if (isDischargeUnit.booleanValue() != aDealIsDischargeUnit) {
						return false;
					}
				}
			}
		}
		return true;
	}
	private static boolean isCorrectDDConMode_(Vertx vertx, List<JsonObject> deals, DDCon.Mode mode) {
		boolean allInactive = true;
		for (JsonObject aDeal : deals) {
			if (Deal.masterSideUnitMustBeActive(aDeal)) {
				allInactive = false;
				DDCon.Mode cvMode = (Deal.isDischargeUnit(aDeal, ApisConfig.unitId())) ? DDCon.Mode.DISCHARGE : DDCon.Mode.CHARGE;
				if (Deal.bothSideUnitsMustBeActive(aDeal)) {
					if (DDCon.Mode.VOLTAGE_REFERENCE != mode && cvMode != mode) {
						return false;
					}
				} else {
					if (DDCon.Mode.VOLTAGE_REFERENCE != mode && cvMode != mode && DDCon.Mode.WAIT != mode) {
						return false;
					}
				}
			}
		}
		if (allInactive) {
			return (DDCon.Mode.WAIT == mode);
		}
		return true;
	}

	private static boolean checkFloatRange_(Vertx vertx, String apisOperationMode, JsonObject unitData, String[] valuePath, Error.Level level, boolean required) {
		Float value = JsonObjectUtil.getFloat(unitData, valuePath);
		if (value == null) {
			if (required) {
				ErrorUtil.report(vertx, Error.Category.HARDWARE, Error.Extent.LOCAL, Error.Level.ERROR, "no " + String.join(".", valuePath) + " value in unit data : " + unitData);
				return false;
			} else {
				return true;
			}
		}
		JsonObject range = HwConfigKeeping.safetyRange(valuePath);
		if (range == null) {
			ErrorUtil.report(vertx, Error.Category.USER, Error.Extent.LOCAL, Error.Level.ERROR, "no " + String.join(".", valuePath) + " safety range values in hwConfig : " + HwConfigKeeping.CACHE.jsonObject());
			return false;
		}
		Float min = JsonObjectUtil.getFloat(range, "min");
		Float max = JsonObjectUtil.getFloat(range, "max");
		if (min == null || max == null) {
			ErrorUtil.report(vertx, Error.Category.USER, Error.Extent.LOCAL, Error.Level.ERROR, "no min and/or max value in " + String.join(".", valuePath) + " safety range values : " + range);
			return false;
		}
		if (value < min || max < value) {
			ErrorUtil.report(vertx, Error.Category.HARDWARE, Error.Extent.LOCAL, level, "invalid " + String.join(".", valuePath) + " value : " + value + " ; should between " + min + " and " + max);
			return false;
		}
		return true;
	}

	private static boolean checkDynamic_(Vertx vertx, String apisOperationMode, JsonObject unitData, JsonObject policy) {
		if ("manual".equals(apisOperationMode)) {
			return true;
		}
		if (DeviceControlling.ignoreDynamicSafetyCheck()) {
			if (log.isInfoEnabled()) log.info("skip dynamic safety check");
			return true;
		}
		String mode_ = JsonObjectUtil.getString(unitData, "dcdc", "status", "status");
		DDCon.Mode mode = DDCon.modeFromCode(mode_);
		if (mode == null) {
			return false;
		}
		Float vg = JsonObjectUtil.getFloat(unitData, "dcdc", "meter", "vg");
		Float ig = JsonObjectUtil.getFloat(unitData, "dcdc", "meter", "ig");
		if (vg == null || ig == null) {
			return false;
		}
		Float operationGridVoltageVRangeMin = JsonObjectUtil.getFloat(policy, "operationGridVoltageVRange", "min");
		Float operationGridVoltageVRangeMax = JsonObjectUtil.getFloat(policy, "operationGridVoltageVRange", "max");
		if (operationGridVoltageVRangeMin == null || operationGridVoltageVRangeMax == null) {
			ErrorUtil.report(vertx, Error.Category.USER, Error.Extent.LOCAL, Error.Level.ERROR, "no operationGridVoltageVRange.min and/or operationGridVoltageVRange.max value in policy : " + policy);
			return false;
		}
		Float gridVoltageAllowanceV = JsonObjectUtil.getFloat(policy, "gridVoltageAllowanceV");
		if (gridVoltageAllowanceV == null) {
			ErrorUtil.report(vertx, Error.Category.USER, Error.Extent.LOCAL, Error.Level.ERROR, "no gridVoltageAllowanceV value in policy : " + policy);
			return false;
		}
		Float gridCurrentAllowanceA = HwConfigKeeping.gridCurrentAllowanceA();
		if (gridCurrentAllowanceA == null) {
			ErrorUtil.report(vertx, Error.Category.USER, Error.Extent.LOCAL, Error.Level.ERROR, "no gridCurrentAllowanceA value in hwConfig : " + HwConfigKeeping.CACHE.jsonObject());
			return false;
		}
		Float dig = JsonObjectUtil.getFloat(unitData, "dcdc", "param", "dig");
		Float dvg = JsonObjectUtil.getFloat(unitData, "dcdc", "vdis", "dvg");
		if (dig == null || dvg == null) {
			errors.add(Boolean.FALSE, ERROR_NO_DDCON_DIG_DVG);
			String msg = "no dcdc.param.dig and/or dcdc.vdis.dvg value in unit data : " + unitData;
			if (1 < errors.getJsonArray(ERROR_NO_DDCON_DIG_DVG).size()) {
				ErrorUtil.report(vertx, Error.Category.HARDWARE, Error.Extent.LOCAL, Error.Level.ERROR, msg);
			} else {
				ErrorUtil.report(vertx, Error.Category.HARDWARE, Error.Extent.LOCAL, Error.Level.WARN, msg);
			}
			return false;
		} else {
			errors.remove(ERROR_NO_DDCON_DIG_DVG);
		}
		switch (mode) {
		case WAIT:
			errors.remove(ERROR_VR_INVALID_METER_VG);
			errors.remove(ERROR_CHARGE_INVALID_METER_IG);
			errors.remove(ERROR_CHARGE_INVALID_METER_VG);
			errors.remove(ERROR_DISCHARGE_INVALID_METER_IG);
			errors.remove(ERROR_DISCHARGE_INVALID_METER_VG);
			if (ig < 0 - gridCurrentAllowanceA || 0 + gridCurrentAllowanceA < ig) {
				errors.add(Boolean.FALSE, ERROR_WAIT_INVALID_METER_IG);
				String msg = "dcdc mode : " + mode + " ; invalid dcdc.meter.ig value : " + ig + " ; should between " + (0 - gridCurrentAllowanceA) + " and " + (0 + gridCurrentAllowanceA);
				if (1 < errors.getJsonArray(ERROR_WAIT_INVALID_METER_IG).size()) {
					ErrorUtil.report(vertx, Error.Category.HARDWARE, Error.Extent.LOCAL, Error.Level.ERROR, msg);
				} else {
					ErrorUtil.report(vertx, Error.Category.HARDWARE, Error.Extent.LOCAL, Error.Level.WARN, msg);
				}
				return false;
			} else {
				errors.remove(ERROR_WAIT_INVALID_METER_IG);
			}
			break;
		case VOLTAGE_REFERENCE:
			errors.remove(ERROR_WAIT_INVALID_METER_IG);
			errors.remove(ERROR_CHARGE_INVALID_METER_IG);
			errors.remove(ERROR_CHARGE_INVALID_METER_VG);
			errors.remove(ERROR_DISCHARGE_INVALID_METER_IG);
			errors.remove(ERROR_DISCHARGE_INVALID_METER_VG);
			if (vg < dvg - gridVoltageAllowanceV || dvg + gridVoltageAllowanceV < vg) {
				errors.add(Boolean.FALSE, ERROR_VR_INVALID_METER_VG);
				String msg = "dcdc mode : " + mode + " ; invalid dcdc.meter.vg value : " + vg + " ; should between " + (dvg - gridVoltageAllowanceV) + " and " + (dvg + gridVoltageAllowanceV);
				if (1 < errors.getJsonArray(ERROR_VR_INVALID_METER_VG).size()) {
					ErrorUtil.report(vertx, Error.Category.HARDWARE, Error.Extent.LOCAL, Error.Level.ERROR, msg);
				} else {
					ErrorUtil.report(vertx, Error.Category.HARDWARE, Error.Extent.LOCAL, Error.Level.WARN, msg);
				}
				return false;
			} else {
				errors.remove(ERROR_VR_INVALID_METER_VG);
			}
			break;
		case CHARGE:
			errors.remove(ERROR_WAIT_INVALID_METER_IG);
			errors.remove(ERROR_VR_INVALID_METER_VG);
			errors.remove(ERROR_DISCHARGE_INVALID_METER_IG);
			errors.remove(ERROR_DISCHARGE_INVALID_METER_VG);
			if (ig < dig - gridCurrentAllowanceA || dig + gridCurrentAllowanceA < ig) {
				errors.add(Boolean.FALSE, ERROR_CHARGE_INVALID_METER_IG);
				String msg = "dcdc mode : " + mode + " ; invalid dcdc.meter.ig value : " + ig + " ; should between " + (dig - gridCurrentAllowanceA) + " and " + (dig + gridCurrentAllowanceA);
				if (1 < errors.getJsonArray(ERROR_CHARGE_INVALID_METER_IG).size()) {
					ErrorUtil.report(vertx, Error.Category.HARDWARE, Error.Extent.LOCAL, Error.Level.ERROR, msg);
				} else {
					ErrorUtil.report(vertx, Error.Category.HARDWARE, Error.Extent.LOCAL, Error.Level.WARN, msg);
				}
				return false;
			} else {
				errors.remove(ERROR_CHARGE_INVALID_METER_IG);
			}
			if (vg < operationGridVoltageVRangeMin || operationGridVoltageVRangeMax < vg) {
				errors.add(Boolean.FALSE, ERROR_CHARGE_INVALID_METER_VG);
				String msg = "dcdc mode : " + mode + " ; invalid dcdc.meter.vg value : " + vg + " ; should between " + operationGridVoltageVRangeMin + " and " + operationGridVoltageVRangeMax;
				if (1 < errors.getJsonArray(ERROR_CHARGE_INVALID_METER_VG).size()) {
					ErrorUtil.report(vertx, Error.Category.HARDWARE, Error.Extent.LOCAL, Error.Level.ERROR, msg);
				} else {
					ErrorUtil.report(vertx, Error.Category.HARDWARE, Error.Extent.LOCAL, Error.Level.WARN, msg);
				}
				return false;
			} else {
				errors.remove(ERROR_CHARGE_INVALID_METER_VG);
			}
			break;
		case DISCHARGE:
			errors.remove(ERROR_WAIT_INVALID_METER_IG);
			errors.remove(ERROR_VR_INVALID_METER_VG);
			errors.remove(ERROR_CHARGE_INVALID_METER_IG);
			errors.remove(ERROR_CHARGE_INVALID_METER_VG);
			if (ig < - dig - gridCurrentAllowanceA || - dig + gridCurrentAllowanceA < ig) {
				errors.add(Boolean.FALSE, ERROR_DISCHARGE_INVALID_METER_IG);
				String msg = "dcdc mode : " + mode + " ; invalid dcdc.meter.ig value : " + ig + " ; should between " + (- dig - gridCurrentAllowanceA) + " and " + (- dig + gridCurrentAllowanceA);
				if (1 < errors.getJsonArray(ERROR_DISCHARGE_INVALID_METER_IG).size()) {
					ErrorUtil.report(vertx, Error.Category.HARDWARE, Error.Extent.LOCAL, Error.Level.ERROR, msg);
				} else {
					ErrorUtil.report(vertx, Error.Category.HARDWARE, Error.Extent.LOCAL, Error.Level.WARN, msg);
				}
				return false;
			} else {
				errors.remove(ERROR_DISCHARGE_INVALID_METER_IG);
			}
			if (vg < operationGridVoltageVRangeMin || operationGridVoltageVRangeMax < vg) {
				errors.add(Boolean.FALSE, ERROR_DISCHARGE_INVALID_METER_VG);
				String msg = "dcdc mode : " + mode + " ; invalid dcdc.meter.vg value : " + vg + " ; should between " + operationGridVoltageVRangeMin + " and " + operationGridVoltageVRangeMax;
				if (1 < errors.getJsonArray(ERROR_DISCHARGE_INVALID_METER_VG).size()) {
					ErrorUtil.report(vertx, Error.Category.HARDWARE, Error.Extent.LOCAL, Error.Level.ERROR, msg);
				} else {
					ErrorUtil.report(vertx, Error.Category.HARDWARE, Error.Extent.LOCAL, Error.Level.WARN, msg);
				}
				return false;
			} else {
				errors.remove(ERROR_DISCHARGE_INVALID_METER_VG);
			}
		}
		return true;
	}

}
