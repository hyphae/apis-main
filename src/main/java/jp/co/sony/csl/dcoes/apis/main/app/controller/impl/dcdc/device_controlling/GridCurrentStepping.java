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

public class GridCurrentStepping extends AbstractDcdcDeviceControllingCommand {

	private Float gridCurrentA_;
	private Float gridCurrentStepA_;

	public GridCurrentStepping(Vertx vertx, DcdcDeviceControlling controller, JsonObject params) {
		this(vertx, controller, params, params.getFloat("gridCurrentA"));
	}

	public GridCurrentStepping(Vertx vertx, DcdcDeviceControlling controller, JsonObject params, Float gridCurrentA) {
		super(vertx, controller, params);
		gridCurrentA_ = gridCurrentA;
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
		if (gridCurrentA_ != null) {
			gridCurrentStepA_ = PolicyKeeping.cache().getFloat("gridCurrentStepA");
			if (gridCurrentStepA_ != null) {
				execute__(onComplete);
			} else {
				ErrorUtil.reportAndFail(vertx_, Error.Category.USER, Error.Extent.LOCAL, Error.Level.ERROR,
						"data deficiency; POLICY.gridCurrentStepA : " + gridCurrentStepA_, onComplete);
			}
		} else {
			ErrorUtil.reportAndFail(vertx_, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR,
					"illegal parameters, gridCurrentA : " + gridCurrentA_, onComplete);
		}
	}

	private void execute__(Handler<AsyncResult<JsonObject>> onComplete) {
		Float oldValue = DataAcquisition.cache.getFloat("dcdc", "param", "dig");
		if (oldValue != null) {
			float diff = gridCurrentA_ - oldValue;
			if (gridCurrentStepA_ < Math.abs(diff)) {
				float newValue = (oldValue < gridCurrentA_) ? oldValue + gridCurrentStepA_
						: oldValue - gridCurrentStepA_;
				controller_.setDcdcCurrent(newValue, resSet -> {
					if (resSet.succeeded()) {
						execute__(onComplete);
					} else {
						onComplete.handle(resSet);
					}
				});
			} else {
				controller_.setDcdcCurrent(gridCurrentA_, resSet -> {
					if (resSet.succeeded()) {
						new Checkpoint(vertx_, controller_, params_, null, gridCurrentA_).execute(onComplete);
					} else {
						onComplete.handle(resSet);
					}
				});
			}
		} else {
			ErrorUtil.reportAndFail(vertx_, Error.Category.HARDWARE, Error.Extent.LOCAL, Error.Level.ERROR,
					"no dcdc.param.dig value in unit data : " + DataAcquisition.cache.jsonObject(), onComplete);
		}
	}

}
