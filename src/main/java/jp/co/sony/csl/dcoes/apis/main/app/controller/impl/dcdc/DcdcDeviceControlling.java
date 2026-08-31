package jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectUtil;
import jp.co.sony.csl.dcoes.apis.main.app.HwConfigKeeping;
import jp.co.sony.csl.dcoes.apis.main.app.controller.DataAcquisition;
import jp.co.sony.csl.dcoes.apis.main.app.controller.DeviceControlling;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.device_controlling.AbstractDcdcDeviceControllingCommand;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.device_controlling.CHARGE;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.device_controlling.Checkpoint;
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

public abstract class DcdcDeviceControlling extends DeviceControlling {
	private static final Logger log = LoggerFactory.getLogger(DcdcDeviceControlling.class);

	protected abstract void doSetDcdcMode(DDCon.Mode mode, Number gridVoltageV, Number gridCurrentA, Number droopRatio,
			Handler<AsyncResult<JsonObject>> onComplete);

	protected abstract void doSetDcdcVoltage(Number gridVoltageV, Number droopRatio,
			Handler<AsyncResult<JsonObject>> onComplete);

	protected abstract void doSetDcdcCurrent(Number gridCurrentA, Handler<AsyncResult<JsonObject>> onComplete);

	@Override
	protected abstract void init(Handler<AsyncResult<Void>> onComplete);

	@Override
	protected void doLocalStopWithExclusiveLock(Handler<AsyncResult<JsonObject>> onComplete) {
		new WAIT(vertx, this, null).execute(onComplete);
	}

	@Override
	protected void doScramWithExclusiveLock(boolean excludeVoltageReference,
			Handler<AsyncResult<JsonObject>> onComplete) {
		DDCon.Mode mode = DDCon.modeFromCode(DataAcquisition.cache.getString("dcdc", "status", "status"));
		if (log.isInfoEnabled())
			log.info("mode : " + mode + ", excludeVoltageReference : " + excludeVoltageReference);
		if (DDCon.Mode.VOLTAGE_REFERENCE == mode && excludeVoltageReference) {
			if (log.isInfoEnabled())
				log.info("ignore ...");
			onComplete.handle(Future.succeededFuture(DataAcquisition.cache.getJsonObject("dcdc")));
		} else {
			new Scram(vertx, this, null).execute(onComplete);
		}
	}

	@Override
	protected void doDeviceControllingWithExclusiveLock(JsonObject operation,
			Handler<AsyncResult<JsonObject>> onComplete) {
		if (operation != null) {
			String command = operation.getString("command");
			JsonObject params = operation.getJsonObject("params");
			doDcdcControlling_(command, params, onComplete);
		} else {
			ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR,
					"operation is null", onComplete);
		}
	}

	@Override
	protected JsonObject mergeDeviceStatus(JsonObject value) {
		DataAcquisition.cache.mergeIn(value, "dcdc");
		return DataAcquisition.cache.getJsonObject("dcdc");
	}

	private String checkCurrentValueRange_(DDCon.Mode mode, Number current) {
		if (current == null) {
			String message = "current value is null";
			ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, message);
			return message;
		}
		float value = current.floatValue();
		if (value == 0F) {
			return null;
		} else if (value < 0F) {
			String message = "current value should not be negative : " + value;
			ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, message);
			return message;
		}
		Float gridCurrentCapacityA = HwConfigKeeping.gridCurrentCapacityA();
		if (gridCurrentCapacityA == null) {
			String message = "data deficiency; HWCONFIG.gridCurrentCapacityA : " + gridCurrentCapacityA;
			ErrorUtil.report(vertx, Error.Category.USER, Error.Extent.LOCAL, Error.Level.ERROR, message);
			return message;
		}
		if (gridCurrentCapacityA < value) {
			String message = "invalid current value : " + value + "; should less than or equal to : "
					+ gridCurrentCapacityA;
			ErrorUtil.report(vertx, Error.Category.HARDWARE, Error.Extent.LOCAL, Error.Level.ERROR, message);
			return message;
		}
		return null;
	}

	private void doDcdcControlling_(String command, JsonObject params, Handler<AsyncResult<JsonObject>> onComplete) {
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
				cmd.execute(onComplete);
			} else {
				ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR,
						"unknown command : " + command, onComplete);
			}
		} else {
			ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR,
					"illegal parameters; command : " + command + ", params : " + params, onComplete);
		}
	}

	public void setDcdcMode(DDCon.Mode mode, Number gridVoltageV, Number gridCurrentA,
			Handler<AsyncResult<JsonObject>> onComplete) {
		setDcdcMode(mode, gridVoltageV, gridCurrentA, Integer.valueOf(0), onComplete);
	}

	public void setDcdcMode(DDCon.Mode mode, Number gridVoltageV, Number gridCurrentA, Number droopRatio,
			Handler<AsyncResult<JsonObject>> onComplete) {
		if (mode != null && gridVoltageV != null && gridCurrentA != null && droopRatio != null) {
			String error = checkCurrentValueRange_(mode, gridCurrentA);
			if (error == null) {
				doSetDcdcMode(mode, gridVoltageV, gridCurrentA, droopRatio, res -> {
					if (res.succeeded()) {
						DDCon.OperationMode targetOperationMode = DDCon.operationModeForMode(mode);
						DDCon.OperationMode resultOperationMode = DDCon.operationModeFromCode(
								JsonObjectUtil.getString(res.result(), "status", "operationMode"));
						if (targetOperationMode == resultOperationMode) {
							onComplete.handle(res);
						} else {
							ErrorUtil
									.reportAndFail(vertx, Error.Category.HARDWARE, Error.Extent.LOCAL,
											Error.Level.ERROR, "target mode : " + mode + "; result operationMode : "
													+ resultOperationMode + "; should be : " + targetOperationMode,
											onComplete);
						}
					} else {
						onComplete.handle(res);
					}
				});
			} else {
				onComplete.handle(Future.failedFuture(error));
			}
		} else {
			ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR,
					"illegal parameters; mode : " + mode + ", gridVoltageV : " + gridVoltageV + ", gridCurrentA : "
							+ gridCurrentA + ", droopRatio : " + droopRatio,
					onComplete);
		}
	}

	public void setDcdcVoltage(Number gridVoltageV, Handler<AsyncResult<JsonObject>> onComplete) {
		setDcdcVoltage(gridVoltageV, Integer.valueOf(0), onComplete);
	}

	public void setDcdcVoltage(Number gridVoltageV, Number droopRatio, Handler<AsyncResult<JsonObject>> onComplete) {
		if (gridVoltageV != null && droopRatio != null) {
			doSetDcdcVoltage(gridVoltageV, droopRatio, onComplete);
		} else {
			ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR,
					"illegal parameters; gridVoltageV : " + gridVoltageV + ", droopRatio : " + droopRatio, onComplete);
		}
	}

	public void setDcdcCurrent(Number gridCurrentA, Handler<AsyncResult<JsonObject>> onComplete) {
		if (gridCurrentA != null) {
			String error = checkCurrentValueRange_(null, gridCurrentA);
			if (error == null) {
				doSetDcdcCurrent(gridCurrentA, onComplete);
			} else {
				onComplete.handle(Future.failedFuture(error));
			}
		} else {
			ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR,
					"illegal parameters; gridCurrentA : " + gridCurrentA, onComplete);
		}
	}

}
