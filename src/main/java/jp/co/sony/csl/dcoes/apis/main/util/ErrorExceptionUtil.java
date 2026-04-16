package jp.co.sony.csl.dcoes.apis.main.util;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.ErrorException;
import jp.co.sony.csl.dcoes.apis.common.util.StackTraceUtil;

/**
 * Useful tools associated with {@link ErrorException}.
 * @author OES Project
 *          
 * {@link ErrorException} まわりの便利ツール.
 * @author OES Project
 */
// So FailureExceptionUtil goes here...
// んでこっちが FailureExceptionUtil ...
public class ErrorExceptionUtil {
	private static final Logger log = LoggerFactory.getLogger(ErrorExceptionUtil.class);

	private ErrorExceptionUtil() { }

	/**
	 * Generate an {@link ErrorException}.
	 * @param category an {@link Error.Category} object
	 * @param extent an {@link Error.Extent} object
	 * @param level an {@link Error.Level} object
	 * @param message an error message
	 * @return an {@link ErrorException} object
	 *          
	 * {@link ErrorException} を生成する.
	 * @param category {@link Error.Category} オブジェクト
	 * @param extent {@link Error.Extent} オブジェクト
	 * @param level {@link Error.Level} オブジェクト
	 * @param message エラーメッセージ
	 * @return {@link ErrorException} オブジェクト
	 */
	public static ErrorException create(Error.Category category, Error.Extent extent, Error.Level level, String message) {
		return ErrorException.create(ApisConfig.unitId(), category, extent, level, message);
	}
	/**
	 * Generate an {@link ErrorException}.
	 * @param category an {@link Error.Category} object
	 * @param extent an {@link Error.Extent} object
	 * @param level an {@link Error.Level} object
	 * @param throwable an exception object
	 * @return an {@link ErrorException} object
	 *          
	 * {@link ErrorException} を生成する.
	 * @param category {@link Error.Category} オブジェクト
	 * @param extent {@link Error.Extent} オブジェクト
	 * @param level {@link Error.Level} オブジェクト
	 * @param throwable 例外オブジェクト
	 * @return {@link ErrorException} オブジェクト
	 */
	public static ErrorException create(Error.Category category, Error.Extent extent, Error.Level level, Throwable throwable) {
		return ErrorException.create(ApisConfig.unitId(), category, extent, level, Error.messageFromThrowable(throwable));
	}
	/**
	 * Generate an {@link ErrorException}.
	 * @param category an {@link Error.Category} object
	 * @param extent an {@link Error.Extent} object
	 * @param level an {@link Error.Level} object
	 * @param message an error message
	 * @param throwable an exception object
	 * @return an {@link ErrorException} object
	 *          
	 * {@link ErrorException} を生成する.
	 * @param category {@link Error.Category} オブジェクト
	 * @param extent {@link Error.Extent} オブジェクト
	 * @param level {@link Error.Level} オブジェクト
	 * @param message エラーメッセージ
	 * @param throwable 例外オブジェクト
	 * @return {@link ErrorException} オブジェクト
	 */
	public static ErrorException create(Error.Category category, Error.Extent extent, Error.Level level, String message, Throwable throwable) {
		return ErrorException.create(ApisConfig.unitId(), category, extent, level, Error.messageFromThrowable(message, throwable));
	}

	/**
	 * Output to the log.
	 * @param category an {@link Error.Category} object
	 * @param extent an {@link Error.Extent} object
	 * @param level an {@link Error.Level} object
	 * @param message an error message
	 *          
	 * ログを出力する.
	 * @param category {@link Error.Category} オブジェクト
	 * @param extent {@link Error.Extent} オブジェクト
	 * @param level {@link Error.Level} オブジェクト
	 * @param message エラーメッセージ
	 */
	public static void log(Error.Category category, Error.Extent extent, Error.Level level, String message) {
		ErrorException e = create(category, extent, level, message);
		doWriteLog_(e);
	}
	/**
	 * Output to the log and fail the {@code completionHandler}.
	 * @param <T> the type of the completionHandler object's {@link AsyncResult#result()}
	 * @param category an {@link Error.Category} object
	 * @param extent an {@link Error.Extent} object
	 * @param level an {@link Error.Level} object
	 * @param message an error message
	 * @param completionHandler a failed completionHandler object
	 *          
	 * ログを出力し {@code completionHandler} を fail させる,
	 * @param <T> completionHandler オブジェクトの {@link AsyncResult#result()} の型
	 * @param category {@link Error.Category} オブジェクト
	 * @param extent {@link Error.Extent} オブジェクト
	 * @param level {@link Error.Level} オブジェクト
	 * @param message エラーメッセージ
	 * @param completionHandler fail させる completionHandler オブジェクト
	 */
	public static <T> void logAndFail(Error.Category category, Error.Extent extent, Error.Level level, String message, Handler<AsyncResult<T>> completionHandler) {
		ErrorException e = create(category, extent, level, message);
		doWriteLog_(e);
		completionHandler.handle(Future.failedFuture(e));
	}
	/**
	 * Output to the log.
	 * @param category an {@link Error.Category} object
	 * @param extent an {@link Error.Extent} object
	 * @param level an {@link Error.Level} object
	 * @param throwable an exception object
	 *          
	 * ログを出力する.
	 * @param category {@link Error.Category} オブジェクト
	 * @param extent {@link Error.Extent} オブジェクト
	 * @param level {@link Error.Level} オブジェクト
	 * @param throwable 例外オブジェクト
	 */
	public static void log(Error.Category category, Error.Extent extent, Error.Level level, Throwable throwable) {
		ErrorException e = create(category, extent, level, throwable);
		doWriteLog_(e);
	}
	/**
	 * Output to the log and fail the {@code completionHandler}.
	 * @param <T> the type of the completionHandler object's {@link AsyncResult#result()}
	 * @param category an {@link Error.Category} object
	 * @param extent an {@link Error.Extent} object
	 * @param level an {@link Error.Level} object
	 * @param throwable an exception object
	 * @param completionHandler a failed completionHandler object
	 *          
	 * ログを出力し {@code completionHandler} を fail させる,
	 * @param <T> completionHandler オブジェクトの {@link AsyncResult#result()} の型
	 * @param category {@link Error.Category} オブジェクト
	 * @param extent {@link Error.Extent} オブジェクト
	 * @param level {@link Error.Level} オブジェクト
	 * @param throwable 例外オブジェクト
	 * @param completionHandler fail させる completionHandler オブジェクト
	 */
	public static <T> void logAndFail(Error.Category category, Error.Extent extent, Error.Level level, Throwable throwable, Handler<AsyncResult<T>> completionHandler) {
		ErrorException e = create(category, extent, level, throwable);
		doWriteLog_(e);
		completionHandler.handle(Future.failedFuture(e));
	}
	/**
	 * Output to the log.
	 * @param category an {@link Error.Category} object
	 * @param extent an {@link Error.Extent} object
	 * @param level an {@link Error.Level} object
	 * @param message an error message
	 * @param throwable an exception object
	 *          
	 * ログを出力する.
	 * @param category {@link Error.Category} オブジェクト
	 * @param extent {@link Error.Extent} オブジェクト
	 * @param level {@link Error.Level} オブジェクト
	 * @param message エラーメッセージ
	 * @param throwable 例外オブジェクト
	 */
	public static void log(Error.Category category, Error.Extent extent, Error.Level level, String message, Throwable throwable) {
		ErrorException e = create(category, extent, level, message, throwable);
		doWriteLog_(e);
	}
	/**
	 * Output to the log and fail the {@code completionHandler}.
	 * @param <T> the type of the completionHandler object's {@link AsyncResult#result()}
	 * @param category an {@link Error.Category} object
	 * @param extent an {@link Error.Extent} object
	 * @param level an {@link Error.Level} object
	 * @param message an error message
	 * @param throwable an exception object
	 * @param completionHandler a failed completionHandler object
	 *          
	 * ログを出力し {@code completionHandler} を fail させる,
	 * @param <T> completionHandler オブジェクトの {@link AsyncResult#result()} の型
	 * @param category {@link Error.Category} オブジェクト
	 * @param extent {@link Error.Extent} オブジェクト
	 * @param level {@link Error.Level} オブジェクト
	 * @param message エラーメッセージ
	 * @param throwable 例外オブジェクト
	 * @param completionHandler fail させる completionHandler オブジェクト
	 */
	public static <T> void logAndFail(Error.Category category, Error.Extent extent, Error.Level level, String message, Throwable throwable, Handler<AsyncResult<T>> completionHandler) {
		ErrorException e = create(category, extent, level, message, throwable);
		doWriteLog_(e);
		completionHandler.handle(Future.failedFuture(e));
	}

	private static final Class<?>[] lastStackTraceArg_ = new Class<?>[] { ErrorExceptionUtil.class };
	/**
	 * Throw an error if necessary.
	 * Throw an error if {@code throwable} is a subclass of {@link ErrorException}.
	 * @param vertx a vertx object
	 * @param throwable an exception object
	 *          
	 * 必要に応じてエラーを送出する.
	 * {@code throwable} が {@link ErrorException} のサブクラスならエラーを送出する.
	 * @param vertx vertx オブジェクト
	 * @param throwable 例外オブジェクト
	 */
	public static void reportIfNeed(Vertx vertx, Throwable throwable) {
		if (throwable instanceof ErrorException) {
			ErrorException e = (ErrorException) throwable;
			StackTraceElement ste = StackTraceUtil.lastStackTrace(lastStackTraceArg_);
			Error.report(vertx, e.unitId, e.category, e.extent, e.level, e.getMessage(), ste);
		}
	}

	/**
	 * If {@code result} is successful, reply to {@code toReplyTo} without doing anything.
	 * If {@code result} is unsuccessful, throw an error if necessary and set {@code toReplyTo} to fail.
	 * @param <T> type of {@link AsyncResult#result()} of the result object and {@link Message#body()} of the toReplyTo object
	 * @param vertx a vertx object
	 * @param result an asyncresult object that determines success or failure
	 * @param toReplyTo a message object to which the reply or fail message is sent
	 *          
	 * {@code result} が成功していたら何もせず {@code toReplyTo} に reply する.
	 * {@code result} が失敗していたら必要に応じてエラーを送出し {@code toReplyTo} を fail する.
	 * @param <T> result オブジェクトの {@link AsyncResult#result()} および toReplyTo オブジェクトの {@link Message#body()} の型
	 * @param vertx vertx オブジェクト
	 * @param result 成功か失敗か判定する asyncresult オブジェクト
	 * @param toReplyTo reply もしくは fail させる message オブジェクト
	 */
	public static <T> void reportIfNeedAndReply(Vertx vertx, AsyncResult<T> result, Message<T> toReplyTo) {
		if (result.succeeded()) {
			toReplyTo.reply(result.result());
		} else {
			reportIfNeedAndFail(vertx, result.cause(), toReplyTo);
		}
	}
	/**
	 * If {@code result} is successful, set {@code completionHandler} to succeed without doing anything.
	 * If {@code result} is unsuccessful, throw an error if necessary and set {@code completionHandler} to fail.
	 * @param <T> type of {@link AsyncResult#result()} of the result object and {@link AsyncResult#result()} of the completionHandler object
	 * @param vertx a vertx object
	 * @param result an asyncresult object that determines success or failure
	 * @param completionHandler a completionHandler object that is set to either succeed or fail
	 *          
	 * {@code result} が成功していたら何もせず {@code completionHandler} を succeed する.
	 * {@code result} が失敗していたら必要に応じてエラーを送出し {@code completionHandler} を fail する.
	 * @param <T> result オブジェクトの {@link AsyncResult#result()} および completionHandler オブジェクトの {@link AsyncResult#result()} の型
	 * @param vertx vertx オブジェクト
	 * @param result 成功か失敗か判定する asyncresult オブジェクト
	 * @param completionHandler succeed もしくは fail させる completionHandler オブジェクト
	 */
	public static <T> void reportIfNeedAndHandle(Vertx vertx, AsyncResult<T> result, Handler<AsyncResult<T>> completionHandler) {
		if (result.succeeded()) {
			completionHandler.handle(result);
		} else {
			reportIfNeedAndFail(vertx, result.cause(), completionHandler);
		}
	}

	/**
	 * Throw an error if necessary and set {@code toReplyTo} to fail.
	 * @param <T> type of {@link Message#body()} of the toReplyTo object
	 * @param vertx a vertx object
	 * @param throwable an exception object
	 * @param toReplyTo a message object to which a fail message is sent
	 *          
	 * 必要に応じてエラーを送出し {@code toReplyTo} を fail する.
	 * @param <T> toReplyTo オブジェクトの {@link Message#body()} の型
	 * @param vertx vertx オブジェクト
	 * @param throwable 例外オブジェクト
	 * @param toReplyTo fail させる message オブジェクト
	 */
	public static <T> void reportIfNeedAndFail(Vertx vertx, Throwable throwable, Message<T> toReplyTo) {
		reportIfNeed(vertx, throwable);
		toReplyTo.fail(failureCode_(throwable), throwable.getMessage());
	}
	/**
	 * Throw an error if necessary and set {@code completionHandler} to fail.
	 * @param <T> the type of the completionHandler object's {@link AsyncResult#result()}
	 * @param vertx a vertx object
	 * @param throwable an exception object
	 * @param completionHandler a failed completionHandler object
	 *          
	 * 必要に応じてエラーを送出し {@code completionHandler} を fail する.
	 * @param <T> completionHandler オブジェクトの {@link AsyncResult#result()} の型
	 * @param vertx vertx オブジェクト
	 * @param throwable 例外オブジェクト
	 * @param completionHandler fail させる completionHandler オブジェクト
	 */
	public static <T> void reportIfNeedAndFail(Vertx vertx, Throwable throwable, Handler<AsyncResult<T>> completionHandler) {
		reportIfNeed(vertx, throwable);
		completionHandler.handle(Future.failedFuture(throwable));
	}

	/**
	 * Create a log message and output it to the log at a suitable level.
	 * @param e a {@link ErrorException} object
	 *          
	 * ログメッセージを生成し適切なレベルでログを出力する.
	 * @param e {@link ErrorException} オブジェクト
	 */
	private static void doWriteLog_(ErrorException e) {
		// Get the last line of the stack trace
		// スタックトレースの最後の一行を取得する
		StackTraceElement ste = StackTraceUtil.lastStackTrace(lastStackTraceArg_);
		// Create a log string
		// ログ文字列を生成する
		String message = Error.logMessage(e.category, e.extent, e.level, e.getMessage(), ApisConfig.unitId(), ste);
		switch (e.level) {
		case WARN:
			if (log.isWarnEnabled()) log.warn(message);
			break;
		case ERROR:
			log.error(message);
			break;
		case FATAL:
			log.fatal(message);
			break;
		default:
			log.fatal(message);
			break;
		}
	}

	/**
	 * Get the code to set {@link Message} to {@link Message#fail(int, String)}.
	 * If {@code throwable} is {@link ErrorException}, use the value of {@link Error.Level#ordinal()}.
	 * @param throwable an exception object
	 * @return failureCode
	 *          
	 * {@link Message} を {@link Message#fail(int, String)} させる際のコードを取得する.
	 * {@code throwable} が {@link ErrorException} なら {@link Error.Level#ordinal()} の値を使う.
	 * @param throwable 例外オブジェクト
	 * @return failureCode
	 */
	private static int failureCode_(Throwable throwable) {
		return (throwable instanceof ErrorException) ? ((ErrorException) throwable).level.ordinal() : -1;
	}

}
