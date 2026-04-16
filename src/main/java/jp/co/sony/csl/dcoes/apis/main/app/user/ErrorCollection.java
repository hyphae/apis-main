package jp.co.sony.csl.dcoes.apis.main.app.user;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.ServiceAddress;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectWrapper;
import jp.co.sony.csl.dcoes.apis.main.app.PolicyKeeping;
import jp.co.sony.csl.dcoes.apis.main.util.ApisConfig;

/**
 * A Verticle that manages errors.
 * Launched from the {@link User} Verticle.
 * Of the error information broadcast from any location, only local errors from this unit are retained.
 * @author OES Project
 *          
 * エラーを管理する Verticle.
 * {@link User} Verticle から起動される.
 * 任意の場所からブロードキャストされたエラー情報のうち自ユニットのローカルエラーのみを保持する.
 * @author OES Project
 */
public class ErrorCollection extends AbstractVerticle {
	private static final Logger log = LoggerFactory.getLogger(ErrorCollection.class);

	/**
	 * The default value [ms] of the fixed time for which error states are maintained following the completion of error processing.
	 * Value: {@value}.
	 *          
	 * エラー処理完了後引き続きエラー状態を一定時間維持する時間のデフォルト値 [ms].
	 * 値は {@value}.
	 */
	private static final Long DEFAULT_ERROR_SUSTAINING_MSEC = 30000L;

	/**
	 * A cache that retains local errors.
	 *          
	 * ローカルエラーを保持しておくキャッシュ.
	 */
	public static final JsonObjectWrapper cache = new JsonObjectWrapper();

	private static boolean hasErrors_ = false;
	private static long errorHandledMillis_ = 0;

	/**
	 * Called at startup.
	 * Launches the {@link io.vertx.core.eventbus.EventBus} service.
	 * @param startFuture {@inheritDoc}
	 * @throws Exception {@inheritDoc}
	 *          
	 * 起動時に呼び出される.
	 * {@link io.vertx.core.eventbus.EventBus} サービスを起動する.
	 * @param startFuture {@inheritDoc}
	 * @throws Exception {@inheritDoc}
	 */
	@Override public void start(Future<Void> startFuture) throws Exception {
		startErrorTestingService_(resErrorTesting -> {
			if (resErrorTesting.succeeded()) {
				startErrorCollectingService_(resErrorCollecting -> {
					if (resErrorCollecting.succeeded()) {
						if (log.isTraceEnabled()) log.trace("started : " + deploymentID());
						startFuture.complete();
					} else {
						startFuture.fail(resErrorCollecting.cause());
					}
				});
			} else {
				startFuture.fail(resErrorTesting.cause());
			}
		});
	}

	/**
	 * Called when stopped.
	 * @throws Exception {@inheritDoc}
	 *          
	 * 停止時に呼び出される.
	 * @throws Exception {@inheritDoc}
	 */
	@Override public void stop() throws Exception {
		if (log.isTraceEnabled()) log.trace("stopped : " + deploymentID());
	}

	////

	/**
	 * Launch the {@link io.vertx.core.eventbus.EventBus} service.
	 * Address: {@link ServiceAddress.User#errorTesting(String)}
	 * Scope: global
	 * Function: Check for local errors.
	 * Message body: none
	 * Message header: none
	 * Response: whether or not a local error has occurred [{@link Boolean}]
	 * @param completionHandler the completion handler
	 *          
	 * {@link io.vertx.core.eventbus.EventBus} サービス起動.
	 * アドレス : {@link ServiceAddress.User#errorTesting(String)}
	 * 範囲 : グローバル
	 * 処理 : ローカルエラーの有無を確認する.
	 * メッセージボディ : なし
	 * メッセージヘッダ : なし
	 * レスポンス : ローカルエラーの有無 [{@link Boolean}]
	 * @param completionHandler the completion handler
	 */
	private void startErrorTestingService_(Handler<AsyncResult<Void>> completionHandler) {
		vertx.eventBus().<Void>consumer(ServiceAddress.User.errorTesting(ApisConfig.unitId()), req -> {
			req.reply(Boolean.valueOf(hasErrors()));
		}).completionHandler(completionHandler);
	}

	/**
	 * Launch the {@link io.vertx.core.eventbus.EventBus} service.
	 * Address: {@link ServiceAddress#error()}
	 * Scope: global
	 * Function: Receive errors.
	 *           Only retain local errors of this unit.
	 * Message body: Error information [{@link JsonObject}]
	 * Message header: none
	 * Response: none
	 * @param completionHandler the completion handler
	 *          
	 * {@link io.vertx.core.eventbus.EventBus} サービス起動.
	 * アドレス : {@link ServiceAddress#error()}
	 * 範囲 : グローバル
	 * 処理 : エラーを受信する.
	 * 　　   自ユニットのローカルエラーのみ保持する.
	 * メッセージボディ : エラー情報 [{@link JsonObject}]
	 * メッセージヘッダ : なし
	 * レスポンス : なし
	 * @param completionHandler the completion handler
	 */
	private void startErrorCollectingService_(Handler<AsyncResult<Void>> completionHandler) {
		vertx.eventBus().<JsonObject>consumer(ServiceAddress.error(), req -> {
			handleError_(req);
		}).completionHandler(completionHandler);
	}

	////

	private void handleError_(Message<JsonObject> req) {
		JsonObject error = req.body();
		if (Error.Extent.LOCAL == Error.extent(error)) {
			// Only handle LOCAL errors
			// LOCAL のみを対象とする
			if (ApisConfig.unitId().equals(Error.unitId(error))) {
				doCache_(error);
				doWriteLog_(error);
			}
		}
	}

	////

	private void doCache_(JsonObject error) {
		if (Error.Level.WARN != Error.level(error)) {
			// Targets other than WARN → Treat as ERROR FATAL UNKNOWN
			// WARN 以外を対象とする → ERROR FATAL UNKNOWN が対象
			errorReceived_();
			cache.add(error, Error.category(error).name(), Error.level(error).name());
		}
	}

	private void doWriteLog_(JsonObject error) {
		switch (Error.level(error)) {
		case WARN:
			if (log.isWarnEnabled()) log.warn(Error.logMessage(error));
			break;
		case ERROR:
			log.error(Error.logMessage(error));
			break;
		case FATAL:
			log.fatal(Error.logMessage(error));
			break;
		default:
			log.fatal(Error.logMessage(error));
			break;
		}
	}

	////

	/**
	 * Find out whether or not a local error has occurred.
	 * Judged to be true until POLICY.user.errorSustainingMsec has elapsed since the end of the most recent error processing.
	 * @return true if there is an error.
	 *         Even if there is no error, return true if the most recent error processing ended within the last POLICY.user.errorSustainingMsec.
	 *         Otherwise return false.
	 *          
	 * ローカルエラーの有無を取得する.
	 * 直近のエラー処理終了から POLICY.user.errorSustainingMsec 経つまでは有りと判定する.
	 * @return エラーがあれば true.
	 *         エラーがなくても直近のエラー処理終了から POLICY.user.errorSustainingMsec 以内であれば true.
	 *         それ以外は false.
	 */
	public static boolean hasErrors() {
		if (hasErrors_) {
			return true;
		} else {
			// Even if there are no errors at present
			// 現時点でエラーがなくても
			if (0 < errorHandledMillis_) {
				// Error handling was performed in the past
				// 過去にエラー処理が実行され
				long errorSustainingMsec = PolicyKeeping.cache().getLong(DEFAULT_ERROR_SUSTAINING_MSEC, "user", "errorSustainingMsec");
				if (System.currentTimeMillis() < errorHandledMillis_ + errorSustainingMsec) {
					// If POLICY.user.errorSustainingMsec (default: DEFAULT_ERROR_SUSTAINING_MSEC) has not yet elapsed since the time at which this processing ended
					// その処理が終了した時刻からまだ POLICY.user.errorSustainingMsec ( デフォルト DEFAULT_ERROR_SUSTAINING_MSEC ) 経過していなければ
					// Respond that an error is judged to have occurred
					// エラーあり判定を返す
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
	/**
	 * Notify the completion of error handling.
	 * Record the end time of the most recent error handling.
	 *          
	 * エラー処理が完了したことを通知する.
	 * 直近のエラー処理終了時刻を記録しておく.
	 */
	static synchronized void errorHandled_() {
		if (hasErrors_) {
			hasErrors_ = false;
			errorHandledMillis_ = System.currentTimeMillis();
		}
	}

}
