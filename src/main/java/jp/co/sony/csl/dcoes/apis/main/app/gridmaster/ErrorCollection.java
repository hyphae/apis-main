package jp.co.sony.csl.dcoes.apis.main.app.gridmaster;

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
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

/**
 * A Verticle that manages errors.
 * Launched from {@link GridMaster}.
 * Only retains global errors (actually anything apart from local errors) in the error information broadcast from any location.
 * @author OES Project
 *          
 * エラーを管理する Verticle.
 * {@link GridMaster} から起動される.
 * 任意の場所からブロードキャストされたエラー情報のうちグローバルエラーのみ ( 実際にはローカルエラー以外 ) を保持する.
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
	 * A cache that retains global errors.
	 *          
	 * グローバルエラーを保持しておくキャッシュ.
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
	 * Address: {@link ServiceAddress.GridMaster#errorTesting()}
	 * Scope: global
	 * Function: Checks for global errors
	 * Message body: none
	 * Message header: none
	 * Response: Whether or not a global error has occurred [{@link Boolean}]
	 * @param completionHandler the completion handler
	 *          
	 * {@link io.vertx.core.eventbus.EventBus} サービス起動.
	 * アドレス : {@link ServiceAddress.GridMaster#errorTesting()}
	 * 範囲 : グローバル
	 * 処理 : グローバルエラーの有無を確認する.
	 * メッセージボディ : なし
	 * メッセージヘッダ : なし
	 * レスポンス : グローバルエラーの有無 [{@link Boolean}]
	 * @param completionHandler the completion handler
	 */
	private void startErrorTestingService_(Handler<AsyncResult<Void>> completionHandler) {
		vertx.eventBus().<Void>consumer(ServiceAddress.GridMaster.errorTesting(), req -> {
			req.reply(Boolean.valueOf(hasErrors()));
		}).completionHandler(completionHandler);
	}

	/**
	 * Launch the {@link io.vertx.core.eventbus.EventBus} service.
	 * Address: {@link ServiceAddress#error()}
	 * Scope: global
	 * Function: Receive errors.
	 *           Only retain global errors.
	 * Message body: Error information [{@link JsonObject}]
	 * Message header: none
	 * Response: none
	 * @param completionHandler the completion handler
	 *          
	 * {@link io.vertx.core.eventbus.EventBus} サービス起動.
	 * アドレス : {@link ServiceAddress#error()}
	 * 範囲 : グローバル
	 * 処理 : エラーを受信する.
	 * 　　   グローバルエラーのみ保持する.
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
		if (Error.Extent.LOCAL != Error.extent(error)) {
			// Handle targets other than LOCAL → Includes not only GLOBAL but also UNKNOWN errors
			// LOCAL 以外を対象とする → GLOBAL だけでなく UNKNOWN も受けちゃう
			if (PolicyKeeping.isMember(Error.unitId(error))) {
				doCache_(error);
				doWriteLog_(error);
			} else {
				// Pass through errors from units other than those mentioned in POLICY
				// POLICY に記載されている以外のユニットからのものはスルー
				ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, "global error received from illegal unit : " + Error.unitId(error) + "; error : " + Error.logMessage(error));
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
	 * Find out whether or not a global error has occurred.
	 * Judged to be true until POLICY.gridMaster.errorSustainingMsec has elapsed since the end of the most recent error processing.
	 * @return true if there is an error.
	 *         Even if there is no error, return true if the most recent error processing ended within the last POLICY.gridMaster.errorSustainingMsec.
	 *         Otherwise return false.
	 *          
	 * グローバルエラーの有無を取得する.
	 * 直近のエラー処理終了から POLICY.gridMaster.errorSustainingMsec 経つまでは有りと判定する.
	 * @return エラーがあれば true.
	 *         エラーがなくても直近のエラー処理終了から POLICY.gridMaster.errorSustainingMsec 以内であれば true.
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
				long errorSustainingMsec = PolicyKeeping.cache().getLong(DEFAULT_ERROR_SUSTAINING_MSEC, "gridMaster", "errorSustainingMsec");
				if (System.currentTimeMillis() < errorHandledMillis_ + errorSustainingMsec) {
					// If POLICY.gridMaster.errorSustainingMsec (default: DEFAULT_ERROR_SUSTAINING_MSEC) has not yet elapsed since the time at which this processing ended
					// その処理が終了した時刻からまだ POLICY.gridMaster.errorSustainingMsec ( デフォルト DEFAULT_ERROR_SUSTAINING_MSEC ) 経過していなければ
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
	public static synchronized void errorHandled() {
		if (hasErrors_) {
			hasErrors_ = false;
			errorHandledMillis_ = System.currentTimeMillis();
		}
	}

}
