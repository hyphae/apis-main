package jp.co.sony.csl.dcoes.apis.main.app;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.shareddata.AsyncMap;

import java.util.List;

import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.ServiceAddress;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.EncryptedClusterWideMapUtil;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectUtil;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectWrapper;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.VertxConfig;
import jp.co.sony.csl.dcoes.apis.main.util.ApisConfig;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

/**
 * A Verticle that manages a POLICY.
 * Launched from the {@link jp.co.sony.csl.dcoes.apis.main.app.Apis} Verticle.
 * @author OES Project
 *
 * POLICY を管理する Verticle.
 * {@link jp.co.sony.csl.dcoes.apis.main.app.Apis} Verticle から起動される.
 * @author OES Project
 */
public class PolicyKeeping extends AbstractVerticle {
	private static final Logger LOGGER = LoggerFactory.getLogger(PolicyKeeping.class);

	/**
	 * Default timeout duration when reading POLICY from the file system [ms].
	 * Value: {@value}.
	 *
	 * ファイルシステムから POLICY を読み込む際のタイムアウト時間のデフォルト値 [ms].
	 * 値は {@value}.
	 */
	private static final long LOCAL_FILE_DEFAULT_READ_TIMEOUT_MSEC = 60000L;

	/**
	 * Default duration of the period with which POLICY is read from the file system [ms].
	 * Value: {@value}.
	 *
	 * ファイルシステムから POLICY を読み込む周期のデフォルト値 [ms].
	 * 値は {@value}.
	 */
	private static final long LOCAL_FILE_DEFAULT_REFRESHING_PERIOD_MSEC = 5000L;

	/**
	 * Default duration of the period with which POLICY is fetched from the service center [ms].
	 * Value: {@value}.
	 *
	 * サービスセンタから POLICY を取得する周期のデフォルト値 [ms].
	 * 値は {@value}.
	 */
	private static final long CONTROL_CENTER_DEFAULT_REFRESHING_PERIOD_MSEC = 60000L;

	/**
	 * A cache that holds the file system POLICY.
	 *
	 * ファイルシステムの POLICY を保持しておくキャッシュ.
	 */
	private static final JsonObjectWrapper LOCAL_FILE_CACHE = new JsonObjectWrapper();

	/**
	 * A cache that holds the service center POLICY.
	 *
	 * サービスセンタの POLICY を保持しておくキャッシュ.
	 */
	private static final JsonObjectWrapper controlCenterCache = new JsonObjectWrapper();

	private static final String MAP_NAME = PolicyKeeping.class.getName();
	private static final String MAP_KEY_POLICY = "policy";

	/**
	 * Get a cache that holds a POLICY.
	 * Priority is given to the service center cache, if available.
	 * Otherwise, return the cache in the local file system.
	 * @return a POLICY cache.
	 *
	 * POLICY を保持しておくキャッシュを取得する.
	 * サービスセンタのキャッシュがあればそちらを優先する.
	 * なければローカルファイルのキャッシュを返す.
	 * @return POLICY のキャッシュ.
	 */
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

	/**
	 * Called at startup.
	 * Fetch settings from CONFIG and perform initialization.
	 * - CONFIG.policyFile: POLICY file path
	 * - CONFIG.controlCenter.enabled: a flag indicating use of the service center function [{@link Boolean}]
	 * - CONFIG.controlCenter.account: service center authentication account
	 * - CONFIG.controlCenter.password: service center authentication password
	 * Check that the file is readable.
	 * Check that the contents of POLICY are consistent throughout the cluster.
	 * Launch the {@link io.vertx.core.eventbus.EventBus} service.
	 * Start a timer that periodically reloads the local file.
	 * When using the service center function, start a timer to access it periodically.
	 * @param startFuture {@inheritDoc}
	 * @throws Exception {@inheritDoc}
	 *
	 * 起動時に呼び出される.
	 * CONFIG から設定を取得し初期化する.
	 * - CONFIG.policyFile : POLICY ファイルのパス
	 * - CONFIG.controlCenter.enabled : サービスセンタ機能を使用するかフラグ [{@link Boolean}]
	 * - CONFIG.controlCenter.account : サービスセンタの認証アカウント
	 * - CONFIG.controlCenter.password : サービスセンタの認証パスワード
	 * ファイルが読み込めるか確認する.
	 * POLICY の内容がクラスタ内で一致しているか確認する.
	 * {@link io.vertx.core.eventbus.EventBus} サービスを起動する.
	 * ローカルファイルを定期的に再読み込みするタイマを起動する.
	 * サービスセンタ機能を使用する場合, 定期的にアクセスするタイマを起動する.
	 * @param startFuture {@inheritDoc}
	 * @throws Exception {@inheritDoc}
	 */
	@Override
	public void start(Future<Void> startFuture) throws Exception {
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

		// Initialization process (check that POLICY is readable)
		// 初期化処理 ( POLICY が読み込めるかを確認する )
		init(resInit -> {
			if (resInit.succeeded()) {
				startPolicyService(resPolicy -> {
					if (resPolicy.succeeded()) {
						localFileReadingTimerHandler(0L);
						// Set a timer if using the service center function
						// サービスセンタの機能を使用するならタイマを仕込む
						if (controlCenterEnabled) {
							controlCenterAccessingTimerHandler(0L);
						}
						LOGGER.trace("started : {}", deploymentID());
						startFuture.complete();
					} else {
						startFuture.fail(resPolicy.cause());
					}
				});
			} else {
				startFuture.fail(resInit.cause());
			}
		});
	}

	/**
	 * Called when stopped.
	 * Sets a flag to stop the timer.
	 * @throws Exception {@inheritDoc}
	 *
	 * 停止時に呼び出される.
	 * タイマを止めるためのフラグを立てる.
	 * @throws Exception {@inheritDoc}
	 */
	@Override
	public void stop() throws Exception {
		stopped = true;
		LOGGER.trace("stopped : {}", deploymentID());
	}

	/**
	 * Startup initialization process.
	 * Read POLICY from the file system.
	 * Bail out if reading fails or is not completed within a fixed period.
	 * @param completionHandler the completion handler
	 *
	 * 起動時の初期化処理.
	 * ファイルシステムから POLICY を読み込む.
	 * 読み込みに失敗したり一定時間内に完了しなかったらアウト.
	 * @param completionHandler the completion handler
	 */
	private void init(Handler<AsyncResult<Void>> completionHandler) {
		Boolean[] handled = new Boolean[1];
		Long localFileReadTimeoutMsec = VertxConfig.config.getLong(LOCAL_FILE_DEFAULT_READ_TIMEOUT_MSEC, "policyFileReadTimeoutMsec");
		vertx.setTimer(localFileReadTimeoutMsec, timerId -> {
			if (handled[0] == null) {
				// A timeout occurred before the read process was completed
				// 読み込み処理が完了する前にタイムアウトが起きた
				handled[0] = Boolean.TRUE;
				completionHandler.handle(Future.failedFuture("POLICY read timed out : " + localFileReadTimeoutMsec + "ms"));
			}
		});

		readLocalFile(resRead -> {
			if (handled[0] == null) {
				// The read process was completed before a timeout occurred
				// タイムアウトが起きる前に読み込み処理が完了した
				handled[0] = Boolean.TRUE;
				if (resRead.succeeded()) {
					// Keep in cache
					// キャッシュしておく
					LOCAL_FILE_CACHE.setJsonObject(resRead.result());
					// Guarantee that the contents of POLICY are the same throughout a cluster
					// クラスタ内で POLICY の内容が同じことを保証する
					checkClusterPolicy(LOCAL_FILE_CACHE.jsonObject(), completionHandler);
				} else {
					completionHandler.handle(Future.failedFuture(resRead.cause()));
				}
			}
		});
	}

	/**
	 * Guarantee that the contents of POLICY are the same throughout a cluster.
	 * @param policy a POLICY object
	 * @param completionHandler the completion handler
	 *
	 * クラスタ内で POLICY の内容が同じことを保証する.
	 * @param policy POLICY オブジェクト
	 * @param completionHandler the completion handler
	 */
	private void checkClusterPolicy(JsonObject policy, Handler<AsyncResult<Void>> completionHandler) {
		String myValue = policy.encode();
		EncryptedClusterWideMapUtil.<String, String>getEncryptedClusterWideMap(vertx, MAP_NAME, resMap -> {
			if (resMap.succeeded()) {
				AsyncMap<String, String> theMap = resMap.result();
				// If absent, try writing it
				// "無ければ置く" 操作を試してみる
				theMap.putIfAbsent(MAP_KEY_POLICY, myValue, resPutIfAbsent -> {
					if (resPutIfAbsent.succeeded()) {
						String existingValue = resPutIfAbsent.result();
						if (existingValue == null) {
							// It worked (first started in a cluster)
							// 置けた ( クラスタ内で最初に起動した )
							// → OK
							// → OK
							completionHandler.handle(Future.succeededFuture());
						} else {
							// Didn't work (it was already there)
							// 置けなかった ( もうあった )
							if (existingValue.equals(myValue)) {
								// The value in the cluster matches this unit's own value
								// クラスタ上の値と自分の値が同じ
								// → OK
								// → OK
								completionHandler.handle(Future.succeededFuture());
							} else {
								// The value in the cluster is different
								// クラスタ上の値と異なる
								// → NG
								// → NG
								completionHandler.handle(Future.failedFuture("my POLICY is different from cluster's"));
							}
						}
					} else {
						completionHandler.handle(Future.failedFuture(resPutIfAbsent.cause()));
					}
				});
			} else {
				completionHandler.handle(Future.failedFuture(resMap.cause()));
			}
		});
	}

	/**
	 * Launch the {@link io.vertx.core.eventbus.EventBus} service.
	 * Address: {@link ServiceAddress#policy()}
	 * Scope: local
	 * Function: Get this unit's POLICY.
	 *           Return the periodically refreshed cache contents.
	 * Message body: none
	 * Message header: none
	 * Response: POLICY information [{@link JsonObject}]
	 * @param completionHandler the completion handler
	 *
	 * {@link io.vertx.core.eventbus.EventBus} サービス起動.
	 * アドレス : {@link ServiceAddress#policy()}
	 * 範囲 : ローカル
	 * 処理 : 自ユニットの POLICY を取得する.
	 * 　　   定期的にリフレッシュされているキャッシュ内容を返す.
	 * メッセージボディ : なし
	 * メッセージヘッダ : なし
	 * レスポンス : POLICY 情報 [{@link JsonObject}]
	 * @param completionHandler the completion handler
	 */
	private void startPolicyService(Handler<AsyncResult<Void>> completionHandler) {
		vertx.eventBus().<Void>localConsumer(ServiceAddress.policy(), req -> {
			JsonObject jsonObject = cache().jsonObject();
			req.reply(jsonObject);
		}).completionHandler(completionHandler);
	}

	/**
	 * Set a timer to read POLICY from the file system.
	 * The timeout duration is {@code POLICY.refreshingPeriodMsec} (default: {@link #LOCAL_FILE_DEFAULT_REFRESHING_PERIOD_MSEC}).
	 *
	 * ファイルシステムから POLICY を読み込むタイマ設定.
	 * 待ち時間は {@code POLICY.refreshingPeriodMsec} ( デフォルト値 {@link #LOCAL_FILE_DEFAULT_REFRESHING_PERIOD_MSEC} ).
	 */
	private void setLocalFileReadingTimer() {
		Long delay = LOCAL_FILE_CACHE.getLong(LOCAL_FILE_DEFAULT_REFRESHING_PERIOD_MSEC, "refreshingPeriodMsec");
		setLocalFileReadingTimer(delay);
	}

	/**
	 * Set a timer to read POLICY from the file system.
	 * @param delay cycle duration [ms]
	 *
	 * ファイルシステムから POLICY を読み込むタイマ設定.
	 * @param delay 周期 [ms]
	 */
	private void setLocalFileReadingTimer(long delay) {
		localFileReadingTimerId = vertx.setTimer(delay, this::localFileReadingTimerHandler);
	}

	/**
	 * Process the timer to read POLICY from the file system.
	 * @param timerId timer ID
	 *
	 * ファイルシステムから POLICY を読み込むタイマ処理.
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
				LOCAL_FILE_CACHE.setJsonObject(resRead.result());
			} else {
				// If unable to read
				// 読み込めなかったら
				if (LOCAL_FILE_CACHE.isNull()) {
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
		vertx.fileSystem().readFile(localFilePath, resFile -> {
			if (resFile.succeeded()) {
				JsonObjectUtil.toJsonObject(resFile.result(), resToJsonObject -> {
					if (resToJsonObject.succeeded()) {
						JsonObject jsonObject = resToJsonObject.result();
						completionHandler.handle(Future.succeededFuture(jsonObject));
					} else {
						completionHandler.handle(resToJsonObject);
					}
				});
			} else {
				completionHandler.handle(Future.failedFuture(resFile.cause()));
			}
		});
	}

	/**
	 * Set a timer for fetching POLICY from the service center.
	 * The timeout duration is {@code POLICY.refreshingPeriodMsec} (default: {@link #CONTROL_CENTER_DEFAULT_REFRESHING_PERIOD_MSEC}).
	 *
	 * サービスセンタから POLICY を取得するタイマ設定.
	 * 待ち時間は {@code POLICY.refreshingPeriodMsec} ( デフォルト値 {@link #CONTROL_CENTER_DEFAULT_REFRESHING_PERIOD_MSEC} ).
	 */
	private void setControlCenterAccessingTimer() {
		Long delay = controlCenterCache.getLong(CONTROL_CENTER_DEFAULT_REFRESHING_PERIOD_MSEC, "refreshingPeriodMsec");
		setControlCenterAccessingTimer(delay);
	}

	/**
	 * Set a timer for fetching POLICY from the service center.
	 * @param delay cycle duration [ms]
	 *
	 * サービスセンタから POLICY を取得するタイマ設定.
	 * @param delay 周期 [ms]
	 */
	private void setControlCenterAccessingTimer(long delay) {
		controlCenterAccessingTimerId = vertx.setTimer(delay, this::controlCenterAccessingTimerHandler);
	}

	/**
	 * Process the timer for fetching POLICY from the service center.
	 * @param timerId timer ID
	 *
	 * サービスセンタから POLICY を取得するタイマ処理.
	 * @param timerId タイマ ID
	 */
	private void controlCenterAccessingTimerHandler(Long timerId) {
		if (stopped) return;

		if (null == timerId || timerId != controlCenterAccessingTimerId) {
			ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN,
					"illegal timerId : " + timerId + ", controlCenterAccessingTimerId : " + controlCenterAccessingTimerId);
			return;
		}

		DeliveryOptions options = new DeliveryOptions()
				.addHeader("account", controlCenterAccount)
				.addHeader("password", controlCenterPassword)
				.addHeader("unitId", ApisConfig.unitId());

		vertx.eventBus().<JsonObject>send(ServiceAddress.ControlCenterClient.policy(), null, options, resPolicy -> {
			if (resPolicy.succeeded()) {
				// Keep in cache
				// キャッシュしておく
				controlCenterCache.setJsonObject(resPolicy.result().body());
			} else {
				ErrorUtil.report(vertx, Error.Category.USER, Error.Extent.LOCAL, Error.Level.WARN,
						"Communication failed on EventBus", resPolicy.cause());
			}
			setControlCenterAccessingTimer();
		});
	}

	/**
	 * Fetch a list of IDs of the units participating in a cluster from the POLICY cache.
	 * @return a list of IDs of the units participating in a cluster. May return {@code null}
	 *
	 * POLICY キャッシュからクラスタ参加ユニットの ID のリストを取得する.
	 * @return クラスタ参加ユニットの ID のリスト. {@code null} の可能性あり
	 */
	public static List<String> memberUnitIds() {
		return cache().getStringList("memberUnitIds");
	}

	/**
	 * Get the number of units participating in a cluster defined in the POLICY cache.
	 * @return the number of units participating in the cluster, or null if not defined
	 *
	 * POLICY キャッシュに定義されたクラスタ参加ユニットの数を取得する.
	 * @return クラスタ参加ユニット数. 定義がなければ null
	 */
	public static int numberOfMembers() {
		List<String> memberUnitIds = memberUnitIds();
		return (memberUnitIds != null) ? memberUnitIds.size() : 0;
	}

	/**
	 * Check that the ID specified by unitId is defined as a participating unit in the POLICY cache.
	 * @param unitId ID of the unit to check
	 * @return true if defined. Otherwise return false.
	 *
	 * unitId で指定された ID が POLICY キャッシュに参加ユニットとして定義されているか確認する.
	 * @param unitId 確認するユニットの ID
	 * @return 定義されていれば true. そうでなければ false
	 */
	public static boolean isMember(String unitId) {
		if (unitId != null) {
			List<String> memberUnitIds = memberUnitIds();
			return (memberUnitIds != null && memberUnitIds.contains(unitId));
		}
		return false;
	}
}