package jp.co.sony.csl.dcoes.apis.main.app;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import jp.co.sony.csl.dcoes.apis.main.app.controller.Controller;
import jp.co.sony.csl.dcoes.apis.main.app.mediator.Mediator;
import jp.co.sony.csl.dcoes.apis.main.app.user.User;
import jp.co.sony.csl.dcoes.apis.main.util.ApisConfig;

/**
 * The APIS system object Verticle.
 * Launched from the {@link jp.co.sony.csl.dcoes.apis.main.util.Starter Apis} Verticle.
 * Launches the following Verticles.
 * - {@link Helo}: A Verticle that checks if there is a unit with the same ID in the cluster
 * - {@link HwConfigKeeping}: A Verticle that manages HWCONFIG
 * - {@link PolicyKeeping}: A Verticle that manages POLICY
 * - {@link StateHandling}: A Verticle that manages various operating states
 * - {@link Controller}: Controller service object Verticle
 * - {@link Mediator}: Mediator service object Verticle
 * - {@link User}: User service object Verticle
 * @author OES Project
 *          
 * APIS システムの親玉 Verticle.
 * {@link jp.co.sony.csl.dcoes.apis.main.util.Starter} Verticle から起動される.
 * 以下の Verticle を起動する.
 * - {@link Helo} : クラスタ内に同一 ID のユニットが存在しないかチェックする Verticle
 * - {@link HwConfigKeeping} : HWCONFIG を管理する Verticle
 * - {@link PolicyKeeping} : POLICY を管理する Verticle
 * - {@link StateHandling} : 各種動作状態を管理する Verticle
 * - {@link Controller} : Controller サービスの親玉 Verticle
 * - {@link Mediator} : Mediator サービスの親玉 Verticle
 * - {@link User} : User サービスの親玉 Verticle
 * @author OES Project
 */
public class Apis extends AbstractVerticle {
	private static final Logger LOGGER = LoggerFactory.getLogger(Apis.class);

	/**
	 * Called at startup.
	 * Launches the following Verticles.
	 * - {@link Helo}: A Verticle that checks if there is a unit with the same ID in the cluster
	 * - {@link HwConfigKeeping}: A Verticle that manages HWCONFIG
	 * - {@link PolicyKeeping}: A Verticle that manages POLICY
	 * - {@link StateHandling}: A Verticle that manages various operating states
	 * - {@link Controller}: Controller service object Verticle
	 * - {@link Mediator}: Mediator service object Verticle
	 * - {@link User}: User service object Verticle
	 * Changes the operating state to "running".
	 * @param startFuture {@inheritDoc}
	 * @throws Exception {@inheritDoc}
	 *          
	 * 起動時に呼び出される.
	 * 以下の Verticle を起動する.
	 * - {@link Helo} : クラスタ内に同一 ID のユニットが存在しないかチェックする Verticle
	 * - {@link HwConfigKeeping} : HWCONFIG を管理する Verticle
	 * - {@link PolicyKeeping} : POLICY を管理する Verticle
	 * - {@link StateHandling} : 各種動作状態を管理する Verticle
	 * - {@link Controller} : Controller サービスの親玉 Verticle
	 * - {@link Mediator} : Mediator サービスの親玉 Verticle
	 * - {@link User} : User サービスの親玉 Verticle
	 * 動作状態を稼働中に変更する.
	 * @param startFuture {@inheritDoc}
	 * @throws Exception {@inheritDoc}
	 */
	@Override public void start(Future<Void> startFuture) throws Exception {
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
																if (LOGGER.isTraceEnabled()) LOGGER.trace("started : " + deploymentID());
																startFuture.complete();
															} else {
																startFuture.fail(resUser.cause());
															}
														});
													} else {
														startFuture.fail(resMediator.cause());
													}
												});
											} else {
												startFuture.fail(resController.cause());
											}
										});
									} else {
										startFuture.fail(resStateHandling.cause());
									}
								});
							} else {
								startFuture.fail(resPolicyKeeping.cause());
							}
						});
					} else {
						startFuture.fail(resHwConfigKeeping.cause());
					}
				});
			} else {
				startFuture.fail(resHelo.cause());
			}
		});
	}

	/**
	 * Logs system configuration information
	 */
	private void logSystemInfo() {
		if (LOGGER.isInfoEnabled()) {
			LOGGER.info("unitId       : " + ApisConfig.unitId());
			LOGGER.info("unitName     : " + ApisConfig.unitName());
			LOGGER.info("serialNumber : " + ApisConfig.serialNumber());
			LOGGER.info("systemType   : " + ApisConfig.systemType());
		}
	}

	/**
	 * Called when stopped.
	 * Changes the operating state to "stopped".
	 * @throws Exception {@inheritDoc}
	 *          
	 * 停止時に呼び出される.
	 * 動作状態を停止中に変更する.
	 * @throws Exception {@inheritDoc}
	 */
	@Override public void stop() throws Exception {
		StateHandling.setStopping();
		if (LOGGER.isTraceEnabled()) LOGGER.trace("stopped : " + deploymentID());
	}

}
