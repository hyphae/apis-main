package jp.co.sony.csl.dcoes.apis.main.factory;

import jp.co.sony.csl.dcoes.apis.main.app.controller.DataAcquisition;
import jp.co.sony.csl.dcoes.apis.main.app.controller.DataResponding;
import jp.co.sony.csl.dcoes.apis.main.app.controller.DeviceControlling;

public interface ControllerFactory {

	DataAcquisition createDataAcquisition();

	DataResponding createDataResponding();

	DeviceControlling createDeviceControlling();

}
