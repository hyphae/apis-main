package jp.co.sony.csl.dcoes.apis.main.app.gridmaster.deal_execution;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import jp.co.sony.csl.dcoes.apis.common.Deal;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectUtil;
import jp.co.sony.csl.dcoes.apis.main.app.controller.util.DDCon;
import jp.co.sony.csl.dcoes.apis.main.app.mediator.util.DealUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorExceptionUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

public class DealCumulation extends AbstractStoppableDealExecution {
	private static final Logger log = LoggerFactory.getLogger(DealCumulation.class);

	public DealCumulation(Vertx vertx, JsonObject policy, JsonObject deal, List<JsonObject> otherDeals) {
		super(vertx, policy, deal, otherDeals);
	}

	public DealCumulation(AbstractDealExecution other) {
		super(other);
	}

	@Override
	protected void doExecute(Handler<AsyncResult<Void>> onComplete) {
		if (DDCon.Mode.WAIT != masterSideUnitDDConMode_()) {
			if (DDCon.Mode.WAIT != slaveSideUnitDDConMode_()) {
				cumulateDeal_(resCumulateDeal -> {
					if (resCumulateDeal.succeeded()) {
						Float cumulateAmountWh = deal_.getFloat("cumulateAmountWh");
						Integer dealAmountWh = deal_.getInteger("dealAmountWh");
						if (dealAmountWh < cumulateAmountWh) {
							stopDcdc_(resStopDcdc -> {
								if (resStopDcdc.succeeded()) {
									stopDeal_(resStopDeal -> {
										if (resStopDeal.succeeded()) {
											new DealDeactivation(this).execute(onComplete);
										} else {
											onComplete.handle(resStopDeal);
										}
									});
								} else {
									onComplete.handle(resStopDcdc);
								}
							});
						} else {
							Float dischargeUnitLowerLimitRsoc = JsonObjectUtil.getFloat(policy_, "gridMaster", "deal",
									"forceStopCondition", "dischargeUnitLowerLimitRsoc");
							Float chargeUnitUpperLimitRsoc = JsonObjectUtil.getFloat(policy_, "gridMaster", "deal",
									"forceStopCondition", "chargeUnitUpperLimitRsoc");
							if (dischargeUnitLowerLimitRsoc != null && chargeUnitUpperLimitRsoc != null) {
								Float dischargeUnitRsoc = JsonObjectUtil.getFloat(dischargeUnitData_, "battery",
										"rsoc");
								Float chargeUnitRsoc = JsonObjectUtil.getFloat(chargeUnitData_, "battery", "rsoc");
								if (dischargeUnitRsoc != null && chargeUnitRsoc != null) {
									String abortReason = null;
									if (dischargeUnitRsoc < dischargeUnitLowerLimitRsoc) {
										abortReason = dischargeUnitId_ + " : dischargeUnitLowerLimitRsoc";
									} else if (chargeUnitUpperLimitRsoc < chargeUnitRsoc) {
										abortReason = chargeUnitId_ + " : chargeUnitUpperLimitRsoc";
									}
									if (abortReason != null) {
										onComplete.handle(Future.failedFuture(abortReason));
									} else {
										if (log.isInfoEnabled())
											log.info("deal goes on ...");
										onComplete.handle(Future.succeededFuture());
									}
								} else {
									ErrorUtil.reportAndFail(vertx_, Error.Category.LOGIC, Error.Extent.GLOBAL,
											Error.Level.WARN,
											"no battery.rsoc value in discharge and/or charge unit data : "
													+ dischargeUnitData_ + ", " + chargeUnitData_,
											onComplete);
								}
							} else {
								ErrorUtil.reportAndFail(vertx_, Error.Category.USER, Error.Extent.LOCAL,
										Error.Level.ERROR,
										"data deficiency; dischargeUnitLowerLimitRsoc : " + dischargeUnitLowerLimitRsoc
												+ ", chargeUnitUpperLimitRsoc : " + chargeUnitUpperLimitRsoc
												+ " in POLICY.gridMaster.deal.forceStopCondition values",
										onComplete);
							}
						}
					} else {
						onComplete.handle(resCumulateDeal);
					}
				});
			} else {
				ErrorUtil.reportAndFail(vertx_, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.WARN,
						"invalid slave side unit status ; unit : " + slaveSideUnitId_() + ", mode : "
								+ slaveSideUnitDDConMode_(),
						onComplete);
			}
		} else {
			ErrorUtil.reportAndFail(vertx_, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.WARN,
					"invalid master side unit status ; unit : " + masterSideUnitId_() + ", mode : "
							+ masterSideUnitDDConMode_(),
					onComplete);
		}
	}

	private void cumulateDeal_(Handler<AsyncResult<Void>> onComplete) {
		Float unitWb = referenceUnitWb_();
		if (unitWb != null) {
			if (log.isInfoEnabled())
				log.info("reference unit wb : " + unitWb);
			if (existOtherActiveDealsOnReferenceUnit_()) {
				float sumOfOtherDealIgs = sumOfOtherDealCompensatedGridCurrentAs_(referenceUnitId_());
				Float dealIg = Deal.compensatedGridCurrentA(deal_, referenceUnitId_());
				if (dealIg != null && sumOfOtherDealIgs + dealIg != 0F) {
					float dealWb = unitWb * dealIg / (sumOfOtherDealIgs + dealIg);
					if (log.isInfoEnabled())
						log.info("sum of other deal compensated ig : " + sumOfOtherDealIgs + ", deal compensated ig : "
								+ dealIg + " ; deal wb : " + dealWb);
					DealUtil.cumulate(vertx_, deal_, referenceDateTimeString_(), dealWb,
							resCumulate -> ErrorExceptionUtil.reportIfNeedAndHandle(vertx_, resCumulate, onComplete));
				} else {
					ErrorUtil.reportAndFail(vertx_, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.ERROR,
							"no reference side compensatedGridCurrentA in deal or sum of deal compensated ig is zero ; unit id : "
									+ referenceUnitId_() + ", deal : " + deal_,
							onComplete);
				}
			} else {
				DealUtil.cumulate(vertx_, deal_, referenceDateTimeString_(), unitWb,
						resCumulate -> ErrorExceptionUtil.reportIfNeedAndHandle(vertx_, resCumulate, onComplete));
			}
		} else {
			ErrorUtil.reportAndFail(vertx_, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.WARN,
					"no dcdc.meter.wb value in reference unit data ; unit data : " + referenceUnitData_() + ", deal : "
							+ deal_,
					onComplete);
		}
	}

	private boolean existOtherActiveDealsOnReferenceUnit_() {
		for (JsonObject aDeal : otherDeals_(referenceUnitId_())) {
			if (Deal.isMasterSideUnit(aDeal, referenceUnitId_(), masterSide_)) {
				if (Deal.masterSideUnitMustBeActive(aDeal)) {
					return true;
				}
			} else {
				if (Deal.slaveSideUnitMustBeActive(aDeal)) {
					return true;
				}
			}
		}
		return false;
	}

}
