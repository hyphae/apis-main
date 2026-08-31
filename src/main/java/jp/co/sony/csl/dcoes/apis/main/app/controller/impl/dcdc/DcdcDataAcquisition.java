package jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import jp.co.sony.csl.dcoes.apis.main.app.controller.DataAcquisition;

public abstract class DcdcDataAcquisition extends DataAcquisition {

	@Override
	protected abstract void init(Handler<AsyncResult<Void>> onComplete);

	@Override
	protected abstract void getData(Handler<AsyncResult<JsonObject>> onComplete);

	@Override
	protected abstract void getDeviceStatus(Handler<AsyncResult<JsonObject>> onComplete);

	@Override
	protected JsonObject mergeDeviceStatus(JsonObject value) {
		cache.mergeIn(value, "dcdc");
		return cache.getJsonObject("dcdc");
	}

}
