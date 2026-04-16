package jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.device_controlling;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.main.app.PolicyKeeping;
import jp.co.sony.csl.dcoes.apis.main.app.controller.DataAcquisition;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.DcdcDeviceControlling;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

/**
 * Gradually change the specified grid voltage of the device in steps of the prescribed size.
 * At present, POLICY.gridVoltageStepV is not specified and is instead set internally to a default value of 350F, so stepwise control does not occur.
 * Call {@link Checkpoint} when finished.
 * @author OES Project
 *          
 * デバイスのグリッド電圧指定を規定のステップで段階的に変化させる.
 * ただし現状は POLICY.gridVoltageStepV を指定しておらず内部的にデフォルトを 350F としているためステップ制御は発生しない.
 * 終了後 {@link Checkpoint} を呼ぶ.
 * @author OES Project
 */
public class GridVoltageStepping extends AbstractDcdcDeviceControllingCommand {
//	private static final Logger log = LoggerFactory.getLogger(GridVoltageStepping.class);

	private Float gridVoltageV_;
	private Float gridVoltageStepV_;

	/**
	 * Create an instance.
	 * @param vertx a vertx object
	 * @param controller an object that actually sends commands to the device
	 * @param params control parameters.
	 *        - gridVoltageV: target grid voltage value [{@link Float}]. Required
	 *          
	 * インスタンスを生成する.
	 * @param vertx vertx オブジェクト
	 * @param controller 実際にデバイスに命令を送信するオブジェクト
	 * @param params 制御パラメタ.
	 *        - gridVoltageV : 目標グリッド電圧値 [{@link Float}]. 必須
	 */
	public GridVoltageStepping(Vertx vertx, DcdcDeviceControlling controller, JsonObject params) {
		this(vertx, controller, params, params.getFloat("gridVoltageV"));
	}
	/**
	 * Create an instance.
	 * @param vertx a vertx object
	 * @param controller an object that actually sends commands to the device
	 * @param params control parameters
	 * @param gridVoltageV target grid voltage value. Cannot be {@code null}
	 *          
	 * インスタンスを生成する.
	 * @param vertx vertx オブジェクト
	 * @param controller 実際にデバイスに命令を送信するオブジェクト
	 * @param params 制御パラメタ
	 * @param gridVoltageV 目標グリッド電圧値. {@code null} 不可
	 */
	public GridVoltageStepping(Vertx vertx, DcdcDeviceControlling controller, JsonObject params, Float gridVoltageV) {
		super(vertx, controller, params);
		gridVoltageV_ = gridVoltageV;
	}

	// Do not start skipping dynamic safety checks in this process
	// この処理により動的安全性チェックのスキップを開始しない
	@Override protected boolean startIgnoreDynamicSafetyCheck() { return false; }

	// Do not stop skipping dynamic safety checks in this process
	// この処理により動的安全性チェックのスキップを終了しない
	@Override protected boolean stopIgnoreDynamicSafetyCheck() { return false; }

	@Override protected void doExecute(Handler<AsyncResult<JsonObject>> completionHandler) {
		if (gridVoltageV_ != null) {
			gridVoltageStepV_ = PolicyKeeping.cache().getFloat(350F, "gridVoltageStepV"); // no need to step up/down
			if (gridVoltageStepV_ != null) {
				execute__(completionHandler);
			} else {
				ErrorUtil.reportAndFail(vertx_, Error.Category.USER, Error.Extent.LOCAL, Error.Level.ERROR, "data deficiency; POLICY.gridVoltageStepV : " + gridVoltageStepV_, completionHandler);
			}
		} else {
			ErrorUtil.reportAndFail(vertx_, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, "illegal parameters, gridVoltageV : " + gridVoltageV_, completionHandler);
		}
	}

	private void execute__(Handler<AsyncResult<JsonObject>> completionHandler) {
		Float oldValue = DataAcquisition.cache.getFloat("dcdc", "vdis", "dvg");
		if (oldValue != null) {
			float diff = gridVoltageV_ - oldValue;
			if (gridVoltageStepV_ < Math.abs(diff)) {
				// The difference between the present specified value and the target value is larger than the step size, so change it step by step
				// 現在の指定値と目標値との差がステップ値より大きいので段階的に指定する
				float newValue = (oldValue < gridVoltageV_) ? oldValue + gridVoltageStepV_ : oldValue - gridVoltageStepV_;
				// Calculate and specify the positive (or negative) step size for the present specified value
				// 現在の指定値プラス ( またはマイナス ) ステップ値を算出し指定する
				controller_.setDcdcVoltage(newValue, resSet -> {
					if (resSet.succeeded()) {
						// Repeat
						// 繰り返す
						execute__(completionHandler);
					} else {
						completionHandler.handle(resSet);
					}
				});
			} else {
				// The difference between the present specified value and the target value is smaller than the step size, so specify it directly
				// 現在の指定値と目標値との差がステップ値より小さいので目標値を直接指定する
				controller_.setDcdcVoltage(gridVoltageV_, resSet -> {
					if (resSet.succeeded()) {
						// Proceed to the process that waits for the measured value to approach the specified value
						// 測定値が指定値に近くのを待つ処理に移行する
						new Checkpoint(vertx_, controller_, params_, gridVoltageV_, null).execute(completionHandler);
					} else {
						completionHandler.handle(resSet);
					}
				});
			}
		} else {
			ErrorUtil.reportAndFail(vertx_, Error.Category.HARDWARE, Error.Extent.LOCAL, Error.Level.ERROR, "no dcdc.vdis.dvg value in unit data : " + DataAcquisition.cache.jsonObject(), completionHandler);
		}
	}

}
