package jp.co.sony.csl.dcoes.apis.main.app.gridmaster.main_loop;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import jp.co.sony.csl.dcoes.apis.common.Deal;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.ServiceAddress;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.ReplyFailureUtil;
import jp.co.sony.csl.dcoes.apis.main.app.PolicyKeeping;
import jp.co.sony.csl.dcoes.apis.main.util.ApisConfig;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

public class GridVoltageOptimization {
	private static final Logger log = LoggerFactory.getLogger(GridVoltageOptimization.class);

	private GridVoltageOptimization() {
	}

	public static void execute(Vertx vertx, List<JsonObject> deals, Handler<AsyncResult<Void>> onComplete) {
		float gridVoltage = 0F;
		int numberOfUnits = 0;
		for (JsonObject aDeal : deals) {
			if (Deal.masterSideUnitMustBeActive(aDeal)) {
				if (log.isInfoEnabled())
					log.info("dealId : " + Deal.dealId(aDeal));
				Float dischargeUnitEfficientGridVoltageV = Deal.dischargeUnitEfficientGridVoltageV(aDeal);
				Float chargeUnitEfficientGridVoltageV = Deal.chargeUnitEfficientGridVoltageV(aDeal);
				if (dischargeUnitEfficientGridVoltageV != null) {
					gridVoltage += dischargeUnitEfficientGridVoltageV;
					++numberOfUnits;
					if (log.isInfoEnabled())
						log.info("  discharge unit efficientGridVoltageV : "
								+ Deal.dischargeUnitEfficientGridVoltageV(aDeal));
				}
				if (chargeUnitEfficientGridVoltageV != null) {
					gridVoltage += chargeUnitEfficientGridVoltageV;
					++numberOfUnits;
					if (log.isInfoEnabled())
						log.info(
								"  charge unit efficientGridVoltageV : " + Deal.chargeUnitEfficientGridVoltageV(aDeal));
				}
			}
		}
		if (0 < numberOfUnits) {
			gridVoltage /= numberOfUnits;
			if (log.isInfoEnabled())
				log.info("average of efficientGridVoltageVs : " + gridVoltage);
			Float minOperationGridVoltageV = PolicyKeeping.cache().getFloat("operationGridVoltageVRange", "min");
			Float maxOperationGridVoltageV = PolicyKeeping.cache().getFloat("operationGridVoltageVRange", "max");
			Float gridVoltageSeparationV = PolicyKeeping.cache().getFloat("gridVoltageSeparationV");
			Float gridVoltageDropAllowanceV = PolicyKeeping.cache().getFloat("gridVoltageDropAllowanceV");
			if (minOperationGridVoltageV != null && maxOperationGridVoltageV != null && gridVoltageSeparationV != null
					&& gridVoltageDropAllowanceV != null) {
				float lower = Math.max(minOperationGridVoltageV + gridVoltageDropAllowanceV,
						minOperationGridVoltageV + gridVoltageSeparationV * 3);
				float upper = Math.min(maxOperationGridVoltageV - gridVoltageDropAllowanceV,
						maxOperationGridVoltageV - gridVoltageSeparationV);
				gridVoltage = Math.max(Math.min(gridVoltage, upper), lower);
				if (log.isInfoEnabled())
					log.info("optimization lower : " + lower + " , upper : " + upper + " , result : " + gridVoltage);
				String voltageReferenceUnitId = DealExecution.voltageReferenceUnitId();
				if (voltageReferenceUnitId != null) {
					JsonObject operation = new JsonObject().put("command", "voltage").put("params",
							new JsonObject().put("gridVoltageV", gridVoltage));
					DeliveryOptions options = new DeliveryOptions().addHeader("gridMasterUnitId", ApisConfig.unitId());
					vertx.eventBus().<JsonObject>request(
							ServiceAddress.Controller.deviceControlling(voltageReferenceUnitId), operation, options,
							rep -> {
								if (rep.succeeded()) {
									DealExecution.unitDataCache.mergeIn(rep.result().body(), voltageReferenceUnitId,
											"dcdc");
									onComplete.handle(Future.succeededFuture());
								} else {
									if (ReplyFailureUtil.isRecipientFailure(rep)) {
										onComplete.handle(Future.failedFuture(rep.cause()));
									} else {
										ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL,
												Error.Level.ERROR, "Communication failed on EventBus", rep.cause(),
												onComplete);
									}
								}
							});
				} else {
					ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.ERROR,
							"no voltage reference unit found", onComplete);
				}
			} else {
				ErrorUtil.reportAndFail(vertx, Error.Category.USER, Error.Extent.LOCAL, Error.Level.ERROR,
						"data deficiency; POLICY.operationGridVoltageVRange.min : " + minOperationGridVoltageV
								+ ", POLICY.operationGridVoltageVRange.max : " + maxOperationGridVoltageV
								+ ", POLICY.gridVoltageSeparationV : " + gridVoltageSeparationV
								+ ", POLICY.gridVoltageDropAllowanceV : " + gridVoltageDropAllowanceV,
						onComplete);
			}
		} else {
			onComplete.handle(Future.succeededFuture());
		}
	}

}
