package jp.co.sony.csl.dcoes.apis.main.util;

import jp.co.sony.csl.dcoes.apis.common.util.vertx.VertxConfig;

/**
 * A dedicated CONFIG access tool for apis-main.
 * Only provides very limited support.
 * @author OES Project
 *          
 * apis-main 専用の CONFIG アクセスツール.
 * ごく一部しかサポートしていない.
 * @author OES Project
 */
public class ApisConfig {

	private ApisConfig() { }

	/**
	 * Get the unit ID from CONFIG.
	 * {@code CONFIG.unitId}.
	 * @return unit ID
	 *          
	 * CONFIG からユニット ID を取得.
	 * {@code CONFIG.unitId}.
	 * @return ユニット ID
	 */
	public static String unitId() {
		return VertxConfig.config.getString("unitId");
	}
	/**
	 * Get the unit name from CONFIG.
	 * {@code CONFIG.unitName}.
	 * @return unit name
	 *          
	 * CONFIG からユニット名を取得.
	 * {@code CONFIG.unitName}.
	 * @return ユニット名
	 */
	public static String unitName() {
		return VertxConfig.config.getString("unitName");
	}
	/**
	 * Get the serial number string from CONFIG.
	 * {@code CONFIG.serialNumber}.
	 * @return serial number string
	 *          
	 * CONFIG からシリアル番号文字列を取得.
	 * {@code CONFIG.serialNumber}.
	 * @return シリアル番号文字列
	 */
	public static String serialNumber() {
		return VertxConfig.config.getString("serialNumber");
	}

	/**
	 * Get a string indicating the system type from CONFIG.
	 * {@code CONFIG.systemType}.
	 * The value is one of the following.
	 * - {@code dcdc_emulator}: emulator
	 * - {@code dcdc_v1}:       dcdc_controller & EMU-Driver
	 * - {@code dcdc_v2}:       dcdc_batt_comm
	 * @return a string indicating the system type
	 *          
	 * CONFIG からシステムの種類を示す文字列を取得.
	 * {@code CONFIG.systemType}.
	 * 値は以下のいずれか.
	 * - {@code dcdc_emulator} : emulator
	 * - {@code dcdc_v1}       : dcdc_controller ＆ EMU-Driver
	 * - {@code dcdc_v2}       : dcdc_batt_comm
	 * @return システムの種類を示す文字列
	 */
	public static String systemType() {
		return VertxConfig.config.getString("systemType");
	}

	/**
	 * Get the battery capacity management function enabled flag from CONFIG.
	 * {@code CONFIG.batteryCapacityManagement.enabled}.
	 * Whether or not the battery capacity management function is enabled.
	 * When multiple apis-mains share a single battery, as in gateway operation or the like, perform coordinated management of battery current capacity with the other apis-mains.
	 * At present, all the apis-mains to be coordinated must exist on the same computer (because coordination management is implemented using the file system).
	 * Default: {@code false}
	 * @return valid flag
	 *          
	 * CONFIG からバッテリ容量管理機能の有効フラグを取得.
	 * {@code CONFIG.batteryCapacityManagement.enabled}.
	 * バッテリ容量管理機能を有効にするか否か.
	 * Gateway 運用など一つのバッテリを複数の apis-main で共有している場合に他の apis-main とバッテリ電流容量を協調管理する.
	 * 現状は協調管理する apis-main が全て同一コンピュータ上にある必要がある ( ファイルシステムを用いて協調管理するため ).
	 * デフォルト : {@code false}
	 * @return 有効フラグ
	 */
	public static Boolean isBatteryCapacityManagementEnabled() {
		return VertxConfig.config.getBoolean(Boolean.FALSE, "batteryCapacityManagement", "enabled");
	}

}
