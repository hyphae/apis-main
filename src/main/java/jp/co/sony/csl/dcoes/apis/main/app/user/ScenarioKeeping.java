package jp.co.sony.csl.dcoes.apis.main.app.user;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.LocalTime;

import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.ServiceAddress;
import jp.co.sony.csl.dcoes.apis.common.util.DateTimeUtil;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectWrapper;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.VertxConfig;
import jp.co.sony.csl.dcoes.apis.main.util.ApisConfig;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;
import jp.co.sony.csl.dcoes.apis.main.util.FileHandler;

public class ScenarioKeeping extends AbstractVerticle {
	private static final Logger log = LoggerFactory.getLogger(ScenarioKeeping.class);

	private static final Long LOCAL_FILE_DEFAULT_REFRESHING_PERIOD_MSEC = 5000L;
	private static final Long CONTROL_CENTER_DEFAULT_REFRESHING_PERIOD_MSEC = 60000L;

	private static final JsonObjectWrapper localFileCache_ = new JsonObjectWrapper();
	private static final JsonObjectWrapper controlCenterCache_ = new JsonObjectWrapper();

	public static JsonObjectWrapper cache() {
		return (!controlCenterCache_.isNull()) ? controlCenterCache_ : localFileCache_;
	}

	private String localFilePath_;
	private boolean controlCenterEnabled_ = false;
	private String controlCenterAccount_;
	private String controlCenterPassword_;
	private long localFileReadingTimerId_ = 0L;
	private long controlCenterAccessingTimerId_ = 0L;
	private boolean stopped_ = false;

	@Override
	public void start(Promise<Void> startPromise) throws Exception {
		localFilePath_ = VertxConfig.config.getString("scenarioFile");
		controlCenterEnabled_ = VertxConfig.config.getBoolean(Boolean.TRUE, "controlCenter", "enabled");
		if (controlCenterEnabled_) {
			controlCenterAccount_ = VertxConfig.config.getString("controlCenter", "account");
			controlCenterPassword_ = VertxConfig.config.getString("controlCenter", "password");
		}
		if (log.isInfoEnabled())
			log.info("scenarioFile : " + localFilePath_);
		if (log.isInfoEnabled())
			log.info("scenarioFile.defaultRefreshingPeriodMsec : " + LOCAL_FILE_DEFAULT_REFRESHING_PERIOD_MSEC);
		if (log.isInfoEnabled())
			log.info("controlCenter.enabled : " + controlCenterEnabled_);
		if (log.isInfoEnabled())
			log.info("controlCenter.account : " + controlCenterAccount_);
		if (log.isInfoEnabled())
			log.info("controlCenter.defaultRefreshingPeriodMsec : " + CONTROL_CENTER_DEFAULT_REFRESHING_PERIOD_MSEC);

		startScenarioService_(resScenario -> {
			if (resScenario.succeeded()) {
				localFileReadingTimerHandler_(0L);
				if (controlCenterEnabled_) {
					controlCenterAccessingTimerHandler_(0L);
				}
				if (log.isTraceEnabled())
					log.trace("started : " + deploymentID());
				startPromise.complete();
			} else {
				startPromise.fail(resScenario.cause());
			}
		});
	}

	@Override
	public void stop() throws Exception {
		stopped_ = true;
		if (log.isTraceEnabled())
			log.trace("stopped : " + deploymentID());
	}

	private void startScenarioService_(Handler<AsyncResult<Void>> onComplete) {
		vertx.eventBus().<String>localConsumer(ServiceAddress.User.scenario(), req -> {
			JsonObject globalAcceptSelection = cache().getJsonObject("acceptSelection");
			LocalDateTime dt = DateTimeUtil.toLocalDateTime(req.body());
			if (dt != null) {
				LocalTime t = dt.toLocalTime();
				String hhmmss = DateTimeUtil.toString(t);
				JsonObject jsonObject = cache().jsonObject();
				if (jsonObject != null) {
					for (String aKey : jsonObject.fieldNames()) {
						String[] fromTo = aKey.split("-", 2);
						if (fromTo.length == 2) {
							if (fromTo[0].compareTo(hhmmss) <= 0 && hhmmss.compareTo(fromTo[1]) < 0) {
								JsonObject result = jsonObject.getJsonObject(aKey);
								if (!result.containsKey("acceptSelection") && globalAcceptSelection != null) {
									result = result.copy().put("acceptSelection", globalAcceptSelection);
								}
								req.reply(result);
								return;
							}
						}
					}
					ErrorUtil.reportAndFail(vertx, Error.Category.USER, Error.Extent.LOCAL, Error.Level.WARN,
							"no entry matched for time : " + hhmmss, req);
				} else {
					req.fail(-1, "cache is null");
				}
			} else {
				ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR,
						"message is null or bad value : " + req.body(), req);
			}
		}).completionHandler(onComplete);
	}

	private void setLocalFileReadingTimer_() {
		Long delay = localFileCache_.getLong(LOCAL_FILE_DEFAULT_REFRESHING_PERIOD_MSEC, "refreshingPeriodMsec");
		setLocalFileReadingTimer_(delay);
	}

	private void setLocalFileReadingTimer_(long delay) {
		localFileReadingTimerId_ = vertx.setTimer(delay, this::localFileReadingTimerHandler_);
	}

	private void localFileReadingTimerHandler_(Long timerId) {
		if (stopped_)
			return;
		if (null == timerId || timerId != localFileReadingTimerId_) {
			ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN,
					"illegal timerId : " + timerId + ", localFileReadingTimerId_ : " + localFileReadingTimerId_);
			return;
		}
		doReadLocalFile_(resRead -> {
			if (resRead.succeeded()) {
				localFileCache_.setJsonObject(resRead.result());
			} else {
				if (localFileCache_.isNull()) {
					ErrorUtil.report(vertx, Error.Category.USER, Error.Extent.LOCAL, Error.Level.ERROR,
							resRead.cause());
				} else {
					ErrorUtil.report(vertx, Error.Category.USER, Error.Extent.LOCAL, Error.Level.WARN, resRead.cause());
				}
			}
			setLocalFileReadingTimer_();
		});
	}

	private void doReadLocalFile_(Handler<AsyncResult<JsonObject>> onComplete) {
		FileHandler.readLocalFile(onComplete, vertx.fileSystem(), localFilePath_);
	}

	private void setControlCenterAccessingTimer_() {
		Long delay = controlCenterCache_.getLong(CONTROL_CENTER_DEFAULT_REFRESHING_PERIOD_MSEC, "refreshingPeriodMsec");
		setControlCenterAccessingTimer_(delay);
	}

	private void setControlCenterAccessingTimer_(long delay) {
		controlCenterAccessingTimerId_ = vertx.setTimer(delay, this::controlCenterAccessingTimerHandler_);
	}

	private void controlCenterAccessingTimerHandler_(Long timerId) {
		if (stopped_)
			return;
		if (null == timerId || timerId != controlCenterAccessingTimerId_) {
			ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, "illegal timerId : "
					+ timerId + ", controlCenterAccessingTimerId_ : " + controlCenterAccessingTimerId_);
			return;
		}
		DeliveryOptions options = new DeliveryOptions().addHeader("account", controlCenterAccount_)
				.addHeader("password", controlCenterPassword_).addHeader("unitId", ApisConfig.unitId());
		vertx.eventBus().<JsonObject>request(ServiceAddress.ControlCenterClient.scenario(), null, options,
				resScenario -> {
					if (resScenario.succeeded()) {
						controlCenterCache_.setJsonObject(resScenario.result().body());
					} else {
						ErrorUtil.report(vertx, Error.Category.USER, Error.Extent.LOCAL, Error.Level.WARN,
								"Communication failed on EventBus", resScenario.cause());
					}
					setControlCenterAccessingTimer_();
				});
	}

}
