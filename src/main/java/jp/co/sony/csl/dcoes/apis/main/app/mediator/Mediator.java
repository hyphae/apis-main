package jp.co.sony.csl.dcoes.apis.main.app.mediator;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mediator sevice object Verticle.
 * Launched from the {@link jp.co.sony.csl.dcoes.apis.main.app.Apis} Verticle.
 * Launches the following Verticles.
 * - {@link Interlocking}: A Verticle that manages interlocks
 * - {@link GridMasterManagement}: A Verticle that manages a GridMaster
 * - {@link DealManagement}: A Verticle that manages interchange information
 * - {@link DealLogging}: A Verticle that records interchange information in the file system
 * - {@link ExternalRequestHandling}: A Verticle that handles requests from other units
 * - {@link InternalRequestHandling}: A Verticle that handles requests from its own unit
 * @author OES Project
 *          
 * Mediator サービスの親玉 Verticle.
 * {@link jp.co.sony.csl.dcoes.apis.main.app.Apis} Verticle から起動される.
 * 以下の Verticle を起動する.
 * - {@link Interlocking} : インタロックを管理する Verticle
 * - {@link GridMasterManagement} : GridMaster を管理する Verticle
 * - {@link DealManagement} : 融通情報を管理する Verticle
 * - {@link DealLogging} : 融通情報をファイルシステムに記録する Verticle
 * - {@link ExternalRequestHandling} : 他ユニットからのリクエストを処理する Verticle
 * - {@link InternalRequestHandling} : 自ユニットからのリクエストを処理する Verticle
 * @author OES Project
 */
public class Mediator extends AbstractVerticle {
	private static final Logger log = LoggerFactory.getLogger(Mediator.class);

	/**
	 * Called at startup.
	 * Launches the following Verticles.
	 * - {@link Interlocking}: A Verticle that manages interlocks
	 * - {@link GridMasterManagement}: A Verticle that manages a GridMaster
	 * - {@link DealManagement}: A Verticle that manages interchange information
	 * - {@link DealLogging}: A Verticle that records interchange information in the file system
	 * - {@link ExternalRequestHandling}: A Verticle that handles requests from other units
	 * - {@link InternalRequestHandling}: A Verticle that handles requests from its own unit
	 * @param startFuture {@inheritDoc}
	 * @throws Exception {@inheritDoc}
	 *          
	 * 起動時に呼び出される.
	 * 以下の Verticle を起動する.
	 * - {@link Interlocking} : インタロックを管理する Verticle
	 * - {@link GridMasterManagement} : GridMaster を管理する Verticle
	 * - {@link DealManagement} : 融通情報を管理する Verticle
	 * - {@link DealLogging} : 融通情報をファイルシステムに記録する Verticle
	 * - {@link ExternalRequestHandling} : 他ユニットからのリクエストを処理する Verticle
	 * - {@link InternalRequestHandling} : 自ユニットからのリクエストを処理する Verticle
	 * @param startFuture {@inheritDoc}
	 * @throws Exception {@inheritDoc}
	 */
	@Override public void start(Promise<Void> startPromise) throws Exception {
		vertx.deployVerticle(new Interlocking(), resInterlocking -> {
			if (resInterlocking.succeeded()) {
				vertx.deployVerticle(new GridMasterManagement(), resGridMasterManagement -> {
					if (resGridMasterManagement.succeeded()) {
						vertx.deployVerticle(new DealManagement(), resDealManagement -> {
							if (resDealManagement.succeeded()) {
								vertx.deployVerticle(new DealLogging(), resDealLogging -> {
									if (resDealLogging.succeeded()) {
										vertx.deployVerticle(new ExternalRequestHandling(), resExternalRequestHandling -> {
											if (resExternalRequestHandling.succeeded()) {
												vertx.deployVerticle(new InternalRequestHandling(), resInternalRequestHandling -> {
													if (resInternalRequestHandling.succeeded()) {
														if (log.isTraceEnabled()) log.trace("started : " + deploymentID());
														startPromise.complete();
													} else {
														startPromise.fail(resInternalRequestHandling.cause());
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

	/**
	 * Called when stopped.
	 * @throws Exception {@inheritDoc}
	 *          
	 * 停止時に呼び出される.
	 * @throws Exception {@inheritDoc}
	 */
	@Override public void stop(Promise<Void> stopPromise) throws Exception {
		if (log.isTraceEnabled()) log.trace("stopped : " + deploymentID());
		stopPromise.complete();
	}

}
