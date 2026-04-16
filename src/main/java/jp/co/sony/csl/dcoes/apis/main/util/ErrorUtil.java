package jp.co.sony.csl.dcoes.apis.main.util;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.util.StackTraceUtil;

/**
 * Useful tools associated with {@link Error}.
 * @author OES Project
 *          
 * {@link Error} まわりの便利ツール.
 * @author OES Project
 */
// This is FailureUtil...
// これは FailureUtil ...
public class ErrorUtil {

	private ErrorUtil() { }

	private static final Class<?>[] lastStackTraceArg_ = new Class<?>[] { ErrorUtil.class };
	/**
	 * Create an error object.
	 * @param unitId the unit ID
	 * @param category an {@link Error.Category} object
	 * @param extent an {@link Error.Extent} object
	 * @param level an {@link Error.Level} object
	 * @param message an error message
	 * @return an error object
	 *          
	 * エラーオブジェクトを生成する.
	 * @param unitId ユニット ID
	 * @param category {@link Error.Category} オブジェクト
	 * @param extent {@link Error.Extent} オブジェクト
	 * @param level {@link Error.Level} オブジェクト
	 * @param message エラーメッセージ
	 * @return エラーオブジェクト
	 */
	public static JsonObject generateErrorObject(String unitId, Error.Category category, Error.Extent extent, Error.Level level, String message) {
		// Get the last line of the stack trace
		// スタックトレースの最後の行を取得する
		StackTraceElement ste = StackTraceUtil.lastStackTrace(lastStackTraceArg_);
		return Error.generateErrorObject(unitId, category, extent, level, message, ste);
	}

	/**
	 * Throw an error.
	 * @param vertx a vertx object
	 * @param unitId the unit ID
	 * @param category an {@link Error.Category} object
	 * @param extent an {@link Error.Extent} object
	 * @param level an {@link Error.Level} object
	 * @param message an error message
	 *          
	 * エラーを送出する.
	 * @param vertx vertx オブジェクト
	 * @param unitId ユニット ID
	 * @param category {@link Error.Category} オブジェクト
	 * @param extent {@link Error.Extent} オブジェクト
	 * @param level {@link Error.Level} オブジェクト
	 * @param message エラーメッセージ
	 */
	public static void report(Vertx vertx, String unitId, Error.Category category, Error.Extent extent, Error.Level level, String message) {
		StackTraceElement ste = StackTraceUtil.lastStackTrace(lastStackTraceArg_);
		Error.report(vertx, unitId, category, extent, level, message, ste);
	}
	/**
	 * Throw an error.
	 * Since the unit ID is not specified, use this unit's ID.
	 * @param vertx a vertx object
	 * @param category an {@link Error.Category} object
	 * @param extent an {@link Error.Extent} object
	 * @param level an {@link Error.Level} object
	 * @param message an error message
	 *          
	 * エラーを送出する.
	 * ユニット ID を指定しないので自ユニットの ID が使われる.
	 * @param vertx vertx オブジェクト
	 * @param category {@link Error.Category} オブジェクト
	 * @param extent {@link Error.Extent} オブジェクト
	 * @param level {@link Error.Level} オブジェクト
	 * @param message エラーメッセージ
	 */
	public static void report(Vertx vertx, Error.Category category, Error.Extent extent, Error.Level level, String message) {
		report(vertx, ApisConfig.unitId(), category, extent, level, message);
	}
	/**
	 * Throw an error.
	 * Since the unit ID is not specified, use this unit's ID.
	 * @param vertx a vertx object
	 * @param category an {@link Error.Category} object
	 * @param extent an {@link Error.Extent} object
	 * @param level an {@link Error.Level} object
	 * @param throwable an exception object
	 *          
	 * エラーを送出する.
	 * ユニット ID を指定しないので自ユニットの ID が使われる.
	 * @param vertx vertx オブジェクト
	 * @param category {@link Error.Category} オブジェクト
	 * @param extent {@link Error.Extent} オブジェクト
	 * @param level {@link Error.Level} オブジェクト
	 * @param throwable 例外オブジェクト
	 */
	public static void report(Vertx vertx, Error.Category category, Error.Extent extent, Error.Level level, Throwable throwable) {
		report(vertx, category, extent, level, Error.messageFromThrowable(throwable));
	}
	/**
	 * Throw an error.
	 * Since the unit ID is not specified, use this unit's ID.
	 * @param vertx a vertx object
	 * @param category an {@link Error.Category} object
	 * @param extent an {@link Error.Extent} object
	 * @param level an {@link Error.Level} object
	 * @param message an error message
	 * @param throwable an exception object
	 *          
	 * エラーを送出する.
	 * ユニット ID を指定しないので自ユニットの ID が使われる.
	 * @param vertx vertx オブジェクト
	 * @param category {@link Error.Category} オブジェクト
	 * @param extent {@link Error.Extent} オブジェクト
	 * @param level {@link Error.Level} オブジェクト
	 * @param message エラーメッセージ
	 * @param throwable 例外オブジェクト
	 */
	public static void report(Vertx vertx, Error.Category category, Error.Extent extent, Error.Level level, String message, Throwable throwable) {
		report(vertx, category, extent, level, Error.messageFromThrowable(message, throwable));
	}

	/**
	 * Throw an error and set {@code toReplyTo} to fail.
	 * @param <T> type of {@link Message#body()} of the toReplyTo object
	 * @param vertx a vertx object
	 * @param unitId the unit ID
	 * @param category an {@link Error.Category} object
	 * @param extent an {@link Error.Extent} object
	 * @param level an {@link Error.Level} object
	 * @param message an error message
	 * @param toReplyTo a message object to which a fail message is sent
	 *          
	 * エラーを送出し {@code toReplyTo} を fail させる.
	 * @param <T> toReplyTo オブジェクトの {@link Message#body()} の型
	 * @param vertx vertx オブジェクト
	 * @param unitId ユニット ID
	 * @param category {@link Error.Category} オブジェクト
	 * @param extent {@link Error.Extent} オブジェクト
	 * @param level {@link Error.Level} オブジェクト
	 * @param message エラーメッセージ
	 * @param toReplyTo fail させる message オブジェクト
	 */
	public static <T> void reportAndFail(Vertx vertx, String unitId, Error.Category category, Error.Extent extent, Error.Level level, String message, Message<T> toReplyTo) {
		report(vertx, unitId, category, extent, level, message);
		toReplyTo.fail(level.ordinal(), message);
	}
	/**
	 * Throw an error and set {@code toReplyTo} to fail.
	 * Since the unit ID is not specified, use this unit's ID.
	 * @param <T> type of {@link Message#body()} of the toReplyTo object
	 * @param vertx a vertx object
	 * @param category an {@link Error.Category} object
	 * @param extent an {@link Error.Extent} object
	 * @param level an {@link Error.Level} object
	 * @param message an error message
	 * @param toReplyTo a message object to which a fail message is sent
	 *          
	 * エラーを送出し {@code toReplyTo} を fail させる.
	 * ユニット ID を指定しないので自ユニットの ID が使われる.
	 * @param <T> toReplyTo オブジェクトの {@link Message#body()} の型
	 * @param vertx vertx オブジェクト
	 * @param category {@link Error.Category} オブジェクト
	 * @param extent {@link Error.Extent} オブジェクト
	 * @param level {@link Error.Level} オブジェクト
	 * @param message エラーメッセージ
	 * @param toReplyTo fail させる message オブジェクト
	 */
	public static <T> void reportAndFail(Vertx vertx, Error.Category category, Error.Extent extent, Error.Level level, String message, Message<T> toReplyTo) {
		reportAndFail(vertx, ApisConfig.unitId(), category, extent, level, message, toReplyTo);
	}
	/**
	 * Throw an error and set {@code toReplyTo} to fail.
	 * Since the unit ID is not specified, use this unit's ID.
	 * @param <T> type of {@link Message#body()} of the toReplyTo object
	 * @param vertx a vertx object
	 * @param category an {@link Error.Category} object
	 * @param extent an {@link Error.Extent} object
	 * @param level an {@link Error.Level} object
	 * @param throwable an exception object
	 * @param toReplyTo a message object to which a fail message is sent
	 *          
	 * エラーを送出し {@code toReplyTo} を fail させる.
	 * ユニット ID を指定しないので自ユニットの ID が使われる.
	 * @param <T> toReplyTo オブジェクトの {@link Message#body()} の型
	 * @param vertx vertx オブジェクト
	 * @param category {@link Error.Category} オブジェクト
	 * @param extent {@link Error.Extent} オブジェクト
	 * @param level {@link Error.Level} オブジェクト
	 * @param throwable 例外オブジェクト
	 * @param toReplyTo fail させる message オブジェクト
	 */
	public static <T> void reportAndFail(Vertx vertx, Error.Category category, Error.Extent extent, Error.Level level, Throwable throwable, Message<T> toReplyTo) {
		reportAndFail(vertx, category, extent, level, Error.messageFromThrowable(throwable), toReplyTo);
	}
	/**
	 * Throw an error and set {@code toReplyTo} to fail.
	 * Since the unit ID is not specified, use this unit's ID.
	 * @param <T> type of {@link Message#body()} of the toReplyTo object
	 * @param vertx a vertx object
	 * @param category an {@link Error.Category} object
	 * @param extent an {@link Error.Extent} object
	 * @param level an {@link Error.Level} object
	 * @param message an error message
	 * @param throwable an exception object
	 * @param toReplyTo a message object to which a fail message is sent
	 *          
	 * エラーを送出し {@code toReplyTo} を fail させる.
	 * ユニット ID を指定しないので自ユニットの ID が使われる.
	 * @param <T> toReplyTo オブジェクトの {@link Message#body()} の型
	 * @param vertx vertx オブジェクト
	 * @param category {@link Error.Category} オブジェクト
	 * @param extent {@link Error.Extent} オブジェクト
	 * @param level {@link Error.Level} オブジェクト
	 * @param message エラーメッセージ
	 * @param throwable 例外オブジェクト
	 * @param toReplyTo fail させる message オブジェクト
	 */
	public static <T> void reportAndFail(Vertx vertx, Error.Category category, Error.Extent extent, Error.Level level, String message, Throwable throwable, Message<T> toReplyTo) {
		reportAndFail(vertx, category, extent, level, Error.messageFromThrowable(message, throwable), toReplyTo);
	}

	/**
	 * Throw an error and set {@code future} to fail.
	 * @param <T> type of {@link Future#result()} of the future object
	 * @param vertx a vertx object
	 * @param unitId the unit ID
	 * @param category an {@link Error.Category} object
	 * @param extent an {@link Error.Extent} object
	 * @param level an {@link Error.Level} object
	 * @param message an error message
	 * @param future a future object that raises a "fail" state
	 *          
	 * エラーを送出し {@code future} を fail させる.
	 * @param <T> future オブジェクトの {@link Future#result()} の型
	 * @param vertx vertx オブジェクト
	 * @param unitId ユニット ID
	 * @param category {@link Error.Category} オブジェクト
	 * @param extent {@link Error.Extent} オブジェクト
	 * @param level {@link Error.Level} オブジェクト
	 * @param message エラーメッセージ
	 * @param future fail させる future オブジェクト
	 */
	public static <T> void reportAndFail(Vertx vertx, String unitId, Error.Category category, Error.Extent extent, Error.Level level, String message, Future<T> future) {
		report(vertx, unitId, category, extent, level, message);
		future.fail(message);
	}
	/**
	 * Throw an error and set {@code future} to fail.
	 * Since the unit ID is not specified, use this unit's ID.
	 * @param <T> type of {@link Future#result()} of the future object
	 * @param vertx a vertx object
	 * @param category an {@link Error.Category} object
	 * @param extent an {@link Error.Extent} object
	 * @param level an {@link Error.Level} object
	 * @param message an error message
	 * @param future a future object that raises a "fail" state
	 *          
	 * エラーを送出し {@code future} を fail させる.
	 * ユニット ID を指定しないので自ユニットの ID が使われる.
	 * @param <T> future オブジェクトの {@link Future#result()} の型
	 * @param vertx vertx オブジェクト
	 * @param category {@link Error.Category} オブジェクト
	 * @param extent {@link Error.Extent} オブジェクト
	 * @param level {@link Error.Level} オブジェクト
	 * @param message エラーメッセージ
	 * @param future fail させる future オブジェクト
	 */
	public static <T> void reportAndFail(Vertx vertx, Error.Category category, Error.Extent extent, Error.Level level, String message, Future<T> future) {
		reportAndFail(vertx, ApisConfig.unitId(), category, extent, level, message, future);
	}
	/**
	 * Throw an error and set {@code future} to fail.
	 * Since the unit ID is not specified, use this unit's ID.
	 * @param <T> type of {@link Future#result()} of the future object
	 * @param vertx a vertx object
	 * @param category an {@link Error.Category} object
	 * @param extent an {@link Error.Extent} object
	 * @param level an {@link Error.Level} object
	 * @param throwable an exception object
	 * @param future a future object that raises a "fail" state
	 *          
	 * エラーを送出し {@code future} を fail させる.
	 * ユニット ID を指定しないので自ユニットの ID が使われる.
	 * @param <T> future オブジェクトの {@link Future#result()} の型
	 * @param vertx vertx オブジェクト
	 * @param category {@link Error.Category} オブジェクト
	 * @param extent {@link Error.Extent} オブジェクト
	 * @param level {@link Error.Level} オブジェクト
	 * @param throwable 例外オブジェクト
	 * @param future fail させる future オブジェクト
	 */
	public static <T> void reportAndFail(Vertx vertx, Error.Category category, Error.Extent extent, Error.Level level, Throwable throwable, Future<T> future) {
		reportAndFail(vertx, category, extent, level, Error.messageFromThrowable(throwable), future);
	}
	/**
	 * Throw an error and set {@code future} to fail.
	 * Since the unit ID is not specified, use this unit's ID.
	 * @param <T> type of {@link Future#result()} of the future object
	 * @param vertx a vertx object
	 * @param category an {@link Error.Category} object
	 * @param extent an {@link Error.Extent} object
	 * @param level an {@link Error.Level} object
	 * @param message an error message
	 * @param throwable an exception object
	 * @param future a future object that raises a "fail" state
	 *          
	 * エラーを送出し {@code future} を fail させる.
	 * ユニット ID を指定しないので自ユニットの ID が使われる.
	 * @param <T> future オブジェクトの {@link Future#result()} の型
	 * @param vertx vertx オブジェクト
	 * @param category {@link Error.Category} オブジェクト
	 * @param extent {@link Error.Extent} オブジェクト
	 * @param level {@link Error.Level} オブジェクト
	 * @param message エラーメッセージ
	 * @param throwable 例外オブジェクト
	 * @param future fail させる future オブジェクト
	 */
	public static <T> void reportAndFail(Vertx vertx, Error.Category category, Error.Extent extent, Error.Level level, String message, Throwable throwable, Future<T> future) {
		reportAndFail(vertx, category, extent, level, Error.messageFromThrowable(message, throwable), future);
	}

	/**
	 * Throw an error and set {@code completionHandler} to fail.
	 * @param <T> the type of the completionHandler object's {@link AsyncResult#result()}
	 * @param vertx a vertx object
	 * @param unitId the unit ID
	 * @param category an {@link Error.Category} object
	 * @param extent an {@link Error.Extent} object
	 * @param level an {@link Error.Level} object
	 * @param message an error message
	 * @param completionHandler the completionhandler object invoked if failure occurs
	 *          
	 * エラーを送出し {@code completionHandler} を fail する.
	 * @param <T> completionHandler オブジェクトの {@link AsyncResult#result()} の型
	 * @param vertx vertx オブジェクト
	 * @param unitId ユニット ID
	 * @param category {@link Error.Category} オブジェクト
	 * @param extent {@link Error.Extent} オブジェクト
	 * @param level {@link Error.Level} オブジェクト
	 * @param message エラーメッセージ
	 * @param completionHandler fail させる completionhandler オブジェクト
	 */
	public static <T> void reportAndFail(Vertx vertx, String unitId, Error.Category category, Error.Extent extent, Error.Level level, String message, Handler<AsyncResult<T>> completionHandler) {
		report(vertx, unitId, category, extent, level, message);
		completionHandler.handle(Future.failedFuture(message));
	}
	/**
	 * Throw an error and set {@code completionHandler} to fail.
	 * Since the unit ID is not specified, use this unit's ID.
	 * @param <T> the type of the completionHandler object's {@link AsyncResult#result()}
	 * @param vertx a vertx object
	 * @param category an {@link Error.Category} object
	 * @param extent an {@link Error.Extent} object
	 * @param level an {@link Error.Level} object
	 * @param message an error message
	 * @param completionHandler the completionhandler object invoked if failure occurs
	 *          
	 * エラーを送出し {@code completionHandler} を fail する.
	 * ユニット ID を指定しないので自ユニットの ID が使われる.
	 * @param <T> completionHandler オブジェクトの {@link AsyncResult#result()} の型
	 * @param vertx vertx オブジェクト
	 * @param category {@link Error.Category} オブジェクト
	 * @param extent {@link Error.Extent} オブジェクト
	 * @param level {@link Error.Level} オブジェクト
	 * @param message エラーメッセージ
	 * @param completionHandler fail させる completionhandler オブジェクト
	 */
	public static <T> void reportAndFail(Vertx vertx, Error.Category category, Error.Extent extent, Error.Level level, String message, Handler<AsyncResult<T>> completionHandler) {
		reportAndFail(vertx, ApisConfig.unitId(), category, extent, level, message, completionHandler);
	}
	/**
	 * Throw an error and set {@code completionHandler} to fail.
	 * Since the unit ID is not specified, use this unit's ID.
	 * @param <T> the type of the completionHandler object's {@link AsyncResult#result()}
	 * @param vertx a vertx object
	 * @param category an {@link Error.Category} object
	 * @param extent an {@link Error.Extent} object
	 * @param level an {@link Error.Level} object
	 * @param throwable an exception object
	 * @param completionHandler the completionhandler object invoked if failure occurs
	 *          
	 * エラーを送出し {@code completionHandler} を fail する.
	 * ユニット ID を指定しないので自ユニットの ID が使われる.
	 * @param <T> completionHandler オブジェクトの {@link AsyncResult#result()} の型
	 * @param vertx vertx オブジェクト
	 * @param category {@link Error.Category} オブジェクト
	 * @param extent {@link Error.Extent} オブジェクト
	 * @param level {@link Error.Level} オブジェクト
	 * @param throwable 例外オブジェクト
	 * @param completionHandler fail させる completionhandler オブジェクト
	 */
	public static <T> void reportAndFail(Vertx vertx, Error.Category category, Error.Extent extent, Error.Level level, Throwable throwable, Handler<AsyncResult<T>> completionHandler) {
		reportAndFail(vertx, category, extent, level, Error.messageFromThrowable(throwable), completionHandler);
	}
	/**
	 * Throw an error and set {@code completionHandler} to fail.
	 * Since the unit ID is not specified, use this unit's ID.
	 * @param <T> the type of the completionHandler object's {@link AsyncResult#result()}
	 * @param vertx a vertx object
	 * @param category an {@link Error.Category} object
	 * @param extent an {@link Error.Extent} object
	 * @param level an {@link Error.Level} object
	 * @param message an error message
	 * @param throwable an exception object
	 * @param completionHandler the completionhandler object invoked if failure occurs
	 *          
	 * エラーを送出し {@code completionHandler} を fail する.
	 * ユニット ID を指定しないので自ユニットの ID が使われる.
	 * @param <T> completionHandler オブジェクトの {@link AsyncResult#result()} の型
	 * @param vertx vertx オブジェクト
	 * @param category {@link Error.Category} オブジェクト
	 * @param extent {@link Error.Extent} オブジェクト
	 * @param level {@link Error.Level} オブジェクト
	 * @param message エラーメッセージ
	 * @param throwable 例外オブジェクト
	 * @param completionHandler fail させる completionhandler オブジェクト
	 */
	public static <T> void reportAndFail(Vertx vertx, Error.Category category, Error.Extent extent, Error.Level level, String message, Throwable throwable, Handler<AsyncResult<T>> completionHandler) {
		reportAndFail(vertx, category, extent, level, Error.messageFromThrowable(message, throwable), completionHandler);
	}

}
