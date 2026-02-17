package jp.co.sony.csl.dcoes.apis.main.error.action;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jp.co.sony.csl.dcoes.apis.common.ServiceAddress;

public class ResetLocal extends AbstractErrorAction {
	private static final Logger log = LoggerFactory.getLogger(ResetLocal.class);

	public ResetLocal(Vertx vertx, JsonObject policy, JsonArray logMessages) {
		super(vertx, policy, logMessages);
	}

	@Override
	protected void doAction(Handler<AsyncResult<Void>> completionHandler) {
		if (log.isInfoEnabled())
			log.info("publishing reset message to this unit ...");
		vertx_.eventBus().publish(ServiceAddress.resetLocal(), null);
		if (log.isInfoEnabled())
			log.info("done");
		completionHandler.handle(Future.succeededFuture());
	}

}
