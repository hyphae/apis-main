package jp.co.sony.csl.dcoes.apis.main.util;

import jp.co.sony.csl.dcoes.apis.common.util.vertx.VertxConfig;

public class ApisConfig {

	private ApisConfig() {
	}

	public static String unitId() {
		return VertxConfig.config.getString("unitId");
	}

	public static String unitName() {
		return VertxConfig.config.getString("unitName");
	}

	public static String serialNumber() {
		return VertxConfig.config.getString("serialNumber");
	}

	public static String systemType() {
		return VertxConfig.config.getString("systemType");
	}

	public static Boolean isBatteryCapacityManagementEnabled() {
		return VertxConfig.config.getBoolean(Boolean.FALSE, "batteryCapacityManagement", "enabled");
	}

}
