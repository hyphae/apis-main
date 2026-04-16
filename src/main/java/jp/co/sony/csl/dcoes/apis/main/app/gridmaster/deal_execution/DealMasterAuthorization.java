package jp.co.sony.csl.dcoes.apis.main.app.gridmaster.deal_execution;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.List;

import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.main.app.controller.util.DDCon;
import jp.co.sony.csl.dcoes.apis.main.app.mediator.util.DealUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorExceptionUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

/**
 * Perform operations to obtain voltage reference privilege.
 * @author OES Project
 *          
 * 電圧リファレンス権限取得動作を実行する.
 * @author OES Project
 */
public class DealMasterAuthorization extends AbstractDealExecution {
	private static final Logger log = LoggerFactory.getLogger(DealMasterAuthorization.class);

	/**
	 * Create an instance.
	 * @param vertx a vertx object
	 * @param policy a POLICY object. To prevent changes from taking effect while running, a copy is passed at startup to {@link jp.co.sony.csl.dcoes.apis.main.app.gridmaster.main_loop.DealExecution DealExecution}.
	 * @param deal the DEAL object to be processed
	 * @param otherDeals a list of other DEAL objects that exist at the same time
	 *          
	 * インスタンスを生成する.
	 * @param vertx vertx オブジェクト
	 * @param policy POLICY オブジェクト. 処理中に変更されても影響しないように {@link jp.co.sony.csl.dcoes.apis.main.app.gridmaster.main_loop.DealExecution DealExecution} 開始時にコピーしたものが渡される.
	 * @param deal 処理対象 DEAL オブジェクト
	 * @param otherDeals 同時に存在している他の DEAL オブジェクトのリスト
	 */
	public DealMasterAuthorization(Vertx vertx, JsonObject policy, JsonObject deal, List<JsonObject> otherDeals) {
		super(vertx, policy, deal, otherDeals);
	}
	/**
	 * Create an instance.
	 * Initialization is not required because the internal state of another {@link AbstractDealExecution} is inherited as-is.
	 * @param other another abstractdealexecution object
	 *          
	 * インスタンスを生成する.
	 * 他の {@link AbstractDealExecution} の内部状態をそのまま受け継ぐため初期化不要.
	 * @param other 他の abstractdealexecution オブジェクト
	 */
	public DealMasterAuthorization(AbstractDealExecution other) {
		super(other);
	}

	@Override protected void doExecute(Handler<AsyncResult<Void>> completionHandler) {
		if (DDCon.Mode.VOLTAGE_REFERENCE == masterSideUnitDDConMode_()) {
			if (DDCon.Mode.WAIT == slaveSideUnitDDConMode_()) {
				// Perform operations to acquire voltage reference privilege
				// 電圧リファレンス権限獲得動作を実行する
				doAuthorize_(resAuthorize -> {
					if (resAuthorize.succeeded()) {
						// If voltage reference privilege is successfully obtained
						// 電圧リファレンス権限獲得が成功したら
						if (log.isInfoEnabled()) log.info("deal master authorized");
						// Start up the non-voltage-reference side and proceed to the current compensation process
						// 電圧リファレンス側じゃない方を起動し電流コンペンセイションを実行する処理に移行する
						new DealCompensation(this).execute(completionHandler);
					} else {
						// If voltage reference privilege acquisition fails
						// 電圧リファレンス権限獲得が失敗したら
						// Stop the device
						// デバイスを止めて
						deactivateDcdc_(resDeactivateDcdc -> {
							if (resDeactivateDcdc.succeeded()) {
								String resetReason = masterSideUnitId_() + " : " + resAuthorize.cause();
								// Place the DEAL object in the "Reset 1" state (NOTE: this comment is unclear)
								// DEAL オブジェクトを "リセット 1" する
								resetDeal_(resetReason, completionHandler);
							} else {
								completionHandler.handle(resDeactivateDcdc);
							}
						});
					}
				});
			} else {
				ErrorUtil.reportAndFail(vertx_, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.WARN, "invalid slave side unit status; unit : " + slaveSideUnitId_() + ", mode : " + slaveSideUnitDDConMode_(), completionHandler);
			}
		} else {
			ErrorUtil.reportAndFail(vertx_, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.WARN, "invalid master side unit status; unit : " + masterSideUnitId_() + ", mode : " + masterSideUnitDDConMode_(), completionHandler);
		}
	}

	private void doAuthorize_(Handler<AsyncResult<Void>> completionHandler) {
		// Fail if dcdc/failBeforeAuthorize = true in the DEAL object
		// DEAL オブジェクト中に dcdc/failBeforeAuthorize = true があったら fail させる
		if (testFeature_failIfNeed_("dcdc", "failBeforeAuthorize", completionHandler)) return;
		// Run voltageReferenceAuthorization
		// voltageReferenceAuthorization 実行
		// If successful, fail if dcdc/failAfterAuthorize = true in the DEAL object
		// 成功した場合に DEAL オブジェクト中に dcdc/failAfterAuthorize = true があったら fail させる
		controlMasterSideUnitDcdc_("voltageReferenceAuthorization", null, res -> testFeature_failIfNeed_(res, "dcdc", "failAfterAuthorize", completionHandler));
	}

	private void deactivateDcdc_(Handler<AsyncResult<Void>> completionHandler) {
		// Fail if dcdc/failBeforeDeactivate = true in the DEAL object
		// DEAL オブジェクト中に dcdc/failBeforeDeactivate = true があったら fail させる
		if (testFeature_failIfNeed_("dcdc", "failBeforeDeactivate", completionHandler)) return;
		// Stop the device
		// デバイスを止める
		// If successful, fail if dcdc/failAfterDeactivate = true in the DEAL object
		// 成功した場合に DEAL オブジェクト中に dcdc/failAfterDeactivate = true があったら fail させる
		controlMasterSideUnitDcdc_(DDCon.Mode.WAIT.name(), null, res -> testFeature_failIfNeed_(res, "dcdc", "failAfterDeactivate", completionHandler));
	}

	private void resetDeal_(String reason, Handler<AsyncResult<Void>> completionHandler) {
		// Reset the DEAL object
		// DEAL オブジェクトをリセットする
		// Something like "Path 1" (NOTE: this comment is unclear)
		// "パス 1" みたいなもの
		DealUtil.reset(vertx_, deal_, referenceDateTimeString_(), reason, resReset -> ErrorExceptionUtil.reportIfNeedAndHandle(vertx_, resReset, completionHandler));
	}

}
