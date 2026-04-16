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

/**
 * Data acquisition service Verticle for the DCDC system emulator environment.
 * Launched from the {@link jp.co.sony.csl.dcoes.apis.main.app.controller.Controller} Verticle.
 * @author OES Project
 *          
 * DCDC システムの emulator 環境向けデータ取得サービス Verticle.
 * {@link jp.co.sony.csl.dcoes.apis.main.app.controller.Controller} Verticle から起動される.
 * @author OES Project
 */
/**
 * @author OES Project
 *
 */
public class DcdcEmulatorDataAcquisition extends DcdcDataAcquisition {

	private HttpClient client_;
	private String dataUri_;
	private String statusUri_;

	/**
	 * {@inheritDoc}
	 * Fetch settings from CONFIG and perform initialization.
	 * - CONFIG.connection.emulator.host: emulator connection host name [{@link String}]
	 * - CONFIG.connection.emulator.port: emulator connection port number [{@link Integer}]
	 *          
	 * {@inheritDoc}
	 * CONFIG から設定を取得し初期化する.
	 * - CONFIG.connection.emulator.host : emulator 接続ホスト名 [{@link String}]
	 * - CONFIG.connection.emulator.port : emulator 接続ポート [{@link Integer}]
	 */
	@Override protected void init(Handler<AsyncResult<Void>> completionHandler) {
		String host = VertxConfig.config.getString("connection", "emulator", "host");
		Integer port = VertxConfig.config.getInteger("connection", "emulator", "port");
		if (host != null && port != null) {
			client_ = vertx.createHttpClient(new HttpClientOptions().setDefaultHost(host).setDefaultPort(port));
			dataUri_ = "/get/unit/" + ApisConfig.unitId();
			statusUri_ = "/get/dcdc/status/" + ApisConfig.unitId();
			completionHandler.handle(Future.succeededFuture());
		} else {
			completionHandler.handle(Future.failedFuture("invalid connection.emulator.host and/or connection.emulator.port value in config : " + VertxConfig.config.jsonObject()));
		}
	}

	/**
	 * {@inheritDoc}
	 * Data obtained from the emulator is returned after performing the following conversion process.
	 * - Set {@code emu.rsoc} to {@code battery.rsoc}
	 * - Set {@code emu.battery_operation_status} to {@code battery.battery_operation_status}
	 *          
	 * {@inheritDoc}
	 * emulator から取得したデータに対し以下のコンバート処理をして返す.
	 * - {@code emu.rsoc} を {@code battery.rsoc} にセットする
	 * - {@code emu.battery_operation_status} を {@code battery.battery_operation_status} にセットする
	 */
	@Override protected void getData(Handler<AsyncResult<JsonObject>> completionHandler) {
		send(client_, dataUri_, res -> {
			if (res.succeeded()) {
				JsonObject result = res.result();
				JsonObject battery = new JsonObject().put("rsoc", JsonObjectUtil.getValue(result, "emu", "rsoc")).put("battery_operation_status", JsonObjectUtil.getValue(result, "emu", "battery_operation_status"));
				result.put("battery", battery);
			}
			completionHandler.handle(res);
		});
	}

	/**
	 * {@inheritDoc}
	 */
	@Override protected void getDeviceStatus(Handler<AsyncResult<JsonObject>> completionHandler) {
		send(client_, statusUri_, completionHandler);
	}

}
