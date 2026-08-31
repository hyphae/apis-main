package jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.device_controlling;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.main.app.HwConfigKeeping;
import jp.co.sony.csl.dcoes.apis.main.app.controller.DataAcquisition;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.DcdcDeviceControlling;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

public class VoltageReferenceWillHandOver extends AbstractDcdcDeviceControllingCommand {

	private Float operationGridVoltageV_;
	private Float droopRatio_;

	public VoltageReferenceWillHandOver(Vertx vertx, DcdcDeviceControlling controller, JsonObject params) {
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
		operationGridVoltageV_ = DataAcquisition.cache.getFloat("dcdc", "vdis", "dvg");
		if (operationGridVoltageV_ != null) {
			droopRatio_ = HwConfigKeeping.droopRatio();
			if (operationGridVoltageV_ != null && droopRatio_ != null) {
				execute__(onComplete);
			} else {
				ErrorUtil.reportAndFail(vertx_, Error.Category.USER, Error.Extent.LOCAL, Error.Level.ERROR,
						"data deficiency; POLICY.operationGridVoltageV : " + operationGridVoltageV_
								+ ", HWCONFIG.droopRatio : " + droopRatio_,
						onComplete);
			}
		} else {
			ErrorUtil.reportAndFail(vertx_, Error.Category.HARDWARE, Error.Extent.LOCAL, Error.Level.ERROR,
					"no dcdc.vdis.dvg value in unit data : " + DataAcquisition.cache.jsonObject(), onComplete);
		}
	}

	private void execute__(Handler<AsyncResult<JsonObject>> onComplete) {
		controller_.setDcdcVoltage(operationGridVoltageV_, droopRatio_, onComplete);
	}

}
