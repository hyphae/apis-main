package jp.co.sony.csl.dcoes.apis.main.app.util;

import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.VertxLoggerFormatter;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.VertxConfig;
import jp.co.sony.csl.dcoes.apis.main.util.ApisMainLoggerFormatter;

@RunWith(VertxUnitRunner.class)
public class ApisMainLoggerFormatterTest {

	public ApisMainLoggerFormatterTest() {
		super();
	}

	private static final VertxLoggerFormatter BASE_ = new VertxLoggerFormatter();

	@Test public void nullProgramIdNullUnitId(TestContext context) {
		VertxConfig.config.setJsonObject(null);
		LogRecord record = new LogRecord(Level.INFO, "test message");
		String result = new ApisMainLoggerFormatter().format(record);
		context.assertEquals("[[[:]]] " + BASE_.format(record), result);
	}

	@Test public void emptyProgramIdEmptyUnitId(TestContext context) {
		VertxConfig.config.setJsonObject(new JsonObject("{\"programId\":\"\",\"unitId\":\"\"}"));
		LogRecord record = new LogRecord(Level.INFO, "test message");
		String result = new ApisMainLoggerFormatter().format(record);
		context.assertEquals("[[[:]]] " + BASE_.format(record), result);
	}

	@Test public void normalProgramIdNormalUnitId(TestContext context) {
		VertxConfig.config.setJsonObject(new JsonObject("{\"programId\":\"apis-main\",\"unitId\":\"E001\"}"));
		LogRecord record = new LogRecord(Level.INFO, "test message");
		String result = new ApisMainLoggerFormatter().format(record);
		context.assertEquals("[[[apis-main:E001]]] " + BASE_.format(record), result);
	}

}
