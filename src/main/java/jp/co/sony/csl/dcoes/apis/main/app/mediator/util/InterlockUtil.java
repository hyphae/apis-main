package jp.co.sony.csl.dcoes.apis.main.app.mediator.util;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.shareddata.AsyncMap;
import io.vertx.core.shareddata.LocalMap;

import java.util.Collection;

import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.EncryptedClusterWideMapUtil;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.LocalExclusiveLock;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorExceptionUtil;

/**
 * A tool for managing interlocks.
 * GridMaster interlocks are managed in shared memory.
 * Interchange interlocks are managed in local memory.
 * @author OES Project
 *          
 * インタロックを管理するツール.
 * GridMaster インタロックは共有メモリ上に管理する.
 * 融通インタロックはローカルメモリ上に管理する.
 * @author OES Project
 */
public class InterlockUtil {
	private static final Logger log = LoggerFactory.getLogger(InterlockUtil.class);

	private static final String MAP_NAME = InterlockUtil.class.getName();
	private static final String MAP_KEY_GRID_MASTER_UNIT_ID = "gridMasterUnitId";
	private static final String MAP_KEY_DEAL_ID = "dealId";

	private static final LocalExclusiveLock exclusiveLock_ = new LocalExclusiveLock(InterlockUtil.class.getName());
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

	private InterlockUtil() { }

	/**
	 * Acquire a GridMaster interlock.
	 * @param vertx a vertx object
	 * @param value the value to acquire
	 * @param ignoreInconsistency what to do if an inconsistency arises.
	 *        - true: Fail with a warning if an interlock with the same value has already been acquired, or if an interlock with a different value has already been acquired
	 *        - false: LOCAL:WARN if an interlock with the same value has already been acquired. LOCAL:ERROR if an interlock with a different value has already been acquired.
	 * @param completionHandler the completion handler
	 *          
	 * GridMaster インタロックを獲得する.
	 * @param vertx vertx オブジェクト
	 * @param value 獲得する値
	 * @param ignoreInconsistency 不整合時の挙動.
	 *        - true : 同じ値で獲得済みの場合, 別の値で獲得済みの場合, ともに警告を出して失敗
	 *        - false : 同じ値で獲得済みの場合は LOCAL:WARN. 別の値で獲得済みの場合は LOCAL:ERROR にする.
	 * @param completionHandler the completion handler
	 */
	public static void lockGridMasterUnitId(Vertx vertx, String value, boolean ignoreInconsistency, Handler<AsyncResult<Void>> completionHandler) {
		lockClusterWide_(vertx, MAP_KEY_GRID_MASTER_UNIT_ID, value, ignoreInconsistency, completionHandler);
	}
	/**
	 * Release a GridMaster interlock.
	 * LOCAL:WARN if the interlock has not been acquired.
	 * LOCAL:ERROR if interlock has been acquired with a different value.
	 * @param vertx a vertx object
	 * @param value The value to release
	 * @param completionHandler the completion handler
	 *          
	 * GridMaster インタロックを開放する.
	 * インタロックが獲得中でない場合は LOCAL:WARN にする.
	 * 別の値で獲得中の場合は LOCAL:ERROR にする.
	 * @param vertx vertx オブジェクト
	 * @param value 開放する値
	 * @param completionHandler the completion handler
	 */
	public static void unlockGridMasterUnitId(Vertx vertx, String value, Handler<AsyncResult<Void>> completionHandler) {
		unlockClusterWide_(vertx, MAP_KEY_GRID_MASTER_UNIT_ID, value, completionHandler);
	}
	/**
	 * Get a GridMaster interlock value.
	 * Results are received with the {@link AsyncResult#result()} method of completionHandler.
	 * @param vertx a vertx object
	 * @param completionHandler the completion handler
	 *          
	 * GridMaster インタロック値を取得する.
	 * completionHandler の {@link AsyncResult#result()} で受け取る.
	 * @param vertx vertx オブジェクト
	 * @param completionHandler the completion handler
	 */
	public static void getGridMasterUnitId(Vertx vertx, Handler<AsyncResult<String>> completionHandler) {
		getClusterWide_(vertx, MAP_KEY_GRID_MASTER_UNIT_ID, completionHandler);
	}
	/**
	 * Reset a GridMaster interlock.
	 * @param vertx a vertx object
	 * @param completionHandler the completion handler
	 *          
	 * GridMaster インタロックをリセットする.
	 * @param vertx vertx オブジェクト
	 * @param completionHandler the completion handler
	 */
	public static void resetGridMasterUnitId(Vertx vertx, Handler<AsyncResult<Void>> completionHandler) {
		resetClusterWide_(vertx, MAP_KEY_GRID_MASTER_UNIT_ID, completionHandler);
	}

	/**
	 * Acquire an interchange interlock.
	 * @param vertx a vertx object
	 * @param value the value to acquire
	 * @param capacity the number of locks that can be acquired at the same time
	 * @param ignoreInconsistency what to do if an inconsistency arises.
	 *        - true: Fail with a warning if full capacity has been reached, or if an interlock with the same value has already been acquired
	 *        - false: LOCAL:ERROR if full capacity has been reached, or LOCAL:WARN if an interlock with the same value has already been acquired
	 *        The possible inconsistencies are as follows.
	 *        - Capacity exceeded
	 *        - Interlock with the same value has already been acquired
	 * @param completionHandler the completion handler
	 *          
	 * 融通インタロックを獲得する.
	 * @param vertx vertx オブジェクト
	 * @param value 獲得する値
	 * @param capacity 同時に獲得できるロックの数
	 * @param ignoreInconsistency 不整合時の挙動.
	 *        - true : capacity いっぱいの場合, 同じ値で獲得済みの場合, ともに警告を出して失敗
	 *        - false : capacity いっぱいの場合 LOCAL:ERROR, 同じ値で獲得済みの場合は LOCAL:WARN にする
	 *        不整合は以下の通り.
	 *        - capacity を超える
	 *        - 指定した値で獲得済みである
	 * @param completionHandler the completion handler
	 */
	public static void lockDealId(Vertx vertx, String value, int capacity, boolean ignoreInconsistency, Handler<AsyncResult<Void>> completionHandler) {
		lockLocalMultiple_(vertx, MAP_KEY_DEAL_ID, value, capacity, ignoreInconsistency, completionHandler);
	}
	/**
	 * Release an interchange interlock.
	 * If the interlock has not been acquired, LOCAL:WARN if there are no locks at all, or LOCAL:ERROR if another interlock has been acquired.
	 * @param vertx a vertx object
	 * @param value The value to release
	 * @param completionHandler the completion handler
	 *          
	 * 融通インタロックを開放する.
	 * インタロックが獲得中でない場合, 一つもロックがなければ LOCAL:WARN, 他に獲得中なら LOCAL:ERROR にする.
	 * @param vertx vertx オブジェクト
	 * @param value 開放する値
	 * @param completionHandler the completion handler
	 */
	public static void unlockDealId(Vertx vertx, String value, Handler<AsyncResult<Void>> completionHandler) {
		unlockLocalMultiple_(vertx, MAP_KEY_DEAL_ID, value, completionHandler);
	}
	/**
	 * Get an interchange interlock value.
	 * Results are received with the {@link AsyncResult#result()} method of completionHandler.
	 * @param vertx a vertx object
	 * @param completionHandler the completion handler
	 *          
	 * 融通インタロック値を取得する.
	 * completionHandler の {@link AsyncResult#result()} で受け取る.
	 * @param vertx vertx オブジェクト
	 * @param completionHandler the completion handler
	 */
	public static void getDealIds(Vertx vertx, Handler<AsyncResult<Collection<String>>> completionHandler) {
		getLocalMultiple_(vertx, MAP_KEY_DEAL_ID, completionHandler);
	}
	/**
	 * Reset an interchange interlock.
	 * @param vertx a vertx object
	 * @param completionHandler the completion handler
	 *          
	 * 融通インタロックをリセットする.
	 * @param vertx vertx オブジェクト
	 * @param completionHandler the completion handler
	 */
	public static void resetDealId(Vertx vertx, Handler<AsyncResult<Void>> completionHandler) {
		resetLocalMultiple_(vertx, MAP_KEY_DEAL_ID, completionHandler);
	}

	////

	/**
	 * Acquire an interlock for an entire cluster.
	 * @param vertx a vertx object
	 * @param key the name of the lock
	 * @param value the value to acquire
	 * @param ignoreInconsistency what to do if an inconsistency arises.
	 *        - true: Fail with a warning if an interlock with the same value has already been acquired, or if an interlock with a different value has already been acquired
	 *        - false: LOCAL:WARN if an interlock with the same value has already been acquired. LOCAL:ERROR if an interlock with a different value has already been acquired.
	 * @param completionHandler the completion handler
	 *          
	 * クラスタ全体でのインタロックを獲得する.
	 * @param vertx vertx オブジェクト
	 * @param key ロックの名前
	 * @param value 獲得する値
	 * @param ignoreInconsistency 不整合時の挙動.
	 *        - true : 同じ値で獲得済みの場合, 別の値で獲得済みの場合, ともに警告を出して失敗
	 *        - false : 同じ値で獲得済みの場合は LOCAL:WARN. 別の値で獲得済みの場合は LOCAL:ERROR にする.
	 * @param completionHandler the completion handler
	 */
	private static void lockClusterWide_(Vertx vertx, String key, String value, boolean ignoreInconsistency, Handler<AsyncResult<Void>> completionHandler) {
		if (key != null && value != null && !key.isEmpty() && !value.isEmpty()) {
			EncryptedClusterWideMapUtil.<String, String>getEncryptedClusterWideMap(vertx, MAP_NAME, resMap -> {
				if (resMap.succeeded()) {
					AsyncMap<String, String> lockMap = resMap.result();
					lockMap.putIfAbsent(key, value, resPutIfAbsent -> {
						if (resPutIfAbsent.succeeded()) {
							String existingValue = resPutIfAbsent.result();
							if (existingValue == null) {
								completionHandler.handle(Future.succeededFuture());
							} else {
								String msg = (existingValue.equals(value)) ? "already locked with same " + key + " : " + existingValue : "already locked with different " + key + " : " + existingValue;
								if (ignoreInconsistency) {
									if (log.isDebugEnabled()) log.debug(msg);
									completionHandler.handle(Future.failedFuture(msg));
								} else if (existingValue.equals(value)) {
									ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, msg, completionHandler);
								} else {
									ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, msg, completionHandler);
								}
							}
						} else {
							ErrorExceptionUtil.logAndFail(Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR, "Communication failed on SharedData", resPutIfAbsent.cause(), completionHandler);
						}
					});
				} else {
					ErrorExceptionUtil.logAndFail(Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR, "Communication failed on SharedData", resMap.cause(), completionHandler);
				}
			});
		} else {
			ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, "illegal parameters; key : " + key + ", value : " + value, completionHandler);
		}
	}
	/**
	 * Release the interlock for an entire cluster.
	 * LOCAL:WARN if interlock has not been acquired.
	 * LOCAL:ERROR if interlock has been acquired with a different value.
	 * @param vertx a vertx object
	 * @param key the name of the lock
	 * @param value The value to release
	 * @param completionHandler the completion handler
	 *          
	 * クラスタ全体でのインタロックを開放する.
	 * 獲得中でない場合は LOCAL:WARN にする.
	 * 別の値で獲得中の場合は LOCAL:ERROR にする.
	 * @param vertx vertx オブジェクト
	 * @param key ロックの名前
	 * @param value 開放する値
	 * @param completionHandler the completion handler
	 */
	private static void unlockClusterWide_(Vertx vertx, String key, String value, Handler<AsyncResult<Void>> completionHandler) {
		if (key != null && value != null && !key.isEmpty() && !value.isEmpty()) {
			EncryptedClusterWideMapUtil.<String, String>getEncryptedClusterWideMap(vertx, MAP_NAME, resMap -> {
				if (resMap.succeeded()) {
					AsyncMap<String, String> lockMap = resMap.result();
					lockMap.removeIfPresent(key, value, resRemoveIfPresent -> {
						if (resRemoveIfPresent.succeeded()) {
							Boolean removed = resRemoveIfPresent.result();
							if (removed) {
								completionHandler.handle(Future.succeededFuture());
							} else {
								lockMap.get(key, resGet -> {
									if (resGet.succeeded()) {
										String currentValue = resGet.result();
										String msg = "locked with different " + key + " : " + currentValue;
										if (null == currentValue) {
											ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, msg, completionHandler);
										} else {
											ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, msg, completionHandler);
										}
									} else {
										ErrorExceptionUtil.logAndFail(Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR, "Communication failed on SharedData", resGet.cause(), completionHandler);
									}
								});
							}
						} else {
							ErrorExceptionUtil.logAndFail(Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR, "Communication failed on SharedData", resRemoveIfPresent.cause(), completionHandler);
						}
					});
				} else {
					ErrorExceptionUtil.logAndFail(Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR, "Communication failed on SharedData", resMap.cause(), completionHandler);
				}
			});
		} else {
			ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, "illegal parameters; key : " + key + ", value : " + value, completionHandler);
		}
	}
	/**
	 * Get an interlock for an entire cluster.
	 * Results are received with the {@link AsyncResult#result()} method of completionHandler.
	 * @param vertx a vertx object
	 * @param key the name of the lock
	 * @param completionHandler the completion handler
	 *          
	 * クラスタ全体でのインタロックを取得する.
	 * completionHandler の {@link AsyncResult#result()} で受け取る.
	 * @param vertx vertx オブジェクト
	 * @param key ロックの名前
	 * @param completionHandler the completion handler
	 */
	private static void getClusterWide_(Vertx vertx, String key, Handler<AsyncResult<String>> completionHandler) {
		if (key != null && !key.isEmpty()) {
			EncryptedClusterWideMapUtil.<String, String>getEncryptedClusterWideMap(vertx, MAP_NAME, resMap -> {
				if (resMap.succeeded()) {
					AsyncMap<String, String> lockMap = resMap.result();
					lockMap.get(key, resGet -> {
						if (resGet.succeeded()) {
							completionHandler.handle(Future.succeededFuture(resGet.result()));
						} else {
							ErrorExceptionUtil.logAndFail(Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR, "Communication failed on SharedData", resGet.cause(), completionHandler);
						}
					});
				} else {
					ErrorExceptionUtil.logAndFail(Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR, "Communication failed on SharedData", resMap.cause(), completionHandler);
				}
			});
		} else {
			ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, "illegal parameters; key : " + key, completionHandler);
		}
	}
	/**
	 * Reset the interlock for an entire cluster.
	 * @param vertx a vertx object
	 * @param key the name of the lock
	 * @param completionHandler the completion handler
	 *          
	 * クラスタ全体でのインタロックをリセットする.
	 * @param vertx vertx オブジェクト
	 * @param key ロックの名前
	 * @param completionHandler the completion handler
	 */
	private static void resetClusterWide_(Vertx vertx, String key, Handler<AsyncResult<Void>> completionHandler) {
		if (key != null && !key.isEmpty()) {
			EncryptedClusterWideMapUtil.<String, String>getEncryptedClusterWideMap(vertx, MAP_NAME, resMap -> {
				if (resMap.succeeded()) {
					AsyncMap<String, String> lockMap = resMap.result();
					lockMap.remove(key, resRemove -> {
						if (resRemove.succeeded()) {
							completionHandler.handle(Future.succeededFuture());
						} else {
							ErrorExceptionUtil.logAndFail(Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR, "Communication failed on SharedData", resRemove.cause(), completionHandler);
						}
					});
				} else {
					ErrorExceptionUtil.logAndFail(Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR, "Communication failed on SharedData", resMap.cause(), completionHandler);
				}
			});
		} else {
			ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, "illegal parameters; key : " + key, completionHandler);
		}
	}

	////

	/**
	 * Acquire an interlock within a unit.
	 * @param vertx a vertx object
	 * @param key the name of the lock
	 * @param value the value to acquire
	 * @param ignoreInconsistency what to do if an inconsistency arises.
	 *        - true: Fail with a warning if an interlock with the same value has already been acquired, or if an interlock with a different value has already been acquired
	 *        - false: LOCAL:WARN if an interlock with the same value has already been acquired. LOCAL:ERROR if an interlock with a different value has already been acquired.
	 * @param completionHandler the completion handler
	 *          
	 * ユニット内でのインタロックを獲得する.
	 * @param vertx vertx オブジェクト
	 * @param key ロックの名前
	 * @param value 獲得する値
	 * @param ignoreInconsistency 不整合時の挙動.
	 *        - true : 同じ値で獲得済みの場合, 別の値で獲得済みの場合, ともに警告を出して失敗
	 *        - false : 同じ値で獲得済みの場合は LOCAL:WARN. 別の値で獲得済みの場合は LOCAL:ERROR にする.
	 * @param completionHandler the completion handler
	 */
	@SuppressWarnings("unused") private static void lockLocal_(Vertx vertx, String key, String value, boolean ignoreInconsistency, Handler<AsyncResult<Void>> completionHandler) {
		if (key != null && value != null && !key.isEmpty() && !value.isEmpty()) {
			LocalMap<String, String> lockMap = vertx.sharedData().getLocalMap(MAP_NAME);
			String existingValue = lockMap.putIfAbsent(key, value);
			if (existingValue == null) {
				completionHandler.handle(Future.succeededFuture());
			} else {
				String msg = (existingValue.equals(value)) ? "already locked with same " + key + " : " + existingValue : "already locked with different " + key + " : " + existingValue;
				if (ignoreInconsistency) {
					if (log.isDebugEnabled()) log.debug(msg);
					completionHandler.handle(Future.failedFuture(msg));
				} else if (existingValue.equals(value)) {
					ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, msg, completionHandler);
				} else {
					ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, msg, completionHandler);
				}
			}
		} else {
			ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, "illegal parameters; key : " + key + ", value : " + value, completionHandler);
		}
	}
	/**
	 * Release an interlock within a unit.
	 * LOCAL:WARN if interlock has not been acquired.
	 * LOCAL:ERROR if interlock has been acquired with a different value.
	 * @param vertx a vertx object
	 * @param key the name of the lock
	 * @param value The value to release
	 * @param completionHandler the completion handler
	 *          
	 * ユニット内でのインタロックを開放する.
	 * 獲得中でない場合は LOCAL:WARN にする.
	 * 別の値で獲得中の場合は LOCAL:ERROR にする.
	 * @param vertx vertx オブジェクト
	 * @param key ロックの名前
	 * @param value 開放する値
	 * @param completionHandler the completion handler
	 */
	@SuppressWarnings("unused") private static void unlockLocal_(Vertx vertx, String key, String value, Handler<AsyncResult<Void>> completionHandler) {
		if (key != null && value != null && !key.isEmpty() && !value.isEmpty()) {
			LocalMap<String, String> lockMap = vertx.sharedData().getLocalMap(MAP_NAME);
			boolean removed = lockMap.removeIfPresent(key, value);
			if (removed) {
				completionHandler.handle(Future.succeededFuture());
			} else {
				String currentValue = lockMap.get(key);
				String msg = "locked with different " + key + " : " + currentValue;
				if (null == currentValue) {
					ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, msg, completionHandler);
				} else {
					ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, msg, completionHandler);
				}
			}
		} else {
			ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, "illegal parameters; key : " + key + ", value : " + value, completionHandler);
		}
	}
	/**
	 * Get an interlock within a unit.
	 * Results are received with the {@link AsyncResult#result()} method of completionHandler.
	 * @param vertx a vertx object
	 * @param key the name of the lock
	 * @param completionHandler the completion handler
	 *          
	 * ユニット内でのインタロックを取得する.
	 * completionHandler の {@link AsyncResult#result()} で受け取る.
	 * @param vertx vertx オブジェクト
	 * @param key ロックの名前
	 * @param completionHandler the completion handler
	 */
	@SuppressWarnings("unused") private static void getLocal_(Vertx vertx, String key, Handler<AsyncResult<String>> completionHandler) {
		if (key != null && !key.isEmpty()) {
			LocalMap<String, String> lockMap = vertx.sharedData().getLocalMap(MAP_NAME);
			completionHandler.handle(Future.succeededFuture(lockMap.get(key)));
		} else {
			ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, "illegal parameters; key : " + key, completionHandler);
		}
	}
	/**
	 * Reset an interlock within a unit.
	 * @param vertx a vertx object
	 * @param key the name of the lock
	 * @param completionHandler the completion handler
	 *          
	 * ユニット内でのインタロックをリセットする.
	 * @param vertx vertx オブジェクト
	 * @param key ロックの名前
	 * @param completionHandler the completion handler
	 */
	@SuppressWarnings("unused") private static void resetLocal_(Vertx vertx, String key, Handler<AsyncResult<Void>> completionHandler) {
		if (key != null && !key.isEmpty()) {
			LocalMap<String, String> lockMap = vertx.sharedData().getLocalMap(MAP_NAME);
			lockMap.remove(key);
			completionHandler.handle(Future.succeededFuture());
		} else {
			ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, "illegal parameters; key : " + key, completionHandler);
		}
	}

	////

	/**
	 * Acquire an interlock within a unit.
	 * It is possible to retain multiple interlocks.
	 * @param vertx a vertx object
	 * @param key the name of the lock
	 * @param value the value to acquire
	 * @param capacity the number of locks that can be acquired at the same time
	 * @param ignoreInconsistency what to do if an inconsistency arises.
	 *        - true: Fail with a warning if full capacity has been reached, or if an interlock with the same value has already been acquired
	 *        - false: LOCAL:ERROR is full capacity has been reached, or LOCAL:WARN if an interlock with the same value has already been acquired
	 *        The possible inconsistencies are as follows.
	 *        - Capacity exceeded
	 *        - Interlock with the same value has already been acquired
	 * @param completionHandler the completion handler
	 *          
	 * ユニット内でのインタロックを獲得する.
	 * 複数のインタロックを保持できる.
	 * @param vertx vertx オブジェクト
	 * @param key ロックの名前
	 * @param value 獲得する値
	 * @param capacity 同時に獲得できるロックの数
	 * @param ignoreInconsistency 不整合時の挙動.
	 *        - true : capacity いっぱいの場合, 同じ値で獲得済みの場合, ともに警告を出して失敗
	 *        - false : capacity いっぱいの場合 LOCAL:ERROR, 同じ値で獲得済みの場合は LOCAL:WARN にする
	 *        不整合は以下の通り.
	 *        - capacity を超える
	 *        - 指定した値で獲得済みである
	 * @param completionHandler the completion handler
	 */
	private static void lockLocalMultiple_(Vertx vertx, String key, String value, int capacity, boolean ignoreInconsistency, Handler<AsyncResult<Void>> completionHandler) {
		if (key != null && value != null && !key.isEmpty() && !value.isEmpty()) {
			// Since multiple locks are retained, acquire and process a local exclusive lock
			// 複数のロックを保持するのでローカル排他ロックを獲得して処理する
			acquireExclusiveLock(vertx, resExclusiveLock -> {
				if (resExclusiveLock.succeeded()) {
					LocalExclusiveLock.Lock lock = resExclusiveLock.result();
					doLockLocalMultipleWithExclusiveLock_(vertx, key, value, capacity, ignoreInconsistency, resDoLockLocalMultipleWithExclusiveLock -> {
						lock.release();
						completionHandler.handle(resDoLockLocalMultipleWithExclusiveLock);
					});
				} else {
					ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, resExclusiveLock.cause(), completionHandler);
				}
			});
		} else {
			ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, "illegal parameters; key : " + key + ", value : " + value, completionHandler);
		}
	}
	private static void doLockLocalMultipleWithExclusiveLock_(Vertx vertx, String key, String value, int capacity, boolean ignoreInconsistency, Handler<AsyncResult<Void>> completionHandler) {
		LocalMap<String, String> lockMap = vertx.sharedData().getLocalMap(MAP_NAME + "_m_" + key);
		if (!lockMap.values().contains(value)) {
			if (lockMap.size() < capacity) {
				lockMap.put(value, value);
				completionHandler.handle(Future.succeededFuture());
			} else {
				String msg = key + " locks are occupied; capacity : " + capacity;
				if (ignoreInconsistency) {
					if (log.isDebugEnabled()) log.debug(msg);
					completionHandler.handle(Future.failedFuture(msg));
				} else {
					ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, msg, completionHandler);
				}
			}
		} else {
			String msg = "already locked with same " + key + " : " + value;
			if (ignoreInconsistency) {
				if (log.isDebugEnabled()) log.debug(msg);
				completionHandler.handle(Future.failedFuture(msg));
			} else {
				ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, msg, completionHandler);
			}
		}
	}
	/**
	 * Release an interlock within a unit.
	 * If the interlock has not been acquired, LOCAL:WARN if there are no locks at all, or LOCAL:ERROR if another interlock has been acquired.
	 * @param vertx a vertx object
	 * @param key the name of the lock
	 * @param value The value to release
	 * @param completionHandler the completion handler
	 *          
	 * ユニット内でのインタロックを開放する.
	 * インタロックが獲得中でない場合, 一つもロックがなければ LOCAL:WARN, 他に獲得中なら LOCAL:ERROR にする.
	 * @param vertx vertx オブジェクト
	 * @param key ロックの名前
	 * @param value 開放する値
	 * @param completionHandler the completion handler
	 */
	private static void unlockLocalMultiple_(Vertx vertx, String key, String value, Handler<AsyncResult<Void>> completionHandler) {
		if (key != null && value != null && !key.isEmpty() && !value.isEmpty()) {
			// Since multiple locks are retained, acquire and process a local exclusive lock
			// 複数のロックを保持するのでローカル排他ロックを獲得して処理する
			acquireExclusiveLock(vertx, resExclusiveLock -> {
				if (resExclusiveLock.succeeded()) {
					LocalExclusiveLock.Lock lock = resExclusiveLock.result();
					doUnlockLocalMultipleWithExclusiveLock_(vertx, key, value, resDoLockLocalMultipleWithExclusiveLock -> {
						lock.release();
						completionHandler.handle(resDoLockLocalMultipleWithExclusiveLock);
					});
				} else {
					ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, resExclusiveLock.cause(), completionHandler);
				}
			});
		} else {
			ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, "illegal parameters; key : " + key + ", value : " + value, completionHandler);
		}
	}
	private static void doUnlockLocalMultipleWithExclusiveLock_(Vertx vertx, String key, String value, Handler<AsyncResult<Void>> completionHandler) {
		LocalMap<String, String> lockMap = vertx.sharedData().getLocalMap(MAP_NAME + "_m_" + key);
		if (lockMap.values().contains(value)) {
			lockMap.remove(value);
			completionHandler.handle(Future.succeededFuture());
		} else {
			String msg = "not locked with same " + key + " : " + value;
			if (lockMap.size() == 0) {
				ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, msg, completionHandler);
			} else {
				ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, msg, completionHandler);
			}
		}
	}
	/**
	 * Get the interlock value within a unit.
	 * Results are received with the {@link AsyncResult#result()} method of completionHandler.
	 * @param vertx a vertx object
	 * @param key the name of the lock
	 * @param completionHandler the completion handler
	 *          
	 * ユニット内でのインタロック値を取得する.
	 * completionHandler の {@link AsyncResult#result()} で受け取る.
	 * @param vertx vertx オブジェクト
	 * @param key ロックの名前
	 * @param completionHandler the completion handler
	 */
	private static void getLocalMultiple_(Vertx vertx, String key, Handler<AsyncResult<Collection<String>>> completionHandler) {
		if (key != null && !key.isEmpty()) {
			LocalMap<String, String> lockMap = vertx.sharedData().getLocalMap(MAP_NAME + "_m_" + key);
			completionHandler.handle(Future.succeededFuture(lockMap.values()));
		} else {
			ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, "illegal parameters; key : " + key, completionHandler);
		}
	}
	/**
	 * Reset an interlock within a unit.
	 * @param vertx a vertx object
	 * @param key the name of the lock
	 * @param completionHandler the completion handler
	 *          
	 * ユニット内でのインタロックをリセットする.
	 * @param vertx vertx オブジェクト
	 * @param key ロックの名前
	 * @param completionHandler the completion handler
	 */
	private static void resetLocalMultiple_(Vertx vertx, String key, Handler<AsyncResult<Void>> completionHandler) {
		if (key != null && !key.isEmpty()) {
			LocalMap<String, String> lockMap = vertx.sharedData().getLocalMap(MAP_NAME + "_m_" + key);
			lockMap.clear();
			completionHandler.handle(Future.succeededFuture());
		} else {
			ErrorExceptionUtil.logAndFail(Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, "illegal parameters; key : " + key, completionHandler);
		}
	}

}
