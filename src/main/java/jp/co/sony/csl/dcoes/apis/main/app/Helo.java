package jp.co.sony.csl.dcoes.apis.main.app;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.ServiceAddress;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.ReplyFailureUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ApisConfig;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

public class Helo extends AbstractVerticle {
	private static final Logger LOGGER = LoggerFactory.getLogger(Helo.class);

	private static final Long DEFAULT_HELO_PERIOD_MSEC = 5000L;

	private long heloTimerId_ = 0L;
	private boolean stopped_ = false;

	@Override
	public void start(Promise<Void> startPromise) throws Exception {
		checkUniqueness_(resCheckUniqueness -> {
			if (resCheckUniqueness.succeeded()) {
				startHeloService_(resHelo -> {
					if (resHelo.succeeded()) {
						heloTimerHandler_(0L);
						if (LOGGER.isTraceEnabled())
							LOGGER.trace("started : " + deploymentID());
						startPromise.complete();
					} else {
						startPromise.fail(resHelo.cause());
					}
				});
			} else {
				startPromise.fail(resCheckUniqueness.cause());
			}
		});
	}

	@Override
	public void stop() throws Exception {
		stopped_ = true;
		if (LOGGER.isTraceEnabled())
			LOGGER.trace("stopped : " + deploymentID());
	}

	private void checkUniqueness_(Handler<AsyncResult<Void>> onComplete) {
		vertx.eventBus().request(ServiceAddress.helo(ApisConfig.unitId()), null, repHelo -> {
			if (repHelo.succeeded()) {
				onComplete.handle(Future.failedFuture("unit with id " + ApisConfig.unitId() + " already exists !!!"));
			} else {
				if (ReplyFailureUtil.isNoHandlers(repHelo)) {
					onComplete.handle(Future.succeededFuture());
				} else {
					onComplete.handle(Future.failedFuture(repHelo.cause()));
				}
			}
		});
	}

	private void startHeloService_(Handler<AsyncResult<Void>> onComplete) {
		vertx.eventBus().<String>consumer(ServiceAddress.helo(ApisConfig.unitId()), req -> {
			String senderDeploymentID = req.body();
			if (null == senderDeploymentID) {
				req.reply(ApisConfig.unitId());
			} else {
				if (!senderDeploymentID.equals(deploymentID())) {
					ErrorUtil.report(vertx, Error.Category.USER, Error.Extent.LOCAL, Error.Level.ERROR,
							"another unit with id " + ApisConfig.unitId() + " found !!!");
				}
			}
		}).completionHandler(onComplete);
	}

	private void setHeloTimer_() {
		Long delay = PolicyKeeping.cache().getLong(DEFAULT_HELO_PERIOD_MSEC, "heloPeriodMsec");
		setHeloTimer_(delay);
	}

	private void setHeloTimer_(long delay) {
		heloTimerId_ = vertx.setTimer(delay, this::heloTimerHandler_);
	}

	private void heloTimerHandler_(Long timerId) {
		if (stopped_)
			return;
		if (null == timerId || timerId != heloTimerId_) {
			ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN,
					"illegal timerId : " + timerId + ", heloTimerId_ : " + heloTimerId_);
			return;
		}
		vertx.eventBus().publish(ServiceAddress.helo(ApisConfig.unitId()), deploymentID());
		setHeloTimer_();
	}

}
