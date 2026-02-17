package jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.device_controlling;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.main.app.PolicyKeeping;
import jp.co.sony.csl.dcoes.apis.main.app.controller.DataAcquisition;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.DcdcDeviceControlling;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

public class VoltageReferenceAuthorization extends AbstractDcdcDeviceControllingCommand {
	private static final Logger log = LoggerFactory.getLogger(VoltageReferenceAuthorization.class);

	private Float operationGridVoltageV_;
	private int numberOfTrials_;
	private List<Float> gridVoltageVList_;

	public VoltageReferenceAuthorization(Vertx vertx, DcdcDeviceControlling controller, JsonObject params) {
		super(vertx, controller, params);
	}

	@Override
	protected boolean startIgnoreDynamicSafetyCheck() {
		return true;
	}

	@Override
	protected boolean stopIgnoreDynamicSafetyCheck() {
		return true;
	}

	@Override
	protected void doExecute(Handler<AsyncResult<JsonObject>> onComplete) {
		operationGridVoltageV_ = DataAcquisition.cache.getFloat("dcdc", "vdis", "dvg");
		if (operationGridVoltageV_ != null) {
			Float defaultOperationGridVoltageV = PolicyKeeping.cache().getFloat("operationGridVoltageV");
			Float minOperationGridVoltageV = PolicyKeeping.cache().getFloat("operationGridVoltageVRange", "min");
			Float maxOperationGridVoltageV = PolicyKeeping.cache().getFloat("operationGridVoltageVRange", "max");
			Float gridVoltageSeparationV = PolicyKeeping.cache().getFloat("gridVoltageSeparationV");
			Integer numberOfTrials = PolicyKeeping.cache().getInteger("controller", "dcdc", "voltageReference",
					"authorization", "numberOfTrials");
			if (defaultOperationGridVoltageV != null && minOperationGridVoltageV != null
					&& maxOperationGridVoltageV != null && gridVoltageSeparationV != null && numberOfTrials != null) {
				numberOfTrials_ = numberOfTrials;
				float min = minOperationGridVoltageV + (gridVoltageSeparationV * 3F);
				float max = maxOperationGridVoltageV - gridVoltageSeparationV;
				gridVoltageVList_ = new ArrayList<>();
				for (float target = min; target <= max; target += gridVoltageSeparationV) {
					if (target == operationGridVoltageV_)
						continue;
					gridVoltageVList_.add(target);
				}
				if (log.isDebugEnabled())
					log.debug("gridVoltageVList_ : " + gridVoltageVList_);
				execute__(onComplete);
			} else {
				ErrorUtil.reportAndFail(vertx_, Error.Category.USER, Error.Extent.LOCAL, Error.Level.ERROR,
						"data deficiency; POLICY.operationGridVoltageV : " + defaultOperationGridVoltageV
								+ ", POLICY.operationGridVoltageVRange.min : " + minOperationGridVoltageV
								+ ", POLICY.operationGridVoltageVRange.max : " + maxOperationGridVoltageV
								+ ", POLICY.gridVoltageSeparationV : " + gridVoltageSeparationV
								+ ", POLICY.controller.dcdc.voltageReference.authorization.numberOfTrials : "
								+ numberOfTrials,
						onComplete);
			}
		} else {
			ErrorUtil.reportAndFail(vertx_, Error.Category.HARDWARE, Error.Extent.LOCAL, Error.Level.ERROR,
					"no dcdc.vdis.dvg value in unit data : " + DataAcquisition.cache.jsonObject(), onComplete);
		}
	}

	private void execute__(Handler<AsyncResult<JsonObject>> onComplete) {
		if (log.isDebugEnabled())
			log.debug("numberOfTrials_ : " + numberOfTrials_);
		int idx = (int) (gridVoltageVList_.size() * Math.random());
		if (idx == gridVoltageVList_.size())
			--idx;
		Float targetGridVoltageV = gridVoltageVList_.get(idx);
		if (log.isDebugEnabled())
			log.debug("target grid voltage : " + targetGridVoltageV);
		new GridVoltageStepping(vertx_, controller_, params_, targetGridVoltageV).execute(res -> {
			if (res.succeeded()) {
				if (--numberOfTrials_ <= 0) {
					new GridVoltageStepping(vertx_, controller_, params_, operationGridVoltageV_).execute(onComplete);
				} else {
					execute__(onComplete);
				}
			} else {
				onComplete.handle(res);
			}
		});
	}

}
