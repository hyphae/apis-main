package jp.co.sony.csl.dcoes.apis.main.app.controller;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import jp.co.sony.csl.dcoes.apis.main.factory.Factory;

/**
 * Controller service object Verticle.
 * Launched from the {@link jp.co.sony.csl.dcoes.apis.main.app.Apis} Verticle.
 * Launches the following Verticles.
 * - {@link DataAcquisition}: Data acquisition Verticle. The actual class is generated according to the system type
 * - {@link DataResponding}: Data response Verticle. The actual class is generated according to the system type
 * - {@link DeviceControlling}: Device control Verticle. The actual class is generated according to the system type
 * - {@link BatteryCapacityManagement}: Battery capacity management Verticle
 * @author OES Project
 *          
 * Controller サービスの親玉 Verticle.
 * {@link jp.co.sony.csl.dcoes.apis.main.app.Apis} Verticle から起動される.
 * 以下の Verticle を起動する.
 * - {@link DataAcquisition} : データ取得 Verticle. システムの種類に応じた実クラスが生成される
 * - {@link DataResponding} : データ応答 Verticle. システムの種類に応じた実クラスが生成される
 * - {@link DeviceControlling} : デバイス制御 Verticle. システムの種類に応じた実クラスが生成される
 * - {@link BatteryCapacityManagement} : バッテリ容量管理 Verticle
 * @author OES Project
 */
public class Controller extends AbstractVerticle {
	private static final Logger log = LoggerFactory.getLogger(Controller.class);

	/**
	 * Called at startup.
	 * Launches the following Verticles.
	 * - {@link DataAcquisition}: Data acquisition Verticle. The actual class is generated according to the system type
	 * - {@link DataResponding}: Data response Verticle. The actual class is generated according to the system type
	 * - {@link DeviceControlling}: Device control Verticle. The actual class is generated according to the system type
	 * - {@link BatteryCapacityManagement}: Battery capacity management Verticle
	 * @param startFuture {@inheritDoc}
	 * @throws Exception {@inheritDoc}
	 *          
	 * 起動時に呼び出される.
	 * 以下の Verticle を起動する.
	 * - {@link DataAcquisition} : データ取得 Verticle. システムの種類に応じた実クラスが生成される
	 * - {@link DataResponding} : データ応答 Verticle. システムの種類に応じた実クラスが生成される
	 * - {@link DeviceControlling} : デバイス制御 Verticle. システムの種類に応じた実クラスが生成される
	 * - {@link BatteryCapacityManagement} : バッテリ容量管理 Verticle
	 * @param startFuture {@inheritDoc}
	 * @throws Exception {@inheritDoc}
	 */
	@Override public void start(Future<Void> startFuture) throws Exception {
		DataAcquisition dataAcquisition = Factory.factory().controllerFactory().createDataAcquisition();
		vertx.deployVerticle(dataAcquisition, resDataAcquisition -> {
			if (resDataAcquisition.succeeded()) {
				DataResponding dataResponding = Factory.factory().controllerFactory().createDataResponding();
				vertx.deployVerticle(dataResponding, resDataResponding -> {
					if (resDataResponding.succeeded()) {
						DeviceControlling deviceControlling = Factory.factory().controllerFactory().createDeviceControlling();
						vertx.deployVerticle(deviceControlling, resDeviceControlling -> {
							if (resDeviceControlling.succeeded()) {
								vertx.deployVerticle(new BatteryCapacityManagement(), resBatteryCapacityManagement -> {
									if (resBatteryCapacityManagement.succeeded()) {
										if (log.isTraceEnabled()) log.trace("started : " + deploymentID());
										startFuture.complete();
									} else {
										startFuture.fail(resBatteryCapacityManagement.cause());
									}
								});
							} else {
								startFuture.fail(resDeviceControlling.cause());
							}
						});
					} else {
						startFuture.fail(resDataResponding.cause());
					}
				});
			} else {
				startFuture.fail(resDataAcquisition.cause());
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
