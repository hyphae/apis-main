package jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.v1;

import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.json.JsonObject;

import java.time.LocalDateTime;

import jp.co.sony.csl.dcoes.apis.common.util.DateTimeUtil;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.VertxConfig;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.DcdcDataAcquisition;

public class DcdcV1DataAcquisition extends DcdcDataAcquisition {

	private HttpClient controllerClient_;
	private String controllerDataUri_;
	private String controllerStatusUri_;
	private HttpClient emuDriverClient_;
	private String emuDriverDataUri_;

	@Override
	protected void init(Handler<AsyncResult<Void>> onComplete) {
		String host = VertxConfig.config.getString("connection", "dcdc_controller", "host");
		Integer port = VertxConfig.config.getInteger("connection", "dcdc_controller", "port");
		if (host != null && port != null) {
			controllerClient_ = vertx
					.createHttpClient(new HttpClientOptions().setDefaultHost(host).setDefaultPort(port));
			controllerDataUri_ = "/remote/get";
			controllerStatusUri_ = "/remote/get/status";
			host = VertxConfig.config.getString("connection", "emu_driver", "host");
			port = VertxConfig.config.getInteger("connection", "emu_driver", "port");
			if (host != null && port != null) {
				emuDriverClient_ = vertx
						.createHttpClient(new HttpClientOptions().setDefaultHost(host).setDefaultPort(port));
				emuDriverDataUri_ = "/1/log/data";
				onComplete.handle(Future.succeededFuture());
			} else {
				onComplete.handle(Future.failedFuture(
						"invalid connection.emu_driver.host and/or connection.emu_driver.port value in config : "
								+ VertxConfig.config.jsonObject()));
			}
		} else {
			onComplete.handle(Future.failedFuture(
					"invalid connection.dcdc_controller.host and/or connection.dcdc_controller.port value in config : "
							+ VertxConfig.config.jsonObject()));
		}
	}

	@Override
	protected void getData(Handler<AsyncResult<JsonObject>> onComplete) {
		Promise<JsonObject> getDcdcPromise = Promise.promise();
		Promise<JsonObject> getEmuPromise = Promise.promise();
		getDcdc_(getDcdcPromise);
		getEmu_(getEmuPromise);
		CompositeFuture.all(getDcdcPromise.future(), getEmuPromise.future()).onComplete(ar -> {
			if (ar.succeeded()) {
				JsonObject dcdc = ar.result().resultAt(0);
				JsonObject emu = ar.result().resultAt(1);
				JsonObject battery = new JsonObject().put("rsoc", emu.getValue("rsoc")).put("battery_operation_status",
						emu.getValue("battery_operation_status"));
				String time = DateTimeUtil.toString(LocalDateTime.now());
				JsonObject result = new JsonObject().put("dcdc", dcdc).put("emu", emu).put("battery", battery)
						.put("time", time);
				onComplete.handle(Future.succeededFuture(result));
			} else {
				onComplete.handle(Future.failedFuture(ar.cause()));
			}
		});
	}

	@Override
	protected void getDeviceStatus(Handler<AsyncResult<JsonObject>> onComplete) {
		send(controllerClient_, controllerStatusUri_, onComplete);
	}

	private void getDcdc_(Handler<AsyncResult<JsonObject>> onComplete) {
		send(controllerClient_, controllerDataUri_, onComplete);
	}

	private void getEmu_(Handler<AsyncResult<JsonObject>> onComplete) {
		send(emuDriverClient_, emuDriverDataUri_, onComplete);
	}

}
