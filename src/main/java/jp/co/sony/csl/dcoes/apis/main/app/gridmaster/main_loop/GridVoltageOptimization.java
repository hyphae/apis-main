package jp.co.sony.csl.dcoes.apis.main.app.gridmaster.main_loop;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.List;

import jp.co.sony.csl.dcoes.apis.common.Deal;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.ServiceAddress;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.ReplyFailureUtil;
import jp.co.sony.csl.dcoes.apis.main.app.PolicyKeeping;
import jp.co.sony.csl.dcoes.apis.main.util.ApisConfig;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

/**
 * Optimize the grid voltage.
 * Called from {@link DealExecution}.
 * Calculate the sum and average of desired grid voltages of units on the discharging side and charging side of all active interchanges.
 * @author OES Project
 *          
 * グリッド電圧を最適化する.
 * {@link DealExecution} から呼ばれる.
 * 実行中の全融通の送電側ユニットと受電側ユニットの希望グリッド電圧値を合計し平均する.
 * @author OES Project
 */
public class GridVoltageOptimization {
	private static final Logger log = LoggerFactory.getLogger(GridVoltageOptimization.class);

	private GridVoltageOptimization() { }

	/**
	 * Process called from {@link DealExecution}.
	 * Calculate the sum and average of desired grid voltages of units on the discharging side and charging side of all active interchanges.
	 * @param vertx a vertx object
	 * @param deals a list of DEAL objects
	 * @param completionHandler the completion handler
	 *          
	 * {@link DealExecution} から呼ばれる処理.
	 * 実行中の全融通の送電側ユニットと受電側ユニットの希望グリッド電圧値を合計し平均する.
	 * @param vertx vertx オブジェクト
	 * @param deals DEAL オブジェクトのリスト
	 * @param completionHandler the completion handler
	 */
	public static void execute(Vertx vertx, List<JsonObject> deals, Handler<AsyncResult<Void>> completionHandler) {
		float gridVoltage = 0F;
		int numberOfUnits = 0;
		// For all active interchanges
		// 全融通のうち動いているものについて
		for (JsonObject aDeal : deals) {
			if (Deal.masterSideUnitMustBeActive(aDeal)) {
				if (log.isInfoEnabled()) log.info("dealId : " + Deal.dealId(aDeal));
				// If the desired grid voltage is recorded for the units on both ends, add them together
				// 両端ユニットそれぞれご希望グリッド電圧が記録されていれば加算していく
				// At the same time, count the number of units
				// 同時にユニット数も数えておく
				// If one unit participates in multiple interchanges, it will be added multiple times.
				// ひとつのユニットが複数の融通に参加している場合には複数回加算される
				Float dischargeUnitEfficientGridVoltageV = Deal.dischargeUnitEfficientGridVoltageV(aDeal);
				Float chargeUnitEfficientGridVoltageV = Deal.chargeUnitEfficientGridVoltageV(aDeal);
				if (dischargeUnitEfficientGridVoltageV != null) {
					gridVoltage += dischargeUnitEfficientGridVoltageV;
					++numberOfUnits;
					if (log.isInfoEnabled()) log.info("  discharge unit efficientGridVoltageV : " + Deal.dischargeUnitEfficientGridVoltageV(aDeal));
				}
				if (chargeUnitEfficientGridVoltageV != null) {
					gridVoltage += chargeUnitEfficientGridVoltageV;
					++numberOfUnits;
					if (log.isInfoEnabled()) log.info("  charge unit efficientGridVoltageV : " + Deal.chargeUnitEfficientGridVoltageV(aDeal));
				}
			}
		}
		if (0 < numberOfUnits) {
			// Calculate the average value of the desired grid voltage
			// ご希望グリッド電圧の平均値を計算する
			gridVoltage /= numberOfUnits;
			if (log.isInfoEnabled()) log.info("average of efficientGridVoltageVs : " + gridVoltage);
			Float minOperationGridVoltageV = PolicyKeeping.cache().getFloat("operationGridVoltageVRange", "min");
			Float maxOperationGridVoltageV = PolicyKeeping.cache().getFloat("operationGridVoltageVRange", "max");
			Float gridVoltageSeparationV = PolicyKeeping.cache().getFloat("gridVoltageSeparationV");
			Float gridVoltageDropAllowanceV = PolicyKeeping.cache().getFloat("gridVoltageDropAllowanceV");
			if (minOperationGridVoltageV != null && maxOperationGridVoltageV != null && gridVoltageSeparationV != null && gridVoltageDropAllowanceV != null) {
				// Apply upper and lower limits
				// 上限と下限の制限をかける
				// Lower limit: Either {POLICY.operationGridVoltageVRange.min + POLICY.gridVoltageDropAllowanceV} or {POLICY.operationGridVoltageVRange.min + POLICY.gridVoltageSeparationV * 3}, whichever is larger
				// 下限 : POLICY.operationGridVoltageVRange.min + POLICY.gridVoltageDropAllowanceV と POLICY.operationGridVoltageVRange.min + POLICY.gridVoltageSeparationV * 3 の大きい方
				// Upper limit: Either {POLICY.operationGridVoltageVRange.max - POLICY.gridVoltageDropAllowanceV} or {POLICY.operationGridVoltageVRange.max - POLICY.gridVoltageSeparationV}, whichever is smaler
				// 上限 : POLICY.operationGridVoltageVRange.max - POLICY.gridVoltageDropAllowanceV と POLICY.operationGridVoltageVRange.max - POLICY.gridVoltageSeparationV の小さい方
				// In other words, the following conditions:
				// つまり
				//// At least {POLICY.operationGridVoltageVRange.min + POLICY.gridVoltageDropAllowanceV}
				//// POLICY.operationGridVoltageVRange.min + POLICY.gridVoltageDropAllowanceV 以上
				//// At least {POLICY.operationGridVoltageVRange.min + POLICY.gridVoltageSeparationV * 3}
				//// POLICY.operationGridVoltageVRange.min + POLICY.gridVoltageSeparationV * 3 以上
				//// No more than {POLICY.operationGridVoltageVRange.max - POLICY.gridVoltageDropAllowanceV}
				//// POLICY.operationGridVoltageVRange.max - POLICY.gridVoltageDropAllowanceV 以下
				//// No more than {POLICY.operationGridVoltageVRange.max - POLICY.gridVoltageSeparationV}
				//// POLICY.operationGridVoltageVRange.max - POLICY.gridVoltageSeparationV 以下
				// should be met
				// に収める
				float lower = Math.max(minOperationGridVoltageV + gridVoltageDropAllowanceV, minOperationGridVoltageV + gridVoltageSeparationV * 3);
				float upper = Math.min(maxOperationGridVoltageV - gridVoltageDropAllowanceV, maxOperationGridVoltageV - gridVoltageSeparationV);
				gridVoltage = Math.max(Math.min(gridVoltage, upper), lower);
				if (log.isInfoEnabled()) log.info("optimization lower : " + lower + " , upper : " + upper + " , result : " + gridVoltage);
				String voltageReferenceUnitId = DealExecution.voltageReferenceUnitId();
				if (voltageReferenceUnitId != null) {
					JsonObject operation = new JsonObject().put("command", "voltage").put("params", new JsonObject().put("gridVoltageV", gridVoltage));
					DeliveryOptions options = new DeliveryOptions().addHeader("gridMasterUnitId", ApisConfig.unitId());
					// Issue an order to change the grid voltage setting for the voltage reference unit
					// 電圧リファレンスユニットに対しグリッド電圧設定値の変更を命令する
					vertx.eventBus().<JsonObject>send(ServiceAddress.Controller.deviceControlling(voltageReferenceUnitId), operation, options, rep -> {
						if (rep.succeeded()) {
							// Reflect the returned device control status in the cache
							// 返ってきたデバイス制御状態をキャッシュ反映しておく
							DealExecution.unitDataCache.mergeIn(rep.result().body(), voltageReferenceUnitId, "dcdc");
							completionHandler.handle(Future.succeededFuture());
						} else {
							if (ReplyFailureUtil.isRecipientFailure(rep)) {
								completionHandler.handle(Future.failedFuture(rep.cause()));
							} else {
								ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR, "Communication failed on EventBus", rep.cause(), completionHandler);
							}
						}
					});
				} else {
					ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.ERROR, "no voltage reference unit found", completionHandler);
				}
			} else {
				ErrorUtil.reportAndFail(vertx, Error.Category.USER, Error.Extent.LOCAL, Error.Level.ERROR, "data deficiency; POLICY.operationGridVoltageVRange.min : " + minOperationGridVoltageV + ", POLICY.operationGridVoltageVRange.max : " + maxOperationGridVoltageV + ", POLICY.gridVoltageSeparationV : " + gridVoltageSeparationV + ", POLICY.gridVoltageDropAllowanceV : " + gridVoltageDropAllowanceV, completionHandler);
			}
		} else {
			completionHandler.handle(Future.succeededFuture());
		}
	}

}
