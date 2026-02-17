package jp.co.sony.csl.dcoes.apis.main.error.handling;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jp.co.sony.csl.dcoes.apis.main.error.action.Scram;

public class GlobalUserErrorsHandling extends AbstractErrorsHandling {

	public GlobalUserErrorsHandling(Vertx vertx, JsonObject policy, JsonArray errors) {
		super(vertx, policy, errors);
	}

	@Override
	protected void doHandle(Handler<AsyncResult<Void>> completionHandler) {
		new Scram(vertx_, policy_, logMessages_).action(completionHandler);
	}

}
