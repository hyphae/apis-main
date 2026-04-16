package jp.co.sony.csl.dcoes.apis.main.app.user;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

/**
 * User service object Verticle.
 * Launched from the {@link jp.co.sony.csl.dcoes.apis.main.app.Apis} Verticle.
 * Launches the following Verticles.
 * - {@link ErrorCollection}: A Verticle that manages errors
 * - {@link ErrorHandling}: A Verticle that handles errors
 * - {@link ScenarioKeeping}: A Verticle that manages a SCENARIO
 * - {@link HouseKeeping}: A Verticle that monitors the status of this unit and issues requests when necessary
 * - {@link MediatorRequestHandling}: A Verticle that handles requests from other units
 * - {@link MediatorAcceptsHandling}: A Verticle that processes "accept" responses returned from other units following a request sent from this unit
 * @author OES Project
 *          
 * User サービスの親玉 Verticle.
 * {@link jp.co.sony.csl.dcoes.apis.main.app.Apis} Verticle から起動される.
 * 以下の Verticle を起動する.
 * - {@link ErrorCollection} : エラーを管理する Verticle
 * - {@link ErrorHandling} : エラーに対応する Verticle
 * - {@link ScenarioKeeping} : SCENARIO を管理する Verticle
 * - {@link HouseKeeping} : 自ユニットの状態を監視し必要に応じてリクエストを発する Verticle
 * - {@link MediatorRequestHandling} : 他ユニットからのリクエストを処理する Verticle
 * - {@link MediatorAcceptsHandling} : 自ユニットからのリクエストに対し他ユニットから返されるアクセプト群を処理する Verticle
 * @author OES Project
 */
public class User extends AbstractVerticle {
	private static final Logger log = LoggerFactory.getLogger(User.class);

	/**
	 * Called at startup.
	 * Launches the following Verticles.
	 * - {@link ErrorCollection}: A Verticle that manages errors
	 * - {@link ErrorHandling}: A Verticle that handles errors
	 * - {@link ScenarioKeeping}: A Verticle that manages a SCENARIO
	 * - {@link HouseKeeping}: A Verticle that monitors the status of this unit and issues requests when necessary
	 * - {@link MediatorRequestHandling}: A Verticle that handles requests from other units
	 * - {@link MediatorAcceptsHandling}: A Verticle that processes "accept" responses returned from other units following a request sent from this unit
	 * @param startFuture {@inheritDoc}
	 * @throws Exception {@inheritDoc}
	 *          
	 * 起動時に呼び出される.
	 * 以下の Verticle を起動する.
	 * - {@link ErrorCollection} : エラーを管理する Verticle
	 * - {@link ErrorHandling} : エラーに対応する Verticle
	 * - {@link ScenarioKeeping} : SCENARIO を管理する Verticle
	 * - {@link HouseKeeping} : 自ユニットの状態を監視し必要に応じてリクエストを発する Verticle
	 * - {@link MediatorRequestHandling} : 他ユニットからのリクエストを処理する Verticle
	 * - {@link MediatorAcceptsHandling} : 自ユニットからのリクエストに対し他ユニットから返されるアクセプト群を処理する Verticle
	 * @param startFuture {@inheritDoc}
	 * @throws Exception {@inheritDoc}
	 */
	@Override public void start(Future<Void> startFuture) throws Exception {
		vertx.deployVerticle(new ErrorCollection(), resErrorCollection -> {
			if (resErrorCollection.succeeded()) {
				vertx.deployVerticle(new ErrorHandling(), resErrorHandling -> {
					if (resErrorHandling.succeeded()) {
						vertx.deployVerticle(new ScenarioKeeping(), resScenarioKeeping -> {
							if (resScenarioKeeping.succeeded()) {
								vertx.deployVerticle(new HouseKeeping(), resHouseKeeping -> {
									if (resHouseKeeping.succeeded()) {
										vertx.deployVerticle(new MediatorRequestHandling(), resMediatorRequestHandling -> {
											if (resMediatorRequestHandling.succeeded()) {
												vertx.deployVerticle(new MediatorAcceptsHandling(), resMediatorAcceptsHandling -> {
													if (resMediatorAcceptsHandling.succeeded()) {
														if (log.isTraceEnabled()) log.trace("started : " + deploymentID());
														startFuture.complete();
													} else {
														startFuture.fail(resMediatorAcceptsHandling.cause());
													}
												});
											} else {
												startFuture.fail(resMediatorRequestHandling.cause());
											}
										});
									} else {
										startFuture.fail(resHouseKeeping.cause());
									}
								});
							} else {
								startFuture.fail(resScenarioKeeping.cause());
							}
						});
					} else {
						startFuture.fail(resErrorHandling.cause());
					}
				});
			} else {
				startFuture.fail(resErrorCollection.cause());
			}
		});
	}

	/**
	 * Called when stopped.
	 * @throws Exception {@inheritDoc}
	 *          
	 * 停止時に呼び出される.
	 * @throws Exception {@inheritDoc}
	 */
	@Override public void stop() throws Exception {
		if (log.isTraceEnabled()) log.trace("stopped : " + deploymentID());
	}

}
