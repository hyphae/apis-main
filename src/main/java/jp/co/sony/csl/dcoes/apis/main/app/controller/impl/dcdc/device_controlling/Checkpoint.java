package jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.device_controlling;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.ServiceAddress;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectUtil;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.ReplyFailureUtil;
import jp.co.sony.csl.dcoes.apis.main.app.HwConfigKeeping;
import jp.co.sony.csl.dcoes.apis.main.app.PolicyKeeping;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.DcdcDeviceControlling;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

public class Checkpoint extends AbstractDcdcDeviceControllingCommand {
	private static final Logger log = LoggerFactory.getLogger(Checkpoint.class);

	private Float gridVoltageV_;
	private Float gridCurrentA_;
	private float gridVoltageAllowanceV_;
	private float gridCurrentAllowanceA_;
	private int retryLimit_;
	private long retryWaitMsec_;
	private boolean toFail_ = false;

	public Checkpoint(Vertx vertx, DcdcDeviceControlling controller, JsonObject params) {
		this(vertx, controller, params, params.getFloat("gridVoltageV"), params.getFloat("gridCurrentA"));
	}

	public Checkpoint(Vertx vertx, DcdcDeviceControlling controller, JsonObject params, Float gridVoltageV,
			Float gridCurrentA) {
		super(vertx, controller, params);
		gridVoltageV_ = gridVoltageV;
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
		Float gridVoltageAllowanceV = PolicyKeeping.cache().getFloat("gridVoltageAllowanceV");
		Float gridCurrentAllowanceA = HwConfigKeeping.gridCurrentAllowanceA();
		Integer retryLimit = PolicyKeeping.cache().getInteger("controller", "dcdc", "checkpoint", "retryLimit");
		Long retryWaitMsec = PolicyKeeping.cache().getLong("controller", "dcdc", "checkpoint", "retryWaitMsec");
		if (gridVoltageAllowanceV != null && gridCurrentAllowanceA != null && retryLimit != null
				&& retryWaitMsec != null) {
			gridVoltageAllowanceV_ = gridVoltageAllowanceV;
			gridCurrentAllowanceA_ = gridCurrentAllowanceA;
			retryLimit_ = retryLimit;
			if (retryLimit_ < 0) {
				retryLimit_ = -retryLimit_;
				toFail_ = true;
			}
			retryWaitMsec_ = retryWaitMsec;
			if (log.isDebugEnabled())
				log.debug("grid voltage allowance (V) : " + gridVoltageAllowanceV_ + ", grid current allowance (A) : "
						+ gridCurrentAllowanceA_ + ", retry limit : " + retryLimit_ + ", retry wait (msec) : "
						+ retryWaitMsec_ + ", to fail : " + toFail_);
			execute__(onComplete);
		} else {
			ErrorUtil.reportAndFail(vertx_, Error.Category.USER, Error.Extent.LOCAL, Error.Level.ERROR,
					"data deficiency; POLICY.gridVoltageAllowanceV : " + gridVoltageAllowanceV
							+ ", HWCONFIG.gridCurrentAllowanceA : " + gridCurrentAllowanceA
							+ ", POLICY.controller.dcdc.checkpoint.retryLimit : " + retryLimit
							+ ", POLICY.controller.dcdc.checkpoint.retryWaitMsec : " + retryWaitMsec,
					onComplete);
		}
	}

	private void execute__(Handler<AsyncResult<JsonObject>> onComplete) {
		if (log.isDebugEnabled())
			log.debug("retryLimit_ : " + retryLimit_);
		vertx_.setTimer(retryWaitMsec_, timerId -> {
			vertx_.eventBus().<JsonObject>request(ServiceAddress.Controller.urgentUnitDeviceStatus(), null, rep -> {
				if (rep.succeeded()) {
					boolean vgResult = true;
					boolean igResult = true;
					JsonObject dcdcResponse = rep.result().body();
					if (gridVoltageV_ != null) {
						Float vg = JsonObjectUtil.getFloat(dcdcResponse, "meter", "vg");
						if (vg != null) {
							float target = gridVoltageV_;
							if (log.isDebugEnabled())
								log.debug("vg : " + vg + ", target : " + target + ", allowance : "
										+ gridVoltageAllowanceV_);
							float left = target - gridVoltageAllowanceV_;
							float right = target + gridVoltageAllowanceV_;
							vgResult = (left <= vg && vg <= right);
							if (log.isDebugEnabled())
								log.debug(((vgResult) ? "OK" : "NG") + " ( " + left + " <= " + vg + " <= " + right
										+ " )");
						} else {
							vgResult = false;
							ErrorUtil.report(vertx_, Error.Category.HARDWARE, Error.Extent.LOCAL, Error.Level.ERROR,
									"no meter.vg value in dcdc status : " + dcdcResponse);
						}
					}
					if (gridCurrentA_ != null) {
						Float ig = JsonObjectUtil.getFloat(dcdcResponse, "meter", "ig");
						if (ig != null) {
							float target = (ig < 0F) ? -gridCurrentA_ : gridCurrentA_;
							if (log.isDebugEnabled())
								log.debug("ig : " + ig + ", target : " + target + ", allowance : "
										+ gridCurrentAllowanceA_);
							float left = target - gridCurrentAllowanceA_;
							float right = target + gridCurrentAllowanceA_;
							igResult = (left <= ig && ig <= right);
							if (log.isDebugEnabled())
								log.debug(((igResult) ? "OK" : "NG") + " ( " + left + " <= " + ig + " <= " + right
										+ " )");
						} else {
							igResult = false;
							ErrorUtil.report(vertx_, Error.Category.HARDWARE, Error.Extent.LOCAL, Error.Level.ERROR,
									"no meter.ig value in dcdc status : " + dcdcResponse);
						}
					}
					if (vgResult && igResult && !toFail_) {
						succeeded(onComplete);
					} else {
						if (0 < --retryLimit_) {
							execute__(onComplete);
						} else {
							ErrorUtil.reportAndFail(vertx_, Error.Category.HARDWARE, Error.Extent.LOCAL,
									Error.Level.WARN, "checkpoint failed", onComplete);
						}
					}
				} else {
					if (ReplyFailureUtil.isRecipientFailure(rep)) {
						onComplete.handle(Future.failedFuture(rep.cause()));
					} else {
						ErrorUtil.reportAndFail(vertx_, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR,
								"Communication failed on EventBus", rep.cause(), onComplete);
					}
				}
			});
		});
	}

}
