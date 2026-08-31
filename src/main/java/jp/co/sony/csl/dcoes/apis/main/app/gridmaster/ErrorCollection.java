package jp.co.sony.csl.dcoes.apis.main.app.gridmaster;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.ServiceAddress;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectWrapper;
import jp.co.sony.csl.dcoes.apis.main.app.PolicyKeeping;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

public class ErrorCollection extends AbstractVerticle {
	private static final Logger log = LoggerFactory.getLogger(ErrorCollection.class);

	private static final Long DEFAULT_ERROR_SUSTAINING_MSEC = 30000L;

	public static final JsonObjectWrapper cache = new JsonObjectWrapper();

	private static boolean hasErrors_ = false;
	private static long errorHandledMillis_ = 0;

	@Override
	public void start(Promise<Void> startPromise) throws Exception {
		startErrorTestingService_(resErrorTesting -> {
			if (resErrorTesting.succeeded()) {
				startErrorCollectingService_(resErrorCollecting -> {
					if (resErrorCollecting.succeeded()) {
						startPromise.complete();
					} else {
						startPromise.fail(resErrorCollecting.cause());
					}
				});
			} else {
				startPromise.fail(resErrorTesting.cause());
			}
		});
	}

	@Override
	public void stop() throws Exception {
	}

	private void startErrorTestingService_(Handler<AsyncResult<Void>> onComplete) {
		vertx.eventBus().<Void>consumer(ServiceAddress.GridMaster.errorTesting(), req -> {
			req.reply(Boolean.valueOf(hasErrors()));
		}).completionHandler(onComplete);
	}

	private void startErrorCollectingService_(Handler<AsyncResult<Void>> onComplete) {
		vertx.eventBus().<JsonObject>consumer(ServiceAddress.error(), req -> {
			handleError_(req);
		}).completionHandler(onComplete);
	}

	private void handleError_(Message<JsonObject> req) {
		JsonObject error = req.body();
		if (Error.Extent.LOCAL != Error.extent(error)) {
			if (PolicyKeeping.isMember(Error.unitId(error))) {
				doCache_(error);
				doWriteLog_(error);
			} else {
				ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN,
						"global error received from illegal unit : " + Error.unitId(error) + "; error : "
								+ Error.logMessage(error));
			}
		}
	}

	private void doCache_(JsonObject error) {
		if (Error.Level.WARN != Error.level(error)) {
			errorReceived_();
			cache.add(error, Error.category(error).name(), Error.level(error).name());
		}
	}

	private void doWriteLog_(JsonObject error) {
		switch (Error.level(error)) {
			case WARN:
				if (log.isWarnEnabled())
					log.warn(Error.logMessage(error));
				break;
			case ERROR:
			case FATAL:
			default:
				log.error(Error.logMessage(error));
				break;
		}
	}

	public static boolean hasErrors() {
		if (hasErrors_) {
			return true;
		} else {
			if (0 < errorHandledMillis_) {
				long errorSustainingMsec = PolicyKeeping.cache().getLong(DEFAULT_ERROR_SUSTAINING_MSEC, "gridMaster",
						"errorSustainingMsec");
				if (System.currentTimeMillis() < errorHandledMillis_ + errorSustainingMsec) {
					return true;
				} else {
					errorHandledMillis_ = 0;
				}
			}
		}
		return false;
	}

	static synchronized void errorReceived_() {
		hasErrors_ = true;
	}

	public static synchronized void errorHandled() {
		if (hasErrors_) {
			hasErrors_ = false;
			errorHandledMillis_ = System.currentTimeMillis();
		}
	}

}
