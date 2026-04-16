package jp.co.sony.csl.dcoes.apis.main.factory.impl.dcdc.v1;

import jp.co.sony.csl.dcoes.apis.main.app.controller.DataAcquisition;
import jp.co.sony.csl.dcoes.apis.main.app.controller.DataResponding;
import jp.co.sony.csl.dcoes.apis.main.app.controller.DeviceControlling;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.DcdcDataResponding;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.v1.DcdcV1DataAcquisition;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.v1.DcdcV1DeviceControlling;
import jp.co.sony.csl.dcoes.apis.main.factory.ControllerFactory;

/**
 * An implementation controller factory for DC system emulators.
 * Connects to dcdc_controller and EMU-Driver.
 * @author OES Project
 *          
 * DC 系の実機用 Controller ファクトリ.
 * 接続先は dcdc_controller と EMU-Driver.
 * @author OES Project
 */
public class DcdcV1ControllerFactory implements ControllerFactory {

	/**
	 * {@inheritDoc}
	 */
	@Override public DataAcquisition createDataAcquisition() {
		return new DcdcV1DataAcquisition();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override public DataResponding createDataResponding() {
		return new DcdcDataResponding();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override public DeviceControlling createDeviceControlling() {
		return new DcdcV1DeviceControlling();
	}

}
