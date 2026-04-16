package jp.co.sony.csl.dcoes.apis.main.app.mediator;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

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

/**
 * A Verticle that records interchange information in the file system.
 * Launched from the {@link Mediator} Verticle.
 * Periodically write the information of interchanges in which this unit participates to the file system.
 * This data is written even if externally requested.
 * @author OES Project
 *          
 * 融通情報をファイルシステムに記録する Verticle.
 * {@link Mediator} Verticle から起動される.
 * 自ユニットが参加する融通情報を定期的にファイルシステムに書き込む.
 * 外部からの要求があったときにも書き込む.
 * @author OES Project
 */
public class DealLogging extends AbstractVerticle {
	private static final Logger log = LoggerFactory.getLogger(DealLogging.class);

	/**
	 * Default duration of the interchange information storage cycle [ms].
	 * Value: {@value}.
	 *          
	 * 融通情報保存周期のデフォルト値 [ms].
	 * 値は {@value}.
	 */
	private static final Long DEFAULT_DEAL_LOGGING_PERIOD_MSEC = 5000L;
	/**
	 * Default value for interchange information save path format.
	 * Value: {@value}.
	 *          
	 * 融通情報保存パスのフォーマットのデフォルト値.
	 * 値は {@value}.
	 */
	private static final JsonObjectUtil.DefaultString DEFAULT_DEAL_LOG_DIR_FORMAT = new JsonObjectUtil.DefaultString("'" + StringUtil.TMPDIR + "/apis/dealLog/'uuuu'/'MM'/'dd");

	private long dealLoggingTimerId_ = 0L;
	private boolean stopped_ = false;

	/**
	 * Called at startup.
	 * Launches the {@link io.vertx.core.eventbus.EventBus} service.
	 * Start a timer that records interchange information periodically.
	 * @param startFuture {@inheritDoc}
	 * @throws Exception {@inheritDoc}
	 *          
	 * 起動時に呼び出される.
	 * {@link io.vertx.core.eventbus.EventBus} サービスを起動する.
	 * 定期的に融通を記録するタイマを起動する.
	 * @param startFuture {@inheritDoc}
	 * @throws Exception {@inheritDoc}
	 */
	@Override public void start(Future<Void> startFuture) throws Exception {
		startDealLoggingService_(resDealLogging -> {
			if (resDealLogging.succeeded()) {
				dealLoggingTimerHandler_(0L);
				if (log.isTraceEnabled()) log.trace("started : " + deploymentID());
				startFuture.complete();
			} else {
				startFuture.fail(resDealLogging.cause());
			}
		});
	}

	/**
	 * Called when stopped.
	 * Set a flag to stop the timer.
	 * @throws Exception {@inheritDoc}
	 *          
	 * 停止時に呼び出される.
	 * タイマを止めるためのフラグを立てる.
	 * @throws Exception {@inheritDoc}
	 */
	@Override public void stop() throws Exception {
		stopped_ = true;
		if (log.isTraceEnabled()) log.trace("stopped : " + deploymentID());
	}

	////

	/**
	 * Launch the {@link io.vertx.core.eventbus.EventBus} service.
	 * Address: {@link ServiceAddress.Mediator#dealLogging()}
	 * Scope: global
	 * Function: Records an interchange
	 *           If this unit is participating in the received interchange, store it in the file system.
	 *           Pass through if this unit is not participating in the received interchange.
	 * Message body: Interchange information [{@link JsonObject}]
	 * Message header: none
	 * Response: ID of this unit if it is participating in the received interchange [{@link String}].
	 *           {@code "N/A"} if this unit is not participating in the received interchange.
	 *           Fails if an error occurs.
	 * @param completionHandler the completion handler
	 *          
	 * {@link io.vertx.core.eventbus.EventBus} サービス起動.
	 * アドレス : {@link ServiceAddress.Mediator#dealLogging()}
	 * 範囲 : グローバル
	 * 処理 : 融通を記録する.
	 * 　　   受け取った融通に自ユニットが参加していたらファイルシステムに保存する.
	 * 　　   受け取った融通に自ユニットが参加していなければスルー.
	 * メッセージボディ : 融通情報 [{@link JsonObject}]
	 * メッセージヘッダ : なし
	 * レスポンス : 受け取った融通に自ユニットが参加していたら自ユニットの ID [{@link String}].
	 * 　　　　　   受け取った融通に自ユニットが参加していなかったら {@code "N/A"}.
	 * 　　　　　   エラーが起きたら fail.
	 * @param completionHandler the completion handler
	 */
	private void startDealLoggingService_(Handler<AsyncResult<Void>> completionHandler) {
		vertx.eventBus().<JsonObject>consumer(ServiceAddress.Mediator.dealLogging(), req -> {
			JsonObject deal = req.body();
			if (deal != null) {
				if (Deal.isInvolved(deal, ApisConfig.unitId()) && Deal.isSaveworthy(deal)) {
					// Only record details if this unit is participating in the interchange and is in an internal state that should be recorded
					// 自ユニットがその融通に参加しており記録しておくべき内部状態である場合にのみ記録する
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
				ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, "deal is null", req);
			}
		}).completionHandler(completionHandler);
	}

	/**
	 * Set an interchange storage timer.
	 * The timeout duration is {@code POLICY.mediator.dealLoggingPeriodMsec} (default: {@link #DEFAULT_DEAL_LOGGING_PERIOD_MSEC}).
	 *          
	 * 融通保存タイマ設定.
	 * 待ち時間は {@code POLICY.mediator.dealLoggingPeriodMsec} ( デフォルト値 {@link #DEFAULT_DEAL_LOGGING_PERIOD_MSEC} ).
	 */
	private void setDealLoggingTimer_() {
		Long delay = PolicyKeeping.cache().getLong(DEFAULT_DEAL_LOGGING_PERIOD_MSEC, "mediator", "dealLoggingPeriodMsec");
		setDealLoggingTimer_(delay);
	}
	/**
	 * Set an interchange storage timer.
	 * @param delay cycle duration [ms]
	 *          
	 * 融通保存タイマ設定.
	 * @param delay 周期 [ms]
	 */
	private void setDealLoggingTimer_(long delay) {
		dealLoggingTimerId_ = vertx.setTimer(delay, this::dealLoggingTimerHandler_);
	}
	/**
	 * Perform interchange storage timer processing.
	 * @param timerId timer ID
	 *          
	 * 融通保存タイマ処理.
	 * @param timerId タイマ ID
	 */
	private void dealLoggingTimerHandler_(Long timerId) {
		if (stopped_) return;
		if (null == timerId || timerId.longValue() != dealLoggingTimerId_) {
			ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, "illegal timerId : " + timerId + ", dealLoggingTimerId_ : " + dealLoggingTimerId_);
			return;
		}
		doDealLogging_(res -> {
			setDealLoggingTimer_();
		});
	}
	private void doDealLogging_(Handler<AsyncResult<Void>> completionHandler) {
		// Get the interchange that this unit is participating in
		// 自ユニットが参加している融通を取得
		DealUtil.withUnitId(vertx, ApisConfig.unitId(), resWithUnitId -> {
			if (resWithUnitId.succeeded()) {
				List<JsonObject> deals = resWithUnitId.result();
				if (!deals.isEmpty()) {
					// Exclude items that do not have to be recorded
					// 記録する必要がないものは除外する
					List<JsonObject> filtered = new ArrayList<>(deals.size());
					for (JsonObject aDeal : deals) {
						if (Deal.isSaveworthy(aDeal)) {
							filtered.add(aDeal);
						}
					}
					deals = filtered;
				}
				if (!deals.isEmpty()) {
					new DealLogging_(deals, completionHandler).doLoop_();
				} else {
					completionHandler.handle(Future.succeededFuture());
				}
			} else {
				ErrorExceptionUtil.reportIfNeedAndFail(vertx, resWithUnitId.cause(), completionHandler);
			}
		});
	}
	private static final DateTimeFormatter LOG_DIR_FORMATTER_;
	static {
		String s = VertxConfig.config.getString(DEFAULT_DEAL_LOG_DIR_FORMAT, "dealLogDirFormat");
		s = StringUtil.fixFilePath(s);
		LOG_DIR_FORMATTER_ = DateTimeFormatter.ofPattern(s);
	}
	/**
	 * A class that stores interchange information in the file system.
	 * @author OES Project
	 *          
	 * 融通情報をファイルシステムに保存するクラス.
	 * @author OES Project
	 */
	private class DealLogging_ {
		private List<JsonObject> deals_;
		private Handler<AsyncResult<Void>> completionHandler_;
		private List<JsonObject> dealsForLoop_;
		/**
		 * Make an instance.
		 * @param deals a list of DEAL objects
		 * @param completionHandler the completion handler
		 *          
		 * インスタンス作成.
		 * @param deals DEAL オブジェクトのリスト
		 * @param completionHandler the completion handler
		 */
		private DealLogging_(List<JsonObject> deals, Handler<AsyncResult<Void>> completionHandler) {
			deals_ = deals;
			completionHandler_ = completionHandler;
			dealsForLoop_ = new ArrayList<>(deals_);
		}
		private void doLoop_() {
			if (dealsForLoop_.isEmpty()) {
				completionHandler_.handle(Future.succeededFuture());
			} else {
				JsonObject aDeal = dealsForLoop_.remove(0);
				writeToFile_(aDeal, resWriteToFile -> {
					doLoop_();
				});
			}
		}
		private void writeToFile_(JsonObject deal, Handler<AsyncResult<Void>> completionHandler) {
			LocalDateTime createDateTime = JsonObjectUtil.getLocalDateTime(deal, "createDateTime");
			String logDir = LOG_DIR_FORMATTER_.format(createDateTime);
			String filename = Deal.dealId(deal);
			String path = logDir + File.separatorChar + filename;
			if (log.isDebugEnabled()) log.debug("path : " + path);
			ensureFile_(logDir, filename, path, resEnsureFile -> {
				if (resEnsureFile.succeeded()) {
					vertx.fileSystem().writeFile(path, Buffer.buffer(deal.encode()), resWriteFile -> {
						if (resWriteFile.succeeded()) {
							completionHandler.handle(Future.succeededFuture());
						} else {
							ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.FATAL, "Operation failed on File System", resWriteFile.cause(), completionHandler);
						}
					});
				} else {
					completionHandler.handle(resEnsureFile);
				}
			});
		}
		private void ensureFile_(String logDir, String filename, String path, Handler<AsyncResult<Void>> completionHandler) {
			FileSystemUtil.ensureDirectory(vertx, logDir, resEnsureDir -> {
				if (resEnsureDir.succeeded()) {
					vertx.fileSystem().exists(path, resExists -> {
						if (resExists.succeeded()) {
							if (resExists.result()) {
								completionHandler.handle(Future.succeededFuture());
							} else {
								vertx.fileSystem().createFile(path, resCreate -> {
									if (resCreate.succeeded()) {
										completionHandler.handle(Future.succeededFuture());
									} else {
										// Failed when attempting to create a file that doesn't exist...
										// 無いから作ろうとしたら失敗したけど...
										vertx.fileSystem().exists(path, resExistsAgain -> {
											if (resExistsAgain.succeeded()) {
												if (resExistsAgain.result()) {
													// Checked again and it was there
													// 再確認してみたらあった
													// → Someone else must have created this file while we were trying to do so
													// → 作ろうとしている隙に誰かが作ったに違いない
													// → Maybe this is OK
													// → たぶん OK
													completionHandler.handle(Future.succeededFuture());
												} else {
													// Checked again and it's not there
													// 再確認してみたけどなかった
													// → NG
													// → NG
													ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.FATAL, "Operation failed on File System", resCreate.cause(), completionHandler);
												}
											} else {
												ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.FATAL, "Operation failed on File System", resExistsAgain.cause(), completionHandler);
											}
										});
									}
								});
							}
						} else {
							ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.FATAL, "Operation failed on File System", resExists.cause(), completionHandler);
						}
					});
				} else {
					ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.FATAL, "Operation failed on File System", resEnsureDir.cause(), completionHandler);
				}
			});
		}
	}

}
