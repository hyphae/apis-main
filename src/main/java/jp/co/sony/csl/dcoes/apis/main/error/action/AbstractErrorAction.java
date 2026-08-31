package jp.co.sony.csl.dcoes.apis.main.error.action;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public abstract class AbstractErrorAction {

	protected final Vertx vertx_;
	protected final JsonObject policy_;
	protected final JsonArray logMessages_;

	public AbstractErrorAction(Vertx vertx, JsonObject policy, JsonArray logMessages) {
		vertx_ = vertx;
		policy_ = policy;
		logMessages_ = logMessages;
	}

	public void action(Handler<AsyncResult<Void>> completionHandler) {
		doAction(completionHandler);
	}

	protected abstract void doAction(Handler<AsyncResult<Void>> completionHandler);

}
