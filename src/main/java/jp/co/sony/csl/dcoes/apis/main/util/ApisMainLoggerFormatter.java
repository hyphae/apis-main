package jp.co.sony.csl.dcoes.apis.main.util;

import jp.co.sony.csl.dcoes.apis.common.util.vertx.ApisLoggerFormatter;

public class ApisMainLoggerFormatter extends ApisLoggerFormatter {

	private String UNIT_ID_ = null;

	@Override
	protected String programId() {
		return super.programId() + ':' + unitId();
	}

	protected String unitId() {
		if (UNIT_ID_ == null)
			UNIT_ID_ = ApisConfig.unitId();
		return (UNIT_ID_ != null) ? UNIT_ID_ : "";
	}

}
