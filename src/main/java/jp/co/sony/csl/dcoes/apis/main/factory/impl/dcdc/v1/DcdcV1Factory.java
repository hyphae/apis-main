package jp.co.sony.csl.dcoes.apis.main.factory.impl.dcdc.v1;

import jp.co.sony.csl.dcoes.apis.main.factory.ControllerFactory;
import jp.co.sony.csl.dcoes.apis.main.factory.Factory;

/**
 * A factory for DC system implementation factories.
 * Connects to dcdc_controller and EMU-Driver.
 * @author OES Project
 *          
 * DC 系の実機用ファクトリのファクトリ.
 * 接続先は dcdc_controller と EMU-Driver.
 * @author OES Project
 */
public class DcdcV1Factory extends Factory {

	/**
	 * {@inheritDoc}
	 */
	@Override protected ControllerFactory createControllerFactory() {
		return new DcdcV1ControllerFactory();
	}

}
