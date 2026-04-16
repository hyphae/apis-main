package jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.device_controlling;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.main.app.PolicyKeeping;
import jp.co.sony.csl.dcoes.apis.main.app.controller.DataAcquisition;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.DcdcDeviceControlling;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

/**
 * Perform operations to acquire voltage reference privilege.
 * Set the grid voltage to a random value within the specified range and check that it is reflected.
 * Repeat the specified number of times.
 * Fails if the measured grid voltage does not follow the specified value.
 * If successful, return to the original grid voltage and finish.
 * To set the grid voltage, call {@link GridVoltageStepping}.
 * @author OES Project
 *          
 * 電圧リファレンス権限獲得動作を実行する.
 * 決められた範囲でランダムにグリッド電圧を設定し反映を確認する.
 * 規定回数繰り返す.
 * グリッド電圧の測定値が指定値に追従しない場合は失敗とする.
 * 成功したら本来のグリッド電圧に戻して終了する.
 * グリッド電圧の設定は {@link GridVoltageStepping} を呼ぶ.
 * @author OES Project
 */
public class VoltageReferenceAuthorization extends AbstractDcdcDeviceControllingCommand {
	private static final Logger log = LoggerFactory.getLogger(VoltageReferenceAuthorization.class);

	private Float operationGridVoltageV_;
	private int numberOfTrials_;
	private List<Float> gridVoltageVList_;

	/**
	 * Create an instance.
	 * @param vertx a vertx object
	 * @param controller an object that actually sends commands to the device
	 * @param params control parameters. Not required
	 *          
	 * インスタンスを生成する.
	 * @param vertx vertx オブジェクト
	 * @param controller 実際にデバイスに命令を送信するオブジェクト
	 * @param params 制御パラメタ. 不要
	 */
	public VoltageReferenceAuthorization(Vertx vertx, DcdcDeviceControlling controller, JsonObject params) {
		super(vertx, controller, params);
	}

	// Start skipping dynamic safety checks in this process
	// この処理により動的安全性チェックのスキップを開始する
	@Override protected boolean startIgnoreDynamicSafetyCheck() { return true; }

	// Stop skipping dynamic safety checks in this process
	// この処理により動的安全性チェックのスキップを終了する
	@Override protected boolean stopIgnoreDynamicSafetyCheck() { return true; }

	@Override protected void doExecute(Handler<AsyncResult<JsonObject>> completionHandler) {
		operationGridVoltageV_ = DataAcquisition.cache.getFloat("dcdc", "vdis", "dvg");
		if (operationGridVoltageV_ != null) {
			Float defaultOperationGridVoltageV = PolicyKeeping.cache().getFloat("operationGridVoltageV");
			Float minOperationGridVoltageV = PolicyKeeping.cache().getFloat("operationGridVoltageVRange", "min");
			Float maxOperationGridVoltageV = PolicyKeeping.cache().getFloat("operationGridVoltageVRange", "max");
			Float gridVoltageSeparationV = PolicyKeeping.cache().getFloat("gridVoltageSeparationV");
			Integer numberOfTrials = PolicyKeeping.cache().getInteger("controller", "dcdc", "voltageReference", "authorization", "numberOfTrials");
			if (defaultOperationGridVoltageV != null && minOperationGridVoltageV != null && maxOperationGridVoltageV != null && gridVoltageSeparationV != null && numberOfTrials != null) {
				numberOfTrials_ = numberOfTrials;
				float min = minOperationGridVoltageV + (gridVoltageSeparationV * 3F);
				float max = maxOperationGridVoltageV - gridVoltageSeparationV;
				gridVoltageVList_ = new ArrayList<>();
				for (float target = min; target <= max; target += gridVoltageSeparationV) {
					// Number of trials: POLICY.controller.dcdc.voltageReference.authorization.numberOfTrials
					// 実行回数 : POLICY.controller.dcdc.voltageReference.authorization.numberOfTrials
					// Lower limit: POLICY.operationGridVoltageVRange.min + ( 3 * POLICY.gridVoltageSeparationV )
					// 下限値 : POLICY.operationGridVoltageVRange.min + ( 3 * POLICY.gridVoltageSeparationV )
					// Upper limit: POLICY.operationGridVoltageVRange.max - POLICY.gridVoltageSeparationV
					// 上限値 : POLICY.operationGridVoltageVRange.max - POLICY.gridVoltageSeparationV
					// Step: POLICY.gridVoltageSeparationV
					// ステップ : POLICY.gridVoltageSeparationV
					// However, a value that matches dcdc.vdis.dvg is excluded.
					// ただし dcdc.vdis.dvg と一致する値は除く
					if (target == operationGridVoltageV_) continue;
					gridVoltageVList_.add(target);
				}
				if (log.isDebugEnabled()) log.debug("gridVoltageVList_ : " + gridVoltageVList_);
				execute__(completionHandler);
			} else {
				ErrorUtil.reportAndFail(vertx_, Error.Category.USER, Error.Extent.LOCAL, Error.Level.ERROR, "data deficiency; POLICY.operationGridVoltageV : " + defaultOperationGridVoltageV + ", POLICY.operationGridVoltageVRange.min : " + minOperationGridVoltageV + ", POLICY.operationGridVoltageVRange.max : " + maxOperationGridVoltageV + ", POLICY.gridVoltageSeparationV : " + gridVoltageSeparationV + ", POLICY.controller.dcdc.voltageReference.authorization.numberOfTrials : " + numberOfTrials, completionHandler);
			}
		} else {
			ErrorUtil.reportAndFail(vertx_, Error.Category.HARDWARE, Error.Extent.LOCAL, Error.Level.ERROR, "no dcdc.vdis.dvg value in unit data : " + DataAcquisition.cache.jsonObject(), completionHandler);
		}
	}

	private void execute__(Handler<AsyncResult<JsonObject>> completionHandler) {
		if (log.isDebugEnabled()) log.debug("numberOfTrials_ : " + numberOfTrials_);
		int idx = (int) (gridVoltageVList_.size() * Math.random());
		if (idx == gridVoltageVList_.size()) --idx;
		Float targetGridVoltageV = gridVoltageVList_.get(idx);
		if (log.isDebugEnabled()) log.debug("target grid voltage : " + targetGridVoltageV);
		// Perform voltage step processing
		// 電圧ステップ処理を実行する
		new GridVoltageStepping(vertx_, controller_, params_, targetGridVoltageV).execute(res -> {
			if (res.succeeded()) {
				if (--numberOfTrials_ <= 0) {
					// Successfully performed the required number of trials → return to original voltage
					// 実行回数に達したので成功 → 元の電圧に戻す
					new GridVoltageStepping(vertx_, controller_, params_, operationGridVoltageV_).execute(completionHandler);
				} else {
					// Repeat if required number of trials not yet reached
					// 実行回数に達していないので繰り返す
					execute__(completionHandler);
				}
			} else {
				completionHandler.handle(res);
			}
		});
	}

}
