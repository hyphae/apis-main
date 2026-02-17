package jp.co.sony.csl.dcoes.apis.main.app.mediator;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Mediator extends AbstractVerticle {
	private static final Logger log = LoggerFactory.getLogger(Mediator.class);

	@Override
	public void start(Promise<Void> startPromise) throws Exception {
		vertx.deployVerticle(new Interlocking(), resInterlocking -> {
			if (resInterlocking.succeeded()) {
				vertx.deployVerticle(new GridMasterManagement(), resGridMasterManagement -> {
					if (resGridMasterManagement.succeeded()) {
						vertx.deployVerticle(new DealManagement(), resDealManagement -> {
							if (resDealManagement.succeeded()) {
								vertx.deployVerticle(new DealLogging(), resDealLogging -> {
									if (resDealLogging.succeeded()) {
										vertx.deployVerticle(new ExternalRequestHandling(),
												resExternalRequestHandling -> {
													if (resExternalRequestHandling.succeeded()) {
														vertx.deployVerticle(new InternalRequestHandling(),
																resInternalRequestHandling -> {
																	if (resInternalRequestHandling.succeeded()) {
																		if (log.isTraceEnabled())
																			log.trace("started : " + deploymentID());
																		startPromise.complete();
																	} else {
																		startPromise.fail(
																				resInternalRequestHandling.cause());
																	}
																});
													} else {
														startPromise.fail(resExternalRequestHandling.cause());
													}
												});
									} else {
										startPromise.fail(resDealLogging.cause());
									}
								});
							} else {
								startPromise.fail(resDealManagement.cause());
							}
						});
					} else {
						startPromise.fail(resGridMasterManagement.cause());
					}
				});
			} else {
				startPromise.fail(resInterlocking.cause());
			}
		});
	}

	@Override
	public void stop() throws Exception {
		if (log.isTraceEnabled())
			log.trace("stopped : " + deploymentID());
	}

}
