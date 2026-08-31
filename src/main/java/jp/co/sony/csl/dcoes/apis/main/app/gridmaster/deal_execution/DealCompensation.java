package jp.co.sony.csl.dcoes.apis.main.app.gridmaster.deal_execution;

import io.vertx.core.AsyncResult;
import io.vertx.core.Promise;
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
import jp.co.sony.csl.dcoes.apis.main.app.PolicyKeeping;
import jp.co.sony.csl.dcoes.apis.main.app.controller.util.DDCon;
import jp.co.sony.csl.dcoes.apis.main.app.gridmaster.main_loop.DealExecution;
import jp.co.sony.csl.dcoes.apis.main.app.mediator.util.DealUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorExceptionUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

public class DealCompensation extends AbstractDealExecution {
	private static final Logger log = LoggerFactory.getLogger(DealCompensation.class);

	public DealCompensation(Vertx vertx, JsonObject policy, JsonObject deal, List<JsonObject> otherDeals) {
		super(vertx, policy, deal, otherDeals);
	}

	public DealCompensation(AbstractDealExecution other) {
		super(other);
	}

	@Override
	protected void doExecute(Handler<AsyncResult<Void>> onComplete) {
		if (DDCon.Mode.WAIT != masterSideUnitDDConMode_()) {
			warmUpDcdc_(resWarmUpDcdc -> {
				if (resWarmUpDcdc.succeeded()) {
					warmUpDeal_(resWarmUpDeal -> {
						if (resWarmUpDeal.succeeded()) {
							doCompensate_(resCompensate -> {
								if (resCompensate.succeeded()) {
									if (log.isInfoEnabled())
										log.info("deal compensated");
									saveCompensatedGridCurrentA_(resSave -> {
										if (resSave.succeeded()) {
											DealUtil.start(vertx_, deal_, referenceDateTimeString_(),
													resStart -> ErrorExceptionUtil.reportIfNeedAndHandle(vertx_,
															resStart, onComplete));
										} else {
											onComplete.handle(resSave);
										}
									});
								} else {
									onComplete.handle(resCompensate);
								}
							});
						} else {
							onComplete.handle(resWarmUpDeal);
						}
					});
				} else {
					onComplete.handle(resWarmUpDcdc);
				}
			});
		} else {
			ErrorUtil.reportAndFail(vertx_, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.WARN,
					"invalid master side unit status; unit : " + masterSideUnitId_() + ", mode : "
							+ masterSideUnitDDConMode_(),
					onComplete);
		}
	}

	private void warmUpDcdc_(Handler<AsyncResult<Void>> onComplete) {
		if (!Deal.isWarmedUp(deal_)) {
			if (testFeature_failIfNeed_("dcdc", "failBeforeWarmUp", onComplete))
				return;
			Float dealGridCurrentA = Deal.dealGridCurrentA(deal_);
			if (dealGridCurrentA != null) {
				if (DDCon.Mode.WAIT == slaveSideUnitDDConMode_()) {
					if (log.isInfoEnabled())
						log.info("start slave side unit");
					DDCon.Mode slaveSideUnitNewDDConMode = ("dischargeUnit".equals(masterSide_)) ? DDCon.Mode.CHARGE
							: DDCon.Mode.DISCHARGE;
					JsonObject params = new JsonObject().put("gridCurrentA", dealGridCurrentA);
					controlSlaveSideUnitDcdc_(slaveSideUnitNewDDConMode.name(), params,
							res -> testFeature_failIfNeed_(res, "dcdc", "failAfterWarmUp", onComplete));
				} else {
					float newDig = Math.abs(sumOfOtherDealCompensatedGridCurrentAs_(slaveSideUnitId_()))
							+ dealGridCurrentA;
					if (log.isInfoEnabled())
						log.info("dig : " + JsonObjectUtil.getFloat(slaveSideUnitData_(), "dcdc", "param", "dig")
								+ " -> " + newDig);
					JsonObject params = new JsonObject().put("gridCurrentA", newDig);
					controlSlaveSideUnitDcdc_("current", params,
							res -> testFeature_failIfNeed_(res, "dcdc", "failAfterWarmUp", onComplete));
				}
			} else {
				ErrorUtil.reportAndFail(vertx_, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.WARN,
						"no dealGridCurrentA value in deal : " + deal_, onComplete);
			}
		} else {
			if (log.isInfoEnabled())
				log.info("already warmed up");
			onComplete.handle(Future.succeededFuture());
		}
	}

	private void warmUpDeal_(Handler<AsyncResult<Void>> onComplete) {
		if (!Deal.isWarmedUp(deal_)) {
			DealUtil.warmUp(vertx_, deal_, referenceDateTimeString_(),
					res -> ErrorExceptionUtil.reportIfNeedAndHandle(vertx_, res, onComplete));
		} else {
			onComplete.handle(Future.succeededFuture());
		}
	}

	private void saveCompensatedGridCurrentA_(Handler<AsyncResult<Void>> onComplete) {
		Promise<Void> dischargePromise = Promise.promise();
		Promise<Void> chargePromise = Promise.promise();
		updateUnitDcdcStatus_(dischargeUnitId_, dischargePromise);
		updateUnitDcdcStatus_(chargeUnitId_, chargePromise);
		Future.all(dischargePromise.future(), chargePromise.future()).onComplete(ar -> {
			if (ar.succeeded()) {
				Float dischargeUnitIg = JsonObjectUtil.getFloat(dischargeUnitData_, "dcdc", "meter", "ig");
				Float chargeUnitIg = JsonObjectUtil.getFloat(chargeUnitData_, "dcdc", "meter", "ig");
				if (dischargeUnitIg != null && chargeUnitIg != null) {
					if (log.isInfoEnabled())
						log.info("discharge unit ig : " + dischargeUnitIg + ", charge unit ig : " + chargeUnitIg);
					float dischargeUnitSumOfOtherDealCompensatedGridCurrentA = sumOfOtherDealCompensatedGridCurrentAs_(
							dischargeUnitId_);
					float chargeUnitSumOfOtherDealCompensatedGridCurrentA = sumOfOtherDealCompensatedGridCurrentAs_(
							chargeUnitId_);
					float dischargeUnitCompensatedGridCurrentA = dischargeUnitIg
							- dischargeUnitSumOfOtherDealCompensatedGridCurrentA;
					float chargeUnitCompensatedGridCurrentA = chargeUnitIg
							- chargeUnitSumOfOtherDealCompensatedGridCurrentA;
					deal_.put("dischargeUnitCompensatedGridCurrentA", dischargeUnitCompensatedGridCurrentA);
					deal_.put("chargeUnitCompensatedGridCurrentA", chargeUnitCompensatedGridCurrentA);
					if (log.isInfoEnabled())
						log.info("discharge unit compensated deal ig : " + dischargeUnitCompensatedGridCurrentA
								+ ", charge unit compensated deal ig : " + chargeUnitCompensatedGridCurrentA);
					onComplete.handle(Future.succeededFuture());
				} else {
					ErrorUtil.reportAndFail(vertx_, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.WARN,
							"no dcdc.meter.ig value in discharge and/or charge unit data : " + dischargeUnitData_ + ", "
									+ chargeUnitData_,
							onComplete);
				}
			} else {
				onComplete.handle(Future.failedFuture(ar.cause()));
			}
		});
	}

	private void doCompensate_(Handler<AsyncResult<Void>> onComplete) {
		if (testFeature_failIfNeed_("dcdc", "failBeforeCompensate", onComplete))
			return;
		Integer limitOfTrials = PolicyKeeping.cache().getInteger("gridMaster", "currentCompensation", "limitOfTrials");
		Float driftAllowanceA = PolicyKeeping.cache().getFloat("gridMaster", "currentCompensation", "driftAllowanceA");
		if (limitOfTrials != null && driftAllowanceA != null) {
			Float compensationTargetVoltageReferenceGridCurrentA = Deal
					.compensationTargetVoltageReferenceGridCurrentA(deal_);
			if (compensationTargetVoltageReferenceGridCurrentA != null) {
				if (log.isInfoEnabled())
					log.info("compensationTargetVoltageReferenceGridCurrentA : "
							+ compensationTargetVoltageReferenceGridCurrentA);
				if (DDCon.Mode.VOLTAGE_REFERENCE == masterSideUnitDDConMode_()) {
					if (masterSide_.equals(referenceSide_)) {
						if ("dischargeUnit".equals(referenceSide_)) {
							new CurrentCompensationExecutor_(limitOfTrials, driftAllowanceA,
									compensationTargetVoltageReferenceGridCurrentA, dischargeUnitId_, chargeUnitId_,
									false)
									.execute_(res -> testFeature_failIfNeed_(res, "dcdc", "failAfterCompensate",
											onComplete));
						} else {
							new CurrentCompensationExecutor_(limitOfTrials, driftAllowanceA,
									compensationTargetVoltageReferenceGridCurrentA, chargeUnitId_, dischargeUnitId_,
									true)
									.execute_(res -> testFeature_failIfNeed_(res, "dcdc", "failAfterCompensate",
											onComplete));
						}
					} else {
						if (testFeature_failIfNeed_("dcdc", "failAfterCompensate", onComplete))
							return;
						onComplete.handle(Future.succeededFuture());
					}
				} else {
					JsonObject masterDeal = masterDeal_();
					if (masterDeal != null) {
						if ("dischargeUnit".equals(referenceSide_)) {
							new CurrentCompensationExecutor_(limitOfTrials, driftAllowanceA,
									compensationTargetVoltageReferenceGridCurrentA,
									Deal.masterSideUnitId(masterDeal, masterSide_), chargeUnitId_, false)
									.execute_(res -> testFeature_failIfNeed_(res, "dcdc", "failAfterCompensate",
											onComplete));
						} else {
							new CurrentCompensationExecutor_(limitOfTrials, driftAllowanceA,
									compensationTargetVoltageReferenceGridCurrentA,
									Deal.masterSideUnitId(masterDeal, masterSide_), dischargeUnitId_, true)
									.execute_(res -> testFeature_failIfNeed_(res, "dcdc", "failAfterCompensate",
											onComplete));
						}
					} else {
						ErrorUtil.reportAndFail(vertx_, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.ERROR,
								"no master deal found", onComplete);
					}
				}
			} else {
				ErrorUtil.reportAndFail(vertx_, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.WARN,
						"no compensationTargetVoltageReferenceGridCurrentA value in deal : " + deal_, onComplete);
			}
		} else {
			ErrorUtil.reportAndFail(vertx_, Error.Category.USER, Error.Extent.LOCAL, Error.Level.ERROR,
					"data deficiency; limitOfTrials : " + limitOfTrials + ", driftAllowanceA : " + driftAllowanceA
							+ " in POLICY.gridMaster.currentCompensation values",
					onComplete);
		}
	}

	private class CurrentCompensationExecutor_ {
		private int limitOfTrials_;
		private float driftAllowanceA_;
		private float targetValue_;
		private String voltageReferenceUnitId_;
		private String adjusterUnitId_;
		private boolean minus_;
		private boolean toFail_ = false;

		private CurrentCompensationExecutor_(int limitOfTrials, float driftAllowanceA, float targetValue,
				String voltageReferenceUnitId, String adjusterUnitId, boolean minus) {
			limitOfTrials_ = limitOfTrials;
			driftAllowanceA_ = driftAllowanceA;
			targetValue_ = targetValue;
			voltageReferenceUnitId_ = voltageReferenceUnitId;
			adjusterUnitId_ = adjusterUnitId;
			minus_ = minus;
			if (limitOfTrials_ < 0) {
				limitOfTrials_ = -limitOfTrials_;
				toFail_ = true;
			}
			if (log.isDebugEnabled())
				log.debug("limit of trials : " + limitOfTrials_ + ", voltage reference unit : "
						+ voltageReferenceUnitId_ + ", adjuster unit : " + adjusterUnitId_ + ", target value : "
						+ targetValue_ + ", drift allowance : " + driftAllowanceA_ + ", minus : " + minus_
						+ ", to fail : " + toFail_);
		}

		private void execute_(Handler<AsyncResult<Void>> onComplete) {
			if (log.isDebugEnabled())
				log.debug("limitOfTrials_ : " + limitOfTrials_);
			updateUnitDcdcStatus_(voltageReferenceUnitId_, resVoltageReferenceUnitDcdcStatus -> {
				if (resVoltageReferenceUnitDcdcStatus.succeeded()) {
					Float currentValue = DealExecution.unitDataCache.getFloat(voltageReferenceUnitId_, "dcdc", "meter",
							"ig");
					if (currentValue != null) {
						if (log.isDebugEnabled())
							log.debug("voltage reference unit ig : " + currentValue + ", target : " + targetValue_
									+ ", allowance : " + driftAllowanceA_);
						float diff = Math.abs(currentValue - targetValue_);
						if (log.isDebugEnabled())
							log.debug("Math.abs(ig - target) : " + diff + ", allowance : " + driftAllowanceA_);
						if (diff <= driftAllowanceA_ && !toFail_) {
							onComplete.handle(Future.succeededFuture());
						} else {
							if (log.isDebugEnabled())
								log.debug("NG ( " + diff + " <= " + driftAllowanceA_ + " )");
							if (0 < limitOfTrials_--) {
								Float dig = DealExecution.unitDataCache.getFloat(adjusterUnitId_, "dcdc", "param",
										"dig");
								if (dig != null) {
									if (log.isDebugEnabled())
										log.debug("adjuster unit dig : " + dig);
									if (minus_) {
										dig -= currentValue - targetValue_;
									} else {
										dig += currentValue - targetValue_;
									}
									if (log.isDebugEnabled())
										log.debug("adjuster unit new dig : " + dig);
									JsonObject params = new JsonObject().put("gridCurrentA", dig);
									controlDcdc_(adjusterUnitId_, "current", params, resControlDcdc -> {
										if (resControlDcdc.succeeded()) {
											execute_(onComplete);
										} else {
											onComplete.handle(resControlDcdc);
										}
									});
								} else {
									ErrorUtil.reportAndFail(vertx_, Error.Category.LOGIC, Error.Extent.GLOBAL,
											Error.Level.WARN,
											"no dcdc.param.dig value in adjuster unit data : "
													+ DealExecution.unitDataCache.getJsonObject(adjusterUnitId_),
											onComplete);
								}
							} else {
								ErrorUtil.reportAndFail(vertx_, Error.Category.HARDWARE, Error.Extent.GLOBAL,
										Error.Level.WARN, "current compensation failed", onComplete);
							}
						}
					} else {
						ErrorUtil.reportAndFail(vertx_, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.WARN,
								"no dcdc.meter.ig value in voltage reference unit data : "
										+ DealExecution.unitDataCache.getJsonObject(voltageReferenceUnitId_),
								onComplete);
					}
				} else {
					onComplete.handle(Future.failedFuture(resVoltageReferenceUnitDcdcStatus.cause()));
				}
			});
		}
	}

}
