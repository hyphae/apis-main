package jp.co.sony.csl.dcoes.apis.main.app.controller.util;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

/**
 * DCDC converter constants.
 * TODO: Since this relates to DCDC, doesn't it belong under {@link jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc}?
 * @author OES Project
 *          
 * DCDC コンバータの定数.
 * TODO: DCDC なのだから {@link jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc} の下にあるべきでは ?
 * @author OES Project
 */
public class DDCon {
	private static final Logger log = LoggerFactory.getLogger(DDCon.class);

	private DDCon() { }

	/**
	 * A string representing the DCDC converter alarm value {@code No alarm}.
	 * Value: {@value}
	 *          
	 * DCDC コンバータのアラーム値 {@code No alarm} を表す文字列.
	 * 値は {@value}
	 */
	public static final String ALARM_STATE_CODE_NO_ALARM = "No alarm";
	/**
	 * A string representing the DCDC converter alarm value {@code Light alarm}.
	 * Value: {@value}
	 *          
	 * DCDC コンバータのアラーム値 {@code Light alarm} を表す文字列.
	 * 値は {@value}
	 */
	public static final String ALARM_STATE_CODE_LIGHT_ALARM = "Light alarm";
	/**
	 * A string representing the DCDC converter alarm value {@code Heavy alarm}.
	 * Value: {@value}
	 *          
	 * DCDC コンバータのアラーム値 {@code Heavy alarm} を表す文字列.
	 * 値は {@value}
	 */
	public static final String ALARM_STATE_CODE_HEAVY_ALARM = "Heavy alarm";
	/**
	 * DCDC converter alarm value constants.
	 * @author OES Project
	 *          
	 * DCDC コンバータのアラーム値の定数.
	 * @author OES Project
	 */
	public enum AlarmState {
		/**
		 * {@code "No alarm"}
		 */
		NO_ALARM,
		/**
		 * {@code "Light alarm"}
		 */
		LIGHT_ALARM,
		/**
		 * {@code "Heavy alarm"}
		 */
		HEAVY_ALARM,
	}

	/**
	 * Fetch the constant corresponding to a DCDC converter alarm constant string.
	 * @param value alarm constant string. Required
	 * @return alarm constant. If not found, return {@code null}
	 *          
	 * DCDC コンバータのアラーム定数の文字列から定数を取得する.
	 * @param value アラーム定数文字列. 必須
	 * @return アラーム定数. 見つからなければ {@code null}
	 */
	public static AlarmState alarmState(String value) {
		try {
			return AlarmState.valueOf(value.toUpperCase());
		} catch (Exception e) {
			log.error(e);
			return null;
		}
	}
	/**
	 * Fetch the constant corresponding to a DCDC converter alarm string.
	 * @param value the alarm string Optional
	 * @return alarm constant. If not found, return {@code null}
	 *          
	 * DCDC コンバータのアラーム文字列から定数を取得する.
	 * @param value アラーム文字列. 任意
	 * @return アラーム定数. 見つからなければ {@code null}
	 */
	public static AlarmState alarmStateFromCode(String value) {
		if (ALARM_STATE_CODE_NO_ALARM.equals(value)) return AlarmState.NO_ALARM;
		if (ALARM_STATE_CODE_LIGHT_ALARM.equals(value)) return AlarmState.LIGHT_ALARM;
		if (ALARM_STATE_CODE_HEAVY_ALARM.equals(value)) return AlarmState.HEAVY_ALARM;
		return null;
	}
	/**
	 * Fetch the string corresponding to a DCDC converter alarm constant.
	 * @param value alarm constant Optional
	 * @return alarm string If not found, return {@code null}
	 *          
	 * DCDC コンバータのアラーム定数から文字列を取得する.
	 * @param value アラーム定数. 任意
	 * @return アラーム文字列. 見つからなければ {@code null}
	 */
	public static String codeFromAlarmState(AlarmState value) {
		switch (value) {
		case NO_ALARM : return ALARM_STATE_CODE_NO_ALARM;
		case LIGHT_ALARM : return ALARM_STATE_CODE_LIGHT_ALARM;
		case HEAVY_ALARM : return ALARM_STATE_CODE_HEAVY_ALARM;
		default : return null;
		}
	}

	////

	/**
	 * A string representing the stop mode specification of the DCDC converter.
	 * Value: {@value}
	 *          
	 * DCDC コンバータの停止モード指定を表す文字列.
	 * 値は {@value}
	 */
	public static final String MODE_CODE_WAIT = "0x0000";
	/**
	 * A string representing the voltage reference mode specification of the DCDC converter.
	 * Value: {@value}
	 *          
	 * DCDC コンバータの電圧リファレンスモード指定を表す文字列.
	 * 値は {@value}
	 */
	public static final String MODE_CODE_VOLTAGE_REFERENCE = "0x0014";
	/**
	 * A string representing the charge mode specification of the DCDC converter.
	 * Value: {@value}
	 *          
	 * DCDC コンバータの受電モード指定を表す文字列.
	 * 値は {@value}
	 */
	public static final String MODE_CODE_CHARGE = "0x0041";
	/**
	 * A string representing the discharge mode specification of the DCDC converter.
	 * Value: {@value}
	 *          
	 * DCDC コンバータの送電モード指定を表す文字列.
	 * 値は {@value}
	 */
	public static final String MODE_CODE_DISCHARGE = "0x0002";
	/**
	 * DCDC converter mode specification constant.
	 * @author OES Project
	 *          
	 * DCDC コンバータのモード指定の定数.
	 * @author OES Project
	 */
	public enum Mode {
		/**
		 * Stop mode
		 *          
		 * 停止モード指定
		 */
		WAIT,
		/**
		 * Voltage reference mode
		 *          
		 * 電圧リファレンスモード指定
		 */
		VOLTAGE_REFERENCE,
		/**
		 * Charge mode
		 *          
		 * 受電モード指定
		 */
		CHARGE,
		/**
		 * Discharge mode
		 *          
		 * 送電モード指定
		 */
		DISCHARGE,
	}

	/**
	 * Fetch the constant corresponding to a DCDC converter mode specification constant string.
	 * @param value mode specification constant string. Required
	 * @return mode specification constant. If not found, return {@code null}
	 *          
	 * DCDC コンバータのモード指定定数の文字列から定数を取得する.
	 * @param value モード指定定数文字列. 必須
	 * @return モード指定定数. 見つからなければ {@code null}
	 */
	public static Mode mode(String value) {
		try {
			return Mode.valueOf(value.toUpperCase());
		} catch (Exception e) {
			log.error(e);
			return null;
		}
	}
	/**
	 * Fetch the constant corresponding to a DCDC converter mode specification string.
	 * @param value mode specification string. Optional
	 * @return mode specification constant. If not found, return {@code null}
	 *          
	 * DCDC コンバータのモード指定文字列から定数を取得する.
	 * @param value モード指定文字列. 任意
	 * @return モード指定定数. 見つからなければ {@code null}
	 */
	public static Mode modeFromCode(String value) {
		if (MODE_CODE_WAIT.equals(value)) return Mode.WAIT;
		if (MODE_CODE_VOLTAGE_REFERENCE.equals(value)) return Mode.VOLTAGE_REFERENCE;
		if (MODE_CODE_CHARGE.equals(value)) return Mode.CHARGE;
		if (MODE_CODE_DISCHARGE.equals(value)) return Mode.DISCHARGE;
		return null;
	}
	/**
	 * Fetch the corresponding to a DCDC converter mode specification constant.
	 * @param value mode specification constant Optional
	 * @return mode specification string If not found, return {@code null}
	 *          
	 * DCDC コンバータのモード指定定数から文字列を取得する.
	 * @param value モード指定定数. 任意
	 * @return モード指定文字列. 見つからなければ {@code null}
	 */
	public static String codeFromMode(Mode value) {
		switch (value) {
		case WAIT : return MODE_CODE_WAIT;
		case VOLTAGE_REFERENCE : return MODE_CODE_VOLTAGE_REFERENCE;
		case CHARGE : return MODE_CODE_CHARGE;
		case DISCHARGE : return MODE_CODE_DISCHARGE;
		default : return null;
		}
	}

	////

	/**
	 * A string representing the stop operation mode of the DCDC converter.
	 * Value: {@value}
	 *          
	 * DCDC コンバータの停止動作モードを表す文字列.
	 * 値は {@value}
	 */
	public static final String OPERATION_MODE_CODE_WAITING = "Waiting";
	/**
	 * A string representing the voltage reference operating mode of the DCDC converter.
	 * Value: {@value}
	 *          
	 * DCDC コンバータの電圧リファレンス動作モードを表す文字列.
	 * 値は {@value}
	 */
	public static final String OPERATION_MODE_CODE_GRID_AUTONOMY = "Grid Autonomy";
	/**
	 * A string representing the current operating mode of the DCDC converter.
	 * Value: {@value}
	 *          
	 * DCDC コンバータの電流動作モードを表す文字列.
	 * 値は {@value}
	 */
	public static final String OPERATION_MODE_CODE_HETERONOMY_CV = "Heteronomy CV";
	/**
	 * DCDC converter operating mode constants.
	 * @author OES Project
	 *          
	 * DCDC コンバータの動作モードの定数.
	 * @author OES Project
	 */
	public enum OperationMode {
		/**
		 * Stop operation mode
		 *          
		 * 停止動作モード
		 */
		WAITING,
		/**
		 * Voltage reference operating mode
		 *          
		 * 電圧リファレンス動作モード
		 */
		GRID_AUTONOMY,
		/**
		 * Current operating mode
		 *          
		 * 電流動作モード
		 */
		HETERONOMY_CV,
	}

	/**
	 * Fetch the constant corresponding to a DCDC converter operating mode constant string.
	 * @param value operating mode constant string. Required
	 * @return operating mode constant. If not found, return {@code null}
	 *          
	 * DCDC コンバータの動作モード定数の文字列から定数を取得する.
	 * @param value 動作モード定数文字列. 必須
	 * @return 動作モード定数. 見つからなければ {@code null}
	 */
	public static OperationMode operationMode(String value) {
		try {
			return OperationMode.valueOf(value.toUpperCase());
		} catch (Exception e) {
			log.error(e);
			return null;
		}
	}
	/**
	 * Fetch the constant corresponding to a DCDC converter operating mode string.
	 * @param value operating mode string. Optional
	 * @return operating mode constant. If not found, return {@code null}
	 *          
	 * DCDC コンバータの動作モード文字列から定数を取得する.
	 * @param value 動作モード文字列. 任意
	 * @return 動作モード定数. 見つからなければ {@code null}
	 */
	public static OperationMode operationModeFromCode(String value) {
		if (OPERATION_MODE_CODE_WAITING.equals(value)) return OperationMode.WAITING;
		if (OPERATION_MODE_CODE_GRID_AUTONOMY.equals(value)) return OperationMode.GRID_AUTONOMY;
		if (OPERATION_MODE_CODE_HETERONOMY_CV.equals(value)) return OperationMode.HETERONOMY_CV;
		return null;
	}
	/**
	 * Fetch the string corresponding to a DCDC converter operating mode constant.
	 * @param value operating mode constant. Optional
	 * @return operating mode string If not found, return {@code null}
	 *          
	 * DCDC コンバータの動作モード定数から文字列を取得する.
	 * @param value 動作モード定数. 任意
	 * @return 動作モード文字列. 見つからなければ {@code null}
	 */
	public static String codeFromOperationMode(OperationMode value) {
		switch (value) {
		case WAITING : return OPERATION_MODE_CODE_WAITING;
		case GRID_AUTONOMY : return OPERATION_MODE_CODE_GRID_AUTONOMY;
		case HETERONOMY_CV : return OPERATION_MODE_CODE_HETERONOMY_CV;
		default : return null;
		}
	}

	////

	/**
	 * Fetch the operating mode corresponding to a mode specification.
	 * @param value mode specification. Optional
	 * @return operating mode. If not found, return {@code null}
	 *          
	 * モード指定に対応する動作モードを取得する.
	 * @param value モード指定. 任意
	 * @return 動作モード. 見つからなければ {@code null}
	 */
	public static OperationMode operationModeForMode(Mode value) {
		switch (value) {
		case WAIT : return OperationMode.WAITING;
		case VOLTAGE_REFERENCE : return OperationMode.GRID_AUTONOMY;
		case CHARGE : return OperationMode.HETERONOMY_CV;
		case DISCHARGE : return OperationMode.HETERONOMY_CV;
		default : return null;
		}
	}

}
