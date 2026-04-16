package jp.co.sony.csl.dcoes.apis.main.factory.impl.dcdc.v2;

import jp.co.sony.csl.dcoes.apis.main.factory.ControllerFactory;
import jp.co.sony.csl.dcoes.apis.main.factory.Factory;

/**
 * A factory for DC system implementation factories.
 * Connects to dcdc_batt_comm.
 * @author OES Project
 *          
 * DC 系の実機用ファクトリのファクトリ.
 * 接続先は dcdc_batt_comm.
 * @author OES Project
 */
public class DcdcV2Factory extends Factory {

	/**
	 * {@inheritDoc}
	 */
	@Override protected ControllerFactory createControllerFactory() {
		return new DcdcV2ControllerFactory();
	}

}
