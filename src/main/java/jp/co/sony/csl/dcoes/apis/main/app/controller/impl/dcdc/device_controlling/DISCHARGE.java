package jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.device_controlling;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.main.app.PolicyKeeping;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.DcdcDeviceControlling;
import jp.co.sony.csl.dcoes.apis.main.app.controller.util.DDCon;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

public class DISCHARGE extends AbstractDcdcDeviceControllingCommand {

	private Float gridCurrentA_;
	private Float maxOperationGridVoltageV_;
	private Float gridCurrentStepA_;

	public DISCHARGE(Vertx vertx, DcdcDeviceControlling controller, JsonObject params) {
		this(vertx, controller, params, params.getFloat("gridCurrentA"));
	}

	public DISCHARGE(Vertx vertx, DcdcDeviceControlling controller, JsonObject params, Float gridCurrentA) {
		super(vertx, controller, params);
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
		if (gridCurrentA_ != null) {
			maxOperationGridVoltageV_ = PolicyKeeping.cache().getFloat("operationGridVoltageVRange", "max");
			gridCurrentStepA_ = PolicyKeeping.cache().getFloat("gridCurrentStepA");
			if (maxOperationGridVoltageV_ != null && gridCurrentStepA_ != null) {
				execute__(onComplete);
			} else {
				ErrorUtil.reportAndFail(vertx_, Error.Category.USER, Error.Extent.LOCAL, Error.Level.ERROR,
						"data deficiency; POLICY.operationGridVoltageVRange.max : " + maxOperationGridVoltageV_
								+ ", POLICY.gridCurrentStepA : " + gridCurrentStepA_,
						onComplete);
			}
		} else {
			ErrorUtil.reportAndFail(vertx_, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR,
					"illegal parameters, gridCurrentA : " + gridCurrentA_, onComplete);
		}
	}

	private void execute__(Handler<AsyncResult<JsonObject>> onComplete) {
		if (gridCurrentA_ < gridCurrentStepA_) {
			controller_.setDcdcMode(DDCon.Mode.DISCHARGE, maxOperationGridVoltageV_, gridCurrentA_, res -> {
				if (res.succeeded()) {
					new Checkpoint(vertx_, controller_, params_, null, gridCurrentA_).execute(onComplete);
				} else {
					onComplete.handle(res);
				}
			});
		} else {
			controller_.setDcdcMode(DDCon.Mode.DISCHARGE, maxOperationGridVoltageV_, gridCurrentStepA_, res -> {
				if (res.succeeded()) {
					new GridCurrentStepping(vertx_, controller_, params_, gridCurrentA_).execute(onComplete);
				} else {
					onComplete.handle(res);
				}
			});
		}
	}

}
