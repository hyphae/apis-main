package jp.co.sony.csl.dcoes.apis.main.evaluation.scenario.impl;

import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import jp.co.sony.csl.dcoes.apis.common.util.NumberUtil;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectUtil;
import jp.co.sony.csl.dcoes.apis.main.evaluation.scenario.ScenarioEvaluation;

/**
 * A base class for the actual class used in SCENARIO evaluation.
 * Implements communication functions.
 * The only actual class is {@link SimpleScenarioEvaluationImpl}.
 * @author OES Project
 *          
 * SCENARIO 評価の実クラスのための元クラス.
 * 共通機能が置いてある.
 * 実クラスは {@link SimpleScenarioEvaluationImpl} のみ.
 * @author OES Project
 */
public abstract class AbstractScenarioEvaluationImpl implements ScenarioEvaluation.Impl {
	private static final Logger log = LoggerFactory.getLogger(AbstractScenarioEvaluationImpl.class);

	/**
	 * Get the status name corresponding to the remaining battery capacity.
	 * @param scenario a SCENARIO object
	 * @param unitData unit data
	 * @return a status name
	 *          
	 * バッテリ残量に対応するステータス名を取得する.
	 * @param scenario SCENARIO オブジェクト
	 * @param unitData ユニットデータ
	 * @return ステータス名
	 */
	protected String batteryStatus(JsonObject scenario, JsonObject unitData) {
		Integer remainingWh = JsonObjectUtil.getInteger(unitData, "apis", "remaining_capacity_wh");
		if (remainingWh != null) {
			JsonObject batteryStatuses = JsonObjectUtil.getJsonObject(scenario, "batteryStatus");
			if (batteryStatuses != null) {
				for (String aKey : batteryStatuses.fieldNames()) {
					// The status name for each remaining battery capacity is registered with a key in the format "minimum remaining power-maximum remaining power".
					// バッテリ残量ごとのステータス名は "最低残電力-最高残電力" というフォーマットのキーで登録されている
					String[] fromTo = aKey.split("-", 2);
					if (fromTo.length == 2) {
						// TODO: Allow defaults to be specified in NumberUtil.toInteger()
						// TODO : NumberUtil.toInteger() にデフォルトを指定できるようにする
						Integer lowerWh = NumberUtil.toInteger(fromTo[0]);
						Integer upperWh = NumberUtil.toInteger(fromTo[1]);
						// TODO: This complicated if statement could be eliminated by specifying a default range from Integer.MAX_VALUE to Integer.MIN_VALUE
						// TODO : デフォルトに Integer.MAX_VALUE と Integer.MIN_VALUE を指定すれば null のややこしい if 文がなくなる
						if ((lowerWh == null || lowerWh <= remainingWh) && (upperWh == null || remainingWh < upperWh)) {
							// If the "minimum residual power" is empty, or if the residual power is at least the "minimum residual power" and the "maximum residual power" is empty, or if the residual power is below the "maximum residual power"
							// "最低残電力" が空または "最低残電力" 以上 かつ "最高残電力" が空または "最高残電力" 未満なら
							String batteryStatus = batteryStatuses.getString(aKey);
							if (log.isDebugEnabled()) log.debug("batteryStatus : " + batteryStatus);
							// Return
							// 返す
							return batteryStatus;
						}
					}
				}
				if (log.isWarnEnabled()) log.warn("no batteryStatus matched; remainingWh : " + remainingWh);
			} else {
				if (log.isWarnEnabled()) log.warn("no batteryStatus entry in scenario");
			}
		} else {
			if (log.isWarnEnabled()) log.warn("no apis.remaining_capacity_wh value in unitData");
		}
		return null;
	}

}
