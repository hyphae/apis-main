package jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.device_controlling;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.DcdcDeviceControlling;

/**
 * Forcibly stop the device.
 * Since this is not implemented, it is equivalent to the parent class WAIT.
 * @author OES Project
 *          
 * デバイスを強制停止する.
 * 実装は何も無いので親クラスである WAIT と同じ.
 * @author OES Project
 */
public class Scram extends WAIT {
//	private static final Logger log = LoggerFactory.getLogger(Scram.class);

	/**
	 * Create an instance.
	 * @param vertx a vertx object
	 * @param controller an object that actually sends commands to the device
	 * @param params control parameters. Not required
	 *          
	 * インスタンスを生成する.
	 * @param vertx vertx オブジェクト
	 * @param controller 実際にデバイスに命令を送信するオブジェクト
	 * @param params 制御パラメタ. 不要
	 */
	public Scram(Vertx vertx, DcdcDeviceControlling controller, JsonObject params) {
		super(vertx, controller, params);
	}

}
