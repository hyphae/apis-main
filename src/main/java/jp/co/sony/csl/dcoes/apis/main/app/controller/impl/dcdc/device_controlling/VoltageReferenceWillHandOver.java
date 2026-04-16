package jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.device_controlling;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.main.app.HwConfigKeeping;
import jp.co.sony.csl.dcoes.apis.main.app.controller.DataAcquisition;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.DcdcDeviceControlling;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

/**
 * The first of four processes that move a voltage reference.
 * 1. {@link VoltageReferenceWillHandOver}: Prepare the move source node ← You are here
 * 2. {@link VoltageReferenceWillTakeOver}: Launch the move destination node
 * 3. {@link VoltageReferenceDidHandOver}: Stop the move source node
 * 4. {@link VoltageReferenceDidTakeOver}: Clean up the destination node
 * Send a grid voltage setting command by specifying the droop ratio.
 * This is done in order to start droop control.
 * (Specifying a droop ratio causes droop control to start.)
 * @author OES Project
 *          
 * 電圧リファレンスを移動する 4 つの処理のうち 1 番目の処理.
 * 1. {@link VoltageReferenceWillHandOver} : 移動元の準備 ← これ
 * 2. {@link VoltageReferenceWillTakeOver} : 移動先の起動
 * 3. {@link VoltageReferenceDidHandOver} : 移動元の停止
 * 4. {@link VoltageReferenceDidTakeOver} : 移動先の後始末
 * ドループ率を指定してグリッド電圧設定命令を送信する.
 * 目的はドループ制御の開始.
 * ドループ率の指定をすることでドループ制御が開始される.
 * @author OES Project
 */
public class VoltageReferenceWillHandOver extends AbstractDcdcDeviceControllingCommand {
//	private static final Logger log = LoggerFactory.getLogger(VoltageReferenceWillHandOver.class);

	private Float operationGridVoltageV_;
	private Float droopRatio_;

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
	public VoltageReferenceWillHandOver(Vertx vertx, DcdcDeviceControlling controller, JsonObject params) {
		super(vertx, controller, params);
	}

	// Start skipping dynamic safety checks in this process
	// この処理により動的安全性チェックのスキップを開始する
	@Override protected boolean startIgnoreDynamicSafetyCheck() { return true; }

	// Do not stop skipping dynamic safety checks in this process
	// この処理により動的安全性チェックのスキップを終了しない
	@Override protected boolean stopIgnoreDynamicSafetyCheck() { return false; }

	@Override protected void doExecute(Handler<AsyncResult<JsonObject>> completionHandler) {
		operationGridVoltageV_ = DataAcquisition.cache.getFloat("dcdc", "vdis", "dvg");
		if (operationGridVoltageV_ != null) {
			droopRatio_ = HwConfigKeeping.droopRatio();
			if (operationGridVoltageV_ != null && droopRatio_ != null) {
				execute__(completionHandler);
			} else {
				ErrorUtil.reportAndFail(vertx_, Error.Category.USER, Error.Extent.LOCAL, Error.Level.ERROR, "data deficiency; POLICY.operationGridVoltageV : " + operationGridVoltageV_ + ", HWCONFIG.droopRatio : " + droopRatio_, completionHandler);
			}
		} else {
			ErrorUtil.reportAndFail(vertx_, Error.Category.HARDWARE, Error.Extent.LOCAL, Error.Level.ERROR, "no dcdc.vdis.dvg value in unit data : " + DataAcquisition.cache.jsonObject(), completionHandler);
		}
	}
	private void execute__(Handler<AsyncResult<JsonObject>> completionHandler) {
		// Specify the droop ratio while remaining in voltage reference mode
		// 電圧リファレンスモードのままドループ率を指定する
		controller_.setDcdcVoltage(operationGridVoltageV_, droopRatio_, completionHandler);
	}

}
