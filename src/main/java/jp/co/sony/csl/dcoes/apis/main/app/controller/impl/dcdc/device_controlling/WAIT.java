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

public class WAIT extends AbstractDcdcDeviceControllingCommand {

	private Float operationGridVoltageV_;
	private Float minOperationGridVoltageV_;
	private Float gridVoltageSeparationV_;

	public WAIT(Vertx vertx, DcdcDeviceControlling controller, JsonObject params) {
		super(vertx, controller, params);
	}

	@Override
	protected boolean startIgnoreDynamicSafetyCheck() {
		return false;
	}

	@Override
	protected boolean stopIgnoreDynamicSafetyCheck() {
		return true;
	}

	@Override
	protected void doExecute(Handler<AsyncResult<JsonObject>> onComplete) {
		operationGridVoltageV_ = PolicyKeeping.cache().getFloat("operationGridVoltageV");
		minOperationGridVoltageV_ = PolicyKeeping.cache().getFloat("operationGridVoltageVRange", "min");
		gridVoltageSeparationV_ = PolicyKeeping.cache().getFloat("gridVoltageSeparationV");
		if (operationGridVoltageV_ != null && minOperationGridVoltageV_ != null && gridVoltageSeparationV_ != null) {
		} else {
			ErrorUtil.report(vertx_, Error.Category.USER, Error.Extent.LOCAL, Error.Level.ERROR,
					"data deficiency; POLICY.operationGridVoltageV : " + operationGridVoltageV_
							+ ", POLICY.operationGridVoltageVRange.min : " + minOperationGridVoltageV_
							+ ", POLICY.gridVoltageSeparationV : " + gridVoltageSeparationV_);
			if (operationGridVoltageV_ == null)
				operationGridVoltageV_ = 0F;
			if (minOperationGridVoltageV_ == null)
				minOperationGridVoltageV_ = 0F;
			if (gridVoltageSeparationV_ == null)
				gridVoltageSeparationV_ = 0F;
		}
		execute__(onComplete);
	}

	private void execute__(Handler<AsyncResult<JsonObject>> onComplete) {
		DDCon.Mode currentMode = DDCon.modeFromCode(DataAcquisition.cache.getString("dcdc", "status", "status"));
		if (currentMode == null) {
			ErrorUtil.report(vertx_, Error.Category.HARDWARE, Error.Extent.LOCAL, Error.Level.WARN,
					"no dcdc.status.status value in unit data : " + DataAcquisition.cache.jsonObject());
			currentMode = DDCon.Mode.WAIT;
		}
		switch (currentMode) {
			case WAIT:
				controller_.setDcdcMode(DDCon.Mode.WAIT, operationGridVoltageV_, 0F, onComplete);
				break;
			case VOLTAGE_REFERENCE:
				float rampDownVoltageV = minOperationGridVoltageV_ + gridVoltageSeparationV_;
				new GridVoltageStepping(vertx_, controller_, params_, rampDownVoltageV).execute(res -> {
					controller_.setDcdcMode(DDCon.Mode.WAIT, operationGridVoltageV_, 0F, onComplete);
				});
				break;
			case CHARGE:
			case DISCHARGE:
				new GridCurrentStepping(vertx_, controller_, params_, 0F).execute(res -> {
					controller_.setDcdcMode(DDCon.Mode.WAIT, operationGridVoltageV_, 0F, onComplete);
				});
				break;
		}
	}

}
