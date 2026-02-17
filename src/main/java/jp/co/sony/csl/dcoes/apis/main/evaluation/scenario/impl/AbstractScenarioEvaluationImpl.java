package jp.co.sony.csl.dcoes.apis.main.evaluation.scenario.impl;

import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jp.co.sony.csl.dcoes.apis.common.util.NumberUtil;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectUtil;
import jp.co.sony.csl.dcoes.apis.main.evaluation.scenario.ScenarioEvaluation;

public abstract class AbstractScenarioEvaluationImpl implements ScenarioEvaluation.Impl {
	private static final Logger log = LoggerFactory.getLogger(AbstractScenarioEvaluationImpl.class);

	protected String batteryStatus(JsonObject scenario, JsonObject unitData) {
		Integer remainingWh = JsonObjectUtil.getInteger(unitData, "apis", "remaining_capacity_wh");
		if (remainingWh != null) {
			JsonObject batteryStatuses = JsonObjectUtil.getJsonObject(scenario, "batteryStatus");
			if (batteryStatuses != null) {
				for (String aKey : batteryStatuses.fieldNames()) {
					String[] fromTo = aKey.split("-", 2);
					if (fromTo.length == 2) {
						Integer lowerWh = NumberUtil.toInteger(fromTo[0]);
						Integer upperWh = NumberUtil.toInteger(fromTo[1]);
						if ((lowerWh == null || lowerWh <= remainingWh) && (upperWh == null || remainingWh < upperWh)) {
							String batteryStatus = batteryStatuses.getString(aKey);
							if (log.isDebugEnabled())
								log.debug("batteryStatus : " + batteryStatus);
							return batteryStatus;
						}
					}
				}
				if (log.isWarnEnabled())
					log.warn("no batteryStatus matched; remainingWh : " + remainingWh);
			} else {
				if (log.isWarnEnabled())
					log.warn("no batteryStatus entry in scenario");
			}
		} else {
			if (log.isWarnEnabled())
				log.warn("no apis.remaining_capacity_wh value in unitData");
		}
		return null;
	}

}
