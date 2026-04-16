package jp.co.sony.csl.dcoes.apis.main.app.gridmaster.main_loop;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.Arrays;

import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectUtil;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectWrapper;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

/**
 * Calculate the state of the entire grid.
 * Called periodically from {@link jp.co.sony.csl.dcoes.apis.main.app.gridmaster.MainLoop}.
 * Calculate and retain the following.
 * - numberOfUnits: The number of units
 * - unitIds: A list of unit IDs
 * - averageRsoc: The average rsoc value of all units
 * - averageRsocNumberOfUnits: The number of units used to calculate averageRsoc (should be the same as numberOfUnits, but there may be some units that do not have an rsoc value)
 * - averageRemainingCapacityWh: The average remaining battery capacity of all units
 * - averageRemainingCapacityWhNumberOfUnits: The number of units used to calculate averageRemainingCapacityWh (should be the same as numberOfUnits, but there may be some units that do not have a remaining battery capacity value)
 * @author OES Project
 *          
 * グリッド全体の状態を計算する.
 * {@link jp.co.sony.csl.dcoes.apis.main.app.gridmaster.MainLoop} から定期的に呼ばれる.
 * 以下を計算し保持する.
 * - numberOfUnits : ユニット数
 * - unitIds : ユニット ID のリスト
 * - averageRsoc : rsoc の全ユニット平均値
 * - averageRsocNumberOfUnits : averageRsoc 算出に用いたユニット数 ( numberOfUnits と同じはずであるが rsoc 値を持たないユニットがある可能性があるため )
 * - averageRemainingCapacityWh : バッテリ残量の全ユニット平均値
 * - averageRemainingCapacityWhNumberOfUnits : averageRemainingCapacityWh 算出に用いたユニット数 ( numberOfUnits と同じはずであるがバッテリ残量値を持たないユニットがある可能性があるため )
 * @author OES Project
 */
public class GlobalDataCalculation {
	private static final Logger log = LoggerFactory.getLogger(GlobalDataCalculation.class);

	/**
	 * A cache that retains calculation results.
	 *          
	 * 計算結果を保持しておくキャッシュ.
	 */
	public static final JsonObjectWrapper cache = new JsonObjectWrapper();

	private GlobalDataCalculation() { }

	/**
	 * Processing called from {@link jp.co.sony.csl.dcoes.apis.main.app.gridmaster.MainLoop}.
	 * Calculate and retain the following.
	 * - numberOfUnits: The number of units
	 * - unitIds: A list of unit IDs
	 * - averageRsoc: The average rsoc value of all units
	 * - averageRsocNumberOfUnits: The number of units used to calculate averageRsoc (should be the same as numberOfUnits, but there may be some units that do not have an rsoc value)
	 * - averageRemainingCapacityWh: The average remaining battery capacity of all units
	 * - averageRemainingCapacityWhNumberOfUnits: The number of units used to calculate averageRemainingCapacityWh (should be the same as numberOfUnits, but there may be some units that do not have a remaining battery capacity value)
	 * @param vertx a vertx object
	 * @param completionHandler the completion handler
	 *          
	 * {@link jp.co.sony.csl.dcoes.apis.main.app.gridmaster.MainLoop} から呼ばれる処理.
	 * 以下を計算し保持する.
	 * - numberOfUnits : ユニット数
	 * - unitIds : ユニット ID のリスト
	 * - averageRsoc : rsoc の全ユニット平均値
	 * - averageRsocNumberOfUnits : averageRsoc 算出に用いたユニット数 ( numberOfUnits と同じはずであるが rsoc 値を持たないユニットがある可能性があるため )
	 * - averageRemainingCapacityWh : バッテリ残量の全ユニット平均値
	 * - averageRemainingCapacityWhNumberOfUnits : averageRemainingCapacityWh 算出に用いたユニット数 ( numberOfUnits と同じはずであるがバッテリ残量値を持たないユニットがある可能性があるため )
	 * @param vertx vertx オブジェクト
	 * @param completionHandler the completion handler
	 */
	public static void execute(Vertx vertx, Handler<AsyncResult<JsonObject>> completionHandler) {
		JsonObject unitData = DealExecution.unitDataCache.jsonObject();
		if (unitData != null) {
			JsonObject globalData = new JsonObject();
			doBasic_(vertx, unitData, globalData);
			doRsoc_(vertx, unitData, globalData);
			doRemainingCapacity_(vertx, unitData, globalData);
			cache.setJsonObject(globalData);
		}
		if (log.isDebugEnabled()) log.debug("global data : " + cache.jsonObject());
		completionHandler.handle(Future.succeededFuture(cache.jsonObject()));
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
				ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.WARN, "no battery.rsoc value; unitId : " + aUnitId);
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
				ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.WARN, "no apis.remaining_capacity_wh value; unitId : " + aUnitId);
			}
		}
		float average = sum / n;
		result.put("averageRemainingCapacityWh", average);
		result.put("averageRemainingCapacityWhNumberOfUnits", n);
	}

}
