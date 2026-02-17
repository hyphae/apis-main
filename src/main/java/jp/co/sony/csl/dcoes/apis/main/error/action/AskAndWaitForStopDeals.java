package jp.co.sony.csl.dcoes.apis.main.error.action;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import jp.co.sony.csl.dcoes.apis.common.Deal;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.ServiceAddress;
import jp.co.sony.csl.dcoes.apis.main.app.PolicyKeeping;
import jp.co.sony.csl.dcoes.apis.main.app.mediator.util.DealUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ApisConfig;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

public class AskAndWaitForStopDeals extends AbstractErrorAction {
	private static final Logger log = LoggerFactory.getLogger(AskAndWaitForStopDeals.class);

	public static final Long DEFAULT_STOP_ME_TIMEOUT_MSEC = 60000L;
	public static final Long DEFAULT_STOP_ME_CHECK_PERIOD_MSEC = 1000L;

	public AskAndWaitForStopDeals(Vertx vertx, JsonObject policy, JsonArray logMessages) {
		super(vertx, policy, logMessages);
	}

	@Override
	protected void doAction(Handler<AsyncResult<Void>> completionHandler) {
		new AskAndWaitForStopDeals_().execute_(completionHandler);
	}

	private class AskAndWaitForStopDeals_ {
		private Handler<AsyncResult<Void>> completionHandler_;
		private long timedOutTimerId_ = 0L;
		private long waitForStopTimerId_ = 0L;
		private boolean timedOut_ = false;

		private void execute_(Handler<AsyncResult<Void>> completionHandler) {
			completionHandler_ = completionHandler;
			timedOutTimerId_ = vertx_.setTimer(
					PolicyKeeping.cache().getLong(DEFAULT_STOP_ME_TIMEOUT_MSEC, "controller", "stopMeTimeoutMsec"),
					timerId -> {
						timedOut_ = true;
					});
			waitForStopTimerHandler_(0L);
		}

		private void setWaitForStopTimer_() {
			Long delay = PolicyKeeping.cache().getLong(DEFAULT_STOP_ME_CHECK_PERIOD_MSEC, "controller",
					"stopMeCheckPeriodMsec");
			setWaitForStopTimer_(delay);
		}

		private void setWaitForStopTimer_(long delay) {
			if (log.isInfoEnabled())
				log.info("waiting for deal stops ...");
			waitForStopTimerId_ = vertx_.setTimer(delay, this::waitForStopTimerHandler_);
		}

		private void waitForStopTimerHandler_(Long timerId) {
			if (null == timerId || timerId.longValue() != waitForStopTimerId_) {
				ErrorUtil.report(vertx_, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN,
						"illegal timerId : " + timerId + ", waitForStopTimerId_ : " + waitForStopTimerId_);
				return;
			}
			DealUtil.withUnitId(vertx_, ApisConfig.unitId(), resWithUnitId -> {
				if (resWithUnitId.succeeded()) {
					List<JsonObject> deals = resWithUnitId.result();
					if (deals.isEmpty()) {
						if (log.isInfoEnabled())
							log.info("done");
						vertx_.cancelTimer(timedOutTimerId_);
						completionHandler_.handle(Future.succeededFuture());
					} else if (timedOut_) {
						if (log.isWarnEnabled())
							log.warn("... timed out");
						completionHandler_.handle(Future.failedFuture("timed out"));
					} else {
						if (log.isInfoEnabled())
							log.info("deal exists ...");
						doAskForStop_(deals);
						setWaitForStopTimer_();
					}
				} else {
					if (log.isWarnEnabled())
						log.warn("... failed");
					vertx_.cancelTimer(timedOutTimerId_);
					completionHandler_.handle(Future.failedFuture(resWithUnitId.cause()));
				}
			});
		}

		private void doAskForStop_(List<JsonObject> deals) {
			if (log.isInfoEnabled())
				log.info(deals.size() + " deal(s) found");
			for (JsonObject aDeal : deals) {
				if (Deal.isDeactivated(aDeal)) {
					if (log.isInfoEnabled())
						log.info("no need to ask for stop deal : " + Deal.dealId(aDeal));
				} else {
					if (log.isInfoEnabled())
						log.info("asking for stop deal : " + Deal.dealId(aDeal));
					JsonObject message = new JsonObject().put("dealId", Deal.dealId(aDeal)).put("reasons",
							logMessages_);
					vertx_.eventBus().send(ServiceAddress.Mediator.dealNeedToStop(), message);
				}
			}
		}

	}

}
