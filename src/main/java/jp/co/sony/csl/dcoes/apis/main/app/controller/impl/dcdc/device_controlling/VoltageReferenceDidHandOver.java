package jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.device_controlling;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectUtil;
import jp.co.sony.csl.dcoes.apis.main.app.PolicyKeeping;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.DcdcDeviceControlling;
import jp.co.sony.csl.dcoes.apis.main.app.controller.util.DDCon;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

/**
 * The third of four processes that move a voltage reference.
 * 1. {@link VoltageReferenceWillHandOver}: Prepare the move source node
 * 2. {@link VoltageReferenceWillTakeOver}: Launch the move destination node
 * 3. {@link VoltageReferenceDidHandOver}: Stop the move source node ← You are here
 * 4. {@link VoltageReferenceDidTakeOver}: Clean up the destination node
 * Proceeds to the following modes depending on the interchange state.
 * - Charge (charge mode)
 * - Discharge (discharge mode)
 * - Stop (stop after gradually reducing the grid current by {@link GridCurrentStepping})
 * @author OES Project
 *          
 * 電圧リファレンスを移動する 4 つの処理のうち 3 番目の処理.
 * 1. {@link VoltageReferenceWillHandOver} : 移動元の準備
 * 2. {@link VoltageReferenceWillTakeOver} : 移動先の起動
 * 3. {@link VoltageReferenceDidHandOver} : 移動元の停止 ← これ
 * 4. {@link VoltageReferenceDidTakeOver} : 移動先の後始末
 * 融通の状態により以下のモードに移行する.
 * - 受電
 * - 送電
 * - 停止 ( {@link GridCurrentStepping} でグリッド電流を段階的に落としてから停止する
 * @author OES Project
 */
public class VoltageReferenceDidHandOver extends AbstractDcdcDeviceControllingCommand {
//	private static final Logger log = LoggerFactory.getLogger(VoltageReferenceDidHandOver.class);

	private String mode_;
	private Float gridCurrentA_;
	private Float operationGridVoltageV_;
	private Float maxOperationGridVoltageV_;

	/**
	 * Create an instance.
	 * @param vertx a vertx object
	 * @param controller an object that actually sends commands to the device
	 * @param params control parameters.
	 *        - mode: the next mode [{@link String}]. WAIT if {@code null}
	 *        - gridCurrentA: the grid current value [{@link Float}]. Required if mode is DISCHARGE or CHARGE
	 *          
	 * インスタンスを生成する.
	 * @param vertx vertx オブジェクト
	 * @param controller 実際にデバイスに命令を送信するオブジェクト
	 * @param params 制御パラメタ.
	 *        - mode         : 次のモード [{@link String}]. {@code null} なら WAIT
	 *        - gridCurrentA : グリッド電流値 [{@link Float}]. mode が DISCHARGE または CHARGE なら必須
	 */
	public VoltageReferenceDidHandOver(Vertx vertx, DcdcDeviceControlling controller, JsonObject params) {
		this(vertx, controller, params, JsonObjectUtil.getString(params, "mode"), JsonObjectUtil.getFloat(params, "gridCurrentA"));
	}
	/**
	 * Create an instance.
	 * @param vertx a vertx object
	 * @param controller an object that actually sends commands to the device
	 * @param params control parameters
	 * @param mode the next mode. WAIT if {@code null}
	 * @param gridCurrentA grid current value. Cannot be {@code null} if mode is DISCHARGE or CHARGE
	 *          
	 * インスタンスを生成する.
	 * @param vertx vertx オブジェクト
	 * @param controller 実際にデバイスに命令を送信するオブジェクト
	 * @param params 制御パラメタ
	 * @param mode 次のモード. {@code null} なら WAIT
	 * @param gridCurrentA グリッド電流値. mode が DISCHARGE または CHARGE なら {@code null} 不可
	 */
	public VoltageReferenceDidHandOver(Vertx vertx, DcdcDeviceControlling controller, JsonObject params, String mode, Float gridCurrentA) {
		super(vertx, controller, params);
		mode_ = mode;
		gridCurrentA_ = gridCurrentA;
	}

	// Do not start skipping dynamic safety checks in this process
	// この処理により動的安全性チェックのスキップを開始しない
	@Override protected boolean startIgnoreDynamicSafetyCheck() { return false; }

	// Stop skipping dynamic safety checks in this process
	// この処理により動的安全性チェックのスキップを終了する
	@Override protected boolean stopIgnoreDynamicSafetyCheck() { return true; }

	@Override protected void doExecute(Handler<AsyncResult<JsonObject>> completionHandler) {
		operationGridVoltageV_ = PolicyKeeping.cache().getFloat("operationGridVoltageV");
		maxOperationGridVoltageV_ = PolicyKeeping.cache().getFloat("operationGridVoltageVRange", "max");
		if (operationGridVoltageV_ != null && maxOperationGridVoltageV_ != null) {
			// Decide which mode to switch to
			// どのモードに移行するか決める
			// Assume WAIT if not specified
			// 指定がなければ WAIT とする
			// Raise an error if a specification is provided but it isn't possible tell what it is
			// 指定があるけどどれだかわからない場合はエラーにする
			DDCon.Mode mode = (mode_ != null) ? DDCon.mode(mode_) : DDCon.Mode.WAIT;
			if (mode != null) {
				switch (mode) {
				case DISCHARGE:
					execute_DISCHARGE_(completionHandler);
					break;
				case CHARGE:
					execute_CHARGE_(completionHandler);
					break;
				case WAIT:
					execute_WAIT_(completionHandler);
					break;
				case VOLTAGE_REFERENCE:
					ErrorUtil.reportAndFail(vertx_, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, "invalid mode : " + mode, completionHandler);
					break;
				}
			} else {
				ErrorUtil.reportAndFail(vertx_, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, "invalid mode in params : " + mode_, completionHandler);
			}
		} else {
			ErrorUtil.reportAndFail(vertx_, Error.Category.USER, Error.Extent.LOCAL, Error.Level.ERROR, "data deficiency ; POLICY.operationGridVoltageV : " + operationGridVoltageV_ + ", POLICY.operationGridVoltageVRange.max : " + maxOperationGridVoltageV_, completionHandler);
		}
	}

	private void execute_DISCHARGE_(Handler<AsyncResult<JsonObject>> completionHandler) {
		// Set to discharge mode
		// 送電モードにする
		controller_.setDcdcMode(DDCon.Mode.DISCHARGE, maxOperationGridVoltageV_, gridCurrentA_, completionHandler);
	}
	private void execute_CHARGE_(Handler<AsyncResult<JsonObject>> completionHandler) {
		// Set to charge mode
		// 受電モードにする
		controller_.setDcdcMode(DDCon.Mode.CHARGE, operationGridVoltageV_, gridCurrentA_, completionHandler);
	}
	private void execute_WAIT_(Handler<AsyncResult<JsonObject>> completionHandler) {
		// After dropping the grid current to 0,
		// グリッド電流値を 0 まで落としてから
		new GridCurrentStepping(vertx_, controller_, params_, 0F).execute(res -> {
			// enter WAIT mode regardless of success or failure
			// 成否にかかわらず WAIT にする
			controller_.setDcdcMode(DDCon.Mode.WAIT, operationGridVoltageV_, 0F, completionHandler);
		});
	}

}
