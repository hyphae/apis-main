package jp.co.sony.csl.dcoes.apis.main.app.mediator.util;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import jp.co.sony.csl.dcoes.apis.common.Deal;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.util.DateTimeUtil;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.EncryptedClusterWideMapUtil;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectUtil;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.LocalExclusiveLock;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorExceptionUtil;

/**
 * A tool for managing interchange information in shared memory.
 * @author OES Project
 *          
 * 融通情報を共有メモリ上に管理するツール.
 * @author OES Project
 */
public class DealUtil {
	private static final Logger log = LoggerFactory.getLogger(DealUtil.class);

	private static final LocalExclusiveLock exclusiveLock_ = new LocalExclusiveLock(DealUtil.class.getName());
	/**
	 * Acquire an exclusive lock.
	 * Results are received with the {@link AsyncResult#result()} method of completionHandler.
	 * @param vertx a vertx object
	 * @param completionHandler the completion handler
	 *          
	 * 排他ロックを獲得する.
	 * completionHandler の {@link AsyncResult#result()} で受け取る.
	 * @param vertx vertx オブジェクト
	 * @param completionHandler the completion handler
	 */
	public static void acquireExclusiveLock(Vertx vertx, Handler<AsyncResult<LocalExclusiveLock.Lock>> completionHandler) {
		exclusiveLock_.acquire(vertx, completionHandler);
	}
	/**
	 * Reset an exclusive lock.
	 * @param vertx a vertx object
	 *          
	 * 排他ロックをリセットする.
	 * @param vertx vertx オブジェクト
	 */
	public static void resetExclusiveLock(Vertx vertx) {
		exclusiveLock_.reset(vertx);
	}

	private static final String MAP_NAME = DealUtil.class.getName();

	private DealUtil() { }

	/**
	 * Store DEAL information in shared memory.
	 * Global error if a DEAL with the same ID already exists.
	 * @param vertx a vertx object
	 * @param deal a DEAL object
	 * @param completionHandler the completion handler
	 *          
	 * DEAL 情報を共有メモリに格納する.
	 * 同じ ID の DEAL がすでに存在していたらグローバルエラー.
	 * @param vertx vertx オブジェクト
	 * @param deal DEAL オブジェクト
	 * @param completionHandler the completion handler
	 */
	public static void add(Vertx vertx, JsonObject deal, Handler<AsyncResult<Void>> completionHandler) {
		String dealId = Deal.dealId(deal);
		if (dealId != null) {
			EncryptedClusterWideMapUtil.<String, JsonObject>getEncryptedClusterWideMap(vertx, MAP_NAME, resMap -> {
				if (resMap.succeeded()) {
					resMap.result().putIfAbsent(dealId, deal, resPutIfAbsent -> {
						if (resPutIfAbsent.succeeded()) {
							JsonObject existingValue = resPutIfAbsent.result();
							if (existingValue == null) {
								if (log.isInfoEnabled()) log.info("deal created : " + dealId);
								completionHandler.handle(Future.succeededFuture());
							} else {
								String msg = "DealUtil.add(); deal already exists with same id : " + dealId;
								ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.ERROR, msg, completionHandler);
							}
						} else {
							ErrorExceptionUtil.logAndFail(Error.Category.FRAMEWORK, Error.Extent.GLOBAL, Error.Level.ERROR, "Communication failed on SharedData", resPutIfAbsent.cause(), completionHandler);
						}
					});
				} else {
					ErrorExceptionUtil.logAndFail(Error.Category.FRAMEWORK, Error.Extent.GLOBAL, Error.Level.ERROR, "Communication failed on SharedData", resMap.cause(), completionHandler);
				}
			});
		} else {
			ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.ERROR, "DealUtil.add(); no dealId in deal : " + deal, completionHandler);
		}
	}

	/**
	 * Get all DEAL objects stored in shared memory.
	 * Results are received with the {@link AsyncResult#result()} method of completionHandler.
	 * @param vertx a vertx object
	 * @param completionHandler the completion handler
	 *          
	 * 共有メモリに格納されている DEAL オブジェクトを全て取得する.
	 * completionHandler の {@link AsyncResult#result()} で受け取る.
	 * @param vertx vertx オブジェクト
	 * @param completionHandler the completion handler
	 */
	public static void all(Vertx vertx, Handler<AsyncResult<List<JsonObject>>> completionHandler) {
		EncryptedClusterWideMapUtil.<String, JsonObject>getEncryptedClusterWideMap(vertx, MAP_NAME, resMap -> {
			if (resMap.succeeded()) {
				resMap.result().values(completionHandler);
			} else {
				ErrorExceptionUtil.logAndFail(Error.Category.FRAMEWORK, Error.Extent.GLOBAL, Error.Level.ERROR, "Communication failed on SharedData", resMap.cause(), completionHandler);
			}
		});
	}

	/**
	 * Get the Master Deal from shared memory.
	 * Results are received with the {@link AsyncResult#result()} method of completionHandler.
	 * @param vertx a vertx object
	 * @param completionHandler the completion handler
	 *          
	 * 共有メモリから Master Deal を取得する.
	 * completionHandler の {@link AsyncResult#result()} で受け取る.
	 * @param vertx vertx オブジェクト
	 * @param completionHandler the completion handler
	 */
	public static void master(Vertx vertx, Handler<AsyncResult<JsonObject>> completionHandler) {
		all(vertx, resAll -> {
			if (resAll.succeeded()) {
				for (JsonObject aDeal : resAll.result()) {
					if (Deal.isMaster(aDeal)) {
						completionHandler.handle(Future.succeededFuture(aDeal));
						return;
					}
				}
				completionHandler.handle(Future.succeededFuture());
			} else {
				completionHandler.handle(Future.failedFuture(resAll.cause()));
			}
		});
	}

	/**
	 * Get the DEAL object in which the unit specified by {@code unitId} participates from shared memory.
	 * Results are received with the {@link AsyncResult#result()} method of completionHandler.
	 * @param vertx a vertx object
	 * @param unitId the unit ID
	 * @param completionHandler the completion handler
	 *          
	 * {@code unitId} で指定したユニットが参加している DEAL オブジェクトを共有メモリから取得する.
	 * completionHandler の {@link AsyncResult#result()} で受け取る.
	 * @param vertx vertx オブジェクト
	 * @param unitId ユニット ID
	 * @param completionHandler the completion handler
	 */
	public static void withUnitId(Vertx vertx, String unitId, Handler<AsyncResult<List<JsonObject>>> completionHandler) {
		if (unitId != null) {
			all(vertx, resAll -> {
				if (resAll.succeeded()) {
					List<JsonObject> result = new ArrayList<JsonObject>();
					for (JsonObject aDeal : resAll.result()) {
						if (Deal.isInvolved(aDeal, unitId)) {
							result.add(aDeal);
						}
					}
					completionHandler.handle(Future.succeededFuture(result));
				} else {
					completionHandler.handle(Future.failedFuture(resAll.cause()));
				}
			});
		} else {
			ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.ERROR, "DealUtil.withUnitId(); no unitId", completionHandler);
		}
	}

	/**
	 * Get the DEAL object with the ID specified by {@code dealId} from shared memory.
	 * Raise an error if it doesn't exist.
	 * Results are received with the {@link AsyncResult#result()} method of completionHandler.
	 * @param vertx a vertx object
	 * @param dealId an interchange ID
	 * @param completionHandler the completion handler
	 *          
	 * {@code dealId} で指定した ID を持つ DEAL オブジェクトを共有メモリから取得する.
	 * 存在しなければエラーになる.
	 * completionHandler の {@link AsyncResult#result()} で受け取る.
	 * @param vertx vertx オブジェクト
	 * @param dealId 融通 ID
	 * @param completionHandler the completion handler
	 */
	public static void get(Vertx vertx, String dealId, Handler<AsyncResult<JsonObject>> completionHandler) {
		get(vertx, dealId, false, completionHandler);
	}
	/**
	 * Get the DEAL object with the ID specified by {@code dealId} from shared memory.
	 * If it doesn't exist, handle according to the state of {@code ignoreNotExists}.
	 * Results are received with the {@link AsyncResult#result()} method of completionHandler.
	 * @param vertx a vertx object
	 * @param dealId an interchange ID
	 * @param ignoreNotExists the action to take if an interchange with the specified {@code dealId} does not exist
	 *        - true: Issue a warning and return {@code null}
	 *        - false: Raise a global error
	 * @param completionHandler the completion handler
	 *          
	 * {@code dealId} で指定した ID を持つ DEAL オブジェクトを共有メモリから取得する.
	 * 存在しない場合は {@code ignoreNotExists} に応じて対応する.
	 * completionHandler の {@link AsyncResult#result()} で受け取る.
	 * @param vertx vertx オブジェクト
	 * @param dealId 融通 ID
	 * @param ignoreNotExists 指定した {@code dealId} を持つものが存在しない場合の挙動
	 *        - true : 警告を出力して {@code null} を返す
	 *        - false : グローバルエラーにする
	 * @param completionHandler the completion handler
	 */
	public static void get(Vertx vertx, String dealId, boolean ignoreNotExists, Handler<AsyncResult<JsonObject>> completionHandler) {
		if (dealId != null) {
			EncryptedClusterWideMapUtil.<String, JsonObject>getEncryptedClusterWideMap(vertx, MAP_NAME, resMap -> {
				if (resMap.succeeded()) {
					resMap.result().get(dealId, resGet -> {
						if (resGet.succeeded()) {
							JsonObject result = resGet.result();
							if (result != null) {
								completionHandler.handle(Future.succeededFuture(result));
							} else {
								String msg = "DealUtil.get(); no deal found with dealId : " + dealId;
								if (ignoreNotExists) {
									ErrorExceptionUtil.log(Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.WARN, msg);
									completionHandler.handle(Future.succeededFuture());
								} else {
									ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.ERROR, msg, completionHandler);
								}
							}
						} else {
							ErrorExceptionUtil.logAndFail(Error.Category.FRAMEWORK, Error.Extent.GLOBAL, Error.Level.ERROR, "Communication failed on SharedData", resGet.cause(), completionHandler);
						}
					});
				} else {
					ErrorExceptionUtil.logAndFail(Error.Category.FRAMEWORK, Error.Extent.GLOBAL, Error.Level.ERROR, "Communication failed on SharedData", resMap.cause(), completionHandler);
				}
			});
		} else {
			ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.ERROR, "DealUtil.get(); no dealId", completionHandler);
		}
	}

	/**
	 * Update shared memory with the contents of the DEAL object specified by {@code deal}.
	 * Raise an error if it doesn't exist.
	 * @param vertx a vertx object
	 * @param deal a DEAL object
	 * @param completionHandler the completion handler
	 *          
	 * {@code deal} で指定する DEAL オブジェクトの内容で共有メモリを更新する.
	 * 存在しなければエラーになる.
	 * @param vertx vertx オブジェクト
	 * @param deal DEAL オブジェクト
	 * @param completionHandler the completion handler
	 */
	public static void update(Vertx vertx, JsonObject deal, Handler<AsyncResult<Void>> completionHandler) {
		update(vertx, deal, false, completionHandler);
	}
	/**
	 * Update shared memory with the contents of the DEAL object specified by {@code deal}.
	 * If it doesn't exist, handle according to the state of {@code ignoreNotExists}.
	 * @param vertx a vertx object
	 * @param deal a DEAL object
	 * @param ignoreNotExists the action to take if the specified DEAL does not exist
	 *        - true: Issue a warning
	 *        - false: Raise a global error
	 * @param completionHandler the completion handler
	 *          
	 * {@code deal} で指定する DEAL オブジェクトの内容で共有メモリを更新する.
	 * 存在しない場合は {@code ignoreNotExists} に応じて対応する.
	 * @param vertx vertx オブジェクト
	 * @param deal DEAL オブジェクト
	 * @param ignoreNotExists 指定した DEAL が存在しない場合の挙動
	 *        - true : 警告を出力する
	 *        - false : グローバルエラーにする
	 * @param completionHandler the completion handler
	 */
	public static void update(Vertx vertx, JsonObject deal, boolean ignoreNotExists, Handler<AsyncResult<Void>> completionHandler) {
		acquireExclusiveLock(vertx, resExclusiveLock -> {
			if (resExclusiveLock.succeeded()) {
				LocalExclusiveLock.Lock lock = resExclusiveLock.result();
				doUpdateWithExclusiveLock_(vertx, deal, ignoreNotExists, resDoUpdateWithExclusiveLock -> {
					lock.release();
					completionHandler.handle(resDoUpdateWithExclusiveLock);
				});
			} else {
				ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, resExclusiveLock.cause(), completionHandler);
			}
		});
	}
	private static void doUpdateWithExclusiveLock_(Vertx vertx, JsonObject deal, boolean ignoreNotExists, Handler<AsyncResult<Void>> completionHandler) {
		String dealId = Deal.dealId(deal);
		if (dealId != null) {
			EncryptedClusterWideMapUtil.<String, JsonObject>getEncryptedClusterWideMap(vertx, MAP_NAME, resMap -> {
				if (resMap.succeeded()) {
					resMap.result().get(dealId, resGet -> {
						if (resGet.succeeded()) {
							JsonObject old = resGet.result();
							if (old != null) {
								resMap.result().replaceIfPresent(dealId, old, deal, resReplaceIfPresent -> {
									if (resReplaceIfPresent.succeeded()) {
										Boolean replaced = resReplaceIfPresent.result();
										if (replaced) {
											if (log.isInfoEnabled()) log.info("deal updated : " + dealId);
											completionHandler.handle(Future.succeededFuture());
										} else {
											// Replacement failed because the old value has changed
											// old の値が変わっていたので差し替え失敗
											String msg = "DealUtil.update(); failed to replace with dealId : " + dealId;
											ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.ERROR, msg, completionHandler);
										}
									} else {
										ErrorExceptionUtil.logAndFail(Error.Category.FRAMEWORK, Error.Extent.GLOBAL, Error.Level.ERROR, "Communication failed on SharedData", resReplaceIfPresent.cause(), completionHandler);
									}
								});
							} else {
								String msg = "DealUtil.update(); no deal found with dealId : " + dealId;
								if (ignoreNotExists) {
									ErrorExceptionUtil.log(Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.WARN, msg);
									completionHandler.handle(Future.succeededFuture());
								} else {
									ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.ERROR, msg, completionHandler);
								}
							}
						} else {
							ErrorExceptionUtil.logAndFail(Error.Category.FRAMEWORK, Error.Extent.GLOBAL, Error.Level.ERROR, "Communication failed on SharedData", resGet.cause(), completionHandler);
						}
					});
				} else {
					ErrorExceptionUtil.logAndFail(Error.Category.FRAMEWORK, Error.Extent.GLOBAL, Error.Level.ERROR, "Communication failed on SharedData", resMap.cause(), completionHandler);
				}
			});
		} else {
			ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.ERROR, "DealUtil.update(); no dealId in deal : " + deal, completionHandler);
		}
	}

	/**
	 * Delete the DEAL object specified by {@code dealId} from shared memory.
	 * Raise an error if it doesn't exist.
	 * The deleted DEAL object is received by the {@link AsyncResult#result()} method of completionHandler.
	 * @param vertx a vertx object
	 * @param dealId an interchange ID
	 * @param completionHandler the completion handler
	 *          
	 * {@code dealId} で指定する DEAL オブジェクトを共有メモリから削除する.
	 * 存在しなければエラーになる.
	 * completionHandler の {@link AsyncResult#result()} で削除した DEAL オブジェクトを受け取る.
	 * @param vertx vertx オブジェクト
	 * @param dealId 融通 ID
	 * @param completionHandler the completion handler
	 */
	public static void remove(Vertx vertx, String dealId, Handler<AsyncResult<JsonObject>> completionHandler) {
		remove(vertx, dealId, false, completionHandler);
	}
	/**
	 * Delete the DEAL object specified by {@code dealId} from shared memory.
	 * If it doesn't exist, handle according to the state of {@code ignoreNotExists}.
	 * The deleted DEAL object is received by the {@link AsyncResult#result()} method of completionHandler.
	 * @param vertx a vertx object
	 * @param dealId an interchange ID
	 * @param ignoreNotExists the action to take if an interchange with the specified {@code dealId} does not exist
	 *        - true: Issue a warning and return {@code null}
	 *        - false: Raise a global error
	 * @param completionHandler the completion handler
	 *          
	 * {@code dealId} で指定する DEAL オブジェクトを共有メモリから削除する.
	 * 存在しない場合は {@code ignoreNotExists} に応じて対応する.
	 * completionHandler の {@link AsyncResult#result()} で削除した DEAL オブジェクトを受け取る.
	 * @param vertx vertx オブジェクト
	 * @param dealId 融通 ID
	 * @param ignoreNotExists 指定した {@code dealId} を持つものが存在しない場合の挙動
	 *        - true : 警告を出力して {@code null} を返す
	 *        - false : グローバルエラーにする
	 * @param completionHandler the completion handler
	 */
	public static void remove(Vertx vertx, String dealId, boolean ignoreNotExists, Handler<AsyncResult<JsonObject>> completionHandler) {
		if (dealId != null) {
			EncryptedClusterWideMapUtil.<String, JsonObject>getEncryptedClusterWideMap(vertx, MAP_NAME, resMap -> {
				if (resMap.succeeded()) {
					resMap.result().get(dealId, resGet -> {
						if (resGet.succeeded()) {
							JsonObject old = resGet.result();
							if (old != null) {
								resMap.result().removeIfPresent(dealId, old, resRemoveIfPresent -> {
									if (resRemoveIfPresent.succeeded()) {
										Boolean removed = resRemoveIfPresent.result();
										if (removed) {
											if (log.isInfoEnabled()) log.info("deal removed : " + dealId);
											completionHandler.handle(Future.succeededFuture());
										} else {
											String msg = "DealUtil.remove(); failed to remove with dealId : " + dealId;
											ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.ERROR, msg, completionHandler);
										}
									} else {
										ErrorExceptionUtil.logAndFail(Error.Category.FRAMEWORK, Error.Extent.GLOBAL, Error.Level.ERROR, "Communication failed on SharedData", resRemoveIfPresent.cause(), completionHandler);
									}
								});
							} else {
								String msg = "DealUtil.remove(); no deal found with dealId : " + dealId;
								if (ignoreNotExists) {
									ErrorExceptionUtil.log(Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.WARN, msg);
									completionHandler.handle(Future.succeededFuture());
								} else {
									ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.ERROR, msg, completionHandler);
								}
							}
						} else {
							ErrorExceptionUtil.logAndFail(Error.Category.FRAMEWORK, Error.Extent.GLOBAL, Error.Level.ERROR, "Communication failed on SharedData", resGet.cause(), completionHandler);
						}
					});
				} else {
					ErrorExceptionUtil.logAndFail(Error.Category.FRAMEWORK, Error.Extent.GLOBAL, Error.Level.ERROR, "Communication failed on SharedData", resMap.cause(), completionHandler);
				}
			});
		} else {
			ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.ERROR, "DealUtil.remove(); no dealId", completionHandler);
		}
	}

	////

	/**
	 * Put the DEAL object specified by {@code deal} into the "activated" state and update shared memory.
	 * @param vertx a vertx object
	 * @param deal a DEAL object
	 * @param dateTime the activation date and time. Uses the standard APIS program format
	 * @param completionHandler the completion handler
	 *          
	 * {@code deal} で指定する DEAL オブジェクトを activate 済みにし共有メモリを更新する.
	 * @param vertx vertx オブジェクト
	 * @param deal DEAL オブジェクト
	 * @param dateTime activate 日時. APIS プログラムの標準フォーマット
	 * @param completionHandler the completion handler
	 */
	public static void activate(Vertx vertx, JsonObject deal, String dateTime, Handler<AsyncResult<Void>> completionHandler) {
		if (!Deal.isActivated(deal)) {
			deal.put("activateDateTime", dateTime);
			if (log.isInfoEnabled()) log.info("deal activated");
			update(vertx, deal, completionHandler);
		} else {
			ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.ERROR, "DealUtil.activate(); already activated : " + Deal.activateDateTime(deal), completionHandler);
		}
	}
	/**
	 * Put the DEAL object specified by {@code deal} into the "rampUp" state (complete master-side activation), and update shared memory.
	 * @param vertx a vertx object
	 * @param deal a DEAL object
	 * @param dateTime the rampUp date and time. Uses the standard APIS program format
	 * @param completionHandler the completion handler
	 *          
	 * {@code deal} で指定する DEAL オブジェクトを rampUp 済み ( master 側起動完了 ) にし共有メモリを更新する.
	 * @param vertx vertx オブジェクト
	 * @param deal DEAL オブジェクト
	 * @param dateTime rampUp 日時. APIS プログラムの標準フォーマット
	 * @param completionHandler the completion handler
	 */
	public static void rampUp(Vertx vertx, JsonObject deal, String dateTime, Handler<AsyncResult<Void>> completionHandler) {
		if (!Deal.isRampedUp(deal)) {
			deal.put("rampUpDateTime", dateTime);
			if (log.isInfoEnabled()) log.info("deal ramped up");
			update(vertx, deal, completionHandler);
		} else {
			ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.ERROR, "DealUtil.rampUp(); already ramped up : " + Deal.rampUpDateTime(deal), completionHandler);
		}
	}
	/**
	 * Put the DEAL object specified by {@code deal} into the "warmUp" state (complete slave-side activation), and update shared memory.
	 * @param vertx a vertx object
	 * @param deal a DEAL object
	 * @param dateTime the warmUp date and time. Uses the standard APIS program format
	 * @param completionHandler the completion handler
	 *          
	 * {@code deal} で指定する DEAL オブジェクトを warmUp 済み ( slave 側起動完了 ) にし共有メモリを更新する.
	 * @param vertx vertx オブジェクト
	 * @param deal DEAL オブジェクト
	 * @param dateTime warmUp 日時. APIS プログラムの標準フォーマット
	 * @param completionHandler the completion handler
	 */
	public static void warmUp(Vertx vertx, JsonObject deal, String dateTime, Handler<AsyncResult<Void>> completionHandler) {
		if (!Deal.isWarmedUp(deal)) {
			deal.put("warmUpDateTime", dateTime);
			if (log.isInfoEnabled()) log.info("deal warmed up");
			update(vertx, deal, completionHandler);
		} else {
			ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.ERROR, "DealUtil.warmUp(); already warmed up : " + Deal.warmUpDateTime(deal), completionHandler);
		}
	}
	/**
	 * Put the DEAL object specified by {@code deal} into the "started" state (start summing) and update shared memory.
	 * @param vertx a vertx object
	 * @param deal a DEAL object
	 * @param dateTime the start date and time. Uses the standard APIS program format
	 * @param completionHandler the completion handler
	 *          
	 * {@code deal} で指定する DEAL オブジェクトを start 済み ( 積算開始 ) にし共有メモリを更新する.
	 * @param vertx vertx オブジェクト
	 * @param deal DEAL オブジェクト
	 * @param dateTime start 日時. APIS プログラムの標準フォーマット
	 * @param completionHandler the completion handler
	 */
	public static void start(Vertx vertx, JsonObject deal, String dateTime, Handler<AsyncResult<Void>> completionHandler) {
		if (Deal.isActivated(deal)) {
			if (!Deal.isStarted(deal)) {
				deal.put("startDateTime", dateTime);
				deal.put("cumulateDateTime", dateTime);
				deal.put("cumulateAmountWh", 0);
				if (log.isInfoEnabled()) log.info("deal started");
				update(vertx, deal, completionHandler);
			} else {
				ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.ERROR, "DealUtil.start(); already started : " + Deal.startDateTime(deal), completionHandler);
			}
		} else {
			ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.ERROR, "DealUtil.start(); not yet activated", completionHandler);
		}
	}
	private static final int HOUR_IN_MILLISECOND_ = 60 * 60 * 1000;
	/**
	 * Add up the interchange power based on {@code dateTime} and {@code wb} for the DEAL object specified by {@code deal} and update shared memory.
	 * @param vertx a vertx object
	 * @param deal a DEAL object
	 * @param dateTime the addition date and time Uses the standard APIS program format
	 * @param wb the battery power [Wh]
	 * @param completionHandler the completion handler
	 *          
	 * {@code deal} で指定する DEAL オブジェクトに対し {@code dateTime} と {@code wb} で融通電力を積算し共有メモリを更新する.
	 * @param vertx vertx オブジェクト
	 * @param deal DEAL オブジェクト
	 * @param dateTime 積算日時. APIS プログラムの標準フォーマット
	 * @param wb バッテリ電力 [Wh]
	 * @param completionHandler the completion handler
	 */
	public static void cumulate(Vertx vertx, JsonObject deal, String dateTime, Float wb, Handler<AsyncResult<Void>> completionHandler) {
		if (Deal.isActivated(deal)) {
			if (Deal.isStarted(deal)) {
				if (!Deal.isStopped(deal)) {
					LocalDateTime currentDateTime = DateTimeUtil.toLocalDateTime(dateTime);
					LocalDateTime lastCumulateDateTime = JsonObjectUtil.getLocalDateTime(deal, "cumulateDateTime");
					Float lastCumulateAmountWh = deal.getFloat("cumulateAmountWh");
					if (currentDateTime != null && lastCumulateDateTime != null && lastCumulateAmountWh != null && wb != null) {
						Duration duration = Duration.between(lastCumulateDateTime, currentDateTime);
						long milliseconds = duration.toMillis();
						float cumulateAmountWh = wb * milliseconds / HOUR_IN_MILLISECOND_ + lastCumulateAmountWh;
						deal.put("cumulateDateTime", dateTime);
						deal.put("cumulateAmountWh", cumulateAmountWh);
						if (log.isInfoEnabled()) log.info("deal cumulated : " + cumulateAmountWh + " / " + deal.getInteger("dealAmountWh"));
						update(vertx, deal, completionHandler);
					} else {
						ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.ERROR, "DealUtil.cumulate(); data deficiency; wb : " + wb + ", currentDateTime : " + currentDateTime + ", lastCumulateDateTime : " + lastCumulateDateTime + ", lastCumulateAmountWh : " + lastCumulateAmountWh, completionHandler);
					}
				} else {
					ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.ERROR, "DealUtil.cumulate(); already stopped : " + Deal.stopDateTime(deal), completionHandler);
				}
			} else {
				ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.ERROR, "DealUtil.cumulate(); not yet started", completionHandler);
			}
		} else {
			ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.ERROR, "DealUtil.cumulate(); not yet activated", completionHandler);
		}
	}
	/**
	 * Put the DEAL object specified by {@code deal} into the "stopped" state and update shared memory.
	 * @param vertx a vertx object
	 * @param deal a DEAL object
	 * @param dateTime the stop date and time. Uses the standard APIS program format
	 * @param completionHandler the completion handler
	 *          
	 * {@code deal} で指定する DEAL オブジェクトを stop 済みにし共有メモリを更新する.
	 * @param vertx vertx オブジェクト
	 * @param deal DEAL オブジェクト
	 * @param dateTime stop 日時. APIS プログラムの標準フォーマット
	 * @param completionHandler the completion handler
	 */
	public static void stop(Vertx vertx, JsonObject deal, String dateTime, Handler<AsyncResult<Void>> completionHandler) {
		if (Deal.isActivated(deal)) {
			if (Deal.isStarted(deal)) {
				if (!Deal.isStopped(deal)) {
					deal.put("stopDateTime", dateTime);
					if (log.isInfoEnabled()) log.info("deal stopped");
					update(vertx, deal, completionHandler);
				} else {
					ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.ERROR, "DealUtil.stop(); already stopped : " + Deal.stopDateTime(deal), completionHandler);
				}
			} else {
				ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.ERROR, "DealUtil.stop(); not yet started", completionHandler);
			}
		} else {
			ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.ERROR, "DealUtil.stop(); not yet activated", completionHandler);
		}
	}
	/**
	 * Put the DEAL object specified by {@code deal} into the "deactivated" state and update shared memory.
	 * @param vertx a vertx object
	 * @param deal a DEAL object
	 * @param dateTime the deactivation date and time. Uses the standard APIS program format
	 * @param completionHandler the completion handler
	 *          
	 * {@code deal} で指定する DEAL オブジェクトを deactivate 済みにし共有メモリを更新する.
	 * @param vertx vertx オブジェクト
	 * @param deal DEAL オブジェクト
	 * @param dateTime deactivate 日時. APIS プログラムの標準フォーマット
	 * @param completionHandler the completion handler
	 */
	public static void deactivate(Vertx vertx, JsonObject deal, String dateTime, Handler<AsyncResult<Void>> completionHandler) {
		if (Deal.isActivated(deal)) {
			if (!Deal.isStarted(deal) || Deal.isStopped(deal)) {
				if (!Deal.isDeactivated(deal)) {
					deal.put("deactivateDateTime", dateTime);
					if (!Deal.isStarted(deal)) {
						deal.put("startDateTime", Deal.NULL_DATE_TIME_VALUE);
						deal.put("stopDateTime", Deal.NULL_DATE_TIME_VALUE);
					}
					deal.remove("isMaster");
					if (log.isInfoEnabled()) log.info("deal deactivated");
					update(vertx, deal, completionHandler);
				} else {
					ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.ERROR, "DealUtil.deactivate(); already deactivated : " + Deal.deactivateDateTime(deal), completionHandler);
				}
			} else {
				ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.ERROR, "DealUtil.deactivate(); started but not yet stopped : " + Deal.startDateTime(deal), completionHandler);
			}
		} else {
			ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.ERROR, "DealUtil.deactivate(); not yet activated", completionHandler);
		}
	}
	/**
	 * Put the DEAL object specified by {@code deal} into the "reset" state and update shared memory.
	 * @param vertx a vertx object
	 * @param deal a DEAL object
	 * @param dateTime the reset date and time. Uses the standard APIS program format
	 * @param reason the reason for resetting
	 * @param completionHandler the completion handler
	 *          
	 * {@code deal} で指定する DEAL オブジェクトを reset 済みにし共有メモリを更新する.
	 * @param vertx vertx オブジェクト
	 * @param deal DEAL オブジェクト
	 * @param dateTime reset 日時. APIS プログラムの標準フォーマット
	 * @param reason リセット理由
	 * @param completionHandler the completion handler
	 */
	public static void reset(Vertx vertx, JsonObject deal, String dateTime, String reason, Handler<AsyncResult<Void>> completionHandler) {
		deal.remove("activateDateTime");
		deal.remove("rampUpDateTime");
		deal.remove("warmUpDateTime");
		deal.remove("startDateTime");
		deal.remove("stopDateTime");
		deal.remove("deactivateDateTime");
		deal.remove("isMaster");
		JsonObject reset = new JsonObject();
		reset.put("dateTime", dateTime);
		reset.put("reason", reason);
		JsonObjectUtil.add(deal, reset, "reset");
		if (log.isInfoEnabled()) log.info("deal reset; reason : " + reason);
		update(vertx, deal, completionHandler);
	}
	/**
	 * Put the DEAL object specified by {@code deal} into the "aborted" state and update shared memory.
	 * @param vertx a vertx object
	 * @param deal a DEAL object
	 * @param dateTime the abortion date and time. Uses the standard APIS program format
	 * @param reason the reason for abnormal termination
	 * @param completionHandler the completion handler
	 *          
	 * {@code deal} で指定する DEAL オブジェクトを異常終了済みにし共有メモリを更新する.
	 * @param vertx vertx オブジェクト
	 * @param deal DEAL オブジェクト
	 * @param dateTime abort 日時. APIS プログラムの標準フォーマット
	 * @param reason 異常終了理由
	 * @param completionHandler the completion handler
	 */
	public static void abort(Vertx vertx, JsonObject deal, String dateTime, String reason, Handler<AsyncResult<Void>> completionHandler) {
		if (!Deal.isDeactivated(deal)) {
			if (!Deal.isAborted(deal)) {
				deal.put("abortDateTime", dateTime);
				deal.put("abortReason", reason);
			}
			JsonObject abort = new JsonObject();
			abort.put("dateTime", dateTime);
			abort.put("reason", reason);
			JsonObjectUtil.add(deal, abort, "abort");
			if (!Deal.isActivated(deal)) {
				deal.put("activateDateTime", Deal.NULL_DATE_TIME_VALUE);
				deal.put("deactivateDateTime", Deal.NULL_DATE_TIME_VALUE);
			}
			if (!Deal.isStarted(deal)) {
				deal.put("startDateTime", Deal.NULL_DATE_TIME_VALUE);
				deal.put("stopDateTime", Deal.NULL_DATE_TIME_VALUE);
			}
			if (log.isInfoEnabled()) log.info("deal aborted; reason : " + reason);
			update(vertx, deal, completionHandler);
		} else {
			completionHandler.handle(Future.succeededFuture());
		}
	}
	/**
	 * Put the DEAL object specified by {@code deal} into the "SCRAM" state and update shared memory.
	 * @param vertx a vertx object
	 * @param deal a DEAL object
	 * @param dateTime the SCRAM date and time. Uses the standard APIS program format
	 * @param reason the reason for the SCRAM
	 * @param completionHandler the completion handler
	 *          
	 * {@code deal} で指定する DEAL オブジェクトを SCRAM 済みにし共有メモリを更新する.
	 * @param vertx vertx オブジェクト
	 * @param deal DEAL オブジェクト
	 * @param dateTime SCRAM 日時. APIS プログラムの標準フォーマット
	 * @param reason SCRAM 理由
	 * @param completionHandler the completion handler
	 */
	public static void scram(Vertx vertx, JsonObject deal, String dateTime, String reason, Handler<AsyncResult<Void>> completionHandler) {
		if (!Deal.isDeactivated(deal)) {
			deal.put("scramDateTime", dateTime);
			deal.put("scramReason", reason);
			if (!Deal.isActivated(deal)) {
				deal.put("activateDateTime", Deal.NULL_DATE_TIME_VALUE);
				deal.put("deactivateDateTime", Deal.NULL_DATE_TIME_VALUE);
			} else if (!Deal.isDeactivated(deal)) {
				deal.put("deactivateDateTime", dateTime);
			}
			if (!Deal.isStarted(deal)) {
				deal.put("startDateTime", Deal.NULL_DATE_TIME_VALUE);
				deal.put("stopDateTime", Deal.NULL_DATE_TIME_VALUE);
			} else if (!Deal.isStopped(deal)) {
				deal.put("stopDateTime", dateTime);
			}
			if (log.isInfoEnabled()) log.info("deal scrammed; reason : " + reason);
			update(vertx, deal, completionHandler);
		} else {
			completionHandler.handle(Future.succeededFuture());
		}
	}
	/**
	 * Set the Master Deal flag of the DEAL object specified by {@code deal} and update shared memory.
	 * @param vertx a vertx object
	 * @param deal a DEAL object
	 * @param flag true if this object is to become the Master Deal, false otherwise
	 * @param completionHandler the completion handler
	 *          
	 * {@code deal} で指定する DEAL オブジェクトに対し Master Deal フラグを設定し共有メモリを更新する.
	 * @param vertx vertx オブジェクト
	 * @param deal DEAL オブジェクト
	 * @param flag Master Deal か否か
	 * @param completionHandler the completion handler
	 */
	public static void isMaster(Vertx vertx, JsonObject deal, boolean flag, Handler<AsyncResult<Void>> completionHandler) {
		if (Deal.isMaster(deal) != flag) {
			if (flag) {
				deal.put("isMaster", flag);
			} else {
				deal.remove("isMaster");
			}
			if (log.isInfoEnabled()) log.info("deal isMaster : " + flag);
			update(vertx, deal, completionHandler);
		} else {
			completionHandler.handle(Future.succeededFuture());
		}
	}

}
