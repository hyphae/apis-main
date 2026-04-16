package jp.co.sony.csl.dcoes.apis.main.factory.impl.dcdc.v2;

import jp.co.sony.csl.dcoes.apis.main.app.controller.DataAcquisition;
import jp.co.sony.csl.dcoes.apis.main.app.controller.DataResponding;
import jp.co.sony.csl.dcoes.apis.main.app.controller.DeviceControlling;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.DcdcDataResponding;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.v2.DcdcV2DataAcquisition;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.v2.DcdcV2DeviceControlling;
import jp.co.sony.csl.dcoes.apis.main.factory.ControllerFactory;

/**
 * An implementation controller factory for DC system emulators.
 * Connects to dcdc_batt_comm.
 * @author OES Project
 *          
 * DC 系の実機用 Controller ファクトリ.
 * 接続先は dcdc_batt_comm.
 * @author OES Project
 */
public class DcdcV2ControllerFactory implements ControllerFactory {

	/**
	 * {@inheritDoc}
	 */
	@Override public DataAcquisition createDataAcquisition() {
		return new DcdcV2DataAcquisition();
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
		return new DcdcV2DeviceControlling();
	}

}
