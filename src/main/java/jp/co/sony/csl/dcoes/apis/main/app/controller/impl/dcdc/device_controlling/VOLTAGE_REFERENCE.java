package jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.device_controlling;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.main.app.HwConfigKeeping;
import jp.co.sony.csl.dcoes.apis.main.app.PolicyKeeping;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.DcdcDeviceControlling;
import jp.co.sony.csl.dcoes.apis.main.app.controller.util.DDCon;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

/**
 * Put the device in voltage reference mode.
 * @author OES Project
 *          
 * デバイスを電圧リファレンスモードにする.
 * @author OES Project
 */
public class VOLTAGE_REFERENCE extends AbstractDcdcDeviceControllingCommand {
//	private static final Logger log = LoggerFactory.getLogger(VOLTAGE_REFERENCE.class);

	private Float operationGridVoltageV_;
	private Float gridCurrentCapacityA_;

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
	public VOLTAGE_REFERENCE(Vertx vertx, DcdcDeviceControlling controller, JsonObject params) {
		super(vertx, controller, params);
	}

	// Start skipping dynamic safety checks in this process
	// この処理により動的安全性チェックのスキップを開始する
	@Override protected boolean startIgnoreDynamicSafetyCheck() { return true; }

	// Stop skipping dynamic safety checks in this process
	// この処理により動的安全性チェックのスキップを終了する
	@Override protected boolean stopIgnoreDynamicSafetyCheck() { return false; }

	@Override protected void doExecute(Handler<AsyncResult<JsonObject>> completionHandler) {
		operationGridVoltageV_ = PolicyKeeping.cache().getFloat("operationGridVoltageV");
		gridCurrentCapacityA_ = HwConfigKeeping.gridCurrentCapacityA();
		if (operationGridVoltageV_ != null && gridCurrentCapacityA_ != null) {
			execute__(completionHandler);
		} else {
			ErrorUtil.reportAndFail(vertx_, Error.Category.USER, Error.Extent.LOCAL, Error.Level.ERROR, "data deficiency; POLICY.operationGridVoltageV : " + operationGridVoltageV_ + ", HWCONFIG.gridCurrentCapacityA : " + gridCurrentCapacityA_, completionHandler);
		}
	}
	private void execute__(Handler<AsyncResult<JsonObject>> completionHandler) {
		controller_.setDcdcMode(DDCon.Mode.VOLTAGE_REFERENCE, operationGridVoltageV_, gridCurrentCapacityA_, completionHandler);
	}

}
