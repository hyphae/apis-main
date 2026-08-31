package jp.co.sony.csl.dcoes.apis.main.app.mediator;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.core.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.ServiceAddress;
import jp.co.sony.csl.dcoes.apis.main.app.PolicyKeeping;
import jp.co.sony.csl.dcoes.apis.main.util.ApisConfig;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

public class InternalRequestHandling extends AbstractVerticle {
	private static final Logger log = LoggerFactory.getLogger(InternalRequestHandling.class);

	@Override
	public void start(Promise<Void> startPromise) throws Exception {
		startInternalRequestHandlingService_(resInternalRequestHandling -> {
			if (resInternalRequestHandling.succeeded()) {
				if (log.isTraceEnabled())
					log.trace("started : " + deploymentID());
				startPromise.complete();
			} else {
				startPromise.fail(resInternalRequestHandling.cause());
			}
		});
	}

	@Override
	public void stop() throws Exception {
		if (log.isTraceEnabled())
			log.trace("stopped : " + deploymentID());
	}

	private void startInternalRequestHandlingService_(Handler<AsyncResult<Void>> onComplete) {
		vertx.eventBus().<JsonObject>localConsumer(ServiceAddress.Mediator.internalRequest(), req -> {
			JsonObject request = req.body();
			if (log.isDebugEnabled())
				log.debug("request received : " + request);
			if (request != null) {
				Integer dealAmountMinWh = PolicyKeeping.cache().getInteger(0, "mediator", "deal", "amountMinWh");
				Integer amountWh = request.getInteger("amountWh", 0);
				if (dealAmountMinWh < amountWh) {
					request.put("dealGridCurrentA",
							PolicyKeeping.cache().getFloat(0F, "mediator", "deal", "gridCurrentA"));
					request.put("unitId", ApisConfig.unitId());
					vertx.deployVerticle(new Negotiation(request), resDeployNegotiation -> {
						if (resDeployNegotiation.succeeded()) {
							req.reply(resDeployNegotiation.result());
						} else {
							ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL,
									Error.Level.FATAL, resDeployNegotiation.cause(), req);
						}
					});
				} else {
					String msg = "request amount : " + amountWh + " ; less than dealAmountMinWh : " + dealAmountMinWh;
					if (log.isDebugEnabled())
						log.debug(msg);
					req.fail(-1, msg);
				}
			} else {
				ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR,
						"request is null", req);
			}
		}).completionHandler(onComplete);
	}

}
