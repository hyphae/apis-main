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
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.main.app.PolicyKeeping;
import jp.co.sony.csl.dcoes.apis.main.error.handling.AbstractErrorsHandling;
import jp.co.sony.csl.dcoes.apis.main.error.handling.LocalAnyFatalsHandling;
import jp.co.sony.csl.dcoes.apis.main.error.handling.LocalFrameworkErrorsHandling;
import jp.co.sony.csl.dcoes.apis.main.error.handling.LocalHardwareErrorsHandling;
import jp.co.sony.csl.dcoes.apis.main.error.handling.LocalLogicErrorsHandling;
import jp.co.sony.csl.dcoes.apis.main.error.handling.LocalUserErrorsHandling;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

/**
 * A Verticle that handles errors.
 * Launched from the {@link User} Verticle.
 * Periodically check for errors.
 * If an error exists, create and run a suitable response process according to its type, scope and severity.
 * @author OES Project
 *          
 * エラーに対応する Verticle.
 * {@link User} Verticle から起動される.
 * 定期的にエラーの有無を確認する.
 * エラーが存在していたらその種類と範囲と深刻さごとに適切な対応処理を作成して実行させる.
 * @author OES Project
 */
public class ErrorHandling extends AbstractVerticle {
	private static final Logger log = LoggerFactory.getLogger(ErrorHandling.class);

	/**
	 * Default duration of the error processing cycle [ms].
	 * Value: {@value}.
	 *          
	 * エラー処理周期のデフォルト値 [ms].
	 * 値は {@value}.
	 */
	private static final Long DEFAULT_ERROR_HANDLING_PERIOD_MSEC = 1000L;

	private long errorHandlingTimerId_ = 0L;
	private boolean stopped_ = false;

	/**
	 * Called at startup.
	 * Start a timer to perform periodic error checking.
	 * @param startFuture {@inheritDoc}
	 * @throws Exception {@inheritDoc}
	 *          
	 * 起動時に呼び出される.
	 * 定期的にエラーをチェックし処理するタイマを起動する.
	 * @param startFuture {@inheritDoc}
	 * @throws Exception {@inheritDoc}
	 */
	@Override public void start(Future<Void> startFuture) throws Exception {
		errorHandlingTimerHandler_(0L);
		if (log.isTraceEnabled()) log.trace("started : " + deploymentID());
		startFuture.complete();
	}

	/**
	 * Called when stopped.
	 * Set a flag to stop the timer.
	 * @throws Exception {@inheritDoc}
	 *          
	 * 停止時に呼び出される.
	 * タイマを止めるためのフラグを立てる.
	 * @throws Exception {@inheritDoc}
	 */
	@Override public void stop() throws Exception {
		stopped_ = true;
		if (log.isTraceEnabled()) log.trace("stopped : " + deploymentID());
	}

	////

	/**
	 * Set up an error handling timer.
	 * The timeout duration is {@code POLICY.user.errorHandlingPeriodMsec} (default: {@link #DEFAULT_ERROR_HANDLING_PERIOD_MSEC}).
	 *          
	 * エラー処理タイマ設定.
	 * 待ち時間は {@code POLICY.user.errorHandlingPeriodMsec} ( デフォルト値 {@link #DEFAULT_ERROR_HANDLING_PERIOD_MSEC} ).
	 */
	private void setErrorHandlingTimer_() {
		Long delay = PolicyKeeping.cache().getLong(DEFAULT_ERROR_HANDLING_PERIOD_MSEC, "user", "errorHandlingPeriodMsec");
		setErrorHandlingTimer_(delay);
	}
	/**
	 * Set up an error handling timer.
	 * @param delay cycle duration [ms]
	 *          
	 * エラー処理タイマ設定.
	 * @param delay 周期 [ms]
	 */
	private void setErrorHandlingTimer_(long delay) {
		errorHandlingTimerId_ = vertx.setTimer(delay, this::errorHandlingTimerHandler_);
	}
	/**
	 * Error handling timer processing.
	 * @param timerId timer ID
	 *          
	 * エラー処理タイマ処理.
	 * @param timerId タイマ ID
	 */
	private void errorHandlingTimerHandler_(Long timerId) {
		if (stopped_) return;
		if (null == timerId || timerId.longValue() != errorHandlingTimerId_) {
			ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, "illegal timerId : " + timerId + ", errorHandlingTimerId_ : " + errorHandlingTimerId_);
			return;
		}
		// Fetch the POLICY (because it might change during processing)
		// POLICY を確保し ( 処理中に変更される可能性があるので )
		JsonObject policy = PolicyKeeping.cache().jsonObject();
		new HandleErrors_(policy).doLoop_(r -> {
			// When the processing is finished, mark it as finished
			// 処理が終わったら終わったマークをつける
			ErrorCollection.errorHandled_();
			setErrorHandlingTimer_();
		});
	}

	////

	/**
	 * Error handling class.
	 * Loops through the error types.
	 * @author OES Project
	 *          
	 * エラー処理クラス.
	 * エラーの種類をループする.
	 * @author OES Project
	 */
	private class HandleErrors_ {
		private JsonObject policy_;
		private List<Error.Category> categoriesForLoop_;
		/**
		 * Create an instance.
		 * @param policy a POLICY object
		 *          
		 * インスタンス生成.
		 * @param policy POLICY オブジェクト
		 */
		private HandleErrors_(JsonObject policy) {
			policy_ = policy;
			categoriesForLoop_ = new ArrayList<Error.Category>(Arrays.asList(Error.Category.values()));
		}
		private void doLoop_(Handler<AsyncResult<Void>> completionHandler) {
			if (categoriesForLoop_.isEmpty()) {
				completionHandler.handle(Future.succeededFuture());
			} else {
				Error.Category aCategory = categoriesForLoop_.remove(0);
				new HandleErrorsByCategory_(policy_, aCategory).doLoop_(r -> {
					doLoop_(completionHandler);
				});
			}
		}
	}
	/**
	 * Error handling class.
	 * Created by specifying the error type.
	 * Loop through the error severity.
	 * @author OES Project
	 * TODO: Why not make HandleErrorsByCategory_ an inner class of HandleErrors_ as in gridmaster.ErrorHandling?
	 *          
	 * エラー処理クラス.
	 * エラー種類を指定されて誕生する.
	 * エラーの深刻さをループする.
	 * @author OES Project
	 * TODO : gridmaster.ErrorHandling のように HandleErrorsByCategory_ を HandleErrors_ のインナークラスにしてしまってはどうでしょう
	 */
	private class HandleErrorsByCategory_ {
		private JsonObject policy_;
		private Error.Category category_;
		private List<Error.Level> levelsForLoop_;
		private HandleErrorsByCategory_(JsonObject policy, Error.Category category) {
			policy_ = policy;
			category_ = category;
			levelsForLoop_ = new ArrayList<Error.Level>(Arrays.asList(Error.Level.values()));
		}
		private void doLoop_(Handler<AsyncResult<Void>> completionHandler) {
			if (levelsForLoop_.isEmpty()) {
				completionHandler.handle(Future.succeededFuture());
			} else {
				Error.Level aLevel = levelsForLoop_.remove(0);
				JsonArray errors = ErrorCollection.cache.removeJsonArray(category_.name(), aLevel.name());
				if (errors != null && 0 < errors.size()) {
					if (log.isInfoEnabled()) log.info("[" + category_ + ':' + aLevel + "] : " + errors);
					AbstractErrorsHandling handler = null;
					switch (aLevel) {
					case WARN:
						// Warning level errors do nothing
						// 警告レベルは何もしない
						log.fatal("#### should never happen; category : " + category_ + ", level : " + aLevel);
						break;
					case ERROR:
						// Failure level errors are handled by creating a processing object according to the type of error
						// 障害レベルなら種類に応じて処理オブジェクトを作りお任せする
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
							log.fatal("#### should never happen; category : " + category_ + ", level : " + aLevel);
							break;
						}
						break;
					case FATAL:
					case UNKNOWN:
						// Fatal level errors or errors whose level is unknown for some reason are handled by running the FATAL (shutdown) process regardless of the type of error
						// 致命的レベルまたは何らかの理由で不明レベルなら種類にかかわらず FATAL な処理 ( シャットダウン ) を実行する
						handler = new LocalAnyFatalsHandling(vertx, policy_, errors);
						break;
					}
					if (handler != null) {
						handler.handle(resHandle -> {
							doLoop_(completionHandler);
						});
					} else {
						doLoop_(completionHandler);
					}
				} else {
					doLoop_(completionHandler);
				}
			}
		}
	}

}
