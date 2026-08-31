package jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc;

import io.vertx.core.json.JsonObject;
import jp.co.sony.csl.dcoes.apis.main.app.controller.DataAcquisition;
import jp.co.sony.csl.dcoes.apis.main.app.controller.DataResponding;

public class DcdcDataResponding extends DataResponding {

	@Override
	protected JsonObject cachedDeviceStatus() {
		return DataAcquisition.cache.getJsonObject("dcdc");
	}

}
