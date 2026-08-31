package jp.co.sony.csl.dcoes.apis.main.factory.impl.dcdc.emulator;

import jp.co.sony.csl.dcoes.apis.main.factory.ControllerFactory;
import jp.co.sony.csl.dcoes.apis.main.factory.Factory;

public class DcdcEmulatorFactory extends Factory {

	@Override
	protected ControllerFactory createControllerFactory() {
		return new DcdcEmulatorControllerFactory();
	}

}
