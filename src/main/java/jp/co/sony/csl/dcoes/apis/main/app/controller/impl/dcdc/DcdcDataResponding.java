package jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc;

import io.vertx.core.json.JsonObject;
import jp.co.sony.csl.dcoes.apis.main.app.controller.DataAcquisition;
import jp.co.sony.csl.dcoes.apis.main.app.controller.DataResponding;

/**
 * Data response service Verticle for the DCDC system.
 * Launched from the {@link jp.co.sony.csl.dcoes.apis.main.app.controller.Controller} Verticle.
 * @author OES Project
 *          
 * DCDC システム向けデータ応答サービス Verticle.
 * {@link jp.co.sony.csl.dcoes.apis.main.app.controller.Controller} Verticle から起動される.
 * @author OES Project
 */
public class DcdcDataResponding extends DataResponding {

	/**
	 * {@inheritDoc}
	 * Return {@link DataAcquisition#cache}{@code .dcdc}.
	 *          
	 * {@inheritDoc}
	 * {@link DataAcquisition#cache}{@code .dcdc} を返す.
	 */
	@Override protected JsonObject cachedDeviceStatus() {
		return DataAcquisition.cache.getJsonObject("dcdc");
	}

}
