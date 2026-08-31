package jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.device_controlling;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.main.app.controller.DataAcquisition;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.DcdcDeviceControlling;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

public class VoltageReferenceDidTakeOver extends AbstractDcdcDeviceControllingCommand {

	private Float operationGridVoltageV_;

	public VoltageReferenceDidTakeOver(Vertx vertx, DcdcDeviceControlling controller, JsonObject params) {
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
		operationGridVoltageV_ = DataAcquisition.cache.getFloat("dcdc", "vdis", "dvg");
		if (operationGridVoltageV_ != null) {
			execute__(onComplete);
		} else {
			ErrorUtil.reportAndFail(vertx_, Error.Category.HARDWARE, Error.Extent.LOCAL, Error.Level.ERROR,
					"no dcdc.vdis.dvg value in unit data : " + DataAcquisition.cache.jsonObject(), onComplete);
		}
	}

	private void execute__(Handler<AsyncResult<JsonObject>> onComplete) {
		controller_.setDcdcVoltage(operationGridVoltageV_, onComplete);
	}

}
