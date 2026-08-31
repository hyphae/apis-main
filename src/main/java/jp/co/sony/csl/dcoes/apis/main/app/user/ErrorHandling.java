package jp.co.sony.csl.dcoes.apis.main.app.user;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.main.app.PolicyKeeping;
import jp.co.sony.csl.dcoes.apis.main.error.handling.AbstractErrorsHandling;
import jp.co.sony.csl.dcoes.apis.main.error.handling.LocalAnyFatalsHandling;
import jp.co.sony.csl.dcoes.apis.main.error.handling.LocalFrameworkErrorsHandling;
import jp.co.sony.csl.dcoes.apis.main.error.handling.LocalHardwareErrorsHandling;
import jp.co.sony.csl.dcoes.apis.main.error.handling.LocalLogicErrorsHandling;
import jp.co.sony.csl.dcoes.apis.main.error.handling.LocalUserErrorsHandling;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

public class ErrorHandling extends AbstractVerticle {
	private static final Logger log = LoggerFactory.getLogger(ErrorHandling.class);

	private static final Long DEFAULT_ERROR_HANDLING_PERIOD_MSEC = 1000L;

	private long errorHandlingTimerId_ = 0L;
	private boolean stopped_ = false;

	@Override
	public void start(Promise<Void> startPromise) throws Exception {
		errorHandlingTimerHandler_(0L);
		if (log.isTraceEnabled())
			log.trace("started : " + deploymentID());
		startPromise.complete();
	}

	@Override
	public void stop() throws Exception {
		stopped_ = true;
		if (log.isTraceEnabled())
			log.trace("stopped : " + deploymentID());
	}

	private void setErrorHandlingTimer_() {
		Long delay = PolicyKeeping.cache().getLong(DEFAULT_ERROR_HANDLING_PERIOD_MSEC, "user",
				"errorHandlingPeriodMsec");
		setErrorHandlingTimer_(delay);
	}

	private void setErrorHandlingTimer_(long delay) {
		errorHandlingTimerId_ = vertx.setTimer(delay, this::errorHandlingTimerHandler_);
	}

	private void errorHandlingTimerHandler_(Long timerId) {
		if (stopped_)
			return;
		if (null == timerId || timerId.longValue() != errorHandlingTimerId_) {
			ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN,
					"illegal timerId : " + timerId + ", errorHandlingTimerId_ : " + errorHandlingTimerId_);
			return;
		}
		JsonObject policy = PolicyKeeping.cache().jsonObject();
		new HandleErrors_(policy).doLoop_(r -> {
			ErrorCollection.errorHandled_();
			setErrorHandlingTimer_();
		});
	}

	private class HandleErrors_ {
		private JsonObject policy_;
		private List<Error.Category> categoriesForLoop_;

		private HandleErrors_(JsonObject policy) {
			policy_ = policy;
			categoriesForLoop_ = new ArrayList<Error.Category>(Arrays.asList(Error.Category.values()));
		}

		private void doLoop_(Handler<AsyncResult<Void>> onComplete) {
			if (categoriesForLoop_.isEmpty()) {
				onComplete.handle(Future.succeededFuture());
			} else {
				Error.Category aCategory = categoriesForLoop_.remove(0);
				new HandleErrorsByCategory_(policy_, aCategory).doLoop_(r -> {
					doLoop_(onComplete);
				});
			}
		}
	}

	private class HandleErrorsByCategory_ {
		private JsonObject policy_;
		private Error.Category category_;
		private List<Error.Level> levelsForLoop_;

		private HandleErrorsByCategory_(JsonObject policy, Error.Category category) {
			policy_ = policy;
			category_ = category;
			levelsForLoop_ = new ArrayList<Error.Level>(Arrays.asList(Error.Level.values()));
		}

		private void doLoop_(Handler<AsyncResult<Void>> onComplete) {
			if (levelsForLoop_.isEmpty()) {
				onComplete.handle(Future.succeededFuture());
			} else {
				Error.Level aLevel = levelsForLoop_.remove(0);
				JsonArray errors = ErrorCollection.cache.removeJsonArray(category_.name(), aLevel.name());
				if (errors != null && 0 < errors.size()) {
					if (log.isInfoEnabled())
						log.info("[" + category_ + ':' + aLevel + "] : " + errors);
					AbstractErrorsHandling handler = null;
					switch (aLevel) {
						case WARN:
							log.error("#### should never happen; category : " + category_ + ", level : " + aLevel);
							break;
						case ERROR:
							switch (category_) {
								case HARDWARE:
									handler = new LocalHardwareErrorsHandling(vertx, policy_, errors);
									break;
								case FRAMEWORK:
									handler = new LocalFrameworkErrorsHandling(vertx, policy_, errors);
									break;
								case LOGIC:
									handler = new LocalLogicErrorsHandling(vertx, policy_, errors);
									break;
								case USER:
									handler = new LocalUserErrorsHandling(vertx, policy_, errors);
									break;
								case UNKNOWN:
									log.error("#### should never happen; category : " + category_ + ", level : "
											+ aLevel);
									break;
							}
							break;
						case FATAL:
						case UNKNOWN:
							handler = new LocalAnyFatalsHandling(vertx, policy_, errors);
							break;
					}
					if (handler != null) {
						handler.handle(resHandle -> {
							doLoop_(onComplete);
						});
					} else {
						doLoop_(onComplete);
					}
				} else {
					doLoop_(onComplete);
				}
			}
		}
	}

}
