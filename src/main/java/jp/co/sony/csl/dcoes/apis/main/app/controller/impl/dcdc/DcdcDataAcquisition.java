package jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import jp.co.sony.csl.dcoes.apis.main.app.controller.DataAcquisition;

/**
 * Data acquisition service object Verticle for the DCDC system.
 * Launched from the {@link jp.co.sony.csl.dcoes.apis.main.app.controller.Controller} Verticle.
 * The following types are available, depending on the type of system.
 * - {@link jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.emulator.DcdcEmulatorDataAcquisition}
 * - {@link jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.v1.DcdcV1DataAcquisition}
 * - {@link jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.v2.DcdcV2DataAcquisition}
 * @author OES Project
 *          
 * DCDC システム向けデータ取得サービスの親玉 Verticle.
 * {@link jp.co.sony.csl.dcoes.apis.main.app.controller.Controller} Verticle から起動される.
 * システムの種類に応じて以下の種類がある.
 * - {@link jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.emulator.DcdcEmulatorDataAcquisition}
 * - {@link jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.v1.DcdcV1DataAcquisition}
 * - {@link jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.v2.DcdcV2DataAcquisition}
 * @author OES Project
 */
public abstract class DcdcDataAcquisition extends DataAcquisition {

	/**
	 * {@inheritDoc}
	 */
	@Override protected abstract void init(Handler<AsyncResult<Void>> completionHandler);
	/**
	 * {@inheritDoc}
	 */
	@Override protected abstract void getData(Handler<AsyncResult<JsonObject>> completionHandler);
	/**
	 * {@inheritDoc}
	 */
	@Override protected abstract void getDeviceStatus(Handler<AsyncResult<JsonObject>> completionHandler);
	/**
	 * {@inheritDoc}
	 * Merge the device control state specified by {@code value} with {@link #cache}.
	 * - Merge {@code value} into {@link #cache}{@code .dcdc}
	 * Returns the merged result {@link #cache} {@code .dcdc}.
	 *          
	 * {@inheritDoc}
	 * {@code value} で指定したデバイス制御状態を {@link #cache} にマージする.
	 * - {@code value} を {@link #cache}{@code .dcdc} にマージ
	 * マージした結果の {@link #cache}{@code .dcdc} を返す.
	 */
	@Override protected JsonObject mergeDeviceStatus(JsonObject value) {
		cache.mergeIn(value, "dcdc");
		return cache.getJsonObject("dcdc");
	}

}
