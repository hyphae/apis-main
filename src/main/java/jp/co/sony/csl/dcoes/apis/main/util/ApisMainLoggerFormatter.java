package jp.co.sony.csl.dcoes.apis.main.util;

import jp.co.sony.csl.dcoes.apis.common.util.vertx.ApisLoggerFormatter;

/**
 * A dedicated log format for apis-main.
 * Outputs a program identification string and unit ID.
 * @author OES Project
 *          
 * apis-main 専用のログフォーマット.
 * プログラム識別文字列およびユニット ID が出力される.
 * @author OES Project
 */
public class ApisMainLoggerFormatter extends ApisLoggerFormatter {

	private String UNIT_ID_ = null;

	/**
	 * Returns the program identification string to be output to the log.
	 * Create a string of the form "{program identification string}:{unit ID}" instead of the program identification string.
	 * @return {program identification string} + ':' + {unit ID}
	 *          
	 * ログに出力するプログラム識別文字列を返す.
	 * プログラム識別文字列の代わりに「プログラム識別文字:ユニット ID」を生成する.
	 * @return プログラム識別文字列 + ':' + ユニット ID
	 */
	@Override protected String programId() {
		return super.programId() + ':' + unitId();
	}

	/**
	 * Returns the unit ID to be output to the log.
	 * Returns an empty string if {@code null}.
	 * @return unit ID
	 *          
	 * ログに出力するユニット ID を返す.
	 * {@code null} なら空文字列を返す.
	 * @return ユニット ID
	 */
	protected String unitId() {
		if (UNIT_ID_ == null) UNIT_ID_ = ApisConfig.unitId();
		return (UNIT_ID_ != null) ? UNIT_ID_ : "";
	}

}
