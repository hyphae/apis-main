package jp.co.sony.csl.dcoes.apis.main.app;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Promise;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.Future;
import java.util.List;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.ServiceAddress;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.EncryptedClusterWideMapUtil;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectWrapper;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.VertxConfig;
import jp.co.sony.csl.dcoes.apis.main.util.ApisConfig;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;
import jp.co.sony.csl.dcoes.apis.main.util.FileHandler;

public class PolicyKeeping extends AbstractVerticle {
	private static final Logger LOGGER = LoggerFactory.getLogger(PolicyKeeping.class);

	private static final long LOCAL_FILE_DEFAULT_READ_TIMEOUT_MSEC = 60000L;
	private static final long LOCAL_FILE_DEFAULT_REFRESHING_PERIOD_MSEC = 5000L;
	private static final long CONTROL_CENTER_DEFAULT_REFRESHING_PERIOD_MSEC = 60000L;

	private static final JsonObjectWrapper LOCAL_FILE_CACHE = new JsonObjectWrapper();
	private static final JsonObjectWrapper controlCenterCache = new JsonObjectWrapper();

	private static final String MAP_NAME = PolicyKeeping.class.getName();
	private static final String MAP_KEY_POLICY = "policy";

	public static JsonObjectWrapper cache() {
		return (!controlCenterCache.isNull()) ? controlCenterCache : LOCAL_FILE_CACHE;
	}

	private String localFilePath;
	private boolean controlCenterEnabled = false;
	private String controlCenterAccount;
	private String controlCenterPassword;
	private long localFileReadingTimerId = 0L;
	private long controlCenterAccessingTimerId = 0L;
	private boolean stopped = false;

	@Override
	public void start(Promise<Void> startPromise) throws Exception {
		localFilePath = VertxConfig.config.getString("policyFile");
		controlCenterEnabled = VertxConfig.config.getBoolean(Boolean.TRUE, "controlCenter", "enabled");
		if (controlCenterEnabled) {
			controlCenterAccount = VertxConfig.config.getString("controlCenter", "account");
			controlCenterPassword = VertxConfig.config.getString("controlCenter", "password");
		}

		LOGGER.info("policyFile : {}", localFilePath);
		LOGGER.info("policyFile.defaultRefreshingPeriodMsec : {}", LOCAL_FILE_DEFAULT_REFRESHING_PERIOD_MSEC);
		LOGGER.info("controlCenter.enabled : {}", controlCenterEnabled);
		LOGGER.info("controlCenter.account : {}", controlCenterAccount);
		LOGGER.info("controlCenter.defaultRefreshingPeriodMsec : {}", CONTROL_CENTER_DEFAULT_REFRESHING_PERIOD_MSEC);

		init(resInit -> {
			if (resInit.succeeded()) {
				startPolicyService(resPolicy -> {
					if (resPolicy.succeeded()) {
						localFileReadingTimerHandler(0L);
						if (controlCenterEnabled) {
							controlCenterAccessingTimerHandler(0L);
						}
						LOGGER.trace("started : {}", deploymentID());
						startPromise.complete();
					} else {
						startPromise.fail(resPolicy.cause());
					}
				});
			} else {
				startPromise.fail(resInit.cause());
			}
		});
	}

	@Override
	public void stop() throws Exception {
		stopped = true;
		LOGGER.trace("stopped : {}", deploymentID());
	}

	private void init(Handler<AsyncResult<Void>> onComplete) {
		Boolean[] handled = new Boolean[1];
		Long localFileReadTimeoutMsec = VertxConfig.config.getLong(LOCAL_FILE_DEFAULT_READ_TIMEOUT_MSEC,
				"policyFileReadTimeoutMsec");
		vertx.setTimer(localFileReadTimeoutMsec, timerId -> {
			if (handled[0] == null) {
				handled[0] = Boolean.TRUE;
				onComplete.handle(Future.failedFuture("POLICY read timed out : " + localFileReadTimeoutMsec + "ms"));
			}
		});

		readLocalFile(resRead -> {
			if (handled[0] == null) {
				handled[0] = Boolean.TRUE;
				if (resRead.succeeded()) {
					LOCAL_FILE_CACHE.setJsonObject(resRead.result());
					checkClusterPolicy(LOCAL_FILE_CACHE.jsonObject(), onComplete);
				} else {
					onComplete.handle(Future.failedFuture(resRead.cause()));
				}
			}
		});
	}

	private void checkClusterPolicy(JsonObject policy, Handler<AsyncResult<Void>> onComplete) {
		String myValue = policy.encode();
		EncryptedClusterWideMapUtil.<String, String>getEncryptedClusterWideMap(vertx, MAP_NAME, resMap -> {
			if (resMap.succeeded()) {
				AsyncMap<String, String> theMap = resMap.result();
				theMap.putIfAbsent(MAP_KEY_POLICY, myValue, resPutIfAbsent -> {
					if (resPutIfAbsent.succeeded()) {
						String existingValue = resPutIfAbsent.result();
						if (existingValue == null) {
							onComplete.handle(Future.succeededFuture());
						} else {
							if (existingValue.equals(myValue)) {
								onComplete.handle(Future.succeededFuture());
							} else {
								onComplete.handle(Future.failedFuture("my POLICY is different from cluster's"));
							}
						}
					} else {
						onComplete.handle(Future.failedFuture(resPutIfAbsent.cause()));
					}
				});
			} else {
				onComplete.handle(Future.failedFuture(resMap.cause()));
			}
		});
	}

	private void startPolicyService(Handler<AsyncResult<Void>> onComplete) {
		vertx.eventBus().<Void>localConsumer(ServiceAddress.policy(), req -> {
			JsonObject jsonObject = cache().jsonObject();
			req.reply(jsonObject);
		}).completionHandler(onComplete);
	}

	private void setLocalFileReadingTimer() {
		Long delay = LOCAL_FILE_CACHE.getLong(LOCAL_FILE_DEFAULT_REFRESHING_PERIOD_MSEC, "refreshingPeriodMsec");
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
				LOCAL_FILE_CACHE.setJsonObject(resRead.result());
			} else {
				if (LOCAL_FILE_CACHE.isNull()) {
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

	private void setControlCenterAccessingTimer() {
		Long delay = controlCenterCache.getLong(CONTROL_CENTER_DEFAULT_REFRESHING_PERIOD_MSEC, "refreshingPeriodMsec");
		setControlCenterAccessingTimer(delay);
	}

	private void setControlCenterAccessingTimer(long delay) {
		controlCenterAccessingTimerId = vertx.setTimer(delay, this::controlCenterAccessingTimerHandler);
	}

	private void controlCenterAccessingTimerHandler(Long timerId) {
		if (stopped)
			return;

		if (null == timerId || timerId != controlCenterAccessingTimerId) {
			ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, "illegal timerId : "
					+ timerId + ", controlCenterAccessingTimerId : " + controlCenterAccessingTimerId);
			return;
		}

		DeliveryOptions options = new DeliveryOptions()
				.addHeader("account", controlCenterAccount)
				.addHeader("password", controlCenterPassword)
				.addHeader("unitId", ApisConfig.unitId());

		vertx.eventBus().<JsonObject>request(ServiceAddress.ControlCenterClient.policy(), null, options, resPolicy -> {
			if (resPolicy.succeeded()) {
				controlCenterCache.setJsonObject(resPolicy.result().body());
			} else {
				ErrorUtil.report(vertx, Error.Category.USER, Error.Extent.LOCAL, Error.Level.WARN,
						"Communication failed on EventBus", resPolicy.cause());
			}
			setControlCenterAccessingTimer();
		});
	}

	public static List<String> memberUnitIds() {
		return cache().getStringList("memberUnitIds");
	}

	public static int numberOfMembers() {
		List<String> memberUnitIds = memberUnitIds();
		return (memberUnitIds != null) ? memberUnitIds.size() : 0;
	}

	public static boolean isMember(String unitId) {
		if (unitId != null) {
			List<String> memberUnitIds = memberUnitIds();
			return (memberUnitIds != null && memberUnitIds.contains(unitId));
		}
		return false;
	}
}
