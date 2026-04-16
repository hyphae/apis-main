package jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.device_controlling;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.main.app.PolicyKeeping;
import jp.co.sony.csl.dcoes.apis.main.app.controller.DataAcquisition;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.DcdcDeviceControlling;
import jp.co.sony.csl.dcoes.apis.main.app.controller.util.DDCon;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

/**
 * Put the device in stop mode.
 * @author OES Project
 *          
 * デバイスを停止モードにする.
 * @author OES Project
 */
public class WAIT extends AbstractDcdcDeviceControllingCommand {
//	private static final Logger log = LoggerFactory.getLogger(WAIT.class);

	private Float operationGridVoltageV_;
	private Float minOperationGridVoltageV_;
	private Float gridVoltageSeparationV_;

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
	public WAIT(Vertx vertx, DcdcDeviceControlling controller, JsonObject params) {
		super(vertx, controller, params);
	}

	// Do not start skipping dynamic safety checks in this process
	// この処理により動的安全性チェックのスキップを開始しない
	@Override protected boolean startIgnoreDynamicSafetyCheck() { return false; }

	// Stop skipping dynamic safety checks in this process
	// この処理により動的安全性チェックのスキップを終了する
	@Override protected boolean stopIgnoreDynamicSafetyCheck() { return true; }

	@Override protected void doExecute(Handler<AsyncResult<JsonObject>> completionHandler) {
		operationGridVoltageV_ = PolicyKeeping.cache().getFloat("operationGridVoltageV");
		minOperationGridVoltageV_ = PolicyKeeping.cache().getFloat("operationGridVoltageVRange", "min");
		gridVoltageSeparationV_ = PolicyKeeping.cache().getFloat("gridVoltageSeparationV");
		if (operationGridVoltageV_ != null && minOperationGridVoltageV_ != null && gridVoltageSeparationV_ != null) {
			// nop
		} else {
			ErrorUtil.report(vertx_, Error.Category.USER, Error.Extent.LOCAL, Error.Level.ERROR, "data deficiency; POLICY.operationGridVoltageV : " + operationGridVoltageV_ + ", POLICY.operationGridVoltageVRange.min : " + minOperationGridVoltageV_ + ", POLICY.gridVoltageSeparationV : " + gridVoltageSeparationV_);
			if (operationGridVoltageV_ == null) operationGridVoltageV_ = 0F;
			if (minOperationGridVoltageV_ == null) minOperationGridVoltageV_ = 0F;
			if (gridVoltageSeparationV_ == null) gridVoltageSeparationV_ = 0F;
		}
		execute__(completionHandler);
	}
	private void execute__(Handler<AsyncResult<JsonObject>> completionHandler) {
		// Get the current mode
		// いまのモードを取得する
		DDCon.Mode currentMode = DDCon.modeFromCode(DataAcquisition.cache.getString("dcdc", "status", "status"));
		if (currentMode == null) {
			// If the current mode is unknown, it is regarded as WAIT
			// いまのモードが不明の場合は WAIT とみなす
			ErrorUtil.report(vertx_, Error.Category.HARDWARE, Error.Extent.LOCAL, Error.Level.WARN, "no dcdc.status.status value in unit data : " + DataAcquisition.cache.jsonObject());
			currentMode = DDCon.Mode.WAIT;
		}
		switch (currentMode) {
		case WAIT:
			// If currently in WAIT mode, just wait
			// いまが WAIT ならただ WAIT にする
			controller_.setDcdcMode(DDCon.Mode.WAIT, operationGridVoltageV_, 0F, completionHandler);
			break;
		case VOLTAGE_REFERENCE:
			// If this is currently a voltage reference,
			// いまが電圧リファレンスなら
			// First drop the grid voltage to POLICY.operationGridVoltageVRange.min + POLICY.gridVoltageSeparationV, then
			// まずグリッド電圧を POLICY.operationGridVoltageVRange.min + POLICY.gridVoltageSeparationV まで落としてから
			float rampDownVoltageV = minOperationGridVoltageV_ + gridVoltageSeparationV_;
			new GridVoltageStepping(vertx_, controller_, params_, rampDownVoltageV).execute(res -> {
				// enter WAIT mode regardless of success or failure
				// 成否にかかわらず WAIT にする
				controller_.setDcdcMode(DDCon.Mode.WAIT, operationGridVoltageV_, 0F, completionHandler);
			});
			break;
		case CHARGE:
		case DISCHARGE:
			// If discharging or charging, drop the grid current value to zero, then
			// 送電か受電ならグリッド電流値を 0 まで落としてから
			new GridCurrentStepping(vertx_, controller_, params_, 0F).execute(res -> {
				// enter WAIT mode regardless of success or failure
				// 成否にかかわらず WAIT にする
				controller_.setDcdcMode(DDCon.Mode.WAIT, operationGridVoltageV_, 0F, completionHandler);
			});
			break;
		}
	}

}
