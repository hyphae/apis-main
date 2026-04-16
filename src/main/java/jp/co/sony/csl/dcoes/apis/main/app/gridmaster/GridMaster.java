package jp.co.sony.csl.dcoes.apis.main.app.gridmaster;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.ServiceAddress;
import jp.co.sony.csl.dcoes.apis.main.app.gridmaster.main_loop.DealExecution;
import jp.co.sony.csl.dcoes.apis.main.app.gridmaster.main_loop.GlobalDataCalculation;
import jp.co.sony.csl.dcoes.apis.main.evaluation.safety.GlobalSafetyEvaluation;
import jp.co.sony.csl.dcoes.apis.main.util.ApisConfig;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

/**
 * GridMaster service object Verticle.
 * Launched from the {@link jp.co.sony.csl.dcoes.apis.main.app.mediator.GridMasterManagement} Verticle.
 * Launches the following Verticles.
 * - {@link Helo}: A Verticle that checks if there is another Gridmaster in the cluster
 * - {@link ErrorCollection}: A Verticle that manages errors
 * - {@link DataCollection}: A Verticle that collects the unit data of all units
 * - {@link DataResponding}: A Verticle that provides unit data for all units
 * - {@link MainLoop}: A Verticle that periodically executes primary tasks such as interchange processing and error handling
 * @author OES Project
 *          
 * GridMaster サービスの親玉 Verticle.
 * {@link jp.co.sony.csl.dcoes.apis.main.app.mediator.GridMasterManagement} Verticle から起動される.
 * 以下の Verticle を起動する.
 * - {@link Helo} : クラスタ内に他に GridMaster が存在しないかチェックする Verticle
 * - {@link ErrorCollection} : エラーを管理する Verticle
 * - {@link DataCollection} : 全ユニットのユニットデータを収集する Verticle
 * - {@link DataResponding} : 全ユニットのユニットデータを提供する Verticle
 * - {@link MainLoop} : 融通処理やエラー対応などの主業務を定期的に実行する Verticle
 * @author OES Project
 */
public class GridMaster extends AbstractVerticle {
	private static final Logger log = LoggerFactory.getLogger(GridMaster.class);

	/**
	 * Called at startup.
	 * Launches the {@link io.vertx.core.eventbus.EventBus} service.
	 * Launches the following Verticles.
	 * - {@link Helo}: A Verticle that checks if there is another Gridmaster in the cluster
	 * - {@link ErrorCollection}: A Verticle that manages errors
	 * - {@link DataCollection}: A Verticle that collects the unit data of all units
	 * - {@link DataResponding}: A Verticle that provides unit data for all units
	 * - {@link MainLoop}: A Verticle that periodically executes primary tasks such as interchange processing and error handling
	 * @param startFuture {@inheritDoc}
	 * @throws Exception {@inheritDoc}
	 *          
	 * 起動時に呼び出される.
	 * {@link io.vertx.core.eventbus.EventBus} サービスを起動する.
	 * 以下の Verticle を起動する.
	 * - {@link Helo} : クラスタ内に他に GridMaster が存在しないかチェックする Verticle
	 * - {@link ErrorCollection} : エラーを管理する Verticle
	 * - {@link DataCollection} : 全ユニットのユニットデータを収集する Verticle
	 * - {@link DataResponding} : 全ユニットのユニットデータを提供する Verticle
	 * - {@link MainLoop} : 融通処理やエラー対応などの主業務を定期的に実行する Verticle
	 * @param startFuture {@inheritDoc}
	 * @throws Exception {@inheritDoc}
	 */
	@Override public void start(Future<Void> startFuture) throws Exception {
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
															if (log.isTraceEnabled()) log.trace("started : " + deploymentID());
															startFuture.complete();
														} else {
															startFuture.fail(resMainLoop.cause());
														}
													});
												} else {
													startFuture.fail(resDataResponding.cause());
												}
											});
									} else {
										startFuture.fail(resDataCollection.cause());
									}
								});
							} else {
								startFuture.fail(resErrorCollection.cause());
							}
						});
					} else {
						startFuture.fail(resHelo.cause());
					}
				});
			} else {
				startFuture.fail(resGridMasterUndeployment.cause());
			}
		});
	}

	/**
	 * Called when stopped.
	 * Reset various caches.
	 * @throws Exception {@inheritDoc}
	 *          
	 * 停止時に呼び出される.
	 * 各種キャッシュをリセットする.
	 * @throws Exception {@inheritDoc}
	 */
	@Override public void stop() throws Exception {
		DealExecution.unitDataCache.reset();
		DataCollection.cache.reset();
		ErrorCollection.cache.reset();
		GlobalDataCalculation.cache.reset();
		GlobalSafetyEvaluation.errors.reset();
		if (log.isTraceEnabled()) log.trace("stopped : " + deploymentID());
	}

	////

	/**
	 * Launch the {@link io.vertx.core.eventbus.EventBus} service.
	 * Address: {@link ServiceAddress.GridMaster#undeploymentLocal()}
	 * Scope: local
	 * Function: Stop a GridMaster.
	 * Message body: none
	 * Message header: none
	 * Response: this unit's ID [{@link String}].
	 * 　　　　　Fails if an error occurs.
	 * @param completionHandler the completion handler
	 *          
	 * {@link io.vertx.core.eventbus.EventBus} サービス起動.
	 * アドレス : {@link ServiceAddress.GridMaster#undeploymentLocal()}
	 * 範囲 : ローカル
	 * 処理 : GridMaster を停止する.
	 * メッセージボディ : なし
	 * メッセージヘッダ : なし
	 * レスポンス : 自ユニットの ID [{@link String}].
	 * 　　　　　   エラーが起きたら fail.
	 * @param completionHandler the completion handler
	 */
	private void startGridMasterUndeploymentService_(Handler<AsyncResult<Void>> completionHandler) {
		vertx.eventBus().<Void>localConsumer(ServiceAddress.GridMaster.undeploymentLocal(), req -> {
			vertx.undeploy(deploymentID(), res -> {
				if (res.succeeded()) {
					req.reply(ApisConfig.unitId());
				} else {
					ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR, res.cause(), req);
				}
			});
		}).completionHandler(completionHandler);
	}

}
