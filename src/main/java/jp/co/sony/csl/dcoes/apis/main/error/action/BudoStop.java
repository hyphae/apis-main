package jp.co.sony.csl.dcoes.apis.main.error.action;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.ServiceAddress;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.ReplyFailureUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

/**
 * An actual class for error handling.
 * Change the global interchange mode to "stop".
 * @author OES Project
 *          
 * エラー処理の実クラス.
 * グローバル融通モードを stop に変更する.
 * @author OES Project
 */
public class BudoStop extends AbstractErrorAction {
	private static final Logger log = LoggerFactory.getLogger(BudoStop.class);

	/**
	 * Create an instance.
	 * @param vertx a vertx object
	 * @param policy a POLICY object. To prevent changes from taking effect while running, a copy is passed at {@link jp.co.sony.csl.dcoes.apis.main.app.user.ErrorHandling} or {@link jp.co.sony.csl.dcoes.apis.main.app.gridmaster.main_loop.ErrorHandling}.
	 * @param logMessages a list of log messages recorded in error handling
	 *          
	 * インスタンスを生成する.
	 * @param vertx vertx オブジェクト
	 * @param policy POLICY オブジェクト. 処理中に変更されても影響しないように {@link jp.co.sony.csl.dcoes.apis.main.app.user.ErrorHandling} あるいは {@link jp.co.sony.csl.dcoes.apis.main.app.gridmaster.main_loop.ErrorHandling} でコピーしたものが渡される.
	 * @param logMessages エラー処理で記録するログメッセージのリスト
	 */
	public BudoStop(Vertx vertx, JsonObject policy, JsonArray logMessages) {
		super(vertx, policy, logMessages);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override protected void doAction(Handler<AsyncResult<Void>> completionHandler) {
		if (log.isInfoEnabled()) log.info("setting global operationMode : 'stop' ...");
		// Set the global interchange mode to "stop".
		// グローバル融通モードを "stop" に設定する
		DeliveryOptions options = new DeliveryOptions().addHeader("command", "set");
		vertx_.eventBus().send(ServiceAddress.operationMode(), "stop", options, repSetGlobalOperationMode -> {
			if (repSetGlobalOperationMode.succeeded()) {
				if (log.isInfoEnabled()) log.info("done");
				completionHandler.handle(Future.succeededFuture());
			} else {
				if (log.isWarnEnabled()) log.warn("... failed");
				if (ReplyFailureUtil.isRecipientFailure(repSetGlobalOperationMode)) {
					completionHandler.handle(Future.failedFuture(repSetGlobalOperationMode.cause()));
				} else {
					ErrorUtil.reportAndFail(vertx_, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR, "Communication failed on EventBus", repSetGlobalOperationMode.cause(), completionHandler);
				}
			}
		});
	}

}
