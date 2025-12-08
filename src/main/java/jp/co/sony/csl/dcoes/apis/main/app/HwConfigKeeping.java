package jp.co.sony.csl.dcoes.apis.main.app;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectUtil;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectWrapper;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.VertxConfig;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;
import jp.co.sony.csl.dcoes.apis.main.util.FileHandler;

/**
 * A Verticle that manages HWCONFIG.
 * Launched from the {@link jp.co.sony.csl.dcoes.apis.main.app.Apis} Verticle.
 * @author OES Project
 *
 * HWCONFIG を管理する Verticle.
 * {@link jp.co.sony.csl.dcoes.apis.main.app.Apis} Verticle から起動される.
 * @author OES Project
 */
public class HwConfigKeeping extends AbstractVerticle {
	private static final Logger LOGGER = LoggerFactory.getLogger(HwConfigKeeping.class);

	/**
	 * Default duration of the period with which HWCONFIG is read from the file system [ms].
	 * Value: {@value}.
	 *
	 * ファイルシステムから HWCONFIG を読み込む周期のデフォルト値 [ms].
	 * 値は {@value}.
	 */
	private static final Long LOCAL_FILE_DEFAULT_REFRESHING_PERIOD_MSEC = 5000L;

	/**
	 * A cache that retains HWCONFIG.
	 *
	 * HWCONFIG を保持しておくキャッシュ.
	 */
	public static final JsonObjectWrapper CACHE = new JsonObjectWrapper();

	private String localFilePath;
	private long localFileReadingTimerId;
	private boolean stopped;

	/**
	 * Called at startup.
	 * Fetch settings from CONFIG and perform initialization.
	 * - CONFIG.hwConfigFile: HWCONFIG file path
	 * Starts a timer.
	 * @param startFuture {@inheritDoc}
	 * @throws Exception {@inheritDoc}
	 *
	 * 起動時に呼び出される.
	 * CONFIG から設定を取得し初期化する.
	 * - CONFIG.hwConfigFile : HWCONFIG ファイルのパス
	 * タイマを起動する.
	 * @param startFuture {@inheritDoc}
	 * @throws Exception {@inheritDoc}
	 */
	@Override
	public void start(Promise<Void> startPromise) throws Exception {
		localFilePath = VertxConfig.config.getString("hwConfigFile");
		LOGGER.info("hwConfigFile : {}", localFilePath);
		LOGGER.info("hwConfigFile.defaultRefreshingPeriodMsec : {}", LOCAL_FILE_DEFAULT_REFRESHING_PERIOD_MSEC);

		localFileReadingTimerHandler(0L);
		LOGGER.trace("started : {}", deploymentID());
		startPromise.complete();
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
	@Override
	public void stop(Promise<Void> stopPromise) throws Exception {
		stopped = true;
		LOGGER.trace("stopped : {}", deploymentID());
		stopPromise.complete();
	}

	/**
	 * Set a timer to read HWCONFIG from the file system.
	 * The timeout duration is {@code HWCONFIG.refreshingPeriodMsec} (default: {@link #LOCAL_FILE_DEFAULT_REFRESHING_PERIOD_MSEC}).
	 *
	 * ファイルシステムから HWCONFIG を読み込むタイマ設定.
	 * 待ち時間は {@code HWCONFIG.refreshingPeriodMsec} ( デフォルト値 {@link #LOCAL_FILE_DEFAULT_REFRESHING_PERIOD_MSEC} ).
	 */
	private void setLocalFileReadingTimer() {
		Long delay = CACHE.getLong(LOCAL_FILE_DEFAULT_REFRESHING_PERIOD_MSEC, "refreshingPeriodMsec");
		setLocalFileReadingTimer(delay);
	}

	/**
	 * Set a timer to read HWCONFIG from the file system.
	 * @param delay cycle duration [ms]
	 *
	 * ファイルシステムから HWCONFIG を読み込むタイマ設定.
	 * @param delay 周期 [ms]
	 */
	private void setLocalFileReadingTimer(long delay) {
		localFileReadingTimerId = vertx.setTimer(delay, this::localFileReadingTimerHandler);
	}

	/**
	 * Process the timer to read HWCONFIG from the file system.
	 * @param timerId timer ID
	 *
	 * ファイルシステムから HWCONFIG を読み込むタイマ処理.
	 * @param timerId タイマ ID
	 */
	private void localFileReadingTimerHandler(Long timerId) {
		if (stopped) return;

		if (null == timerId || timerId != localFileReadingTimerId) {
			ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN,
					"illegal timerId : " + timerId + ", localFileReadingTimerId : " + localFileReadingTimerId);
			return;
		}

		readLocalFile(resRead -> {
			if (resRead.succeeded()) {
				// Keep in cache
				// キャッシュしておく
				CACHE.setJsonObject(resRead.result());
			} else {
				// If unable to read
				// 読み込めなかったら
				if (CACHE.isNull()) {
					// Raise an error if the cache is empty (can't move)
					// キャッシュが空なら ( 動けないので ) エラーにする
					ErrorUtil.report(vertx, Error.Category.USER, Error.Extent.LOCAL, Error.Level.ERROR, resRead.cause());
				} else {
					// Pass through if a cache is available (can move)
					// キャッシュがあったら ( 動けるので ) スルー
					ErrorUtil.report(vertx, Error.Category.USER, Error.Extent.LOCAL, Error.Level.WARN, resRead.cause());
				}
			}
			setLocalFileReadingTimer();
		});
	}

	private void readLocalFile(Handler<AsyncResult<JsonObject>> completionHandler) {
		FileHandler.readLocalFile(completionHandler, vertx.fileSystem(), localFilePath);
	}

	/**
	 * Get the battery capacity from the HWCONFIG cache.
	 * @return the battery capacity [Wh]. May return {@code null}
	 *
	 * HWCONFIG キャッシュからバッテリ容量を取得する.
	 * @return バッテリ容量 [Wh]. {@code null} の可能性あり
	 */
	public static Float batteryNominalCapacityWh() {
		return CACHE.getFloat("batteryNominalCapacityWh");
	}

	/**
	 * Get the grid current capacity from the HWCONFIG cache.
	 * @return the grid current capacity [A]. May return {@code null}
	 *
	 * HWCONFIG キャッシュからグリッド電流容量を取得する.
	 * @return グリッド電流容量 [A]. {@code null} の可能性あり
	 */
	public static Float gridCurrentCapacityA() {
		return CACHE.getFloat("gridCurrentCapacityA");
	}

	/**
	 * Get the grid current error tolerance from the HWCONFIG cache.
	 * @return the grid current tolerance [A]. May return {@code null}
	 *
	 * HWCONFIG キャッシュからグリッド電流エラー許容値を取得する.
	 * @return グリッド電流エラー許容値 [A]. {@code null} の可能性あり
	 */
	public static Float gridCurrentAllowanceA() {
		return CACHE.getFloat("gridCurrentAllowanceA");
	}

	/**
	 * Get the droop ratio when moving a voltage reference from the HWCONFIG cache.
	 * @return the droop ratio. May return {@code null}
	 *
	 * HWCONFIG キャッシュから電圧リファレンス移動時のドループ率を取得する.
	 * @return ドループ率. {@code null} の可能性あり
	 */
	public static Float droopRatio() {
		return CACHE.getFloat("droopRatio");
	}

	/**
	 * Get the most efficient battery voltage vs. grid voltage ratio from the HWCONFIG cache.
	 * @return the most efficient battery voltage vs. grid voltage ratio. May return {@code null}
	 *
	 * HWCONFIG キャッシュから最も効率の良いバッテリ電圧とグリッド電圧の比を取得する.
	 * @return 最も効率の良いバッテリ電圧とグリッド電圧の比. {@code null} の可能性あり
	 */
	public static Float efficientBatteryGridVoltageRatio() {
		return CACHE.getFloat("efficientBatteryGridVoltageRatio");
	}

	/**
	 * Get the set of upper and lower values used in static safety checks from the HWCONFIG cache.
	 * @return the set of upper and lower values used in static safety checks {@link JsonObject}. May return {@code null}
	 *
	 * HWCONFIG キャッシュから静的安全性チェックで用いる上限下限値のセットを取得する.
	 * @return 静的安全性チェックで用いる上限下限値のセット {@link JsonObject}. {@code null} の可能性あり
	 */
	public static JsonObject safetyRange() {
		return CACHE.getJsonObject("safety", "range");
	}

	/**
	 * Get the upper and lower values used in static safety checks from the HWCONFIG cache as a path.
	 * @param keys path
	 * @return the upper and lower values used in static safety checks specified by a path {@link JsonObject}. May return {@code null}
	 *
	 * HWCONFIG キャッシュから静的安全性チェックで用いる上限下限値をパスで取得する.
	 * @param keys パス
	 * @return パスで指定した静的安全性チェックで用いる上限下限値 {@link JsonObject}. {@code null} の可能性あり
	 */
	public static JsonObject safetyRange(String... keys) {
		return JsonObjectUtil.getJsonObject(safetyRange(), keys);
	}
}
