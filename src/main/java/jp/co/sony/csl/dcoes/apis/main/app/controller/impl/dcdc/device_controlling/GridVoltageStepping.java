package jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.device_controlling;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.main.app.PolicyKeeping;
import jp.co.sony.csl.dcoes.apis.main.app.controller.DataAcquisition;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.DcdcDeviceControlling;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

public class GridVoltageStepping extends AbstractDcdcDeviceControllingCommand {

	private Float gridVoltageV_;
	private Float gridVoltageStepV_;

	public GridVoltageStepping(Vertx vertx, DcdcDeviceControlling controller, JsonObject params) {
		this(vertx, controller, params, params.getFloat("gridVoltageV"));
	}

	public GridVoltageStepping(Vertx vertx, DcdcDeviceControlling controller, JsonObject params, Float gridVoltageV) {
		super(vertx, controller, params);
		gridVoltageV_ = gridVoltageV;
	}

	@Override
	protected boolean startIgnoreDynamicSafetyCheck() {
		return false;
	}

	@Override
	protected boolean stopIgnoreDynamicSafetyCheck() {
		return false;
	}

	@Override
	protected void doExecute(Handler<AsyncResult<JsonObject>> onComplete) {
		if (gridVoltageV_ != null) {
			gridVoltageStepV_ = PolicyKeeping.cache().getFloat(350F, "gridVoltageStepV");
			if (gridVoltageStepV_ != null) {
				execute__(onComplete);
			} else {
				ErrorUtil.reportAndFail(vertx_, Error.Category.USER, Error.Extent.LOCAL, Error.Level.ERROR,
						"data deficiency; POLICY.gridVoltageStepV : " + gridVoltageStepV_, onComplete);
			}
		} else {
			ErrorUtil.reportAndFail(vertx_, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR,
					"illegal parameters, gridVoltageV : " + gridVoltageV_, onComplete);
		}
	}

	private void execute__(Handler<AsyncResult<JsonObject>> onComplete) {
		Float oldValue = DataAcquisition.cache.getFloat("dcdc", "vdis", "dvg");
		if (oldValue != null) {
			float diff = gridVoltageV_ - oldValue;
			if (gridVoltageStepV_ < Math.abs(diff)) {
				float newValue = (oldValue < gridVoltageV_) ? oldValue + gridVoltageStepV_
						: oldValue - gridVoltageStepV_;
				controller_.setDcdcVoltage(newValue, resSet -> {
					if (resSet.succeeded()) {
						execute__(onComplete);
					} else {
						onComplete.handle(resSet);
					}
				});
			} else {
				controller_.setDcdcVoltage(gridVoltageV_, resSet -> {
					if (resSet.succeeded()) {
						new Checkpoint(vertx_, controller_, params_, gridVoltageV_, null).execute(onComplete);
					} else {
						onComplete.handle(resSet);
					}
				});
			}
		} else {
			ErrorUtil.reportAndFail(vertx_, Error.Category.HARDWARE, Error.Extent.LOCAL, Error.Level.ERROR,
					"no dcdc.vdis.dvg value in unit data : " + DataAcquisition.cache.jsonObject(), onComplete);
		}
	}

}
