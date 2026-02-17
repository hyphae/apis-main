package jp.co.sony.csl.dcoes.apis.main.app;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Promise;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectUtil;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectWrapper;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.VertxConfig;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;
import jp.co.sony.csl.dcoes.apis.main.util.FileHandler;

public class HwConfigKeeping extends AbstractVerticle {
	private static final Logger LOGGER = LoggerFactory.getLogger(HwConfigKeeping.class);

	private static final Long LOCAL_FILE_DEFAULT_REFRESHING_PERIOD_MSEC = 5000L;

	public static final JsonObjectWrapper CACHE = new JsonObjectWrapper();

	private String localFilePath;
	private long localFileReadingTimerId;
	private boolean stopped;

	@Override
	public void start(Promise<Void> startPromise) throws Exception {
		localFilePath = VertxConfig.config.getString("hwConfigFile");
		LOGGER.info("hwConfigFile : {}", localFilePath);
		LOGGER.info("hwConfigFile.defaultRefreshingPeriodMsec : {}", LOCAL_FILE_DEFAULT_REFRESHING_PERIOD_MSEC);

		localFileReadingTimerHandler(0L);
		LOGGER.trace("started : {}", deploymentID());
		startPromise.complete();
	}

	@Override
	public void stop() throws Exception {
		stopped = true;
		LOGGER.trace("stopped : {}", deploymentID());
	}

	private void setLocalFileReadingTimer() {
		Long delay = CACHE.getLong(LOCAL_FILE_DEFAULT_REFRESHING_PERIOD_MSEC, "refreshingPeriodMsec");
		setLocalFileReadingTimer(delay);
	}

	private void setLocalFileReadingTimer(long delay) {
		localFileReadingTimerId = vertx.setTimer(delay, this::localFileReadingTimerHandler);
	}

	private void localFileReadingTimerHandler(Long timerId) {
		if (stopped)
			return;

		if (null == timerId || timerId != localFileReadingTimerId) {
			ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN,
					"illegal timerId : " + timerId + ", localFileReadingTimerId : " + localFileReadingTimerId);
			return;
		}

		readLocalFile(resRead -> {
			if (resRead.succeeded()) {
				CACHE.setJsonObject(resRead.result());
			} else {
				if (CACHE.isNull()) {
					ErrorUtil.report(vertx, Error.Category.USER, Error.Extent.LOCAL, Error.Level.ERROR,
							resRead.cause());
				} else {
					ErrorUtil.report(vertx, Error.Category.USER, Error.Extent.LOCAL, Error.Level.WARN, resRead.cause());
				}
			}
			setLocalFileReadingTimer();
		});
	}

	private void readLocalFile(Handler<AsyncResult<JsonObject>> onComplete) {
		FileHandler.readLocalFile(onComplete, vertx.fileSystem(), localFilePath);
	}

	public static Float batteryNominalCapacityWh() {
		return CACHE.getFloat("batteryNominalCapacityWh");
	}

	public static Float gridCurrentCapacityA() {
		return CACHE.getFloat("gridCurrentCapacityA");
	}

	public static Float gridCurrentAllowanceA() {
		return CACHE.getFloat("gridCurrentAllowanceA");
	}

	public static Float droopRatio() {
		return CACHE.getFloat("droopRatio");
	}

	public static Float efficientBatteryGridVoltageRatio() {
		return CACHE.getFloat("efficientBatteryGridVoltageRatio");
	}

	public static JsonObject safetyRange() {
		return CACHE.getJsonObject("safety", "range");
	}

	public static JsonObject safetyRange(String... keys) {
		return JsonObjectUtil.getJsonObject(safetyRange(), keys);
	}
}
