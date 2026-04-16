package jp.co.sony.csl.dcoes.apis.main.error.action;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.ServiceAddress;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.ReplyFailureUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ApisConfig;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

/**
 * An actual class for error handling.
 * If GridMaster is running in this unit, stop it.
 * @author OES Project
 *          
 * エラー処理の実クラス.
 * 自ユニットに GridMaster が動いていたら止める.
 * @author OES Project
 */
public class DeactivateGridMaster extends AbstractErrorAction {
	private static final Logger log = LoggerFactory.getLogger(DeactivateGridMaster.class);

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
	public DeactivateGridMaster(Vertx vertx, JsonObject policy, JsonArray logMessages) {
		super(vertx, policy, logMessages);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override protected void doAction(Handler<AsyncResult<Void>> completionHandler) {
		if (log.isInfoEnabled()) log.info("deactivating GridMaster ...");
		vertx_.eventBus().send(ServiceAddress.Mediator.gridMasterDeactivation(ApisConfig.unitId()), null, repGridMasterDeactivation -> {
			if (repGridMasterDeactivation.succeeded()) {
				if (log.isInfoEnabled()) log.info("done");
				completionHandler.handle(Future.succeededFuture());
			} else {
				if (log.isWarnEnabled()) log.warn("... failed");
				if (ReplyFailureUtil.isRecipientFailure(repGridMasterDeactivation)) {
					completionHandler.handle(Future.failedFuture(repGridMasterDeactivation.cause()));
				} else {
					ErrorUtil.reportAndFail(vertx_, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR, "Communication failed on EventBus", repGridMasterDeactivation.cause(), completionHandler);
				}
			}
		});
	}

}
