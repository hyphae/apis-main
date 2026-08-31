package jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.device_controlling;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.main.app.HwConfigKeeping;
import jp.co.sony.csl.dcoes.apis.main.app.PolicyKeeping;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.DcdcDeviceControlling;
import jp.co.sony.csl.dcoes.apis.main.app.controller.util.DDCon;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

public class VOLTAGE_REFERENCE extends AbstractDcdcDeviceControllingCommand {

	private Float operationGridVoltageV_;
	private Float gridCurrentCapacityA_;

	public VOLTAGE_REFERENCE(Vertx vertx, DcdcDeviceControlling controller, JsonObject params) {
		super(vertx, controller, params);
	}

	@Override
	protected boolean startIgnoreDynamicSafetyCheck() {
		return true;
	}

	@Override
	protected boolean stopIgnoreDynamicSafetyCheck() {
		return false;
	}

	@Override
	protected void doExecute(Handler<AsyncResult<JsonObject>> onComplete) {
		operationGridVoltageV_ = PolicyKeeping.cache().getFloat("operationGridVoltageV");
		gridCurrentCapacityA_ = HwConfigKeeping.gridCurrentCapacityA();
		if (operationGridVoltageV_ != null && gridCurrentCapacityA_ != null) {
			execute__(onComplete);
		} else {
			ErrorUtil.reportAndFail(vertx_, Error.Category.USER, Error.Extent.LOCAL, Error.Level.ERROR,
					"data deficiency; POLICY.operationGridVoltageV : " + operationGridVoltageV_
							+ ", HWCONFIG.gridCurrentCapacityA : " + gridCurrentCapacityA_,
					onComplete);
		}
	}

	private void execute__(Handler<AsyncResult<JsonObject>> onComplete) {
		controller_.setDcdcMode(DDCon.Mode.VOLTAGE_REFERENCE, operationGridVoltageV_, gridCurrentCapacityA_,
				onComplete);
	}

}
