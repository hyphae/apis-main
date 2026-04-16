package jp.co.sony.csl.dcoes.apis.main.factory.impl.dcdc.emulator;

import jp.co.sony.csl.dcoes.apis.main.app.controller.DataAcquisition;
import jp.co.sony.csl.dcoes.apis.main.app.controller.DataResponding;
import jp.co.sony.csl.dcoes.apis.main.app.controller.DeviceControlling;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.DcdcDataResponding;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.emulator.DcdcEmulatorDataAcquisition;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.emulator.DcdcEmulatorDeviceControlling;
import jp.co.sony.csl.dcoes.apis.main.factory.ControllerFactory;

/**
 * A controller factory for DC system emulators.
 * Connects to an emulator.
 * @author OES Project
 *          
 * DC 系のエミュレータ用 Controller ファクトリ.
 * 接続先は emulator.
 * @author OES Project
 */
public class DcdcEmulatorControllerFactory implements ControllerFactory {

	/**
	 * {@inheritDoc}
	 */
	@Override public DataAcquisition createDataAcquisition() {
		return new DcdcEmulatorDataAcquisition();
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
		return new DcdcEmulatorDeviceControlling();
	}

}
