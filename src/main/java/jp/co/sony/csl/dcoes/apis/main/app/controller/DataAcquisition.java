package jp.co.sony.csl.dcoes.apis.main.app.controller;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.core.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collection;
import java.util.Enumeration;

import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.ServiceAddress;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.AbstractStarter;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectUtil;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectWrapper;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.LocalExclusiveLock;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.VertxConfig;
import jp.co.sony.csl.dcoes.apis.main.app.HwConfigKeeping;
import jp.co.sony.csl.dcoes.apis.main.app.PolicyKeeping;
import jp.co.sony.csl.dcoes.apis.main.app.StateHandling;
import jp.co.sony.csl.dcoes.apis.main.app.mediator.Interlocking;
import jp.co.sony.csl.dcoes.apis.main.app.mediator.util.InterlockUtil;
import jp.co.sony.csl.dcoes.apis.main.evaluation.safety.LocalSafetyEvaluation;
import jp.co.sony.csl.dcoes.apis.main.util.ApisConfig;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorExceptionUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

public abstract class DataAcquisition extends AbstractVerticle {
	private static final Logger log = LoggerFactory.getLogger(DataAcquisition.class);

	private static final Long DEFAULT_DATA_ACQUISITION_PERIOD_MSEC = 5000L;
	private static final Long DEFAULT_REQUEST_TIMEOUT_MSEC = 5000L;
	private static final Integer DEFAULT_RETRY_LIMIT = 3;

	private static final LocalExclusiveLock exclusiveLock_ = new LocalExclusiveLock(DataAcquisition.class.getName());

	public static void acquireExclusiveLock(Vertx vertx, Handler<AsyncResult<LocalExclusiveLock.Lock>> onComplete) {
		exclusiveLock_.acquire(vertx, onComplete);
	}

	public static void resetExclusiveLock(Vertx vertx) {
		exclusiveLock_.reset(vertx);
	}

	public static final JsonObjectWrapper cache = new JsonObjectWrapper();

	private long dataAcquisitionTimerId_ = 0L;
	private long lastDataAcquisitionMillis_ = 0L;
	private boolean stopped_ = false;

	@Override
	public void start(Promise<Void> startPromise) throws Exception {
		init(resInit -> {
			if (resInit.succeeded()) {
				startInternalUrgentUnitDataService_(resInternalUrgentUnitData -> {
					if (resInternalUrgentUnitData.succeeded()) {
						startInternalUrgentUnitDeviceStatusService_(resInternalUrgentUnitDeviceStatus -> {
							if (resInternalUrgentUnitDeviceStatus.succeeded()) {
								startResetLocalService_(resResetLocal -> {
									if (resResetLocal.succeeded()) {
										startResetAllService_(resResetAll -> {
											if (resResetAll.succeeded()) {
												dataAcquisitionTimerHandler_(0L);
												if (log.isTraceEnabled())
													log.trace("started : " + deploymentID());
												startPromise.complete();
											} else {
												startPromise.fail(resResetAll.cause());
											}
										});
									} else {
										startPromise.fail(resResetLocal.cause());
									}
								});
							} else {
								startPromise.fail(resInternalUrgentUnitDeviceStatus.cause());
							}
						});
					} else {
						startPromise.fail(resInternalUrgentUnitData.cause());
					}
				});
			} else {
				startPromise.fail(resInit.cause());
			}
		});
	}

	@Override
	public void stop() throws Exception {
		stopped_ = true;
		if (log.isTraceEnabled())
			log.trace("stopped : " + deploymentID());
	}

	protected abstract void init(Handler<AsyncResult<Void>> onComplete);

	protected abstract void getData(Handler<AsyncResult<JsonObject>> onComplete);

	protected abstract void getDeviceStatus(Handler<AsyncResult<JsonObject>> onComplete);

	protected abstract JsonObject mergeDeviceStatus(JsonObject value);

	private void startInternalUrgentUnitDataService_(Handler<AsyncResult<Void>> onComplete) {
		vertx.eventBus().<Void>localConsumer(ServiceAddress.Controller.urgentUnitData(), req -> {
			getData_(res -> {
				if (res.succeeded()) {
					req.reply(res.result());
				} else {
					req.fail(-1, res.cause().getMessage());
				}
			});
		}).completionHandler(onComplete);
	}

	private void startInternalUrgentUnitDeviceStatusService_(Handler<AsyncResult<Void>> onComplete) {
		vertx.eventBus().<Void>localConsumer(ServiceAddress.Controller.urgentUnitDeviceStatus(), req -> {
			getDeviceStatus_(res -> {
				if (res.succeeded()) {
					req.reply(res.result());
				} else {
					req.fail(-1, res.cause().getMessage());
				}
			});
		}).completionHandler(onComplete);
	}

	private void startResetLocalService_(Handler<AsyncResult<Void>> onComplete) {
		vertx.eventBus().<Void>localConsumer(ServiceAddress.resetLocal(), req -> {
			doReset_(req);
		}).completionHandler(onComplete);
	}

	private void startResetAllService_(Handler<AsyncResult<Void>> onComplete) {
		vertx.eventBus().<Void>consumer(ServiceAddress.resetAll(), req -> {
			doReset_(req);
		}).completionHandler(onComplete);
	}

	private void doReset_(Message<?> message) {
		resetExclusiveLock(vertx);
		message.reply(ApisConfig.unitId());
	}

	private void setDataAcquisitionTimer_() {
		Long delay = PolicyKeeping.cache().getLong(DEFAULT_DATA_ACQUISITION_PERIOD_MSEC, "controller",
				"dataAcquisitionPeriodMsec");
		setDataAcquisitionTimer_(delay);
	}

	private void setDataAcquisitionTimer_(long delay) {
		dataAcquisitionTimerId_ = vertx.setTimer(delay, this::dataAcquisitionTimerHandler_);
	}

	private void dataAcquisitionTimerHandler_(Long timerId) {
		if (stopped_)
			return;
		if (null == timerId || timerId.longValue() != dataAcquisitionTimerId_) {
			ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN,
					"illegal timerId : " + timerId + ", dataAcquisitionTimerId_ : " + dataAcquisitionTimerId_);
			return;
		}
		if (lastDataAcquisitionMillis_ != 0L) {
			long millisAfterLastDataAcquisition = (System.currentTimeMillis() - lastDataAcquisitionMillis_);
			Long period = PolicyKeeping.cache().getLong(DEFAULT_DATA_ACQUISITION_PERIOD_MSEC, "controller",
					"dataAcquisitionPeriodMsec");
			if (millisAfterLastDataAcquisition < period) {
				setDataAcquisitionTimer_(period - millisAfterLastDataAcquisition);
				return;
			}
		}
		acquireExclusiveLock(vertx, resExclusiveLock -> {
			if (resExclusiveLock.succeeded()) {
				LocalExclusiveLock.Lock lock = resExclusiveLock.result();
				doTimerWithExclusiveLock_(resDoTimerWithExclusiveLock -> {
					lock.release();
					setDataAcquisitionTimer_();
				});
			} else {
				setDataAcquisitionTimer_();
				ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN,
						resExclusiveLock.cause());
			}
		});
	}

	private void doTimerWithExclusiveLock_(Handler<AsyncResult<JsonObject>> onComplete) {
		getData_(onComplete);
	}

	private void getData_(Handler<AsyncResult<JsonObject>> onComplete) {
		long ts = System.currentTimeMillis();
		Promise<JsonObject> getDataPromise = Promise.promise();
		Promise<JsonObject> getOesunitPromise = Promise.promise();
		getData(getDataPromise);
		getOesunit_(getOesunitPromise);
		Future.all(getDataPromise.future(), getOesunitPromise.future()).onComplete(ar -> {
			if (ar.succeeded()) {
				lastDataAcquisitionMillis_ = ts;
				JsonObject result = ar.result().resultAt(0);
				JsonObject oesunit = ar.result().resultAt(1);
				result.put("oesunit", oesunit);
				getApisData_(result, res -> {
					if (res.succeeded()) {
						JsonObject apis = res.result();
						result.put("apis", apis);
					}
					if ("autonomous".equals(JsonObjectUtil.getString(result, "apis", "operation_mode", "effective"))) {
						JsonObjectUtil.put(result, "1", "oesunit", "budo");
					} else {
						JsonObjectUtil.put(result, "0", "oesunit", "budo");
					}
					cache.setJsonObject(result);
					LocalSafetyEvaluation.check(vertx, PolicyKeeping.cache().jsonObject(), result,
							resSafetyEvaluation -> {
								onComplete.handle(Future.succeededFuture(result));
							});
				});
			} else {
				onComplete.handle(Future.failedFuture(ar.cause()));
			}
		});
	}

	private void getOesunit_(Handler<AsyncResult<JsonObject>> onComplete) {
		JsonObject result = new JsonObject();
		doConfig_(result);
		doNetwork_(result);
		onComplete.handle(Future.succeededFuture(result));
	}

	private void doConfig_(JsonObject oesunit) {
		oesunit.put("communityId", VertxConfig.communityId());
		oesunit.put("clusterId", VertxConfig.clusterId());
		oesunit.put("id", ApisConfig.unitId());
		oesunit.put("display", ApisConfig.unitName());
		oesunit.put("sn", ApisConfig.serialNumber());
		oesunit.put("budo", "0");
		if (oesunit.getValue("communityId") == null)
			oesunit.put("communityId", "NA");
		if (oesunit.getValue("clusterId") == null)
			oesunit.put("clusterId", "NA");
		if (oesunit.getValue("display") == null)
			oesunit.put("display", "auto" + ApisConfig.unitId());
		if (oesunit.getValue("sn") == null)
			oesunit.put("sn", "NA");
	}

	private void doNetwork_(JsonObject oesunit) {
		oesunit.put("ip", "NA");
		oesunit.put("ipv6_ll", "NA");
		oesunit.put("ipv6_g", "NA");
		oesunit.put("mac", "NA");
		try {
			Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
			if (nis != null) {
				while (nis.hasMoreElements()) {
					NetworkInterface ni = nis.nextElement();
					if (!ni.isLoopback() && ni.isUp()) {
						if (ni.getName() != null && (ni.getName().startsWith("e") || ni.getName().startsWith("w"))) {
							byte[] ha = ni.getHardwareAddress();
							if (ha != null) {
								String ipv4 = null;
								String ipv6 = null;
								String ipv6LinkLocal = null;
								Enumeration<InetAddress> ias = ni.getInetAddresses();
								if (ias != null) {
									while (ias.hasMoreElements()) {
										InetAddress ia = ias.nextElement();
										if (ia instanceof Inet4Address) {
											ipv4 = ia.getHostAddress();
										} else if (ia instanceof Inet6Address) {
											if (ia.isLinkLocalAddress()) {
												ipv6LinkLocal = ia.getHostAddress();
											} else {
												ipv6 = ia.getHostAddress();
											}
										}
									}
								}
								if (ipv4 != null || ipv6 != null || ipv6LinkLocal != null) {
									if (ipv4 != null)
										oesunit.put("ip", ipv4);
									if (ipv6LinkLocal != null)
										oesunit.put("ipv6_ll", ipv6LinkLocal);
									if (ipv6 != null)
										oesunit.put("ipv6_g", ipv6);
									StringBuilder mac = new StringBuilder();
									for (int i = 0; i < ha.length; i++) {
										if (0 < i)
											mac.append(':');
										mac.append(String.format("%02x", ha[i]));
									}
									oesunit.put("mac", String.valueOf(mac));
								}
							}
						}
					}
				}
			}
		} catch (SocketException e) {
			ErrorUtil.report(vertx, Error.Category.HARDWARE, Error.Extent.LOCAL, Error.Level.ERROR, e);
		}
	}

	private void getApisData_(JsonObject data, Handler<AsyncResult<JsonObject>> onComplete) {
		JsonObject result = new JsonObject();
		result.put("version", AbstractStarter.APIS_VERSION);
		Float batteryNominalCapacityWh = HwConfigKeeping.batteryNominalCapacityWh();
		if (batteryNominalCapacityWh != null) {
			Float rsoc = JsonObjectUtil.getFloat(data, "battery", "rsoc");
			if (rsoc != null) {
				int remainingWh = (int) (batteryNominalCapacityWh.floatValue() * rsoc.floatValue() / 100.0);
				result.put("remaining_capacity_wh", remainingWh);
			} else {
				ErrorUtil.report(vertx, Error.Category.HARDWARE, Error.Extent.LOCAL, Error.Level.WARN,
						"no battery.rsoc value in unit data : " + cache.jsonObject());
			}
		} else {
			ErrorUtil.report(vertx, Error.Category.USER, Error.Extent.LOCAL, Error.Level.WARN,
					"no batteryNominalCapacityWh value in hwConfig : " + HwConfigKeeping.CACHE.jsonObject());
		}
		int dealInterlockCapacity = Interlocking.dealInterlockCapacity(vertx);
		result.put("deal_interlock_capacity", dealInterlockCapacity);
		Promise<String> getGridMasterUnitIdPromise = Promise.promise();
		Promise<Collection<String>> getDealIdsPromise = Promise.promise();
		Promise<JsonObject> getOperationModesPromise = Promise.promise();
		InterlockUtil.getGridMasterUnitId(vertx, getGridMasterUnitIdPromise);
		InterlockUtil.getDealIds(vertx, getDealIdsPromise);
		StateHandling.operationModes(vertx, getOperationModesPromise);
		Future.all(getGridMasterUnitIdPromise.future(), getDealIdsPromise.future(), getOperationModesPromise.future())
				.onComplete(ar -> {
					if (ar.succeeded()) {
						String gridMasterUnitId = ar.result().resultAt(0);
						Collection<String> dealIds = ar.result().resultAt(1);
						JsonObject operationModes = ar.result().resultAt(2);
						if (ApisConfig.unitId().equals(gridMasterUnitId)) {
							result.put("is_grid_master", Boolean.TRUE);
						}
						if (dealIds != null && !dealIds.isEmpty()) {
							result.put("deal_id_list", dealIds);
						}
						result.put("operation_mode", operationModes);
						onComplete.handle(Future.succeededFuture(result));
					} else {
						ErrorExceptionUtil.reportIfNeedAndFail(vertx, ar.cause(), onComplete);
					}
				});
	}

	private void getDeviceStatus_(Handler<AsyncResult<JsonObject>> onComplete) {
		getDeviceStatus(resGetdDeviceStatus -> {
			if (resGetdDeviceStatus.succeeded()) {
				JsonObject result = resGetdDeviceStatus.result();
				result = mergeDeviceStatus(result);
				onComplete.handle(Future.succeededFuture(result));
			} else {
				onComplete.handle(resGetdDeviceStatus);
			}
		});
	}

	protected void send(HttpClient client, String uri, Handler<AsyncResult<JsonObject>> onComplete) {
		Integer retryLimit = PolicyKeeping.cache().getInteger(DEFAULT_RETRY_LIMIT, "controller", "retryLimit");
		new Sender_(retryLimit, client, uri).execute_(onComplete);
	}

	private class Sender_ {
		private int retryLimit_;
		private HttpClient client_;
		private String uri_;
		private boolean completed_ = false;

		private Sender_(Integer retryLimit, HttpClient client, String uri) {
			retryLimit_ = retryLimit;
			client_ = client;
			uri_ = uri;
		}

		private void execute_(Handler<AsyncResult<JsonObject>> onComplete) {
			executeWithRetry_(r -> {
				if (!completed_) {
					completed_ = true;
					onComplete.handle(r);
				} else {
					ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN,
							"executeWithRetry_() result returned more than once : " + r);
				}
			});
		}

		private void executeWithRetry_(Handler<AsyncResult<JsonObject>> onComplete) {
			send_(client_, uri_, r -> {
				if (r.succeeded()) {
					onComplete.handle(r);
				} else {
					if (0 < --retryLimit_) {
						ErrorUtil.report(vertx, Error.Category.HARDWARE, Error.Extent.LOCAL, Error.Level.WARN,
								"Communication failed with Device Driver", r.cause());
						executeWithRetry_(onComplete);
					} else {
						ErrorUtil.reportAndFail(vertx, Error.Category.HARDWARE, Error.Extent.LOCAL, Error.Level.ERROR,
								"Communication failed with Device Driver", r.cause(), onComplete);
					}
				}
			});
		}

		private void send_(HttpClient client, String uri, Handler<AsyncResult<JsonObject>> onComplete) {
			if (log.isInfoEnabled())
				log.info("uri : " + uri);
			Long requestTimeoutMsec = PolicyKeeping.cache().getLong(DEFAULT_REQUEST_TIMEOUT_MSEC, "controller",
					"requestTimeoutMsec");
			client.request(HttpMethod.GET, uri).onComplete(ar -> {
				if (ar.succeeded()) {
					HttpClientRequest req = ar.result();
					if (requestTimeoutMsec != null)
						req.setTimeout(requestTimeoutMsec);
					req.send().onComplete(ar2 -> {
						if (ar2.succeeded()) {
							HttpClientResponse res = ar2.result();
							if (200 == res.statusCode()) {
								res.body().onComplete(ar3 -> {
									if (ar3.succeeded()) {
										JsonObjectUtil.toJsonObject(ar3.result(), onComplete);
									} else {
										onComplete.handle(Future.failedFuture(ar3.cause()));
									}
								});
							} else {
								res.body().onComplete(ar3 -> {
									String bodyStr = (ar3.succeeded()) ? String.valueOf(ar3.result()) : "null";
									onComplete.handle(Future.failedFuture("http request failed : " + res.statusCode()
											+ " : " + res.statusMessage() + " : " + bodyStr));
								});
							}
						} else {
							onComplete.handle(Future.failedFuture(ar2.cause()));
						}
					});
				} else {
					onComplete.handle(Future.failedFuture(ar.cause()));
				}
			});
		}
	}

}
