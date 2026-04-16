package jp.co.sony.csl.dcoes.apis.main.error.action;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Error handling abstract class.
 * @author OES Project
 *          
 * エラー処理の抽象クラス.
 * @author OES Project
 */
public abstract class AbstractErrorAction {

	protected final Vertx vertx_;
	protected final JsonObject policy_;
	protected final JsonArray logMessages_;

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
	public AbstractErrorAction(Vertx vertx, JsonObject policy, JsonArray logMessages) {
		vertx_ = vertx;
		policy_ = policy;
		logMessages_ = logMessages;
	}

	/**
	 * Error handling.
	 * The actual processing is implemented by the {@link #doAction (Handler)} of subclasses.
	 * @param completionHandler the completion handler
	 *          
	 * エラー処理.
	 * 実際の処理内容はサブクラスの {@link #doAction(Handler)} で実装する.
	 * @param completionHandler the completion handler
	 */
	public void action(Handler<AsyncResult<Void>> completionHandler) {
		doAction(completionHandler);
	}

	/**
	 * Implement error handling.
	 * @param completionHandler the completion handler
	 *          
	 * エラー処理の実装.
	 * @param completionHandler the completion handler
	 */
	protected abstract void doAction(Handler<AsyncResult<Void>> completionHandler);

}
