package jp.co.sony.csl.dcoes.apis.main.app.user;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class User extends AbstractVerticle {
	private static final Logger log = LoggerFactory.getLogger(User.class);

	@Override
	public void start(Promise<Void> startPromise) throws Exception {
		vertx.deployVerticle(new ErrorCollection(), resErrorCollection -> {
			if (resErrorCollection.succeeded()) {
				vertx.deployVerticle(new ErrorHandling(), resErrorHandling -> {
					if (resErrorHandling.succeeded()) {
						vertx.deployVerticle(new ScenarioKeeping(), resScenarioKeeping -> {
							if (resScenarioKeeping.succeeded()) {
								vertx.deployVerticle(new HouseKeeping(), resHouseKeeping -> {
									if (resHouseKeeping.succeeded()) {
										vertx.deployVerticle(new MediatorRequestHandling(),
												resMediatorRequestHandling -> {
													if (resMediatorRequestHandling.succeeded()) {
														vertx.deployVerticle(new MediatorAcceptsHandling(),
																resMediatorAcceptsHandling -> {
																	if (resMediatorAcceptsHandling.succeeded()) {
																		if (log.isTraceEnabled())
																			log.trace("started : " + deploymentID());
																		startPromise.complete();
																	} else {
																		startPromise.fail(
																				resMediatorAcceptsHandling.cause());
																	}
																});
													} else {
														startPromise.fail(resMediatorRequestHandling.cause());
													}
												});
									} else {
										startPromise.fail(resHouseKeeping.cause());
									}
								});
							} else {
								startPromise.fail(resScenarioKeeping.cause());
							}
						});
					} else {
						startPromise.fail(resErrorHandling.cause());
					}
				});
			} else {
				startPromise.fail(resErrorCollection.cause());
			}
		});
	}

	@Override
	public void stop() throws Exception {
		if (log.isTraceEnabled())
			log.trace("stopped : " + deploymentID());
	}

}
