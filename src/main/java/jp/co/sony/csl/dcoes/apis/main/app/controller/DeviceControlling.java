package jp.co.sony.csl.dcoes.apis.main.app.controller;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.ServiceAddress;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectUtil;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.LocalExclusiveLock;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.ReplyFailureUtil;
import jp.co.sony.csl.dcoes.apis.main.app.PolicyKeeping;
import jp.co.sony.csl.dcoes.apis.main.app.mediator.util.InterlockUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ApisConfig;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorExceptionUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

public abstract class DeviceControlling extends AbstractVerticle {
	private static final Logger log = LoggerFactory.getLogger(DeviceControlling.class);

	private static final Long DEFAULT_REQUEST_TIMEOUT_MSEC = 5000L;
	private static final Integer DEFAULT_RETRY_LIMIT = 3;

	private static boolean ignoreDynamicSafetyCheck_ = false;

	public static boolean ignoreDynamicSafetyCheck() {
		return ignoreDynamicSafetyCheck_;
	}

	public static void ignoreDynamicSafetyCheck(boolean value) {
		if (log.isInfoEnabled()) {
			if (ignoreDynamicSafetyCheck_ != value) {
				if (value) {
					log.info("begin ignoring dynamic safety check");
				} else {
					log.info("end ignoring dynamic safety check");
				}
			}
		}
		ignoreDynamicSafetyCheck_ = value;
	}

	@Override
	public void start(Promise<Void> startPromise) throws Exception {
		init(resInit -> {
			if (resInit.succeeded()) {
				startLocalStopService_(resLocalStop -> {
					if (resLocalStop.succeeded()) {
						startScramService_(resScram -> {
							if (resScram.succeeded()) {
								startDeviceControllingService_(resDeviceControlling -> {
									if (resDeviceControlling.succeeded()) {
										if (log.isTraceEnabled())
											log.trace("started : " + deploymentID());
										startPromise.complete();
									} else {
										startPromise.fail(resDeviceControlling.cause());
									}
								});
							} else {
								startPromise.fail(resScram.cause());
							}
						});
					} else {
						startPromise.fail(resLocalStop.cause());
					}
				});
			} else {
				startPromise.fail(resInit.cause());
			}
		});
	}

	@Override
	public void stop(Promise<Void> stopPromise) throws Exception {
		vertx.eventBus().request(ServiceAddress.Controller.stopLocal(), null, repStopLocal -> {
			if (repStopLocal.succeeded()) {
				// nop
			} else {
				if (ReplyFailureUtil.isNoHandlers(repStopLocal)) {
					// nop
				} else {
					ErrorUtil.report(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR,
							"Communication failed on EventBus", repStopLocal.cause());
				}
			}
			if (log.isTraceEnabled())
				log.trace("stopped : " + deploymentID());
			stopPromise.complete();
		});
	}

	protected abstract void init(Handler<AsyncResult<Void>> onComplete);

	protected abstract void doLocalStopWithExclusiveLock(Handler<AsyncResult<JsonObject>> onComplete);

	protected abstract void doScramWithExclusiveLock(boolean excludeVoltageReference,
			Handler<AsyncResult<JsonObject>> onComplete);

	protected abstract void doDeviceControllingWithExclusiveLock(JsonObject operation,
			Handler<AsyncResult<JsonObject>> onComplete);

	protected abstract JsonObject mergeDeviceStatus(JsonObject value);

	private void startLocalStopService_(Handler<AsyncResult<Void>> onComplete) {
		vertx.eventBus().<Void>localConsumer(ServiceAddress.Controller.stopLocal(), req -> {
			if (log.isInfoEnabled())
				log.info("STOP LOCAL command received");
			if (log.isInfoEnabled())
				log.info("( do without exclusive lock !!! )");
			doLocalStopWithExclusiveLock(resDoLocalStopWithExclusiveLock -> {
				if (resDoLocalStopWithExclusiveLock.succeeded()) {
					req.reply(resDoLocalStopWithExclusiveLock.result());
				} else {
					req.fail(-1, resDoLocalStopWithExclusiveLock.cause().getMessage());
				}
			});
		}).completionHandler(onComplete);
	}

	private void startScramService_(Handler<AsyncResult<Void>> onComplete) {
		vertx.eventBus().<Void>consumer(ServiceAddress.Controller.scram(), req -> {
			if (log.isInfoEnabled())
				log.info("SCRAM command received");
			if (log.isInfoEnabled())
				log.info("( do without exclusive lock !!! )");
			boolean excludeVoltageReference = Boolean.valueOf(req.headers().get("excludeVoltageReference"));
			doScramWithExclusiveLock(excludeVoltageReference, resDoScramWithExclusiveLock -> {
				if (resDoScramWithExclusiveLock.succeeded()) {
					req.reply(resDoScramWithExclusiveLock.result());
				} else {
					req.fail(-1, resDoScramWithExclusiveLock.cause().getMessage());
				}
			});
		}).completionHandler(onComplete);
	}

	private void startDeviceControllingService_(Handler<AsyncResult<Void>> onComplete) {
		vertx.eventBus().<JsonObject>consumer(ServiceAddress.Controller.deviceControlling(ApisConfig.unitId()), req -> {
			checkGridMasterInterlock_(req, resCheckGridMasterInterlock -> {
				if (resCheckGridMasterInterlock.succeeded()) {
					DataAcquisition.acquireExclusiveLock(vertx, resExclusiveLock -> {
						if (resExclusiveLock.succeeded()) {
							LocalExclusiveLock.Lock lock = resExclusiveLock.result();
							doDeviceControllingWithExclusiveLock(req.body(),
									resDoDeviceControllingWithExclusiveLock -> {
										lock.release();
										if (resDoDeviceControllingWithExclusiveLock.succeeded()) {
											req.reply(resDoDeviceControllingWithExclusiveLock.result());
										} else {
											req.fail(-1, resDoDeviceControllingWithExclusiveLock.cause().getMessage());
										}
									});
						} else {
							ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN,
									resExclusiveLock.cause(), req);
						}
					});
				} else {
					req.fail(-1, resCheckGridMasterInterlock.cause().getMessage());
				}
			});
		}).completionHandler(onComplete);
	}

	private <T> void checkGridMasterInterlock_(Message<T> req, Handler<AsyncResult<Void>> onComplete) {
		String reqGridMasterUnitId = req.headers().get("gridMasterUnitId");
		if (reqGridMasterUnitId != null) {
			if (PolicyKeeping.isMember(reqGridMasterUnitId)) {
				InterlockUtil.getGridMasterUnitId(vertx, resGridMasterUnitId -> {
					if (resGridMasterUnitId.succeeded()) {
						String interlockedGridMasterUnitId = resGridMasterUnitId.result();
						if (interlockedGridMasterUnitId != null) {
							if (reqGridMasterUnitId.equals(interlockedGridMasterUnitId)) {
								onComplete.handle(Future.succeededFuture());
							} else {
								ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL,
										Error.Level.WARN,
										"access from illegal gridMaster; interlocked gridMasterUnitId: "
												+ interlockedGridMasterUnitId + ", gridMasterUnitId in request: "
												+ reqGridMasterUnitId,
										onComplete);
							}
						} else {
							ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN,
									"illegal access; no interlocked gridMasterUnitId", onComplete);
						}
					} else {
						ErrorExceptionUtil.reportIfNeedAndFail(vertx, resGridMasterUnitId.cause(), onComplete);
					}
				});
			} else {
				ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN,
						"request received from illegal unit : " + reqGridMasterUnitId, onComplete);
			}
		} else {
			ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN,
					"illegal access; no gridMasterUnitId in request header", onComplete);
		}
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
					JsonObject data = r.result();
					data = mergeDeviceStatus(data);
					onComplete.handle(Future.succeededFuture(data));
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
