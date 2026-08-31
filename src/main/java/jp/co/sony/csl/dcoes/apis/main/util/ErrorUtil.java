package jp.co.sony.csl.dcoes.apis.main.util;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.util.StackTraceUtil;

public class ErrorUtil {

	private ErrorUtil() {
	}

	private static final Class<?>[] lastStackTraceArg_ = new Class<?>[] { ErrorUtil.class };

	public static JsonObject generateErrorObject(String unitId, Error.Category category, Error.Extent extent,
			Error.Level level, String message) {
		StackTraceElement ste = StackTraceUtil.lastStackTrace(lastStackTraceArg_);
		return Error.generateErrorObject(unitId, category, extent, level, message, ste);
	}

	public static void report(Vertx vertx, String unitId, Error.Category category, Error.Extent extent,
			Error.Level level, String message) {
		StackTraceElement ste = StackTraceUtil.lastStackTrace(lastStackTraceArg_);
		Error.report(vertx, unitId, category, extent, level, message, ste);
	}

	public static void report(Vertx vertx, Error.Category category, Error.Extent extent, Error.Level level,
			String message) {
		report(vertx, ApisConfig.unitId(), category, extent, level, message);
	}

	public static void report(Vertx vertx, Error.Category category, Error.Extent extent, Error.Level level,
			Throwable throwable) {
		report(vertx, category, extent, level, Error.messageFromThrowable(throwable));
	}

	public static void report(Vertx vertx, Error.Category category, Error.Extent extent, Error.Level level,
			String message, Throwable throwable) {
		report(vertx, category, extent, level, Error.messageFromThrowable(message, throwable));
	}

	public static <T> void reportAndFail(Vertx vertx, String unitId, Error.Category category, Error.Extent extent,
			Error.Level level, String message, Message<T> toReplyTo) {
		report(vertx, unitId, category, extent, level, message);
		toReplyTo.fail(level.ordinal(), message);
	}

	public static <T> void reportAndFail(Vertx vertx, Error.Category category, Error.Extent extent, Error.Level level,
			String message, Message<T> toReplyTo) {
		reportAndFail(vertx, ApisConfig.unitId(), category, extent, level, message, toReplyTo);
	}

	public static <T> void reportAndFail(Vertx vertx, Error.Category category, Error.Extent extent, Error.Level level,
			Throwable throwable, Message<T> toReplyTo) {
		reportAndFail(vertx, category, extent, level, Error.messageFromThrowable(throwable), toReplyTo);
	}

	public static <T> void reportAndFail(Vertx vertx, Error.Category category, Error.Extent extent, Error.Level level,
			String message, Throwable throwable, Message<T> toReplyTo) {
		reportAndFail(vertx, category, extent, level, Error.messageFromThrowable(message, throwable), toReplyTo);
	}

	public static <T> void reportAndFail(Vertx vertx, String unitId, Error.Category category, Error.Extent extent,
			Error.Level level, String message, Promise<T> promise) {
		report(vertx, unitId, category, extent, level, message);
		promise.fail(message);
	}

	public static <T> void reportAndFail(Vertx vertx, Error.Category category, Error.Extent extent, Error.Level level,
			String message, Promise<T> promise) {
		reportAndFail(vertx, ApisConfig.unitId(), category, extent, level, message, promise);
	}

	public static <T> void reportAndFail(Vertx vertx, Error.Category category, Error.Extent extent, Error.Level level,
			Throwable throwable, Promise<T> promise) {
		reportAndFail(vertx, category, extent, level, Error.messageFromThrowable(throwable), promise);
	}

	public static <T> void reportAndFail(Vertx vertx, Error.Category category, Error.Extent extent, Error.Level level,
			String message, Throwable throwable, Promise<T> promise) {
		reportAndFail(vertx, category, extent, level, Error.messageFromThrowable(message, throwable), promise);
	}

	public static <T> void reportAndFail(Vertx vertx, String unitId, Error.Category category, Error.Extent extent,
			Error.Level level, String message, Handler<AsyncResult<T>> completionHandler) {
		report(vertx, unitId, category, extent, level, message);
		completionHandler.handle(Future.failedFuture(message));
	}

	public static <T> void reportAndFail(Vertx vertx, Error.Category category, Error.Extent extent, Error.Level level,
			String message, Handler<AsyncResult<T>> completionHandler) {
		reportAndFail(vertx, ApisConfig.unitId(), category, extent, level, message, completionHandler);
	}

	public static <T> void reportAndFail(Vertx vertx, Error.Category category, Error.Extent extent, Error.Level level,
			Throwable throwable, Handler<AsyncResult<T>> completionHandler) {
		reportAndFail(vertx, category, extent, level, Error.messageFromThrowable(throwable), completionHandler);
	}

	public static <T> void reportAndFail(Vertx vertx, Error.Category category, Error.Extent extent, Error.Level level,
			String message, Throwable throwable, Handler<AsyncResult<T>> completionHandler) {
		reportAndFail(vertx, category, extent, level, Error.messageFromThrowable(message, throwable),
				completionHandler);
	}

}
