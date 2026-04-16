package jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.device_controlling;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.main.app.HwConfigKeeping;
import jp.co.sony.csl.dcoes.apis.main.app.controller.DataAcquisition;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.DcdcDeviceControlling;
import jp.co.sony.csl.dcoes.apis.main.app.controller.util.DDCon;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

/**
 * The second of four processes that move a voltage reference.
 * 1. {@link VoltageReferenceWillHandOver}: Prepare the move source node
 * 2. {@link VoltageReferenceWillTakeOver}: Launch the move destination node ← You are here
 * 3. {@link VoltageReferenceDidHandOver}: Stop the move source node
 * 4. {@link VoltageReferenceDidTakeOver}: Clean up the destination node
 * Send a command to change into voltage reference mode.
 * (Specifying a droop ratio causes droop control to start.)
 * @author OES Project
 *          
 * 電圧リファレンスを移動する 4 つの処理のうち 2 番目の処理.
 * 1. {@link VoltageReferenceWillHandOver} : 移動元の準備
 * 2. {@link VoltageReferenceWillTakeOver} : 移動先の起動 ← これ
 * 3. {@link VoltageReferenceDidHandOver} : 移動元の停止
 * 4. {@link VoltageReferenceDidTakeOver} : 移動先の後始末
 * 電圧リファレンスモードへの変更命令を送信する.
 * ドループ率を指定することによりドループ制御を開始する.
 * @author OES Project
 */
public class VoltageReferenceWillTakeOver extends AbstractDcdcDeviceControllingCommand {
	private static final Logger log = LoggerFactory.getLogger(VoltageReferenceWillTakeOver.class);

	private Float gridVoltageV_;
	private Float gridCurrentCapacityA_;
	private Float droopRatio_;

	/**
	 * Create an instance.
	 * @param vertx a vertx object
	 * @param controller an object that actually sends commands to the device
	 * @param params control parameters.
	 *        - gridVoltageV: grid voltage value [{@link Float}]. Optional. If {@code null}, use the existing vg value
	 *          
	 * インスタンスを生成する.
	 * @param vertx vertx オブジェクト
	 * @param controller 実際にデバイスに命令を送信するオブジェクト
	 * @param params 制御パラメタ.
	 *        - gridVoltageV : グリッド電圧値 [{@link Float}]. 任意. {@code null} なら現状の vg 値を使用する
	 */
	public VoltageReferenceWillTakeOver(Vertx vertx, DcdcDeviceControlling controller, JsonObject params) {
		this(vertx, controller, params, params.getFloat("gridVoltageV"));
	}
	/**
	 * Create an instance.
	 * @param vertx a vertx object
	 * @param controller an object that actually sends commands to the device
	 * @param params control parameters
	 * @param gridVoltageV grid voltage value If {@code null}, use the existing vg value
	 *          
	 * インスタンスを生成する.
	 * @param vertx vertx オブジェクト
	 * @param controller 実際にデバイスに命令を送信するオブジェクト
	 * @param params 制御パラメタ
	 * @param gridVoltageV グリッド電圧値. {@code null} なら現状の vg 値を使用する
	 */
	public VoltageReferenceWillTakeOver(Vertx vertx, DcdcDeviceControlling controller, JsonObject params, Float gridVoltageV) {
		super(vertx, controller, params);
		gridVoltageV_ = gridVoltageV;
	}

	// Start skipping dynamic safety checks in this process
	// この処理により動的安全性チェックのスキップを開始する
	@Override protected boolean startIgnoreDynamicSafetyCheck() { return true; }

	// Do not stop skipping dynamic safety checks in this process
	// この処理により動的安全性チェックのスキップを終了しない
	@Override protected boolean stopIgnoreDynamicSafetyCheck() { return false; }

	@Override protected void doExecute(Handler<AsyncResult<JsonObject>> completionHandler) {
		if (gridVoltageV_ == null) {
			// If the grid voltage is not specified, use the present measured value
			// グリッド電圧の指定がなければ現状の測定値を使う
			gridVoltageV_ = DataAcquisition.cache.getFloat("dcdc", "meter", "vg");
			if (log.isInfoEnabled()) log.info("no gridVoltageV parameter given, use dcdc.meter.vg from this unit : " + gridVoltageV_);
		}
		if (gridVoltageV_ != null) {
			gridCurrentCapacityA_ = HwConfigKeeping.gridCurrentCapacityA();
			droopRatio_ = HwConfigKeeping.droopRatio();
			if (gridCurrentCapacityA_ != null && droopRatio_ != null) {
				execute__(completionHandler);
			} else {
				ErrorUtil.reportAndFail(vertx_, Error.Category.USER, Error.Extent.LOCAL, Error.Level.ERROR, "data deficiency; HWCONFIG.gridCurrentCapacityA : " + gridCurrentCapacityA_ + ", HWCONFIG.droopRatio : " + droopRatio_, completionHandler);
			}
		} else {
			ErrorUtil.reportAndFail(vertx_, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, "illegal parameters, gridVoltageV : " + gridVoltageV_, completionHandler);
		}
	}
	private void execute__(Handler<AsyncResult<JsonObject>> completionHandler) {
		// Specify a droop ratio to enter voltage reference mode
		// ドループ率を指定して電圧リファレンスモードにする
		controller_.setDcdcMode(DDCon.Mode.VOLTAGE_REFERENCE, gridVoltageV_, gridCurrentCapacityA_, droopRatio_, completionHandler);
	}

}
