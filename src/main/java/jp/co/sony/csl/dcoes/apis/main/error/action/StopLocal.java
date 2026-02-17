package jp.co.sony.csl.dcoes.apis.main.error.action;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.ServiceAddress;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.ReplyFailureUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

public class StopLocal extends AbstractErrorAction {
	private static final Logger log = LoggerFactory.getLogger(StopLocal.class);

	public StopLocal(Vertx vertx, JsonObject policy, JsonArray logMessages) {
		super(vertx, policy, logMessages);
	}

	@Override
	protected void doAction(Handler<AsyncResult<Void>> completionHandler) {
		if (log.isInfoEnabled())
			log.info("sending wait message to this unit ...");
		vertx_.eventBus().request(ServiceAddress.Controller.stopLocal(), null, repStopLocal -> {
			if (repStopLocal.succeeded()) {
				if (log.isInfoEnabled())
					log.info("done");
				completionHandler.handle(Future.succeededFuture());
			} else {
				if (log.isWarnEnabled())
					log.warn("... failed");
				if (ReplyFailureUtil.isRecipientFailure(repStopLocal)) {
					completionHandler.handle(Future.failedFuture(repStopLocal.cause()));
				} else {
					ErrorUtil.reportAndFail(vertx_, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR,
							"Communication failed on EventBus", repStopLocal.cause(), completionHandler);
				}
			}
		});
	}

}
