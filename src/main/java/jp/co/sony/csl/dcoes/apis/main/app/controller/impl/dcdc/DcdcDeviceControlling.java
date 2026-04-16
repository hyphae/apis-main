package jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectUtil;
import jp.co.sony.csl.dcoes.apis.main.app.HwConfigKeeping;
import jp.co.sony.csl.dcoes.apis.main.app.controller.DataAcquisition;
import jp.co.sony.csl.dcoes.apis.main.app.controller.DeviceControlling;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.device_controlling.AbstractDcdcDeviceControllingCommand;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.device_controlling.CHARGE;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.device_controlling.DISCHARGE;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.device_controlling.GridCurrentStepping;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.device_controlling.GridVoltageStepping;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.device_controlling.Scram;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.device_controlling.VOLTAGE_REFERENCE;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.device_controlling.VoltageReferenceAuthorization;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.device_controlling.VoltageReferenceDidHandOver;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.device_controlling.VoltageReferenceDidTakeOver;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.device_controlling.VoltageReferenceWillHandOver;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.device_controlling.VoltageReferenceWillTakeOver;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.device_controlling.WAIT;
import jp.co.sony.csl.dcoes.apis.main.app.controller.util.DDCon;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

/**
 * Device control service object Verticle for the DCDC system.
 * Launched from the {@link jp.co.sony.csl.dcoes.apis.main.app.controller.Controller} Verticle.
 * The following types are available, depending on the type of system.
 * - {@link jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.emulator.DcdcEmulatorDeviceControlling}
 * - {@link jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.v1.DcdcV1DeviceControlling}
 * - {@link jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.v2.DcdcV2DeviceControlling}
 * @author OES Project
 *          
 * DCDC システム向けデバイス制御サービスの親玉 Verticle.
 * {@link jp.co.sony.csl.dcoes.apis.main.app.controller.Controller} Verticle から起動される.
 * システムの種類に応じて以下の種類がある.
 * - {@link jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.emulator.DcdcEmulatorDeviceControlling}
 * - {@link jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.v1.DcdcV1DeviceControlling}
 * - {@link jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.v2.DcdcV2DeviceControlling}
 * @author OES Project
 */
public abstract class DcdcDeviceControlling extends DeviceControlling {
	private static final Logger log = LoggerFactory.getLogger(DcdcDeviceControlling.class);

	/**
	 * Set the mode of the DCDC converter.
	 * The device control state after performing these settings is received by the {@link AsyncResult#result()} method of completionHandler.
	 * @param mode the mode to set. Required
	 * @param gridVoltageV grid voltage value. Required for some modes, not for others
	 * @param gridCurrentA grid current value. Required for some modes, not for others
	 * @param droopRatio the droop ratio. Only required when the mode is {@link DDCon.Mode#VOLTAGE_REFERENCE}
	 * @param completionHandler the completion handler
	 *          
	 * DCDC コンバータのモードを設定する.
	 * completionHandler の {@link AsyncResult#result()} で設定後のデバイス制御状態を受け取る.
	 * @param mode モード. 必須
	 * @param gridVoltageV グリッド電圧値. mode によって要不要あり
	 * @param gridCurrentA グリッド電流値. mode によって要不要あり
	 * @param droopRatio ドループ率. mode が {@link DDCon.Mode#VOLTAGE_REFERENCE} の場合のみ必須
	 * @param completionHandler the completion handler
	 */
	protected abstract void doSetDcdcMode(DDCon.Mode mode, Number gridVoltageV, Number gridCurrentA, Number droopRatio, Handler<AsyncResult<JsonObject>> completionHandler);
	/**
	 * Set the grid voltage of the DCDC converter.
	 * The device control state after performing these settings is received by the {@link AsyncResult#result()} method of completionHandler.
	 * @param gridVoltageV grid voltage value. Required
	 * @param droopRatio the droop ratio. Required
	 * @param completionHandler the completion handler
	 *          
	 * DCDC コンバータのグリッド電圧を設定する.
	 * completionHandler の {@link AsyncResult#result()} で設定後のデバイス制御状態を受け取る.
	 * @param gridVoltageV グリッド電圧値. 必須
	 * @param droopRatio ドループ率. 必須
	 * @param completionHandler the completion handler
	 */
	protected abstract void doSetDcdcVoltage(Number gridVoltageV, Number droopRatio, Handler<AsyncResult<JsonObject>> completionHandler);
	/**
	 * Set the grid voltage of the DCDC converter.
	 * The device control state after performing these settings is received by the {@link AsyncResult#result()} method of completionHandler.
	 * @param gridCurrentA grid current value. Required
	 * @param completionHandler the completion handler
	 *          
	 * DCDC コンバータのグリッド電圧を設定する.
	 * completionHandler の {@link AsyncResult#result()} で設定後のデバイス制御状態を受け取る.
	 * @param gridCurrentA　グリッド電流値. 必須
	 * @param completionHandler the completion handler
	 */
	protected abstract void doSetDcdcCurrent(Number gridCurrentA, Handler<AsyncResult<JsonObject>> completionHandler);

	/**
	 * {@inheritDoc}
	 */
	@Override protected abstract void init(Handler<AsyncResult<Void>> completionHandler);
	/**
	 * {@inheritDoc}
	 * Simply runs {@link WAIT}.
	 *          
	 * {@inheritDoc}
	 * 単純に {@link WAIT} を実行する.
	 */
	@Override protected void doLocalStopWithExclusiveLock(Handler<AsyncResult<JsonObject>> completionHandler) {
		new WAIT(vertx, this, null).execute(completionHandler);
	}
	/**
	 * {@inheritDoc}
	 * Does nothing if the present mode is voltage reference and {@code excludeVoltageReference} is {@code true}.
	 * Otherwise runs {@link Scram}.
	 *          
	 * {@inheritDoc}
	 * 現在のモードが電圧リファレンスで {@code excludeVoltageReference} が {@code true} なら何もしない.
	 * そうでなければ {@link Scram} を実行する.
	 */
	@Override protected void doScramWithExclusiveLock(boolean excludeVoltageReference, Handler<AsyncResult<JsonObject>> completionHandler) {
		DDCon.Mode mode = DDCon.modeFromCode(DataAcquisition.cache.getString("dcdc", "status", "status"));
		if (log.isInfoEnabled()) log.info("mode : " + mode + ", excludeVoltageReference : " + excludeVoltageReference);
		if (DDCon.Mode.VOLTAGE_REFERENCE == mode && excludeVoltageReference) {
			if (log.isInfoEnabled()) log.info("ignore ...");
			completionHandler.handle(Future.succeededFuture(DataAcquisition.cache.getJsonObject("dcdc")));
		} else {
			new Scram(vertx, this, null).execute(completionHandler);
		}
	}
	/**
	 * {@inheritDoc}
	 * Perform device control according to {@ code operation.command} and {@ code operation.params}.
	 * The specific process is {@link #doDcdcControlling_ (String, JsonObject, Handler)}.
	 *          
	 * {@inheritDoc}
	 * {@code operation.command} と {@code operation.params} に従ってデバイス制御を実行する.
	 * 具体的な処理は {@link #doDcdcControlling_(String, JsonObject, Handler)}.
	 */
	@Override protected void doDeviceControllingWithExclusiveLock(JsonObject operation, Handler<AsyncResult<JsonObject>> completionHandler) {
		if (operation != null) {
			String command = operation.getString("command");
			JsonObject params = operation.getJsonObject("params");
			doDcdcControlling_(command, params, completionHandler);
		} else {
			ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, "operation is null", completionHandler);
		}
	}
	/**
	 * 
	 * {@inheritDoc}
	 * Merge the device control state specified by {@code value} with {@link DataAcquisition#cache}.
	 * - Merge {@code value} into {@link DataAcquisition#cache}{@code .dcdc}.
	 * Returns the merged result {@link DataAcquisition#cache} {@code .dcdc}.
	 *          
	 *
	 * {@inheritDoc}
	 * {@code value} で指定したデバイス制御状態を {@link DataAcquisition#cache} にマージする.
	 * - {@code value} を {@link DataAcquisition#cache}{@code .dcdc} にマージ
	 * マージした結果の {@link DataAcquisition#cache}{@code .dcdc} を返す.
	 */
	@Override protected JsonObject mergeDeviceStatus(JsonObject value) {
		DataAcquisition.cache.mergeIn(value, "dcdc");
		return DataAcquisition.cache.getJsonObject("dcdc");
	}

	////

	/**
	 * Check the validity of the grid current setting.
	 * @param mode the mode
	 * @param current current value
	 * @return an error string if the setting deviates from expectations. Otherwise return {@code null}.
	 *          
	 * グリッド電流設定値の正当性をチェックする.
	 * @param mode モード
	 * @param current 電流値
	 * @return 逸脱していたらエラー文字列. 問題なければ {@code null}
	 */
	private String checkCurrentValueRange_(DDCon.Mode mode, Number current) {
		if (current == null) {
			// null is NG
			// null は NG
			String message = "current value is null";
			ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, message);
			return message;
		}
		float value = current.floatValue();
		if (value == 0F) {
			// 0 is OK
			// 0 は OK
			return null;
		} else if (value < 0F) {
			// a negative value is NG
			// 負の値は NG
			String message = "current value should not be negative : " + value;
			ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, message);
			return message;
		}
		Float gridCurrentCapacityA = HwConfigKeeping.gridCurrentCapacityA();
		if (gridCurrentCapacityA == null) {
			// NG due to misconfiguration
			// 設定ミスで NG
			String message = "data deficiency; HWCONFIG.gridCurrentCapacityA : " + gridCurrentCapacityA;
			ErrorUtil.report(vertx, Error.Category.USER, Error.Extent.LOCAL, Error.Level.ERROR, message);
			return message;
		}
		if (gridCurrentCapacityA < value) {
			// NG because this unit's current capacity is exceeded
			// 自ユニットの電流容量オーバーで NG
			String message = "invalid current value : " + value + "; should less than or equal to : " + gridCurrentCapacityA;
			ErrorUtil.report(vertx, Error.Category.HARDWARE, Error.Extent.LOCAL, Error.Level.ERROR, message);
			return message;
		}
		return null;
	}

	////

	/**
	 * Device control.
	 * The device control state after performing these control operations is received by the {@link AsyncResult#result()} method of completionHandler.
	 * Determine the type of processing performed by {@code command}, create an object, and run it.
	 * @param command the command
	 * @param params control parameters
	 * @param completionHandler the completion handler
	 *          
	 * デバイス制御.
	 * completionHandler の {@link AsyncResult#result()} で制御後のデバイス制御状態を受け取る.
	 * {@code command} で処理の種類を決めオブジェクトを生成し実行する.
	 * @param command コマンド
	 * @param params 制御パラメタ
	 * @param completionHandler the completion handler
	 */
	private void doDcdcControlling_(String command, JsonObject params, Handler<AsyncResult<JsonObject>> completionHandler) {
		if (command != null) {
			AbstractDcdcDeviceControllingCommand cmd = null;
			switch (command) {
			case "WAIT":
				cmd = new WAIT(vertx, this, params);
				break;
			case "VOLTAGE_REFERENCE":
				cmd = new VOLTAGE_REFERENCE(vertx, this, params);
				break;
			case "CHARGE":
				cmd = new CHARGE(vertx, this, params);
				break;
			case "DISCHARGE":
				cmd = new DISCHARGE(vertx, this, params);
				break;
			case "voltageReferenceAuthorization":
				cmd = new VoltageReferenceAuthorization(vertx, this, params);
				break;
			case "voltageReferenceWillHandOver":
				cmd = new VoltageReferenceWillHandOver(vertx, this, params);
				break;
			case "voltageReferenceWillTakeOver":
				cmd = new VoltageReferenceWillTakeOver(vertx, this, params);
				break;
			case "voltageReferenceDidHandOver":
				cmd = new VoltageReferenceDidHandOver(vertx, this, params);
				break;
			case "voltageReferenceDidTakeOver":
				cmd = new VoltageReferenceDidTakeOver(vertx, this, params);
				break;
			case "scram":
				cmd = new Scram(vertx, this, params);
				break;
			case "voltage":
				cmd = new GridVoltageStepping(vertx, this, params);
				break;
			case "current":
				cmd = new GridCurrentStepping(vertx, this, params);
				break;
			}
			if (cmd != null) {
				cmd.execute(completionHandler);
			} else {
				ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, "unknown command : " + command, completionHandler);
			}
		} else {
			ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, "illegal parameters; command : " + command + ", params : " + params, completionHandler);
		}
	}

	////

	/**
	 * Set the mode of the DCDC converter.
	 * Used from subclasses of {@link AbstractDcdcDeviceControllingCommand}.
	 * The device control state after performing these settings is received by the {@link AsyncResult#result()} method of completionHandler.
	 * The actual processing is implemented by the method {@link #doSetDcdcMode(jp.co.sony.csl.dcoes.apis.main.app.controller.util.DDCon.Mode, Number, Number, Number, Handler)} of subclasses.
	 * @param mode the mode to set. Required
	 * @param gridVoltageV grid voltage value. Required for some modes, not for others
	 * @param gridCurrentA grid current value. Required for some modes, not for others
	 * @param completionHandler the completion handler
	 *          
	 * DCDC コンバータのモードを設定する.
	 * {@link AbstractDcdcDeviceControllingCommand} のサブクラスから使用される.
	 * completionHandler の {@link AsyncResult#result()} で設定後のデバイス制御状態を受け取る.
	 * 実際の処理はサブクラスの {@link #doSetDcdcMode(jp.co.sony.csl.dcoes.apis.main.app.controller.util.DDCon.Mode, Number, Number, Number, Handler)} で実装する.
	 * @param mode モード. 必須
	 * @param gridVoltageV グリッド電圧値. mode によって要不要あり
	 * @param gridCurrentA グリッド電流値. mode によって要不要あり
	 * @param completionHandler the completion handler
	 */
	public void setDcdcMode(DDCon.Mode mode, Number gridVoltageV, Number gridCurrentA, Handler<AsyncResult<JsonObject>> completionHandler) {
		setDcdcMode(mode, gridVoltageV, gridCurrentA, Integer.valueOf(0), completionHandler);
	}
	/**
	 * Set the mode of the DCDC converter.
	 * Used from subclasses of {@link AbstractDcdcDeviceControllingCommand}.
	 * The device control state after performing these settings is received by the {@link AsyncResult#result()} method of completionHandler.
	 * The actual processing is implemented by the method {@link #doSetDcdcMode(jp.co.sony.csl.dcoes.apis.main.app.controller.util.DDCon.Mode, Number, Number, Number, Handler)} of subclasses.
	 * @param mode the mode to set. Required
	 * @param gridVoltageV grid voltage value. Required for some modes, not for others
	 * @param gridCurrentA grid current value. Required for some modes, not for others
	 * @param droopRatio the droop ratio. Only required when the mode is {@link DDCon.Mode#VOLTAGE_REFERENCE}
	 * @param completionHandler the completion handler
	 *          
	 * DCDC コンバータのモードを設定する.
	 * {@link AbstractDcdcDeviceControllingCommand} のサブクラスから使用される.
	 * completionHandler の {@link AsyncResult#result()} で設定後のデバイス制御状態を受け取る.
	 * 実際の処理はサブクラスの {@link #doSetDcdcMode(jp.co.sony.csl.dcoes.apis.main.app.controller.util.DDCon.Mode, Number, Number, Number, Handler)} で実装する.
	 * @param mode モード. 必須
	 * @param gridVoltageV グリッド電圧値. mode によって要不要あり
	 * @param gridCurrentA グリッド電流値. mode によって要不要あり
	 * @param droopRatio ドループ率. mode が {@link DDCon.Mode#VOLTAGE_REFERENCE} の場合のみ必須
	 * @param completionHandler the completion handler
	 */
	public void setDcdcMode(DDCon.Mode mode, Number gridVoltageV, Number gridCurrentA, Number droopRatio, Handler<AsyncResult<JsonObject>> completionHandler) {
		if (mode != null && gridVoltageV != null && gridCurrentA != null && droopRatio != null) {
			String error = checkCurrentValueRange_(mode, gridCurrentA);
			if (error == null) {
				doSetDcdcMode(mode, gridVoltageV, gridCurrentA, droopRatio, res -> {
					if (res.succeeded()) {
						DDCon.OperationMode targetOperationMode = DDCon.operationModeForMode(mode);
						DDCon.OperationMode resultOperationMode = DDCon.operationModeFromCode(JsonObjectUtil.getString(res.result(), "status", "operationMode"));
						if (targetOperationMode == resultOperationMode) {
							completionHandler.handle(res);
						} else {
							ErrorUtil.reportAndFail(vertx, Error.Category.HARDWARE, Error.Extent.LOCAL, Error.Level.ERROR, "target mode : " + mode + "; result operationMode : " + resultOperationMode + "; should be : " + targetOperationMode, completionHandler);
						}
					} else {
						completionHandler.handle(res);
					}
				});
			} else {
				completionHandler.handle(Future.failedFuture(error));
			}
		} else {
			ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, "illegal parameters; mode : " + mode + ", gridVoltageV : " + gridVoltageV + ", gridCurrentA : " + gridCurrentA + ", droopRatio : " + droopRatio, completionHandler);
		}
	}
	/**
	 * Set the grid voltage of the DCDC converter.
	 * Used from subclasses of {@link AbstractDcdcDeviceControllingCommand}.
	 * The device control state after performing these settings is received by the {@link AsyncResult#result()} method of completionHandler.
	 * The actual processing is implemented by the method {@link #doSetDcdcVoltage (Number, Number, Handler)} of subclasses.
	 * @param gridVoltageV grid voltage value. Required
	 * @param completionHandler the completion handler
	 *          
	 * DCDC コンバータのグリッド電圧を設定する.
	 * {@link AbstractDcdcDeviceControllingCommand} のサブクラスから使用される.
	 * completionHandler の {@link AsyncResult#result()} で設定後のデバイス制御状態を受け取る.
	 * 実際の処理はサブクラスの {@link #doSetDcdcVoltage(Number, Number, Handler)} で実装する.
	 * @param gridVoltageV グリッド電圧値. 必須
	 * @param completionHandler the completion handler
	 */
	public void setDcdcVoltage(Number gridVoltageV, Handler<AsyncResult<JsonObject>> completionHandler) {
		setDcdcVoltage(gridVoltageV, Integer.valueOf(0), completionHandler);
	}
	/**
	 * Set the grid voltage of the DCDC converter.
	 * Used from subclasses of {@link AbstractDcdcDeviceControllingCommand}.
	 * The device control state after performing these settings is received by the {@link AsyncResult#result()} method of completionHandler.
	 * The actual processing is implemented by the method {@link #doSetDcdcVoltage (Number, Number, Handler)} of subclasses.
	 * @param gridVoltageV grid voltage value. Required
	 * @param droopRatio the droop ratio. Required
	 * @param completionHandler the completion handler
	 *          
	 * DCDC コンバータのグリッド電圧を設定する.
	 * {@link AbstractDcdcDeviceControllingCommand} のサブクラスから使用される.
	 * completionHandler の {@link AsyncResult#result()} で設定後のデバイス制御状態を受け取る.
	 * 実際の処理はサブクラスの {@link #doSetDcdcVoltage(Number, Number, Handler)} で実装する.
	 * @param gridVoltageV グリッド電圧値. 必須
	 * @param droopRatio ドループ率. 必須
	 * @param completionHandler the completion handler
	 */
	public void setDcdcVoltage(Number gridVoltageV, Number droopRatio, Handler<AsyncResult<JsonObject>> completionHandler) {
		if (gridVoltageV != null && droopRatio != null) {
			doSetDcdcVoltage(gridVoltageV, droopRatio, completionHandler);
		} else {
			ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, "illegal parameters; gridVoltageV : " + gridVoltageV + ", droopRatio : " + droopRatio, completionHandler);
		}
	}
	/**
	 * Set the grid voltage of the DCDC converter.
	 * Used from subclasses of {@link AbstractDcdcDeviceControllingCommand}.
	 * The device control state after performing these settings is received by the {@link AsyncResult#result()} method of completionHandler.
	 * The actual processing is implemented by the method {@link #doSetDcdcCurrent (Number, Number, Handler)} of subclasses.
	 * @param gridCurrentA grid current value. Required
	 * @param completionHandler the completion handler
	 *          
	 * DCDC コンバータのグリッド電圧を設定する.
	 * {@link AbstractDcdcDeviceControllingCommand} のサブクラスから使用される.
	 * completionHandler の {@link AsyncResult#result()} で設定後のデバイス制御状態を受け取る.
	 * 実際の処理はサブクラスの {@link #doSetDcdcCurrent(Number, Handler)} で実装する.
	 * @param gridCurrentA　グリッド電流値. 必須
	 * @param completionHandler the completion handler
	 */
	public void setDcdcCurrent(Number gridCurrentA, Handler<AsyncResult<JsonObject>> completionHandler) {
		if (gridCurrentA != null) {
			String error = checkCurrentValueRange_(null, gridCurrentA);
			if (error == null) {
				doSetDcdcCurrent(gridCurrentA, completionHandler);
			} else {
				completionHandler.handle(Future.failedFuture(error));
			}
		} else {
			ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, "illegal parameters; gridCurrentA : " + gridCurrentA, completionHandler);
		}
	}

}
