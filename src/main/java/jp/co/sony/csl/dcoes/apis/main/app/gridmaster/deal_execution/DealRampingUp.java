package jp.co.sony.csl.dcoes.apis.main.app.gridmaster.deal_execution;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import jp.co.sony.csl.dcoes.apis.common.Deal;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.util.DateTimeUtil;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectUtil;
import jp.co.sony.csl.dcoes.apis.main.app.PolicyKeeping;
import jp.co.sony.csl.dcoes.apis.main.app.controller.util.DDCon;
import jp.co.sony.csl.dcoes.apis.main.app.mediator.util.DealUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorExceptionUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

public class DealRampingUp extends AbstractDealExecution {
	private static final Logger log = LoggerFactory.getLogger(DealRampingUp.class);

	public DealRampingUp(Vertx vertx, JsonObject policy, JsonObject deal, List<JsonObject> otherDeals) {
		super(vertx, policy, deal, otherDeals);
	}

	public DealRampingUp(AbstractDealExecution other) {
		super(other);
	}

	@Override
	protected void doExecute(Handler<AsyncResult<Void>> onComplete) {
		if (DDCon.Mode.VOLTAGE_REFERENCE == masterSideUnitDDConMode_()) {
			if (DDCon.Mode.WAIT == slaveSideUnitDDConMode_()) {
				Float gridVoltageV = JsonObjectUtil.getFloat(masterSideUnitData_(), "dcdc", "meter", "vg");
				Float targetGridVoltageV = JsonObjectUtil.getFloat(masterSideUnitData_(), "dcdc", "vdis", "dvg");
				if (log.isInfoEnabled())
					log.info("vg : " + gridVoltageV + " ; target : " + targetGridVoltageV);
				if (gridVoltageV != null && targetGridVoltageV != null) {
					Float gridVoltageAllowanceV = PolicyKeeping.cache().getFloat("gridVoltageAllowanceV");
					if (gridVoltageAllowanceV != null) {
						float gridVoltageMinV = targetGridVoltageV - gridVoltageAllowanceV;
						if (gridVoltageMinV <= gridVoltageV) {
							rampUpDeal_(resRampUpDeal -> {
								if (resRampUpDeal.succeeded()) {
									new DealMasterAuthorization(this).execute(onComplete);
								} else {
									onComplete.handle(resRampUpDeal);
								}
							});
						} else {
							LocalDateTime currentDateTime = DateTimeUtil.toLocalDateTime(referenceDateTimeString_());
							LocalDateTime activateDateTime = JsonObjectUtil.getLocalDateTime(deal_, "activateDateTime");
							Duration duration = Duration.between(activateDateTime, currentDateTime);
							long durationMsec = duration.toMillis();
							Long timeoutMsec = PolicyKeeping.cache().getLong("controller", "dcdc", "voltageReference",
									"rampUp", "first", "timeoutMsec");
							if (timeoutMsec != null) {
								if (durationMsec < timeoutMsec) {
									if (log.isInfoEnabled())
										log.info("ramping up ...");
									onComplete.handle(Future.succeededFuture());
								} else {
									ErrorUtil.reportAndFail(vertx_, Error.Category.HARDWARE, Error.Extent.GLOBAL,
											Error.Level.ERROR,
											"ramping up timed out; activateDateTime : " + Deal.activateDateTime(deal_)
													+ ", currentDateTime : " + referenceDateTimeString_(),
											onComplete);
								}
							} else {
								ErrorUtil.reportAndFail(vertx_, Error.Category.USER, Error.Extent.LOCAL,
										Error.Level.ERROR,
										"data deficiency; POLICY.controller.dcdc.voltageReference.rampUp.first.timeoutMsec : "
												+ timeoutMsec,
										onComplete);
							}
						}
					} else {
						ErrorUtil.reportAndFail(vertx_, Error.Category.USER, Error.Extent.LOCAL, Error.Level.ERROR,
								"data deficiency; POLICY.gridVoltageAllowanceV : " + gridVoltageAllowanceV, onComplete);
					}
				} else {
					ErrorUtil.reportAndFail(vertx_, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.WARN,
							"no dcdc.meter.vg and/or dcdc.vdis.dvg value in unit data : " + masterSideUnitData_(),
							onComplete);
				}
			} else {
				ErrorUtil.reportAndFail(vertx_, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.WARN,
						"invalid slave side unit status; unit : " + slaveSideUnitId_() + ", mode : "
								+ slaveSideUnitDDConMode_(),
						onComplete);
			}
		} else {
			ErrorUtil.reportAndFail(vertx_, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.WARN,
					"invalid master side unit status; unit : " + masterSideUnitId_() + ", mode : "
							+ masterSideUnitDDConMode_(),
					onComplete);
		}
	}

	private void rampUpDeal_(Handler<AsyncResult<Void>> onComplete) {
		DealUtil.rampUp(vertx_, deal_, referenceDateTimeString_(),
				resRampUp -> ErrorExceptionUtil.reportIfNeedAndHandle(vertx_, resRampUp, onComplete));
	}

}
