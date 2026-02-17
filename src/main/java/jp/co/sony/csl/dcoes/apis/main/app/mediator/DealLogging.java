package jp.co.sony.csl.dcoes.apis.main.app.mediator;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import jp.co.sony.csl.dcoes.apis.common.Deal;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.ServiceAddress;
import jp.co.sony.csl.dcoes.apis.common.util.StringUtil;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.FileSystemUtil;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectUtil;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.VertxConfig;
import jp.co.sony.csl.dcoes.apis.main.app.PolicyKeeping;
import jp.co.sony.csl.dcoes.apis.main.app.mediator.util.DealUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ApisConfig;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorExceptionUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

public class DealLogging extends AbstractVerticle {
	private static final Logger log = LoggerFactory.getLogger(DealLogging.class);

	private static final Long DEFAULT_DEAL_LOGGING_PERIOD_MSEC = 5000L;
	private static final JsonObjectUtil.DefaultString DEFAULT_DEAL_LOG_DIR_FORMAT = new JsonObjectUtil.DefaultString(
			"'" + StringUtil.TMPDIR + "/apis/dealLog/'uuuu'/'MM'/'dd");

	private long dealLoggingTimerId_ = 0L;
	private boolean stopped_ = false;

	@Override
	public void start(Promise<Void> startPromise) throws Exception {
		startDealLoggingService_(resDealLogging -> {
			if (resDealLogging.succeeded()) {
				dealLoggingTimerHandler_(0L);
				if (log.isTraceEnabled())
					log.trace("started : " + deploymentID());
				startPromise.complete();
			} else {
				startPromise.fail(resDealLogging.cause());
			}
		});
	}

	@Override
	public void stop() throws Exception {
		stopped_ = true;
		if (log.isTraceEnabled())
			log.trace("stopped : " + deploymentID());
	}

	private void startDealLoggingService_(Handler<AsyncResult<Void>> onComplete) {
		vertx.eventBus().<JsonObject>consumer(ServiceAddress.Mediator.dealLogging(), req -> {
			JsonObject deal = req.body();
			if (deal != null) {
				if (Deal.isInvolved(deal, ApisConfig.unitId()) && Deal.isSaveworthy(deal)) {
					List<JsonObject> deals = new ArrayList<>(1);
					deals.add(deal);
					new DealLogging_(deals, resLogging -> {
						if (resLogging.succeeded()) {
							req.reply(ApisConfig.unitId());
						} else {
							req.fail(-1, resLogging.cause().getMessage());
						}
					}).doLoop_();
				} else {
					req.reply("N/A");
				}
			} else {
				ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN,
						"deal is null", req);
			}
		}).completionHandler(onComplete);
	}

	private void setDealLoggingTimer_() {
		Long delay = PolicyKeeping.cache().getLong(DEFAULT_DEAL_LOGGING_PERIOD_MSEC, "mediator",
				"dealLoggingPeriodMsec");
		setDealLoggingTimer_(delay);
	}

	private void setDealLoggingTimer_(long delay) {
		dealLoggingTimerId_ = vertx.setTimer(delay, this::dealLoggingTimerHandler_);
	}

	private void dealLoggingTimerHandler_(Long timerId) {
		if (stopped_)
			return;
		if (null == timerId || timerId.longValue() != dealLoggingTimerId_) {
			ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN,
					"illegal timerId : " + timerId + ", dealLoggingTimerId_ : " + dealLoggingTimerId_);
			return;
		}
		doDealLogging_(res -> {
			setDealLoggingTimer_();
		});
	}

	private void doDealLogging_(Handler<AsyncResult<Void>> onComplete) {
		DealUtil.withUnitId(vertx, ApisConfig.unitId(), resWithUnitId -> {
			if (resWithUnitId.succeeded()) {
				List<JsonObject> deals = resWithUnitId.result();
				if (!deals.isEmpty()) {
					List<JsonObject> filtered = new ArrayList<>(deals.size());
					for (JsonObject aDeal : deals) {
						if (Deal.isSaveworthy(aDeal)) {
							filtered.add(aDeal);
						}
					}
					deals = filtered;
				}
				if (!deals.isEmpty()) {
					new DealLogging_(deals, onComplete).doLoop_();
				} else {
					onComplete.handle(Future.succeededFuture());
				}
			} else {
				ErrorExceptionUtil.reportIfNeedAndFail(vertx, resWithUnitId.cause(), onComplete);
			}
		});
	}

	private static final DateTimeFormatter LOG_DIR_FORMATTER_;
	static {
		String s = VertxConfig.config.getString(DEFAULT_DEAL_LOG_DIR_FORMAT, "dealLogDirFormat");
		s = StringUtil.fixFilePath(s);
		LOG_DIR_FORMATTER_ = DateTimeFormatter.ofPattern(s);
	}

	private class DealLogging_ {
		private List<JsonObject> deals_;
		private Handler<AsyncResult<Void>> onComplete_;
		private List<JsonObject> dealsForLoop_;

		private DealLogging_(List<JsonObject> deals, Handler<AsyncResult<Void>> onComplete) {
			deals_ = deals;
			onComplete_ = onComplete;
			dealsForLoop_ = new ArrayList<>(deals_);
		}

		private void doLoop_() {
			if (dealsForLoop_.isEmpty()) {
				onComplete_.handle(Future.succeededFuture());
			} else {
				JsonObject aDeal = dealsForLoop_.remove(0);
				writeToFile_(aDeal, resWriteToFile -> {
					doLoop_();
				});
			}
		}

		private void writeToFile_(JsonObject deal, Handler<AsyncResult<Void>> onComplete) {
			LocalDateTime createDateTime = JsonObjectUtil.getLocalDateTime(deal, "createDateTime");
			String logDir = LOG_DIR_FORMATTER_.format(createDateTime);
			String filename = Deal.dealId(deal);
			String path = logDir + File.separatorChar + filename;
			if (log.isDebugEnabled())
				log.debug("path : " + path);
			ensureFile_(logDir, filename, path, resEnsureFile -> {
				if (resEnsureFile.succeeded()) {
					vertx.fileSystem().writeFile(path, Buffer.buffer(deal.encode()), resWriteFile -> {
						if (resWriteFile.succeeded()) {
							onComplete.handle(Future.succeededFuture());
						} else {
							ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL,
									Error.Level.FATAL, "Operation failed on File System", resWriteFile.cause(),
									onComplete);
						}
					});
				} else {
					onComplete.handle(resEnsureFile);
				}
			});
		}

		private void ensureFile_(String logDir, String filename, String path, Handler<AsyncResult<Void>> onComplete) {
			FileSystemUtil.ensureDirectory(vertx, logDir, resEnsureDir -> {
				if (resEnsureDir.succeeded()) {
					vertx.fileSystem().exists(path, resExists -> {
						if (resExists.succeeded()) {
							if (resExists.result()) {
								onComplete.handle(Future.succeededFuture());
							} else {
								vertx.fileSystem().createFile(path, resCreate -> {
									if (resCreate.succeeded()) {
										onComplete.handle(Future.succeededFuture());
									} else {
										vertx.fileSystem().exists(path, resExistsAgain -> {
											if (resExistsAgain.succeeded()) {
												if (resExistsAgain.result()) {
													onComplete.handle(Future.succeededFuture());
												} else {
													ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK,
															Error.Extent.LOCAL, Error.Level.FATAL,
															"Operation failed on File System", resCreate.cause(),
															onComplete);
												}
											} else {
												ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK,
														Error.Extent.LOCAL, Error.Level.FATAL,
														"Operation failed on File System", resExistsAgain.cause(),
														onComplete);
											}
										});
									}
								});
							}
						} else {
							ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL,
									Error.Level.FATAL, "Operation failed on File System", resExists.cause(),
									onComplete);
						}
					});
				} else {
					ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.FATAL,
							"Operation failed on File System", resEnsureDir.cause(), onComplete);
				}
			});
		}
	}

}
