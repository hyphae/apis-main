package jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.v2;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.json.JsonObject;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.VertxConfig;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.DcdcDeviceControlling;
import jp.co.sony.csl.dcoes.apis.main.app.controller.util.DDCon;

public class DcdcV2DeviceControlling extends DcdcDeviceControlling {

	private HttpClient client_;

	@Override
	protected void init(Handler<AsyncResult<Void>> onComplete) {
		String host = VertxConfig.config.getString("connection", "dcdc_controller", "host");
		Integer port = VertxConfig.config.getInteger("connection", "dcdc_controller", "port");
		if (host != null && port != null) {
			client_ = vertx.createHttpClient(new HttpClientOptions().setDefaultHost(host).setDefaultPort(port));
			onComplete.handle(Future.succeededFuture());
		} else {
			onComplete.handle(Future.failedFuture(
					"no connection.dcdc_controller.host and/or connection.dcdc_controller.port value in config : "
							+ VertxConfig.config.jsonObject()));
		}
	}

	@Override
	protected void doSetDcdcMode(DDCon.Mode mode, Number gridVoltageV, Number gridCurrentA, Number droopRatio,
			Handler<AsyncResult<JsonObject>> onComplete) {
		send(client_, setModeUri_(mode, gridVoltageV, gridCurrentA, droopRatio), onComplete);
	}

	@Override
	protected void doSetDcdcVoltage(Number gridVoltageV, Number droopRatio,
			Handler<AsyncResult<JsonObject>> onComplete) {
		send(client_, setVoltageUri_(gridVoltageV, droopRatio), onComplete);
	}

	@Override
	protected void doSetDcdcCurrent(Number gridCurrentA, Handler<AsyncResult<JsonObject>> onComplete) {
		send(client_, setCurrentUri_(gridCurrentA), onComplete);
	}

	private String setModeUri_(DDCon.Mode mode, Number voltage, Number current, Number droopRatio) {
		return "/dcdc/set?mode=" + DDCon.codeFromMode(mode) + "&dvg=" + voltage + "&dig=" + current + "&drg="
				+ droopRatio;
	}

	private String setVoltageUri_(Number voltage, Number droopRatio) {
		return "/dcdc/set?dvg=" + voltage + "&drg=" + droopRatio;
	}

	private String setCurrentUri_(Number current) {
		return "/dcdc/set?dig=" + current;
	}

}
