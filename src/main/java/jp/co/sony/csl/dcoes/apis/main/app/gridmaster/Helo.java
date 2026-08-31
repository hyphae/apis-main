package jp.co.sony.csl.dcoes.apis.main.app.gridmaster;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.ServiceAddress;
import jp.co.sony.csl.dcoes.apis.main.app.PolicyKeeping;
import jp.co.sony.csl.dcoes.apis.main.util.ApisConfig;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

public class Helo extends AbstractVerticle {
	private static final Logger log = LoggerFactory.getLogger(Helo.class);

	private static final Long DEFAULT_HELO_PERIOD_MSEC = 5000L;

	private long heloTimerId_ = 0L;
	private boolean stopped_ = false;

	@Override
	public void start(Promise<Void> startPromise) throws Exception {
		startHeloService_(resHelo -> {
			if (resHelo.succeeded()) {
				heloTimerHandler_(0L);
				startPromise.complete();
			} else {
				startPromise.fail(resHelo.cause());
			}
		});
	}

	@Override
	public void stop() throws Exception {
		stopped_ = true;
	}

	private void startHeloService_(Handler<AsyncResult<Void>> onComplete) {
		vertx.eventBus().<String>consumer(ServiceAddress.GridMaster.helo(), req -> {
			String senderDeploymentID = req.body();
			if (null == senderDeploymentID) {
				req.reply(ApisConfig.unitId());
			} else {
				if (!senderDeploymentID.equals(deploymentID())) {
					ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.ERROR,
							"another GridMaster exists !!!");
				}
			}
		}).completionHandler(onComplete);
	}

	private void setHeloTimer_() {
		Long delay = PolicyKeeping.cache().getLong(DEFAULT_HELO_PERIOD_MSEC, "gridMaster", "heloPeriodMsec");
		setHeloTimer_(delay);
	}

	private void setHeloTimer_(long delay) {
		heloTimerId_ = vertx.setTimer(delay, this::heloTimerHandler_);
	}

	private void heloTimerHandler_(Long timerId) {
		if (stopped_)
			return;
		if (null == timerId || timerId.longValue() != heloTimerId_) {
			ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN,
					"illegal timerId : " + timerId + ", heloTimerId_ : " + heloTimerId_);
			return;
		}
		vertx.eventBus().publish(ServiceAddress.GridMaster.helo(), deploymentID());
		setHeloTimer_();
	}

}
