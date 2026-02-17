package jp.co.sony.csl.dcoes.apis.main.factory.impl.dcdc.emulator;

import jp.co.sony.csl.dcoes.apis.main.app.controller.DataAcquisition;
import jp.co.sony.csl.dcoes.apis.main.app.controller.DataResponding;
import jp.co.sony.csl.dcoes.apis.main.app.controller.DeviceControlling;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.DcdcDataResponding;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.emulator.DcdcEmulatorDataAcquisition;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.emulator.DcdcEmulatorDeviceControlling;
import jp.co.sony.csl.dcoes.apis.main.factory.ControllerFactory;

public class DcdcEmulatorControllerFactory implements ControllerFactory {

	@Override
	public DataAcquisition createDataAcquisition() {
		return new DcdcEmulatorDataAcquisition();
	}

	@Override
	public DataResponding createDataResponding() {
		return new DcdcDataResponding();
	}

	@Override
	public DeviceControlling createDeviceControlling() {
		return new DcdcEmulatorDeviceControlling();
	}

}
