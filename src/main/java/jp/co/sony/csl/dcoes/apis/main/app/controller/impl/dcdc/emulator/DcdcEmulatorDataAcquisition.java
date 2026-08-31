package jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.emulator;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.json.JsonObject;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectUtil;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.VertxConfig;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.DcdcDataAcquisition;
import jp.co.sony.csl.dcoes.apis.main.util.ApisConfig;

public class DcdcEmulatorDataAcquisition extends DcdcDataAcquisition {

	private HttpClient client_;
	private String dataUri_;
	private String statusUri_;

	@Override
	protected void init(Handler<AsyncResult<Void>> onComplete) {
		String host = VertxConfig.config.getString("connection", "emulator", "host");
		Integer port = VertxConfig.config.getInteger("connection", "emulator", "port");
		if (host != null && port != null) {
			client_ = vertx.createHttpClient(new HttpClientOptions().setDefaultHost(host).setDefaultPort(port));
			dataUri_ = "/get/unit/" + ApisConfig.unitId();
			statusUri_ = "/get/dcdc/status/" + ApisConfig.unitId();
			onComplete.handle(Future.succeededFuture());
		} else {
			onComplete.handle(Future
					.failedFuture("invalid connection.emulator.host and/or connection.emulator.port value in config : "
							+ VertxConfig.config.jsonObject()));
		}
	}

	@Override
	protected void getData(Handler<AsyncResult<JsonObject>> onComplete) {
		send(client_, dataUri_, res -> {
			if (res.succeeded()) {
				JsonObject result = res.result();
				JsonObject battery = new JsonObject().put("rsoc", JsonObjectUtil.getValue(result, "emu", "rsoc")).put(
						"battery_operation_status", JsonObjectUtil.getValue(result, "emu", "battery_operation_status"));
				result.put("battery", battery);
			}
			onComplete.handle(res);
		});
	}

	@Override
	protected void getDeviceStatus(Handler<AsyncResult<JsonObject>> onComplete) {
		send(client_, statusUri_, onComplete);
	}

}
