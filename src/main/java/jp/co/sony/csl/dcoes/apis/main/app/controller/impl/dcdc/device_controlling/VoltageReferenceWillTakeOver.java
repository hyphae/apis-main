package jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.device_controlling;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.main.app.HwConfigKeeping;
import jp.co.sony.csl.dcoes.apis.main.app.controller.DataAcquisition;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.DcdcDeviceControlling;
import jp.co.sony.csl.dcoes.apis.main.app.controller.util.DDCon;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

public class VoltageReferenceWillTakeOver extends AbstractDcdcDeviceControllingCommand {
	private static final Logger log = LoggerFactory.getLogger(VoltageReferenceWillTakeOver.class);

	private Float gridVoltageV_;
	private Float gridCurrentCapacityA_;
	private Float droopRatio_;

	public VoltageReferenceWillTakeOver(Vertx vertx, DcdcDeviceControlling controller, JsonObject params) {
		this(vertx, controller, params, params.getFloat("gridVoltageV"));
	}

	public VoltageReferenceWillTakeOver(Vertx vertx, DcdcDeviceControlling controller, JsonObject params,
			Float gridVoltageV) {
		super(vertx, controller, params);
		gridVoltageV_ = gridVoltageV;
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
		if (gridVoltageV_ == null) {
			gridVoltageV_ = DataAcquisition.cache.getFloat("dcdc", "meter", "vg");
			if (log.isInfoEnabled())
				log.info("no gridVoltageV parameter given, use dcdc.meter.vg from this unit : " + gridVoltageV_);
		}
		if (gridVoltageV_ != null) {
			gridCurrentCapacityA_ = HwConfigKeeping.gridCurrentCapacityA();
			droopRatio_ = HwConfigKeeping.droopRatio();
			if (gridCurrentCapacityA_ != null && droopRatio_ != null) {
				execute__(onComplete);
			} else {
				ErrorUtil.reportAndFail(vertx_, Error.Category.USER, Error.Extent.LOCAL, Error.Level.ERROR,
						"data deficiency; HWCONFIG.gridCurrentCapacityA : " + gridCurrentCapacityA_
								+ ", HWCONFIG.droopRatio : " + droopRatio_,
						onComplete);
			}
		} else {
			ErrorUtil.reportAndFail(vertx_, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR,
					"illegal parameters, gridVoltageV : " + gridVoltageV_, onComplete);
		}
	}

	private void execute__(Handler<AsyncResult<JsonObject>> onComplete) {
		controller_.setDcdcMode(DDCon.Mode.VOLTAGE_REFERENCE, gridVoltageV_, gridCurrentCapacityA_, droopRatio_,
				onComplete);
	}

}
