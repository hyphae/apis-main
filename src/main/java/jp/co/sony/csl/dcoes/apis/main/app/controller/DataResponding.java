package jp.co.sony.csl.dcoes.apis.main.app.controller;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.core.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.ServiceAddress;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.LocalExclusiveLock;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.ReplyFailureUtil;
import jp.co.sony.csl.dcoes.apis.main.app.PolicyKeeping;
import jp.co.sony.csl.dcoes.apis.main.app.mediator.util.InterlockUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ApisConfig;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorExceptionUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

public abstract class DataResponding extends AbstractVerticle {
	private static final Logger log = LoggerFactory.getLogger(DataResponding.class);

	@Override
	public void start(Promise<Void> startPromise) throws Exception {
		startInternalUnitDataService_(resInternalUnitData -> {
			if (resInternalUnitData.succeeded()) {
				startExternalUnitDataService_(resExternalUnitData -> {
					if (resExternalUnitData.succeeded()) {
						startInternalUnitDeviceStatusService_(resInternalUnitDeviceStatus -> {
							if (resInternalUnitDeviceStatus.succeeded()) {
								startExternalUnitDeviceStatusService_(resExternalUnitDeviceStatus -> {
									if (resExternalUnitDeviceStatus.succeeded()) {
										startUnitDatasService_(resUnitDatas -> {
											if (resUnitDatas.succeeded()) {
												if (log.isTraceEnabled())
													log.trace("started : " + deploymentID());
												startPromise.complete();
											} else {
												startPromise.fail(resUnitDatas.cause());
											}
										});
									} else {
										startPromise.fail(resExternalUnitDeviceStatus.cause());
									}
								});
							} else {
								startPromise.fail(resInternalUnitDeviceStatus.cause());
							}
						});
					} else {
						startPromise.fail(resExternalUnitData.cause());
					}
				});
			} else {
				startPromise.fail(resInternalUnitData.cause());
			}
		});
	}

	@Override
	public void stop() throws Exception {
		if (log.isTraceEnabled())
			log.trace("stopped : " + deploymentID());
	}

	protected abstract JsonObject cachedDeviceStatus();

	private void startInternalUnitDataService_(Handler<AsyncResult<Void>> onComplete) {
		vertx.eventBus().<Void>localConsumer(ServiceAddress.Controller.unitData(), req -> {
			getDataAndReply_(req);
		}).completionHandler(onComplete);
	}

	private void startExternalUnitDataService_(Handler<AsyncResult<Void>> onComplete) {
		vertx.eventBus().<Void>consumer(ServiceAddress.Controller.unitData(ApisConfig.unitId()), req -> {
			checkGridMasterInterlock_(req, resCheckGridMasterInterlock -> {
				if (resCheckGridMasterInterlock.succeeded()) {
					getDataAndReply_(req);
				} else {
					req.fail(-1, resCheckGridMasterInterlock.cause().getMessage());
				}
			});
		}).completionHandler(onComplete);
	}

	private void startInternalUnitDeviceStatusService_(Handler<AsyncResult<Void>> onComplete) {
		vertx.eventBus().<Void>localConsumer(ServiceAddress.Controller.unitDeviceStatus(), req -> {
			getDeviceStatusAndReply_(req);
		}).completionHandler(onComplete);
	}

	private void startExternalUnitDeviceStatusService_(Handler<AsyncResult<Void>> onComplete) {
		vertx.eventBus().<Void>consumer(ServiceAddress.Controller.unitDeviceStatus(ApisConfig.unitId()), req -> {
			checkGridMasterInterlock_(req, resCheckGridMasterInterlock -> {
				if (resCheckGridMasterInterlock.succeeded()) {
					getDeviceStatusAndReply_(req);
				} else {
					req.fail(-1, resCheckGridMasterInterlock.cause().getMessage());
				}
			});
		}).completionHandler(onComplete);
	}

	private void startUnitDatasService_(Handler<AsyncResult<Void>> onComplete) {
		vertx.eventBus().<Void>consumer(ServiceAddress.Controller.unitDatas(), req -> {
			String replyAddress = req.headers().get("replyAddress");
			if (replyAddress != null) {
				checkGridMasterInterlock_(req, resCheckGridMasterInterlock -> {
					if (resCheckGridMasterInterlock.succeeded()) {
						getData_(req, resGetData -> {
							if (resGetData.succeeded()) {
								vertx.eventBus().send(replyAddress, resGetData.result());
							} else {
								log.error(resGetData.cause().getMessage());
							}
						});
					}
				});
			} else {
				ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN,
						"illegal access; no replyAddress in request header");
			}
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

	private void getDeviceStatusAndReply_(Message<Void> message) {
		getDeviceStatus_(message, resGetDeviceStatus -> {
			if (resGetDeviceStatus.succeeded()) {
				message.reply(resGetDeviceStatus.result());
			} else {
				message.fail(-1, resGetDeviceStatus.cause().getMessage());
			}
		});
	}

	private void getDeviceStatus_(Message<Void> message, Handler<AsyncResult<JsonObject>> onComplete) {
		String urgent = message.headers().get("urgent");
		if (urgent != null && Boolean.valueOf(urgent)) {
			DataAcquisition.acquireExclusiveLock(vertx, resExclusiveLock -> {
				if (resExclusiveLock.succeeded()) {
					LocalExclusiveLock.Lock lock = resExclusiveLock.result();
					doGetDeviceStatusWithExclusiveLock_(resDoGetDeviceStatusWithExclusiveLock -> {
						lock.release();
						onComplete.handle(resDoGetDeviceStatusWithExclusiveLock);
					});
				} else {
					ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN,
							resExclusiveLock.cause(), onComplete);
				}
			});
		} else {
			onComplete.handle(Future.succeededFuture(cachedDeviceStatus()));
		}
	}

	private void doGetDeviceStatusWithExclusiveLock_(Handler<AsyncResult<JsonObject>> onComplete) {
		vertx.eventBus().<JsonObject>request(ServiceAddress.Controller.urgentUnitDeviceStatus(), null, rep -> {
			if (rep.succeeded()) {
				onComplete.handle(Future.succeededFuture(rep.result().body()));
			} else {
				if (ReplyFailureUtil.isRecipientFailure(rep)) {
					onComplete.handle(Future.failedFuture(rep.cause()));
				} else {
					ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR,
							"Communication failed on EventBus", rep.cause(), onComplete);
				}
			}
		});
	}

	private void getDataAndReply_(Message<Void> message) {
		getData_(message, resGetData -> {
			if (resGetData.succeeded()) {
				message.reply(resGetData.result());
			} else {
				message.fail(-1, resGetData.cause().getMessage());
			}
		});
	}

	private void getData_(Message<Void> message, Handler<AsyncResult<JsonObject>> onComplete) {
		String urgent = message.headers().get("urgent");
		if (urgent != null && Boolean.valueOf(urgent)) {
			DataAcquisition.acquireExclusiveLock(vertx, resExclusiveLock -> {
				if (resExclusiveLock.succeeded()) {
					LocalExclusiveLock.Lock lock = resExclusiveLock.result();
					doGetDataWithExclusiveLock_(resDoGetDataWithExclusiveLock -> {
						lock.release();
						onComplete.handle(resDoGetDataWithExclusiveLock);
					});
				} else {
					ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN,
							resExclusiveLock.cause(), onComplete);
				}
			});
		} else {
			onComplete.handle(Future.succeededFuture(DataAcquisition.cache.jsonObject()));
		}
	}

	private void doGetDataWithExclusiveLock_(Handler<AsyncResult<JsonObject>> onComplete) {
		vertx.eventBus().<JsonObject>request(ServiceAddress.Controller.urgentUnitData(), null, rep -> {
			if (rep.succeeded()) {
				onComplete.handle(Future.succeededFuture(rep.result().body()));
			} else {
				if (ReplyFailureUtil.isRecipientFailure(rep)) {
					onComplete.handle(Future.failedFuture(rep.cause()));
				} else {
					ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR,
							"Communication failed on EventBus", rep.cause(), onComplete);
				}
			}
		});
	}

}
