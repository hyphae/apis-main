package jp.co.sony.csl.dcoes.apis.main.app.gridmaster;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.ServiceAddress;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.LocalExclusiveLock;
import jp.co.sony.csl.dcoes.apis.main.app.PolicyKeeping;
import jp.co.sony.csl.dcoes.apis.main.app.StateHandling;
import jp.co.sony.csl.dcoes.apis.main.app.gridmaster.main_loop.DealExecution;
import jp.co.sony.csl.dcoes.apis.main.app.gridmaster.main_loop.ErrorHandling;
import jp.co.sony.csl.dcoes.apis.main.app.gridmaster.main_loop.GlobalDataCalculation;
import jp.co.sony.csl.dcoes.apis.main.evaluation.safety.GlobalSafetyEvaluation;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

public class MainLoop extends AbstractVerticle {
	private static final Logger log = LoggerFactory.getLogger(MainLoop.class);

	public static final Long DEFAULT_MAIN_LOOP_PERIOD_MSEC = 5000L;

	private static final LocalExclusiveLock exclusiveLock_ = new LocalExclusiveLock(MainLoop.class.getName());

	public static void acquirePrivilegedExclusiveLock(Vertx vertx,
			Handler<AsyncResult<LocalExclusiveLock.Lock>> onComplete) {
		exclusiveLock_.acquire(vertx, true, onComplete);
	}

	public static void acquireExclusiveLock(Vertx vertx, Handler<AsyncResult<LocalExclusiveLock.Lock>> onComplete) {
		exclusiveLock_.acquire(vertx, onComplete);
	}

	public static void resetExclusiveLock(Vertx vertx) {
		exclusiveLock_.reset(vertx);
	}

	private long mainLoopTimerId_ = 0L;
	private boolean stopped_ = false;

	@Override
	public void start(Promise<Void> startPromise) throws Exception {
		mainLoopTimerHandler_(0L);
		startPromise.complete();
	}

	@Override
	public void stop() throws Exception {
		stopped_ = true;
	}

	private void setMainLoopTimer_() {
		Long delay = PolicyKeeping.cache().getLong(DEFAULT_MAIN_LOOP_PERIOD_MSEC, "gridMaster", "mainLoopPeriodMsec");
		setMainLoopTimer_(delay);
	}

	private void setMainLoopTimer_(long delay) {
		mainLoopTimerId_ = vertx.setTimer(delay, this::mainLoopTimerHandler_);
	}

	private void mainLoopTimerHandler_(Long timerId) {
		if (stopped_)
			return;
		if (null == timerId || timerId.longValue() != mainLoopTimerId_) {
			ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN,
					"illegal timerId : " + timerId + ", mainLoopTimerId_ : " + mainLoopTimerId_);
			return;
		}
		if (!StateHandling.isInOperation()) {
			setMainLoopTimer_();
		} else {
			acquireExclusiveLock(vertx, resExclusiveLock -> {
				if (resExclusiveLock.succeeded()) {
					LocalExclusiveLock.Lock lock = resExclusiveLock.result();
					doMainLoopWithExclusiveLock_(resDoMainLoopWithExclusiveLock -> {
						lock.release();
						setMainLoopTimer_();
					});
				} else {
					ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN,
							resExclusiveLock.cause());
					setMainLoopTimer_();
				}
			});
		}
	}

	private void doMainLoopWithExclusiveLock_(Handler<AsyncResult<Void>> onComplete) {
		if (stopped_) {
			onComplete.handle(Future.succeededFuture());
		} else {
			ErrorHandling.execute(vertx, resErrorHandling_before -> {
				DealExecution.execute(vertx, resDealExecution -> {
					GlobalSafetyEvaluation.check(vertx, PolicyKeeping.cache().jsonObject(),
							DealExecution.unitDataCache.jsonObject(), resSafetyEvaluation -> {
								GlobalDataCalculation.execute(vertx, resGlobalDataCalculation -> {
									ErrorHandling.execute(vertx, resErrorHandling_after -> {
										vertx.eventBus().request(ServiceAddress.Mediator.gridMasterEnsuring(), null);
										onComplete.handle(Future.succeededFuture());
									});
								});
							});
				});
			});
		}
	}

}
