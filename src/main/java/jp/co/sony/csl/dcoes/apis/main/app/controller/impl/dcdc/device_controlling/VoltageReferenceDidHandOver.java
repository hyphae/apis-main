package jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.device_controlling;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectUtil;
import jp.co.sony.csl.dcoes.apis.main.app.PolicyKeeping;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.DcdcDeviceControlling;
import jp.co.sony.csl.dcoes.apis.main.app.controller.util.DDCon;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

public class VoltageReferenceDidHandOver extends AbstractDcdcDeviceControllingCommand {

	private String mode_;
	private Float gridCurrentA_;
	private Float operationGridVoltageV_;
	private Float maxOperationGridVoltageV_;

	public VoltageReferenceDidHandOver(Vertx vertx, DcdcDeviceControlling controller, JsonObject params) {
		this(vertx, controller, params, JsonObjectUtil.getString(params, "mode"),
				JsonObjectUtil.getFloat(params, "gridCurrentA"));
	}

	public VoltageReferenceDidHandOver(Vertx vertx, DcdcDeviceControlling controller, JsonObject params, String mode,
			Float gridCurrentA) {
		super(vertx, controller, params);
		mode_ = mode;
		gridCurrentA_ = gridCurrentA;
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
		maxOperationGridVoltageV_ = PolicyKeeping.cache().getFloat("operationGridVoltageVRange", "max");
		if (operationGridVoltageV_ != null && maxOperationGridVoltageV_ != null) {
			DDCon.Mode mode = (mode_ != null) ? DDCon.mode(mode_) : DDCon.Mode.WAIT;
			if (mode != null) {
				switch (mode) {
					case DISCHARGE:
						execute_DISCHARGE_(onComplete);
						break;
					case CHARGE:
						execute_CHARGE_(onComplete);
						break;
					case WAIT:
						execute_WAIT_(onComplete);
						break;
					case VOLTAGE_REFERENCE:
						ErrorUtil.reportAndFail(vertx_, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR,
								"invalid mode : " + mode, onComplete);
						break;
				}
			} else {
				ErrorUtil.reportAndFail(vertx_, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR,
						"invalid mode in params : " + mode_, onComplete);
			}
		} else {
			ErrorUtil
					.reportAndFail(vertx_, Error.Category.USER, Error.Extent.LOCAL, Error.Level.ERROR,
							"data deficiency ; POLICY.operationGridVoltageV : " + operationGridVoltageV_
									+ ", POLICY.operationGridVoltageVRange.max : " + maxOperationGridVoltageV_,
							onComplete);
		}
	}

	private void execute_DISCHARGE_(Handler<AsyncResult<JsonObject>> onComplete) {
		controller_.setDcdcMode(DDCon.Mode.DISCHARGE, maxOperationGridVoltageV_, gridCurrentA_, onComplete);
	}

	private void execute_CHARGE_(Handler<AsyncResult<JsonObject>> onComplete) {
		controller_.setDcdcMode(DDCon.Mode.CHARGE, operationGridVoltageV_, gridCurrentA_, onComplete);
	}

	private void execute_WAIT_(Handler<AsyncResult<JsonObject>> onComplete) {
		new GridCurrentStepping(vertx_, controller_, params_, 0F).execute(res -> {
			controller_.setDcdcMode(DDCon.Mode.WAIT, operationGridVoltageV_, 0F, onComplete);
		});
	}

}
