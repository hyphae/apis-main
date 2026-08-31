package jp.co.sony.csl.dcoes.apis.main.factory.impl.dcdc.v1;

import jp.co.sony.csl.dcoes.apis.main.factory.ControllerFactory;
import jp.co.sony.csl.dcoes.apis.main.factory.Factory;

public class DcdcV1Factory extends Factory {

	@Override
	protected ControllerFactory createControllerFactory() {
		return new DcdcV1ControllerFactory();
	}

}
