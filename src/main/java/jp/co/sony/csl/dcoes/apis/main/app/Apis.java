package jp.co.sony.csl.dcoes.apis.main.app;

import io.vertx.core.AbstractVerticle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vertx.core.Promise;
import jp.co.sony.csl.dcoes.apis.main.app.controller.Controller;
import jp.co.sony.csl.dcoes.apis.main.app.mediator.Mediator;
import jp.co.sony.csl.dcoes.apis.main.app.user.User;
import jp.co.sony.csl.dcoes.apis.main.util.ApisConfig;

public class Apis extends AbstractVerticle {
	private static final Logger LOGGER = LoggerFactory.getLogger(Apis.class);

	@Override
	public void start(Promise<Void> startPromise) throws Exception {
		vertx.deployVerticle(new Helo(), resHelo -> {
			if (resHelo.succeeded()) {
				vertx.deployVerticle(new HwConfigKeeping(), resHwConfigKeeping -> {
					if (resHwConfigKeeping.succeeded()) {
						vertx.deployVerticle(new PolicyKeeping(), resPolicyKeeping -> {
							if (resPolicyKeeping.succeeded()) {
								vertx.deployVerticle(new StateHandling(), resStateHandling -> {
									if (resStateHandling.succeeded()) {
										vertx.deployVerticle(new Controller(), resController -> {
											if (resController.succeeded()) {
												vertx.deployVerticle(new Mediator(), resMediator -> {
													if (resMediator.succeeded()) {
														vertx.deployVerticle(new User(), resUser -> {
															if (resUser.succeeded()) {
																logSystemInfo();
																StateHandling.setStarted();
																LOGGER.trace("started : {}", deploymentID());
																startPromise.complete();
															} else {
																startPromise.fail(resUser.cause());
															}
														});
													} else {
														startPromise.fail(resMediator.cause());
													}
												});
											} else {
												startPromise.fail(resController.cause());
											}
										});
									} else {
										startPromise.fail(resStateHandling.cause());
									}
								});
							} else {
								startPromise.fail(resPolicyKeeping.cause());
							}
						});
					} else {
						startPromise.fail(resHwConfigKeeping.cause());
					}
				});
			} else {
				startPromise.fail(resHelo.cause());
			}
		});
	}

	private void logSystemInfo() {
		LOGGER.info("unitId       : {}", ApisConfig.unitId());
		LOGGER.info("unitName     : {}", ApisConfig.unitName());
		LOGGER.info("serialNumber : {}", ApisConfig.serialNumber());
		LOGGER.info("systemType   : {}", ApisConfig.systemType());
	}

	@Override
	public void stop() throws Exception {
		StateHandling.setStopping();
		LOGGER.trace("stopped : {}", deploymentID());
	}

}
