package jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.v2;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.json.JsonObject;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import jp.co.sony.csl.dcoes.apis.common.util.DateTimeUtil;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectUtil;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.VertxConfig;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.DcdcDataAcquisition;

public class DcdcV2DataAcquisition extends DcdcDataAcquisition {

	public static final List<String> INTERFACE_VERSIONS = Arrays.asList("2");

	private HttpClient client_;
	private String dataUri_;
	private String statusUri_;
	private String interfaceVersion_;

	@Override
	protected void init(Handler<AsyncResult<Void>> onComplete) {
		String host = VertxConfig.config.getString("connection", "dcdc_controller", "host");
		Integer port = VertxConfig.config.getInteger("connection", "dcdc_controller", "port");
		if (host != null && port != null) {
			client_ = vertx.createHttpClient(new HttpClientOptions().setDefaultHost(host).setDefaultPort(port));
			dataUri_ = "/all/get";
			statusUri_ = "/dcdc/get/status";
			negotiateInterfaceVersion_(onComplete);
		} else {
			onComplete.handle(Future.failedFuture(
					"invalid connection.dcdc_controller.host and/or connection.dcdc_controller.port value in config : "
							+ VertxConfig.config.jsonObject()));
		}
	}

	private void negotiateInterfaceVersion_(Handler<AsyncResult<Void>> onComplete) {
		String versionUri = "/version/get";
		send(client_, versionUri, res -> {
			if (res.succeeded()) {
				JsonObject result = res.result();
				String major_minor = JsonObjectUtil.getString(result, "comm_interface_version");
				if (major_minor != null) {
					int pos = major_minor.indexOf('.');
					if (0 < pos) {
						interfaceVersion_ = major_minor.substring(0, pos);
						if (INTERFACE_VERSIONS.contains(interfaceVersion_)) {
							onComplete.handle(Future.succeededFuture());
						} else {
							onComplete.handle(
									Future.failedFuture("unsupported major interface version : " + interfaceVersion_));
						}
					} else {
						onComplete.handle(Future.failedFuture("bad interface version : " + major_minor));
					}
				} else {
					onComplete.handle(Future.failedFuture("no interface version ; result : " + result));
				}
			} else {
				onComplete.handle(Future.failedFuture(res.cause()));
			}
		});
	}

	@Override
	protected void getData(Handler<AsyncResult<JsonObject>> onComplete) {
		send(client_, dataUri_, res -> {
			if (res.succeeded()) {
				JsonObject dcdc = res.result();
				JsonObject battery = new JsonObject().put("rsoc", dcdc.remove("rsoc")).put("battery_operation_status",
						dcdc.remove("battery_operation_status"));
				String time = DateTimeUtil.toString(LocalDateTime.now());
				JsonObject result = new JsonObject().put("dcdc", dcdc).put("battery", battery).put("time", time);
				onComplete.handle(Future.succeededFuture(result));
			} else {
				onComplete.handle(res);
			}
		});
	}

	@Override
	protected void getDeviceStatus(Handler<AsyncResult<JsonObject>> onComplete) {
		send(client_, statusUri_, onComplete);
	}

}
