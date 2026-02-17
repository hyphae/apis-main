package jp.co.sony.csl.dcoes.apis.main.app.gridmaster.deal_execution;

import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import jp.co.sony.csl.dcoes.apis.common.Deal;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.ServiceAddress;
import jp.co.sony.csl.dcoes.apis.common.util.NumberUtil;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectUtil;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.ReplyFailureUtil;
import jp.co.sony.csl.dcoes.apis.main.app.controller.util.DDCon;
import jp.co.sony.csl.dcoes.apis.main.app.gridmaster.main_loop.DealExecution;
import jp.co.sony.csl.dcoes.apis.main.app.mediator.util.DealUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ApisConfig;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorExceptionUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;
import jp.co.sony.csl.dcoes.apis.main.util.Policy;

public abstract class AbstractDealExecution {
	private static final Logger log = LoggerFactory.getLogger(AbstractDealExecution.class);

	private boolean initialized_ = false;

	protected Vertx vertx_;
	protected JsonObject policy_;
	protected JsonObject deal_;
	protected List<JsonObject> otherDeals_;

	protected String dealId_;
	protected String dischargeUnitId_;
	protected String chargeUnitId_;
	protected JsonObject dischargeUnitData_;
	protected JsonObject chargeUnitData_;

	protected String masterSide_;
	protected String referenceSide_;

	public AbstractDealExecution(Vertx vertx, JsonObject policy, JsonObject deal, List<JsonObject> otherDeals) {
		vertx_ = vertx;
		policy_ = policy;
		deal_ = deal;
		otherDeals_ = otherDeals;
	}

	public AbstractDealExecution(AbstractDealExecution other) {
		initialized_ = true;

		vertx_ = other.vertx_;
		policy_ = other.policy_;
		deal_ = other.deal_;
		otherDeals_ = other.otherDeals_;

		dealId_ = other.dealId_;
		dischargeUnitId_ = other.dischargeUnitId_;
		chargeUnitId_ = other.chargeUnitId_;
		dischargeUnitData_ = other.dischargeUnitData_;
		chargeUnitData_ = other.chargeUnitData_;

		masterSide_ = other.masterSide_;
		referenceSide_ = other.referenceSide_;
	}

	protected String masterSideUnitId_() {
		return ("dischargeUnit".equals(masterSide_)) ? dischargeUnitId_ : chargeUnitId_;
	}

	protected String slaveSideUnitId_() {
		return ("dischargeUnit".equals(masterSide_)) ? chargeUnitId_ : dischargeUnitId_;
	}

	protected JsonObject masterSideUnitData_() {
		return ("dischargeUnit".equals(masterSide_)) ? dischargeUnitData_ : chargeUnitData_;
	}

	protected JsonObject slaveSideUnitData_() {
		return ("dischargeUnit".equals(masterSide_)) ? chargeUnitData_ : dischargeUnitData_;
	}

	protected DDCon.Mode masterSideUnitDDConMode_() {
		return DDCon.modeFromCode(JsonObjectUtil.getString(masterSideUnitData_(), "dcdc", "status", "status"));
	}

	protected DDCon.Mode slaveSideUnitDDConMode_() {
		return DDCon.modeFromCode(JsonObjectUtil.getString(slaveSideUnitData_(), "dcdc", "status", "status"));
	}

	protected DDCon.Mode dischargeUnitDDConMode_() {
		return DDCon.modeFromCode(JsonObjectUtil.getString(dischargeUnitData_, "dcdc", "status", "status"));
	}

	protected DDCon.Mode chargeUnitDDConMode_() {
		return DDCon.modeFromCode(JsonObjectUtil.getString(chargeUnitData_, "dcdc", "status", "status"));
	}

	protected String referenceUnitId_() {
		return ("dischargeUnit".equals(referenceSide_)) ? dischargeUnitId_ : chargeUnitId_;
	}

	protected JsonObject referenceUnitData_() {
		return ("dischargeUnit".equals(referenceSide_)) ? dischargeUnitData_ : chargeUnitData_;
	}

	protected String referenceDateTimeString_() {
		return ("dischargeUnit".equals(referenceSide_)) ? JsonObjectUtil.getString(dischargeUnitData_, "time")
				: JsonObjectUtil.getString(chargeUnitData_, "time");
	}

	protected Float referenceUnitWb_() {
		return ("dischargeUnit".equals(referenceSide_))
				? NumberUtil.negativeValue(JsonObjectUtil.getFloat(dischargeUnitData_, "dcdc", "meter", "wb"))
				: JsonObjectUtil.getFloat(chargeUnitData_, "dcdc", "meter", "wb");
	}
	// protected Float referenceUnitIg_() {
	// return ("dischargeUnit".equals(referenceSide_)) ?
	// NumberUtil.negativeValue(JsonObjectUtil.getFloat(dischargeUnitData_, "dcdc",
	// "meter", "ig")) : JsonObjectUtil.getFloat(chargeUnitData_, "dcdc", "meter",
	// "ig");
	// }

	protected JsonObject masterDeal_() {
		if (Deal.isMaster(deal_))
			return deal_;
		for (JsonObject aDeal : otherDeals_) {
			if (Deal.isMaster(aDeal))
				return aDeal;
		}
		return null;
	}

	protected List<JsonObject> otherDeals_(String unitId) {
		List<JsonObject> result = new ArrayList<>();
		for (JsonObject aDeal : otherDeals_) {
			if (Deal.isInvolved(aDeal, unitId)) {
				result.add(aDeal);
			}
		}
		return result;
	}

	protected float sumOfOtherDealCompensatedGridCurrentAs_(String unitId) {
		float result = 0F;
		for (JsonObject aDeal : otherDeals_(unitId)) {
			if (Deal.bothSideUnitsMustBeActive(aDeal)) {
				Float value = Deal.compensatedGridCurrentA(aDeal, unitId);
				if (value != null) {
					result += value;
				}
			}
		}
		return result;
	}

	protected boolean canFlipMasterSide_() {
		// if (Deal.isTransitionalState(deal_)) {
		// return false;
		// }
		for (JsonObject aDeal : otherDeals_) {
			if (Deal.isTransitionalState(aDeal)) {
				return false;
			}
		}
		return true;
	}

	protected void flipMasterSide_() {
		masterSide_ = ("dischargeUnit".equals(masterSide_)) ? "chargeUnit" : "dischargeUnit";
	}

	protected boolean tryToFlipMasterSide_() {
		boolean result = canFlipMasterSide_();
		if (result) {
			flipMasterSide_();
		}
		return result;
	}

	////

	protected void controlMasterSideUnitDcdc_(String command, JsonObject params,
			Handler<AsyncResult<Void>> onComplete) {
		controlDcdc_(masterSideUnitId_(), command, params, onComplete);
	}

	protected void controlSlaveSideUnitDcdc_(String command, JsonObject params, Handler<AsyncResult<Void>> onComplete) {
		controlDcdc_(slaveSideUnitId_(), command, params, onComplete);
	}

	protected void controlDcdc_(String unitId, String command, JsonObject params,
			Handler<AsyncResult<Void>> onComplete) {
		JsonObject operation = new JsonObject().put("command", command);
		if (params != null) {
			operation.put("params", params);
		}
		DeliveryOptions options = new DeliveryOptions().addHeader("gridMasterUnitId", ApisConfig.unitId());
		vertx_.eventBus().<JsonObject>request(ServiceAddress.Controller.deviceControlling(unitId), operation, options,
				rep -> {
					if (rep.succeeded()) {
						mergeUnitDcdc_(unitId, rep.result().body());
						onComplete.handle(Future.succeededFuture());
					} else {
						if (ReplyFailureUtil.isRecipientFailure(rep)) {
							onComplete.handle(Future.failedFuture(rep.cause()));
						} else {
							ErrorUtil.reportAndFail(vertx_, Error.Category.FRAMEWORK, Error.Extent.LOCAL,
									Error.Level.ERROR, "Communication failed on EventBus", rep.cause(), onComplete);
						}
					}
				});
	}

	protected void updateUnitDcdcStatus_(String unitId, Handler<AsyncResult<Void>> onComplete) {
		DeliveryOptions options = new DeliveryOptions();
		options.addHeader("gridMasterUnitId", ApisConfig.unitId());
		options.addHeader("urgent", "true");
		vertx_.eventBus().<JsonObject>request(ServiceAddress.Controller.unitDeviceStatus(unitId), null, options,
				rep -> {
					if (rep.succeeded()) {
						mergeUnitDcdc_(unitId, rep.result().body());
						onComplete.handle(Future.succeededFuture());
					} else {
						if (ReplyFailureUtil.isRecipientFailure(rep)) {
							onComplete.handle(Future.failedFuture(rep.cause()));
						} else {
							ErrorUtil.reportAndFail(vertx_, Error.Category.FRAMEWORK, Error.Extent.LOCAL,
									Error.Level.ERROR, "Communication failed on EventBus", rep.cause(), onComplete);
						}
					}
				});
	}

	private void mergeUnitDcdc_(String unitId, JsonObject dcdc) {
		DealExecution.unitDataCache.mergeIn(dcdc, unitId, "dcdc");
		if (unitId.equals(dischargeUnitId_)) {
			if (dischargeUnitData_ != null) {
				JsonObjectUtil.mergeIn(dischargeUnitData_, dcdc, "dcdc");
			} else {
				dischargeUnitData_ = new JsonObject().put("dcdc", dcdc);
			}
		} else if (unitId.equals(chargeUnitId_)) {
			if (chargeUnitData_ != null) {
				JsonObjectUtil.mergeIn(chargeUnitData_, dcdc, "dcdc");
			} else {
				chargeUnitData_ = new JsonObject().put("dcdc", dcdc);
			}
		}
	}

	////

	protected abstract void doExecute(Handler<AsyncResult<Void>> onComplete);

	public void execute(Handler<AsyncResult<Void>> onComplete) {
		if (!initialized_) {
			dealId_ = Deal.dealId(deal_);
			if (log.isInfoEnabled())
				log.info("dealId : " + dealId_);
			DealUtil.get(vertx_, dealId_, res -> {
				if (res.succeeded()) {
					deal_.clear().mergeIn(res.result());
					dischargeUnitId_ = Deal.dischargeUnitId(deal_);
					chargeUnitId_ = Deal.chargeUnitId(deal_);
					if (log.isInfoEnabled())
						log.info("dischargeUnitId : " + dischargeUnitId_ + ", chargeUnitId : " + chargeUnitId_);
					Promise<JsonObject> dischargeUnitDataPromise = Promise.promise();
					Promise<JsonObject> chargeUnitDataPromise = Promise.promise();
					unitData_(dischargeUnitId_, dischargeUnitDataPromise);
					unitData_(chargeUnitId_, chargeUnitDataPromise);
					Future.all(dischargeUnitDataPromise.future(), chargeUnitDataPromise.future()).onComplete(ar -> {
						if (ar.succeeded()) {
							dischargeUnitData_ = ar.result().resultAt(0);
							chargeUnitData_ = ar.result().resultAt(1);
							DealExecution.unitDataCache.mergeIn(dischargeUnitData_, dischargeUnitId_);
							DealExecution.unitDataCache.mergeIn(chargeUnitData_, chargeUnitId_);
							masterSide_ = masterSide_();
							if (log.isInfoEnabled())
								log.info("master side : " + masterSide_);
							referenceSide_ = Policy.dealReferenceSide(policy_);
							if (log.isInfoEnabled())
								log.info("reference side : " + referenceSide_);
							doExecute(onComplete);
						} else {
							onComplete.handle(Future.failedFuture(ar.cause()));
						}
					});
				} else {
					ErrorExceptionUtil.reportIfNeedAndFail(vertx_, res.cause(), onComplete);
				}
			});
		} else {
			doExecute(onComplete);
		}
	}

	private String masterSide_() {
		List<JsonObject> deals = new ArrayList<>(otherDeals_);
		deals.add(deal_);
		return DealExecution.masterSide(vertx_, policy_, deals);
	}

	private void unitData_(String unitId, Handler<AsyncResult<JsonObject>> onComplete) {
		DeliveryOptions options = new DeliveryOptions();
		options.addHeader("gridMasterUnitId", ApisConfig.unitId());
		options.addHeader("urgent", "true");
		vertx_.eventBus().<JsonObject>request(ServiceAddress.Controller.unitData(unitId), null, options, rep -> {
			if (rep.succeeded()) {
				onComplete.handle(Future.succeededFuture(rep.result().body()));
			} else {
				if (ReplyFailureUtil.isRecipientFailure(rep)) {
					onComplete.handle(Future.failedFuture(rep.cause()));
				} else {
					ErrorUtil.reportAndFail(vertx_, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR,
							"Communication failed on EventBus", rep.cause(), onComplete);
				}
			}
		});
	}

	////

	protected boolean testFeature_failIfNeed_(String category, String name, Handler<AsyncResult<Void>> onComplete) {
		if (JsonObjectUtil.getBoolean(deal_, Boolean.FALSE, "testFeature", category, name)) {
			ErrorExceptionUtil.logAndFail(Error.Category.USER, Error.Extent.GLOBAL, Error.Level.WARN,
					"TEST FEATURE : " + category + " / " + name, onComplete);
			return true;
		}
		return false;
	}

	protected void testFeature_failIfNeed_(AsyncResult<Void> res, String category, String name,
			Handler<AsyncResult<Void>> onComplete) {
		if (res.succeeded() && testFeature_failIfNeed_(category, name, onComplete))
			return;
		onComplete.handle(res);
	}

}
