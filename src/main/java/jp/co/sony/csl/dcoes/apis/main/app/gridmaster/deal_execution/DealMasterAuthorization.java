package jp.co.sony.csl.dcoes.apis.main.app.gridmaster.deal_execution;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.main.app.controller.util.DDCon;
import jp.co.sony.csl.dcoes.apis.main.app.mediator.util.DealUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorExceptionUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

public class DealMasterAuthorization extends AbstractDealExecution {
	private static final Logger log = LoggerFactory.getLogger(DealMasterAuthorization.class);

	public DealMasterAuthorization(Vertx vertx, JsonObject policy, JsonObject deal, List<JsonObject> otherDeals) {
		super(vertx, policy, deal, otherDeals);
	}

	public DealMasterAuthorization(AbstractDealExecution other) {
		super(other);
	}

	@Override
	protected void doExecute(Handler<AsyncResult<Void>> onComplete) {
		if (DDCon.Mode.VOLTAGE_REFERENCE == masterSideUnitDDConMode_()) {
			if (DDCon.Mode.WAIT == slaveSideUnitDDConMode_()) {
				doAuthorize_(resAuthorize -> {
					if (resAuthorize.succeeded()) {
						if (log.isInfoEnabled())
							log.info("deal master authorized");
						new DealCompensation(this).execute(onComplete);
					} else {
						// If voltage reference privilege acquisition fails
						// 電圧リファレンス権限獲得が失敗したら
						// Stop the device
						// デバイスを止めて
						deactivateDcdc_(resDeactivateDcdc -> {
							if (resDeactivateDcdc.succeeded()) {
								String resetReason = masterSideUnitId_() + " : " + resAuthorize.cause();
								resetDeal_(resetReason, onComplete);
							} else {
								onComplete.handle(resDeactivateDcdc);
							}
						});
					}
				});
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

	private void doAuthorize_(Handler<AsyncResult<Void>> onComplete) {
		if (testFeature_failIfNeed_("dcdc", "failBeforeAuthorize", onComplete))
			return;
		controlMasterSideUnitDcdc_("voltageReferenceAuthorization", null,
				res -> testFeature_failIfNeed_(res, "dcdc", "failAfterAuthorize", onComplete));
	}

	private void deactivateDcdc_(Handler<AsyncResult<Void>> onComplete) {
		if (testFeature_failIfNeed_("dcdc", "failBeforeDeactivate", onComplete))
			return;
		controlMasterSideUnitDcdc_(DDCon.Mode.WAIT.name(), null,
				res -> testFeature_failIfNeed_(res, "dcdc", "failAfterDeactivate", onComplete));
	}

	private void resetDeal_(String reason, Handler<AsyncResult<Void>> onComplete) {
		DealUtil.reset(vertx_, deal_, referenceDateTimeString_(), reason,
				resReset -> ErrorExceptionUtil.reportIfNeedAndHandle(vertx_, resReset, onComplete));
	}

}
