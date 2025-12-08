package jp.co.sony.csl.dcoes.apis.main.app;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.ServiceAddress;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.ReplyFailureUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ApisConfig;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

/**
 * A Verticle that checks if there is a unit with the same ID in the cluster
 * Launched from the {@link jp.co.sony.csl.dcoes.apis.main.app.Apis} Verticle.
 * @author OES Project
 *          
 * クラスタ内に同一 ID のユニットが存在しないかチェックする Verticle.
 * {@link jp.co.sony.csl.dcoes.apis.main.app.Apis} Verticle から起動される.
 * @author OES Project
 */
public class Helo extends AbstractVerticle {
	private static final Logger LOGGER = LoggerFactory.getLogger(Helo.class);

	/**
	 * Default duration [ms] of the interval between periodic checks for other units with the same ID in the cluster.
	 * Value: {@value}.
	 *          
	 * クラスタ内に同一 ID のユニットが存在しないか定期的にチェックする周期のデフォルト値 [ms].
	 * 値は {@value}.
	 */
	private static final Long DEFAULT_HELO_PERIOD_MSEC = 5000L;

	private long heloTimerId_ = 0L;
	private boolean stopped_ = false;

	/**
	 * Called at startup.
	 * Checks for a unit with the same ID in the cluster.
	 * Launches the {@link io.vertx.core.eventbus.EventBus} service.
	 * Starts a timer.
	 * @param startFuture {@inheritDoc}
	 * @throws Exception {@inheritDoc}
	 *          
	 * 起動時に呼び出される.
	 * クラスタ内に同一 ID のユニットが存在しないかチェックする.
	 * {@link io.vertx.core.eventbus.EventBus} サービスを起動する.
	 * タイマを起動する.
	 * @param startFuture {@inheritDoc}
	 * @throws Exception {@inheritDoc}
	 */
	@Override public void start(Promise<Void> startPromise) throws Exception {
		// Check for the existence of a unit with the same ID at startup
		// 起動時に同一 ID を持つユニットの存在を確認する
		checkUniqueness_(resCheckUniqueness -> {
			if (resCheckUniqueness.succeeded()) {
				// Same ID check passed (not present)
				// 同一 ID 確認 OK ( いなかった )
				startHeloService_(resHelo -> {
					if (resHelo.succeeded()) {
						heloTimerHandler_(0L);
						if (LOGGER.isTraceEnabled()) LOGGER.trace("started : " + deploymentID());
						startPromise.complete();
					} else {
						startPromise.fail(resHelo.cause());
					}
				});
			} else {
				// Same ID check failed (present) → startup failed
				// 同一 ID 確認 NG ( いた ) → 起動失敗
				startPromise.fail(resCheckUniqueness.cause());
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
	@Override public void stop(Promise<Void> stopPromise) throws Exception {
		stopped_ = true;
		if (LOGGER.isTraceEnabled()) LOGGER.trace("stopped : " + deploymentID());
		stopPromise.complete();
	}

	////

	private void checkUniqueness_(Handler<AsyncResult<Void>> completionHandler) {
		vertx.eventBus().request(ServiceAddress.helo(ApisConfig.unitId()), null, repHelo -> {
			if (repHelo.succeeded()) {
				// Fail if there is a reply
				// 返事があったら失敗
				completionHandler.handle(Future.failedFuture("unit with id " + ApisConfig.unitId() + " already exists !!!"));
			} else {
				if (ReplyFailureUtil.isNoHandlers(repHelo)) {
					// Handler is not open, so this is OK
					// 口が開いてなかったので OK
					completionHandler.handle(Future.succeededFuture());
				} else {
					// Fail because an error occurred
					// 何やらエラーが起きたので失敗
					completionHandler.handle(Future.failedFuture(repHelo.cause()));
				}
			}
		});
	}

	/**
	 * Launch the {@link io.vertx.core.eventbus.EventBus} service.
	 * Address: {@link ServiceAddress#helo(String)}
	 * Scope: global
	 * Function: A mechanism to prevent duplicate units with the same ID from existing in a cluster.
	 *           If the message body is empty, return the unit ID of this unit.
	 *           If there is a message body and it matches this unit's {@link #deploymentID()}, pass through this value (because it corresponds to this unit).
	 *           Raise a FATAL error if there is a message body but it does not match this unit's {@link #deploymentID()} (because this means there is another unit with the same ID).
	 * Message body: {@link #deploymentID()} [{@link String}] of source {@link Helo} Verticle
	 * Message header: none
	 * Response: If the message body is empty, the ID of this unit [{@link String}].
	 *           Otherwise, nothing.
	 * @param completionHandler the completion handler
	 *          
	 * {@link io.vertx.core.eventbus.EventBus} サービス起動.
	 * アドレス : {@link ServiceAddress#helo(String)}
	 * 範囲 : グローバル
	 * 処理 : クラスタ内に同一 ID を持つユニットが重複しないための仕組み.
	 * 　　   メッセージボディが空なら自ユニットのユニット ID を返す.
	 * 　　   メッセージボディがあり自分の {@link #deploymentID()} と一致したらスルー ( 自分なので ).
	 * 　　   メッセージボディがあり自分の {@link #deploymentID()} と一致しなかったら FATAL エラー ( 他に同じ ID のユニットがいるということなので ).
	 * メッセージボディ : 送信元 {@link Helo} Verticle の {@link #deploymentID()} [{@link String}]
	 * メッセージヘッダ : なし
	 * レスポンス : メッセージボディが空なら自ユニットの ID [{@link String}].
	 * 　　　　　   それ以外は返さない.
	 * @param completionHandler the completion handler
	 */
	private void startHeloService_(Handler<AsyncResult<Void>> completionHandler) {
		vertx.eventBus().<String>consumer(ServiceAddress.helo(ApisConfig.unitId()), req -> {
			String senderDeploymentID = req.body();
			if (null == senderDeploymentID) {
				// Perform a simple query if no value has been sent
				// 値が送りつけられていなければただの問い合わせ
				// Return this unit's ID
				// 自ユニットの ID を返す
				req.reply(ApisConfig.unitId());
			} else {
				// If a value was sent, this is the sender's main deploymentID
				// 値が送りつけられていたらそれは送り主の deploymentID
				if (!senderDeploymentID.equals(deploymentID())) {
					// If it doesn't match this unit, there must be another unit with the same ID
					// 自分のと違う場合は他にも同じ ID のユニットがいるということ
					// → Shut down with a LOCAL FATAL error
					// → LOCAL FATAL でシャットダウンする
					ErrorUtil.report(vertx, Error.Category.USER, Error.Extent.LOCAL, Error.Level.FATAL, "another unit with id " + ApisConfig.unitId() + " found !!!");
				}
				// If the IDs match, then there is no problem
				// 自分のと同じなら問題なし
			}
		}).completionHandler(completionHandler);
	}

	/**
	 * Set a timer that periodically checks whether or not a unit with the same ID exists in the cluster
	 * The timeout duration is {@code POLICY.heloPeriodMsec} (default: {@link #DEFAULT_HELO_PERIOD_MSEC}).
	 *          
	 * クラスタ内に同一 ID のユニットが存在しないか定期的にチェックするタイマの設定.
	 * 待ち時間は {@code POLICY.heloPeriodMsec} ( デフォルト値 {@link #DEFAULT_HELO_PERIOD_MSEC} ).
	 */
	private void setHeloTimer_() {
		Long delay = PolicyKeeping.cache().getLong(DEFAULT_HELO_PERIOD_MSEC, "heloPeriodMsec");
		setHeloTimer_(delay);
	}
	/**
	 * Set a timer that periodically checks whether or not a unit with the same ID exists in the cluster
	 * @param delay cycle duration [ms]
	 *          
	 * クラスタ内に同一 ID のユニットが存在しないか定期的にチェックするタイマの設定.
	 * @param delay 周期 [ms]
	 */
	private void setHeloTimer_(long delay) {
		heloTimerId_ = vertx.setTimer(delay, this::heloTimerHandler_);
	}
	/**
	 * A process that periodically checks whether or not a unit with the same ID exists in the cluster.
	 * @param timerId timer ID
	 *          
	 * クラスタ内に同一 ID のユニットが存在しないか定期的にチェックするタイマ処理.
	 * @param timerId タイマ ID
	 */
	private void heloTimerHandler_(Long timerId) {
		if (stopped_) return;
		if (null == timerId || timerId != heloTimerId_) {
			ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, "illegal timerId : " + timerId + ", heloTimerId_ : " + heloTimerId_);
			return;
		}
		// Publish with own deployment ID (murder beam)
		// 自分の deploymentID を持って publish する ( 殺人ビーム )
		vertx.eventBus().publish(ServiceAddress.helo(ApisConfig.unitId()), deploymentID());
		setHeloTimer_();
	}

}
