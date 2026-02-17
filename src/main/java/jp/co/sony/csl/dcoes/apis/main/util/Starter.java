package jp.co.sony.csl.dcoes.apis.main.util;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.ServiceAddress;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.AbstractStarter;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.ReplyFailureUtil;
import jp.co.sony.csl.dcoes.apis.main.app.Apis;
import jp.co.sony.csl.dcoes.apis.main.app.PolicyKeeping;
import jp.co.sony.csl.dcoes.apis.main.app.StateHandling;
import jp.co.sony.csl.dcoes.apis.main.error.action.AskAndWaitForStopDeals;
import jp.co.sony.csl.dcoes.apis.main.error.action.DeactivateGridMaster;
import jp.co.sony.csl.dcoes.apis.main.error.action.StopLocal;
import jp.co.sony.csl.dcoes.apis.main.factory.Factory;

public class Starter extends AbstractStarter {
	private static final Logger log = LoggerFactory.getLogger(Starter.class);

	private boolean shuttingDown_ = false;

	@Override
	protected void doStart(Handler<AsyncResult<Void>> completionHandler) {
		Factory.initialize(resInitGlobalFactory -> {
			if (resInitGlobalFactory.succeeded()) {
				startShutdownService_(resShutdown -> {
					if (resShutdown.succeeded()) {
						vertx.deployVerticle(new Apis(), resApis -> {
							if (resApis.succeeded()) {
								completionHandler.handle(Future.succeededFuture());
							} else {
								completionHandler.handle(Future.failedFuture(resApis.cause()));
							}
						});
					} else {
						completionHandler.handle(Future.failedFuture(resShutdown.cause()));
					}
				});
			} else {
				completionHandler.handle(Future.failedFuture(resInitGlobalFactory.cause()));
			}
		});
	}

	@Override
	protected void doShutdown(Handler<AsyncResult<Void>> completionHandler) {
		if (!shuttingDown_) {
			shuttingDown_ = true;
			JsonObject policy = PolicyKeeping.cache().jsonObject();
			JsonArray logMessages = new JsonArray().add(ErrorUtil.generateErrorObject(ApisConfig.unitId(),
					Error.Category.USER, Error.Extent.LOCAL, Error.Level.WARN, "shutting down"));
			new AskAndWaitForStopDeals(vertx, policy, logMessages).action(r -> {
				new StopLocal(vertx, policy, logMessages).action(rr -> {
					StateHandling.setStopping();
					new DeactivateGridMaster(vertx, policy, logMessages).action(rrr -> {
						completionHandler.handle(rrr);
					});
				});
			});
		} else {
			completionHandler.handle(Future.succeededFuture());
		}
	}

	@Override
	protected void handleUnhandledException(Throwable t) {
		log.error("Unhandled exception caught", t);
		ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, t);
	}

	private void startShutdownService_(Handler<AsyncResult<Void>> completionHandler) {
		vertx.eventBus().<Void>consumer(ServiceAddress.shutdown(ApisConfig.unitId()), req -> {
			vertx.eventBus().<String>request(ServiceAddress.shutdownLocal(), null, repShutdownLocal -> {
				if (repShutdownLocal.succeeded()) {
					req.reply(repShutdownLocal.result().body());
				} else {
					if (ReplyFailureUtil.isRecipientFailure(repShutdownLocal)) {
						req.fail(-1, repShutdownLocal.cause().getMessage());
					} else {
						ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR,
								"Communication failed on EventBus", repShutdownLocal.cause(), req);
					}
				}
			});
		}).completionHandler(completionHandler);
	}

}
