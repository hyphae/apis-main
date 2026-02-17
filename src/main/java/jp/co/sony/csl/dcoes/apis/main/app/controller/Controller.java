package jp.co.sony.csl.dcoes.apis.main.app.controller;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jp.co.sony.csl.dcoes.apis.main.factory.Factory;

public class Controller extends AbstractVerticle {
	private static final Logger log = LoggerFactory.getLogger(Controller.class);

	@Override
	public void start(Promise<Void> startPromise) throws Exception {
		DataAcquisition dataAcquisition = Factory.factory().controllerFactory().createDataAcquisition();
		vertx.deployVerticle(dataAcquisition, resDataAcquisition -> {
			if (resDataAcquisition.succeeded()) {
				DataResponding dataResponding = Factory.factory().controllerFactory().createDataResponding();
				vertx.deployVerticle(dataResponding, resDataResponding -> {
					if (resDataResponding.succeeded()) {
						DeviceControlling deviceControlling = Factory.factory().controllerFactory()
								.createDeviceControlling();
						vertx.deployVerticle(deviceControlling, resDeviceControlling -> {
							if (resDeviceControlling.succeeded()) {
								vertx.deployVerticle(new BatteryCapacityManagement(), resBatteryCapacityManagement -> {
									if (resBatteryCapacityManagement.succeeded()) {
										if (log.isTraceEnabled())
											log.trace("started : " + deploymentID());
										startPromise.complete();
									} else {
										startPromise.fail(resBatteryCapacityManagement.cause());
									}
								});
							} else {
								startPromise.fail(resDeviceControlling.cause());
							}
						});
					} else {
						startPromise.fail(resDataResponding.cause());
					}
				});
			} else {
				startPromise.fail(resDataAcquisition.cause());
			}
		});
	}

	@Override
	public void stop() throws Exception {
		if (log.isTraceEnabled())
			log.trace("stopped : " + deploymentID());
	}

}
