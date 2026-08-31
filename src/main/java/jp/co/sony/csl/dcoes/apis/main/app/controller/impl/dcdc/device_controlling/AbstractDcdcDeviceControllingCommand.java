package jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.device_controlling;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jp.co.sony.csl.dcoes.apis.main.app.controller.DataAcquisition;
import jp.co.sony.csl.dcoes.apis.main.app.controller.DeviceControlling;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.DcdcDeviceControlling;

public abstract class AbstractDcdcDeviceControllingCommand {
	private static final Logger log = LoggerFactory.getLogger(AbstractDcdcDeviceControllingCommand.class);

	protected Vertx vertx_;
	protected DcdcDeviceControlling controller_;
	protected JsonObject params_;

	public AbstractDcdcDeviceControllingCommand(Vertx vertx, DcdcDeviceControlling controller, JsonObject params) {
		vertx_ = vertx;
		controller_ = controller;
		params_ = params;
	}

	public final void execute(Handler<AsyncResult<JsonObject>> onComplete) {
		if (log.isInfoEnabled())
			log.info(getClass().getSimpleName() + ".execute()");
		if (startIgnoreDynamicSafetyCheck()) {
			DeviceControlling.ignoreDynamicSafetyCheck(true);
		}
		doExecute(res -> {
			if (log.isInfoEnabled()) {
				log.info(getClass().getSimpleName() + ".execute(); res.succeeded() : " + res.succeeded());
				if (res.succeeded()) {
					log.info(getClass().getSimpleName() + ".execute(); res.result() : " + res.result());
				} else {
					log.info(getClass().getSimpleName() + ".execute(); res.cause() : " + res.cause());
				}
			}
			if (res.succeeded()) {
				if (stopIgnoreDynamicSafetyCheck()) {
					DeviceControlling.ignoreDynamicSafetyCheck(false);
				}
			}
			onComplete.handle(res);
		});
	}

	protected abstract boolean startIgnoreDynamicSafetyCheck();

	protected abstract boolean stopIgnoreDynamicSafetyCheck();

	protected abstract void doExecute(Handler<AsyncResult<JsonObject>> onComplete);

	protected void succeeded(Handler<AsyncResult<JsonObject>> onComplete) {
		onComplete.handle(Future.succeededFuture(DataAcquisition.cache.getJsonObject("dcdc")));
	}

}
