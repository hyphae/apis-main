package jp.co.sony.csl.dcoes.apis.main.factory.impl.dcdc.v2;

import jp.co.sony.csl.dcoes.apis.main.factory.ControllerFactory;
import jp.co.sony.csl.dcoes.apis.main.factory.Factory;

public class DcdcV2Factory extends Factory {

	@Override
	protected ControllerFactory createControllerFactory() {
		return new DcdcV2ControllerFactory();
	}

}
