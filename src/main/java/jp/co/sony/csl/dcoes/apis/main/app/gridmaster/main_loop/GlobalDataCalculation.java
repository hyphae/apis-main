package jp.co.sony.csl.dcoes.apis.main.app.gridmaster.main_loop;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectUtil;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectWrapper;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

public class GlobalDataCalculation {
	private static final Logger log = LoggerFactory.getLogger(GlobalDataCalculation.class);

	public static final JsonObjectWrapper cache = new JsonObjectWrapper();

	private GlobalDataCalculation() {
	}

	public static void execute(Vertx vertx, Handler<AsyncResult<JsonObject>> onComplete) {
		JsonObject unitData = DealExecution.unitDataCache.jsonObject();
		if (unitData != null) {
			JsonObject globalData = new JsonObject();
			doBasic_(vertx, unitData, globalData);
			doRsoc_(vertx, unitData, globalData);
			doRemainingCapacity_(vertx, unitData, globalData);
			cache.setJsonObject(globalData);
		}
		if (log.isDebugEnabled())
			log.debug("global data : " + cache.jsonObject());
		onComplete.handle(Future.succeededFuture(cache.jsonObject()));
	}

	////

	private static void doBasic_(Vertx vertx, JsonObject unitData, JsonObject result) {
		result.put("numberOfUnits", unitData.size());
		String[] unitIds = unitData.fieldNames().toArray(new String[unitData.size()]);
		Arrays.sort(unitIds);
		result.put("unitIds", new JsonArray(Arrays.asList(unitIds)));
	}

	private static void doRsoc_(Vertx vertx, JsonObject unitData, JsonObject result) {
		float sum = 0;
		int n = 0;
		for (String aUnitId : unitData.fieldNames()) {
			JsonObject aUnitData = unitData.getJsonObject(aUnitId);
			Float value = JsonObjectUtil.getFloat(aUnitData, "battery", "rsoc");
			if (value != null) {
				sum += value;
				n++;
			} else {
				ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.WARN,
						"no battery.rsoc value; unitId : " + aUnitId);
			}
		}
		float average = sum / n;
		result.put("averageRsoc", average);
		result.put("averageRsocNumberOfUnits", n);
	}

	private static void doRemainingCapacity_(Vertx vertx, JsonObject unitData, JsonObject result) {
		float sum = 0;
		int n = 0;
		for (String aUnitId : unitData.fieldNames()) {
			JsonObject aUnitData = unitData.getJsonObject(aUnitId);
			Float value = JsonObjectUtil.getFloat(aUnitData, "apis", "remaining_capacity_wh");
			if (value != null) {
				sum += value;
				n++;
			} else {
				ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.WARN,
						"no apis.remaining_capacity_wh value; unitId : " + aUnitId);
			}
		}
		float average = sum / n;
		result.put("averageRemainingCapacityWh", average);
		result.put("averageRemainingCapacityWhNumberOfUnits", n);
	}

}
