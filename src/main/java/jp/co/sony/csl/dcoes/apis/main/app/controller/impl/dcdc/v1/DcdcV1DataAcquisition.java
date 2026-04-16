package jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.v1;

import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.json.JsonObject;

import java.time.LocalDateTime;

import jp.co.sony.csl.dcoes.apis.common.util.DateTimeUtil;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.VertxConfig;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.DcdcDataAcquisition;

/**
 * Data acquisition service Verticle for the DCDC system's dcdc_controller & EMU-Driver.
 * Launched from the {@link jp.co.sony.csl.dcoes.apis.main.app.controller.Controller} Verticle.
 * @author OES Project
 *          
 * DCDC システムの dcdc_controller ＆ EMU-Driver 環境向けデータ取得サービス Verticle.
 * {@link jp.co.sony.csl.dcoes.apis.main.app.controller.Controller} Verticle から起動される.
 * @author OES Project
 */
public class DcdcV1DataAcquisition extends DcdcDataAcquisition {

	private HttpClient controllerClient_;
	private String controllerDataUri_;
	private String controllerStatusUri_;
	private HttpClient emuDriverClient_;
	private String emuDriverDataUri_;

	/**
	 * {@inheritDoc}
	 * Fetch settings from CONFIG and perform initialization.
	 * - CONFIG.connection.dcdc_controller.host: dcdc_controller connection host name [{@link String}]
	 * - CONFIG.connection.dcdc_controller.port: dcdc_controller connection port number [{@link Integer}]
	 * - CONFIG.connection.emu_driver.host: EMU-Driver connection host name [{@link String}]
	 * - CONFIG.connection.emu_driver.port: EMU-Driver connection port number [{@link Integer}]
	 *          
	 * {@inheritDoc}
	 * CONFIG から設定を取得し初期化する.
	 * - CONFIG.connection.dcdc_controller.host : dcdc_controller 接続ホスト名 [{@link String}]
	 * - CONFIG.connection.dcdc_controller.port : dcdc_controller 接続ポート [{@link Integer}]
	 * - CONFIG.connection.emu_driver.host : EMU-Driver 接続ホスト名 [{@link String}]
	 * - CONFIG.connection.emu_driver.port : EMU-Driver 接続ポート [{@link Integer}]
	 */
	@Override protected void init(Handler<AsyncResult<Void>> completionHandler) {
		String host = VertxConfig.config.getString("connection", "dcdc_controller", "host");
		Integer port = VertxConfig.config.getInteger("connection", "dcdc_controller", "port");
		if (host != null && port != null) {
			controllerClient_ = vertx.createHttpClient(new HttpClientOptions().setDefaultHost(host).setDefaultPort(port));
			controllerDataUri_ = "/remote/get";
			controllerStatusUri_ = "/remote/get/status";
			host = VertxConfig.config.getString("connection", "emu_driver", "host");
			port = VertxConfig.config.getInteger("connection", "emu_driver", "port");
			if (host != null && port != null) {
				emuDriverClient_ = vertx.createHttpClient(new HttpClientOptions().setDefaultHost(host).setDefaultPort(port));
				emuDriverDataUri_ = "/1/log/data";
				completionHandler.handle(Future.succeededFuture());
			} else {
				completionHandler.handle(Future.failedFuture("invalid connection.emu_driver.host and/or connection.emu_driver.port value in config : " + VertxConfig.config.jsonObject()));
			}
		} else {
			completionHandler.handle(Future.failedFuture("invalid connection.dcdc_controller.host and/or connection.dcdc_controller.port value in config : " + VertxConfig.config.jsonObject()));
		}
	}

	/**
	 * {@inheritDoc}
	 * Data acquired from dcdc_controller and EMU-Driver is returned after performing the following processing.
	 * - Data obtained from dcdc_controller is set in {@code dcdc}
	 * - Data obtained from EMU-Driver is set in {@code emu}
	 * - Set {@code emu.rsoc} to {@code battery.rsoc}
	 * - Set {@code emu.battery_operation_status} to {@code battery.battery_operation_status}
	 * - Set the current time in {@code time} as a text string in the standard format of the APIS program
	 *          
	 * {@inheritDoc}
	 * dcdc_controller および EMU-Driver から取得したデータに対し以下の処理をして返す.
	 * - dcdc_controller から取得したデータを {@code dcdc} にセットする
	 * - EMU-Driver から取得したデータを {@code emu} にセットする
	 * - {@code emu.rsoc} を {@code battery.rsoc} にセットする
	 * - {@code emu.battery_operation_status} を {@code battery.battery_operation_status} にセットする
	 * - 現在日時を APIS プログラムの標準フォーマットの文字列で {@code time} にセットする
	 */
	@Override protected void getData(Handler<AsyncResult<JsonObject>> completionHandler) {
		Future<JsonObject> getDcdcFuture = Future.future();
		Future<JsonObject> getEmuFuture = Future.future();
		getDcdc_(getDcdcFuture);
		getEmu_(getEmuFuture);
		CompositeFuture.<JsonObject, JsonObject>all(getDcdcFuture, getEmuFuture).setHandler(ar -> {
			if (ar.succeeded()) {
				JsonObject dcdc = ar.result().resultAt(0);
				JsonObject emu = ar.result().resultAt(1);
				JsonObject battery = new JsonObject().put("rsoc", emu.getValue("rsoc")).put("battery_operation_status", emu.getValue("battery_operation_status"));
				String time = DateTimeUtil.toString(LocalDateTime.now());
				JsonObject result = new JsonObject().put("dcdc", dcdc).put("emu", emu).put("battery", battery).put("time", time);
				completionHandler.handle(Future.succeededFuture(result));
			} else {
				completionHandler.handle(Future.failedFuture(ar.cause()));
			}
		});
	}

	/**
	 * {@inheritDoc}
	 */
	@Override protected void getDeviceStatus(Handler<AsyncResult<JsonObject>> completionHandler) {
		send(controllerClient_, controllerStatusUri_, completionHandler);
	}

	////

	private void getDcdc_(Handler<AsyncResult<JsonObject>> completionHandler) {
		send(controllerClient_, controllerDataUri_, completionHandler);
	}

	private void getEmu_(Handler<AsyncResult<JsonObject>> completionHandler) {
		send(emuDriverClient_, emuDriverDataUri_, completionHandler);
	}

}
