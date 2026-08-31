package jp.co.sony.csl.dcoes.apis.main.util;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.ErrorException;
import jp.co.sony.csl.dcoes.apis.common.util.StackTraceUtil;

public class ErrorExceptionUtil {
	private static final Logger log = LoggerFactory.getLogger(ErrorExceptionUtil.class);

	private ErrorExceptionUtil() {
	}

	public static ErrorException create(Error.Category category, Error.Extent extent, Error.Level level,
			String message) {
		return ErrorException.create(ApisConfig.unitId(), category, extent, level, message);
	}

	public static ErrorException create(Error.Category category, Error.Extent extent, Error.Level level,
			Throwable throwable) {
		return ErrorException.create(ApisConfig.unitId(), category, extent, level,
				Error.messageFromThrowable(throwable));
	}

	public static ErrorException create(Error.Category category, Error.Extent extent, Error.Level level, String message,
			Throwable throwable) {
		return ErrorException.create(ApisConfig.unitId(), category, extent, level,
				Error.messageFromThrowable(message, throwable));
	}

	public static void log(Error.Category category, Error.Extent extent, Error.Level level, String message) {
		ErrorException e = create(category, extent, level, message);
		doWriteLog_(e);
	}

	public static <T> void logAndFail(Error.Category category, Error.Extent extent, Error.Level level, String message,
			Handler<AsyncResult<T>> completionHandler) {
		ErrorException e = create(category, extent, level, message);
		doWriteLog_(e);
		completionHandler.handle(Future.failedFuture(e));
	}

	public static void log(Error.Category category, Error.Extent extent, Error.Level level, Throwable throwable) {
		ErrorException e = create(category, extent, level, throwable);
		doWriteLog_(e);
	}

	public static <T> void logAndFail(Error.Category category, Error.Extent extent, Error.Level level,
			Throwable throwable, Handler<AsyncResult<T>> completionHandler) {
		ErrorException e = create(category, extent, level, throwable);
		doWriteLog_(e);
		completionHandler.handle(Future.failedFuture(e));
	}

	public static void log(Error.Category category, Error.Extent extent, Error.Level level, String message,
			Throwable throwable) {
		ErrorException e = create(category, extent, level, message, throwable);
		doWriteLog_(e);
	}

	public static <T> void logAndFail(Error.Category category, Error.Extent extent, Error.Level level, String message,
			Throwable throwable, Handler<AsyncResult<T>> completionHandler) {
		ErrorException e = create(category, extent, level, message, throwable);
		doWriteLog_(e);
		completionHandler.handle(Future.failedFuture(e));
	}

	private static final Class<?>[] lastStackTraceArg_ = new Class<?>[] { ErrorExceptionUtil.class };

	public static void reportIfNeed(Vertx vertx, Throwable throwable) {
		if (throwable instanceof ErrorException) {
			ErrorException e = (ErrorException) throwable;
			StackTraceElement ste = StackTraceUtil.lastStackTrace(lastStackTraceArg_);
			Error.report(vertx, e.unitId, e.category, e.extent, e.level, e.getMessage(), ste);
		}
	}

	public static <T> void reportIfNeedAndReply(Vertx vertx, AsyncResult<T> result, Message<T> toReplyTo) {
		if (result.succeeded()) {
			toReplyTo.reply(result.result());
		} else {
			reportIfNeedAndFail(vertx, result.cause(), toReplyTo);
		}
	}

	public static <T> void reportIfNeedAndHandle(Vertx vertx, AsyncResult<T> result,
			Handler<AsyncResult<T>> completionHandler) {
		if (result.succeeded()) {
			completionHandler.handle(result);
		} else {
			reportIfNeedAndFail(vertx, result.cause(), completionHandler);
		}
	}

	public static <T> void reportIfNeedAndFail(Vertx vertx, Throwable throwable, Message<T> toReplyTo) {
		reportIfNeed(vertx, throwable);
		toReplyTo.fail(failureCode_(throwable), throwable.getMessage());
	}

	public static <T> void reportIfNeedAndFail(Vertx vertx, Throwable throwable,
			Handler<AsyncResult<T>> completionHandler) {
		reportIfNeed(vertx, throwable);
		completionHandler.handle(Future.failedFuture(throwable));
	}

	private static void doWriteLog_(ErrorException e) {
		StackTraceElement ste = StackTraceUtil.lastStackTrace(lastStackTraceArg_);
		String message = Error.logMessage(e.category, e.extent, e.level, e.getMessage(), ApisConfig.unitId(), ste);
		switch (e.level) {
			case WARN:
				if (log.isWarnEnabled())
					log.warn(message);
				break;
			case ERROR:
				log.error(message);
				break;
			case FATAL:
				log.error(message);
				break;
			default:
				log.error(message);
				break;
		}
	}

	private static int failureCode_(Throwable throwable) {
		return (throwable instanceof ErrorException) ? ((ErrorException) throwable).level.ordinal() : -1;
	}

}
