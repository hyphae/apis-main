package jp.co.sony.csl.dcoes.apis.main.app.gridmaster.deal_execution;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import jp.co.sony.csl.dcoes.apis.common.Deal;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectUtil;
import jp.co.sony.csl.dcoes.apis.main.app.controller.util.DDCon;
import jp.co.sony.csl.dcoes.apis.main.app.mediator.util.DealUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorExceptionUtil;

public abstract class AbstractStoppableDealExecution extends AbstractDealExecution {
	private static final Logger log = LoggerFactory.getLogger(AbstractStoppableDealExecution.class);

	public AbstractStoppableDealExecution(Vertx vertx, JsonObject policy, JsonObject deal,
			List<JsonObject> otherDeals) {
		super(vertx, policy, deal, otherDeals);
	}

	public AbstractStoppableDealExecution(AbstractDealExecution other) {
		super(other);
	}

	protected void stopDcdc_(Handler<AsyncResult<Void>> onComplete) {
		if (testFeature_failIfNeed_("dcdc", "failBeforeStop", onComplete))
			return;
		if (canStopDcdc_()) {
			if (log.isInfoEnabled())
				log.info("stop slave side unit");
			controlSlaveSideUnitDcdc_(DDCon.Mode.WAIT.name(), null,
					res -> testFeature_failIfNeed_(res, "dcdc", "failAfterStop", onComplete));
		} else {
			float newDig = Math.abs(sumOfOtherDealCompensatedGridCurrentAs_(slaveSideUnitId_()));
			if (log.isInfoEnabled())
				log.info("dig : " + JsonObjectUtil.getFloat(slaveSideUnitData_(), "dcdc", "param", "dig") + " -> "
						+ newDig);
			JsonObject params = new JsonObject().put("gridCurrentA", newDig);
			controlSlaveSideUnitDcdc_("current", params,
					res -> testFeature_failIfNeed_(res, "dcdc", "failAfterStop", onComplete));
		}
	}

	private boolean canStopDcdc_() {
		for (JsonObject aDeal : otherDeals_(slaveSideUnitId_())) {
			if (Deal.slaveSideUnitMustBeActive(aDeal)) {
				return false;
			}
		}
		return true;
	}

	protected void stopDeal_(Handler<AsyncResult<Void>> onComplete) {
		DealUtil.stop(vertx_, deal_, referenceDateTimeString_(),
				resStop -> ErrorExceptionUtil.reportIfNeedAndHandle(vertx_, resStop, onComplete));
	}

}
