package jp.co.sony.csl.dcoes.apis.main.error.action;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import jp.co.sony.csl.dcoes.apis.common.ServiceAddress;

/**
 * An actual class for error handling.
 * Shut down all the units in a cluster.
 * Also shut down the apis-tools participating in the cluster (apis-web and apis-ccc).
 * @author OES Project
 *          
 * エラー処理の実クラス.
 * クラスタ内の全ユニットをシャットダウンする.
 * クラスタ参加型の apis-tools 達 ( apis-web および apis-ccc ) もシャットダウンする.
 * @author OES Project
 */
public class ShutdownAll extends AbstractErrorAction {
	private static final Logger log = LoggerFactory.getLogger(ShutdownAll.class);

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
	public ShutdownAll(Vertx vertx, JsonObject policy, JsonArray logMessages) {
		super(vertx, policy, logMessages);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override protected void doAction(Handler<AsyncResult<Void>> completionHandler) {
		if (log.isInfoEnabled()) log.info("publishing shutdown message to all units ...");
		vertx_.eventBus().publish(ServiceAddress.shutdownAll(), null);
		if (log.isInfoEnabled()) log.info("done");
		completionHandler.handle(Future.succeededFuture());
	}

}
