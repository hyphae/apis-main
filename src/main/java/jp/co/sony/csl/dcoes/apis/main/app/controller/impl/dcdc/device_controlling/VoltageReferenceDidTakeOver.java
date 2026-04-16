package jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.device_controlling;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.main.app.controller.DataAcquisition;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.DcdcDeviceControlling;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

/**
 * The fourth of four processes that move a voltage reference.
 * 1. {@link VoltageReferenceWillHandOver}: Prepare the move source node
 * 2. {@link VoltageReferenceWillTakeOver}: Launch the move destination node
 * 3. {@link VoltageReferenceDidHandOver}: Stop the move source node
 * 4. {@link VoltageReferenceDidTakeOver}: Clean up the destination node ← You are here
 * Send a grid voltage setting command.
 * This is done in order to release droop control.
 * (If a droop ratio is not specified, then droop control is relinquished.)
 * @author OES Project
 *          
 * 電圧リファレンスを移動する 4 つの処理のうち 4 番目の処理.
 * 1. {@link VoltageReferenceWillHandOver} : 移動元の準備
 * 2. {@link VoltageReferenceWillTakeOver} : 移動先の起動
 * 3. {@link VoltageReferenceDidHandOver} : 移動元の停止
 * 4. {@link VoltageReferenceDidTakeOver} : 移動先の後始末 ← これ
 * グリッド電圧設定命令を送信する.
 * 目的はドループ制御の解除.
 * ドループ率の指定をしないとドループ制御の解除となるため.
 * @author OES Project
 */
public class VoltageReferenceDidTakeOver extends AbstractDcdcDeviceControllingCommand {
//	private static final Logger log = LoggerFactory.getLogger(VoltageReferenceDidTakeOver.class);

	private Float operationGridVoltageV_;

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
	public VoltageReferenceDidTakeOver(Vertx vertx, DcdcDeviceControlling controller, JsonObject params) {
		super(vertx, controller, params);
	}

	// Do not start skipping dynamic safety checks in this process
	// この処理により動的安全性チェックのスキップを開始しない
	@Override protected boolean startIgnoreDynamicSafetyCheck() { return false; }

	// Stop skipping dynamic safety checks in this process
	// この処理により動的安全性チェックのスキップを終了する
	@Override protected boolean stopIgnoreDynamicSafetyCheck() { return true; }

	@Override protected void doExecute(Handler<AsyncResult<JsonObject>> completionHandler) {
		operationGridVoltageV_ = DataAcquisition.cache.getFloat("dcdc", "vdis", "dvg");
		if (operationGridVoltageV_ != null) {
			execute__(completionHandler);
		} else {
			ErrorUtil.reportAndFail(vertx_, Error.Category.HARDWARE, Error.Extent.LOCAL, Error.Level.ERROR, "no dcdc.vdis.dvg value in unit data : " + DataAcquisition.cache.jsonObject(), completionHandler);
		}
	}
	private void execute__(Handler<AsyncResult<JsonObject>> completionHandler) {
		// Just respecify the voltage value while remaining in voltage reference mode
		// 電圧リファレンスモードのまま電圧値を指定しなおすだけ
		// Since no droop ratio is specified, the droop ratio is set to 0.0 as a side effect
		// ドループ率を指定しないので副作用としてドループ率が 0.0 に設定される
		controller_.setDcdcVoltage(operationGridVoltageV_, completionHandler);
	}

}
