package jp.co.sony.csl.dcoes.apis.main.error.handling;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jp.co.sony.csl.dcoes.apis.main.app.StateHandling;
import jp.co.sony.csl.dcoes.apis.main.error.action.AskAndWaitForStopDeals;
import jp.co.sony.csl.dcoes.apis.main.error.action.DeactivateGridMaster;
import jp.co.sony.csl.dcoes.apis.main.error.action.ShutdownLocal;
import jp.co.sony.csl.dcoes.apis.main.error.action.StopLocal;

public class LocalAnyFatalsHandling extends AbstractErrorsHandling {

	public LocalAnyFatalsHandling(Vertx vertx, JsonObject policy, JsonArray errors) {
		super(vertx, policy, errors);
	}

	@Override
	protected void doHandle(Handler<AsyncResult<Void>> completionHandler) {
		new AskAndWaitForStopDeals(vertx_, policy_, logMessages_).action(r -> {
			new StopLocal(vertx_, policy_, logMessages_).action(rr -> {
				StateHandling.setStopping();
				new DeactivateGridMaster(vertx_, policy_, logMessages_).action(rrr -> {
					new ShutdownLocal(vertx_, policy_, logMessages_).action(completionHandler);
				});
			});
		});
	}

}
