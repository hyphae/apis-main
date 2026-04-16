package jp.co.sony.csl.dcoes.apis.main.factory.impl.dcdc.emulator;

import jp.co.sony.csl.dcoes.apis.main.factory.ControllerFactory;
import jp.co.sony.csl.dcoes.apis.main.factory.Factory;

/**
 * A factory for DC system emulator factories.
 * Connects to an emulator.
 * @author OES Project
 *          
 * DC 系のエミュレータ用ファクトリのファクトリ.
 * 接続先は emulator.
 * @author OES Project
 */
public class DcdcEmulatorFactory extends Factory {

	/**
	 * {@inheritDoc}
	 */
	@Override protected ControllerFactory createControllerFactory() {
		return new DcdcEmulatorControllerFactory();
	}

}
