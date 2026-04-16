package jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.device_controlling;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.main.app.PolicyKeeping;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.DcdcDeviceControlling;
import jp.co.sony.csl.dcoes.apis.main.app.controller.util.DDCon;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

/**
 * Put the device in discharge mode.
 * If the specified grid current is larger than the prescribed value, call {@link GridCurrentStepping} to change it step by step.
 * If not, call {@link Checkpoint} to check the reflection.
 * @author OES Project
 *          
 * デバイスを送電モードにする.
 * グリッド電流指定が規定値より大きい場合は {@link GridCurrentStepping} を呼び出し段階的に変化させる.
 * そうでなければ {@link Checkpoint} を呼び反映を確認する.
 * @author OES Project
 */
public class DISCHARGE extends AbstractDcdcDeviceControllingCommand {
//	private static final Logger log = LoggerFactory.getLogger(DISCHARGE.class);

	private Float gridCurrentA_;
	private Float maxOperationGridVoltageV_;
	private Float gridCurrentStepA_;

	/**
	 * Create an instance.
	 * @param vertx a vertx object
	 * @param controller an object that actually sends commands to the device
	 * @param params control parameters.
	 *        - gridCurrentA: the grid current value [{@link Float}]. Required
	 *          
	 * インスタンスを生成する.
	 * @param vertx vertx オブジェクト
	 * @param controller 実際にデバイスに命令を送信するオブジェクト
	 * @param params 制御パラメタ.
	 *        - gridCurrentA : グリッド電流値 [{@link Float}]. 必須
	 */
	public DISCHARGE(Vertx vertx, DcdcDeviceControlling controller, JsonObject params) {
		this(vertx, controller, params, params.getFloat("gridCurrentA"));
	}
	/**
	 * Create an instance.
	 * @param vertx a vertx object
	 * @param controller an object that actually sends commands to the device
	 * @param params control parameters
	 * @param gridCurrentA grid current value. Cannot be {@code null}
	 *          
	 * インスタンスを生成する.
	 * @param vertx vertx オブジェクト
	 * @param controller 実際にデバイスに命令を送信するオブジェクト
	 * @param params 制御パラメタ
	 * @param gridCurrentA グリッド電流値. {@code null} 不可
	 */
	public DISCHARGE(Vertx vertx, DcdcDeviceControlling controller, JsonObject params, Float gridCurrentA) {
		super(vertx, controller, params);
		gridCurrentA_ = gridCurrentA;
	}

	// Do not start skipping dynamic safety checks in this process
	// この処理により動的安全性チェックのスキップを開始しない
	@Override protected boolean startIgnoreDynamicSafetyCheck() { return false; }

	// Stop skipping dynamic safety checks in this process
	// この処理により動的安全性チェックのスキップを終了する
	@Override protected boolean stopIgnoreDynamicSafetyCheck() { return true; }

	@Override protected void doExecute(Handler<AsyncResult<JsonObject>> completionHandler) {
		if (gridCurrentA_ != null) {
			maxOperationGridVoltageV_ = PolicyKeeping.cache().getFloat("operationGridVoltageVRange", "max");
			gridCurrentStepA_ = PolicyKeeping.cache().getFloat("gridCurrentStepA");
			if (maxOperationGridVoltageV_ != null && gridCurrentStepA_ != null) {
				execute__(completionHandler);
			} else {
				ErrorUtil.reportAndFail(vertx_, Error.Category.USER, Error.Extent.LOCAL, Error.Level.ERROR, "data deficiency; POLICY.operationGridVoltageVRange.max : " + maxOperationGridVoltageV_ + ", POLICY.gridCurrentStepA : " + gridCurrentStepA_, completionHandler);
			}
		} else {
			ErrorUtil.reportAndFail(vertx_, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, "illegal parameters, gridCurrentA : " + gridCurrentA_, completionHandler);
		}
	}
	private void execute__(Handler<AsyncResult<JsonObject>> completionHandler) {
		if (gridCurrentA_ < gridCurrentStepA_) {
			// The specified value is smaller than the step value, so specify it directly
			// 指定値がステップ値より小さいので直接指定する
			controller_.setDcdcMode(DDCon.Mode.DISCHARGE, maxOperationGridVoltageV_, gridCurrentA_, res -> {
				if (res.succeeded()) {
					new Checkpoint(vertx_, controller_, params_, null, gridCurrentA_).execute(completionHandler);
				} else {
					completionHandler.handle(res);
				}
			});
		} else {
			// The specified value is larger than the step value, so change it step by step
			// 指定値がステップ値より大きいので段階的に変更する
			// First, place in DISCHARGE mode with the step value
			// まずステップ値で DISCHARGE モードにし
			controller_.setDcdcMode(DDCon.Mode.DISCHARGE, maxOperationGridVoltageV_, gridCurrentStepA_, res -> {
				if (res.succeeded()) {
					// Proceed to current step processing
					// 電流ステップ処理に移行する
					new GridCurrentStepping(vertx_, controller_, params_, gridCurrentA_).execute(completionHandler);
				} else {
					completionHandler.handle(res);
				}
			});
		}
	}

}
