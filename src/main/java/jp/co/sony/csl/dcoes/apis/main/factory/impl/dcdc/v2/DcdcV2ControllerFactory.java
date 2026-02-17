package jp.co.sony.csl.dcoes.apis.main.factory.impl.dcdc.v2;

import jp.co.sony.csl.dcoes.apis.main.app.controller.DataAcquisition;
import jp.co.sony.csl.dcoes.apis.main.app.controller.DataResponding;
import jp.co.sony.csl.dcoes.apis.main.app.controller.DeviceControlling;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.DcdcDataResponding;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.v2.DcdcV2DataAcquisition;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.v2.DcdcV2DeviceControlling;
import jp.co.sony.csl.dcoes.apis.main.factory.ControllerFactory;

public class DcdcV2ControllerFactory implements ControllerFactory {

	@Override
	public DataAcquisition createDataAcquisition() {
		return new DcdcV2DataAcquisition();
	}

	@Override
	public DataResponding createDataResponding() {
		return new DcdcDataResponding();
	}

	@Override
	public DeviceControlling createDeviceControlling() {
		return new DcdcV2DeviceControlling();
	}

}
