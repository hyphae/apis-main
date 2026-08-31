package jp.co.sony.csl.dcoes.apis.main.app.gridmaster;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Promise;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.ServiceAddress;
import jp.co.sony.csl.dcoes.apis.main.app.gridmaster.main_loop.DealExecution;
import jp.co.sony.csl.dcoes.apis.main.app.gridmaster.main_loop.GlobalDataCalculation;
import jp.co.sony.csl.dcoes.apis.main.evaluation.safety.GlobalSafetyEvaluation;
import jp.co.sony.csl.dcoes.apis.main.util.ApisConfig;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

public class GridMaster extends AbstractVerticle {
	private static final Logger log = LoggerFactory.getLogger(GridMaster.class);

	@Override
	public void start(Promise<Void> startPromise) throws Exception {
		startGridMasterUndeploymentService_(resGridMasterUndeployment -> {
			if (resGridMasterUndeployment.succeeded()) {
				vertx.deployVerticle(new Helo(), resHelo -> {
					if (resHelo.succeeded()) {
						vertx.deployVerticle(new ErrorCollection(), resErrorCollection -> {
							if (resErrorCollection.succeeded()) {
								vertx.deployVerticle(new DataCollection(), resDataCollection -> {
									if (resDataCollection.succeeded()) {
										vertx.deployVerticle(new DataResponding(), resDataResponding -> {
											if (resDataResponding.succeeded()) {
												vertx.deployVerticle(new MainLoop(), resMainLoop -> {
													if (resMainLoop.succeeded()) {
														if (log.isTraceEnabled())
															log.trace("started : " + deploymentID());
														startPromise.complete();
													} else {
														startPromise.fail(resMainLoop.cause());
													}
												});
											} else {
												startPromise.fail(resDataResponding.cause());
											}
										});
									} else {
										startPromise.fail(resDataCollection.cause());
									}
								});
							} else {
								startPromise.fail(resErrorCollection.cause());
							}
						});
					} else {
						startPromise.fail(resHelo.cause());
					}
				});
			} else {
				startPromise.fail(resGridMasterUndeployment.cause());
			}
		});
	}

	@Override
	public void stop() throws Exception {
		DealExecution.unitDataCache.reset();
		DataCollection.cache.reset();
		ErrorCollection.cache.reset();
		GlobalDataCalculation.cache.reset();
		GlobalSafetyEvaluation.errors.reset();
		if (log.isTraceEnabled())
			log.trace("stopped : " + deploymentID());
	}

	private void startGridMasterUndeploymentService_(Handler<AsyncResult<Void>> onComplete) {
		vertx.eventBus().<Void>localConsumer(ServiceAddress.GridMaster.undeploymentLocal(), req -> {
			vertx.undeploy(deploymentID(), res -> {
				if (res.succeeded()) {
					req.reply(ApisConfig.unitId());
				} else {
					ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR,
							res.cause(), req);
				}
			});
		}).completionHandler(onComplete);
	}

}
