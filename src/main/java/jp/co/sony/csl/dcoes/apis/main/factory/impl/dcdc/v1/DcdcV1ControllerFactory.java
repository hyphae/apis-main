package jp.co.sony.csl.dcoes.apis.main.factory.impl.dcdc.v1;

import jp.co.sony.csl.dcoes.apis.main.app.controller.DataAcquisition;
import jp.co.sony.csl.dcoes.apis.main.app.controller.DataResponding;
import jp.co.sony.csl.dcoes.apis.main.app.controller.DeviceControlling;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.DcdcDataResponding;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.v1.DcdcV1DataAcquisition;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.v1.DcdcV1DeviceControlling;
import jp.co.sony.csl.dcoes.apis.main.factory.ControllerFactory;

public class DcdcV1ControllerFactory implements ControllerFactory {

	@Override
	public DataAcquisition createDataAcquisition() {
		return new DcdcV1DataAcquisition();
	}

	@Override
	public DataResponding createDataResponding() {
		return new DcdcDataResponding();
	}

	@Override
	public DeviceControlling createDeviceControlling() {
		return new DcdcV1DeviceControlling();
	}

}
