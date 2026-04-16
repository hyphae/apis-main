package jp.co.sony.csl.dcoes.apis.main.app.gridmaster.deal_execution;

import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.List;

import jp.co.sony.csl.dcoes.apis.common.Deal;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectUtil;
import jp.co.sony.csl.dcoes.apis.main.app.PolicyKeeping;
import jp.co.sony.csl.dcoes.apis.main.app.controller.util.DDCon;
import jp.co.sony.csl.dcoes.apis.main.app.gridmaster.main_loop.DealExecution;
import jp.co.sony.csl.dcoes.apis.main.app.mediator.util.DealUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorExceptionUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

/**
 * Start a unit that is not on the voltage reference side and perform the current compensation operation.
 * @author OES Project
 *          
 * 電圧リファレンス側じゃない方のユニットを起動し電流コンペンセイション動作を実行する.
 * @author OES Project
 */
public class DealCompensation extends AbstractDealExecution {
	private static final Logger log = LoggerFactory.getLogger(DealCompensation.class);

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
	public DealCompensation(Vertx vertx, JsonObject policy, JsonObject deal, List<JsonObject> otherDeals) {
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
	public DealCompensation(AbstractDealExecution other) {
		super(other);
	}

	@Override protected void doExecute(Handler<AsyncResult<Void>> completionHandler) {
		if (DDCon.Mode.WAIT != masterSideUnitDDConMode_()) {
			// Attach the unit on the non-voltage-reference side
			// 電圧リファレンスじゃない側のユニットをつける
			warmUpDcdc_(resWarmUpDcdc -> {
				if (resWarmUpDcdc.succeeded()) {
					// Put the DEAL object in the "warmUp" state
					// DEAL オブジェクトを warmUp 状態にする
					warmUpDeal_(resWarmUpDeal -> {
						if (resWarmUpDeal.succeeded()) {
							// Perform current compensation
							// 電流コンペンセイションを実行する
							doCompensate_(resCompensate -> {
								if (resCompensate.succeeded()) {
									if (log.isInfoEnabled()) log.info("deal compensated");
									// In the DEAL object, record the interchange current value after compensation has been performed
									// コンペンセイション実行後の融通電流値を DEAL オブジェクトに記録する
									saveCompensatedGridCurrentA_(resSave -> {
										if (resSave.succeeded()) {
											// Put the DEAL object in the "start" state
											// DEAL オブジェクトを start 状態にする
											DealUtil.start(vertx_, deal_, referenceDateTimeString_(), resStart -> ErrorExceptionUtil.reportIfNeedAndHandle(vertx_, resStart, completionHandler));
										} else {
											completionHandler.handle(resSave);
										}
									});
								} else {
									completionHandler.handle(resCompensate);
								}
							});
						} else {
							completionHandler.handle(resWarmUpDeal);
						}
					});
				} else {
					completionHandler.handle(resWarmUpDcdc);
				}
			});
		} else {
			ErrorUtil.reportAndFail(vertx_, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.WARN, "invalid master side unit status; unit : " + masterSideUnitId_() + ", mode : " + masterSideUnitDDConMode_(), completionHandler);
		}
	}

	/**
	 * Control the unit on the non-voltage-reference side.
	 * @param completionHandler the completion handler
	 *          
	 * 電圧リファレンス側じゃない方のユニットを制御する.
	 * @param completionHandler the completion handler
	 */
	private void warmUpDcdc_(Handler<AsyncResult<Void>> completionHandler) {
		if (!Deal.isWarmedUp(deal_)) {
			// Fail if dcdc/failBeforeWarmUp = true in the DEAL object
			// DEAL オブジェクト中に dcdc/failBeforeWarmUp = true があったら fail させる
			if (testFeature_failIfNeed_("dcdc", "failBeforeWarmUp", completionHandler)) return;
			Float dealGridCurrentA = Deal.dealGridCurrentA(deal_);
			if (dealGridCurrentA != null) {
				if (DDCon.Mode.WAIT == slaveSideUnitDDConMode_()) {
					// If WAIT
					// WAIT だったら
					if (log.isInfoEnabled()) log.info("start slave side unit");
					// Decide between CHARGE and DISCHARGE
					// CHARGE か DISCHARGE か決めて
					DDCon.Mode slaveSideUnitNewDDConMode = ("dischargeUnit".equals(masterSide_)) ? DDCon.Mode.CHARGE : DDCon.Mode.DISCHARGE;
					// Set up the interchange current value in the parameter
					// 融通電流値をパラメタに仕込んで
					JsonObject params = new JsonObject().put("gridCurrentA", dealGridCurrentA);
					// Issue command to change mode
					// モード変更を命令
					// If successful, fail if dcdc/failAfterWarmUp = true in the DEAL object
					// 成功した場合に DEAL オブジェクト中に dcdc/failAfterWarmUp = true があったら fail させる
					controlSlaveSideUnitDcdc_(slaveSideUnitNewDDConMode.name(), params, res -> testFeature_failIfNeed_(res, "dcdc", "failAfterWarmUp", completionHandler));
				} else {
					// If not WAIT
					// WAIT じゃなかったら
					// Add together the interchange currents from the other interchange information of present participants
					// 現在参加中の他の融通情報から融通電流を合計し
					// Also add the interchange current of this interchange
					// さらに当該融通の融通電流を加え
					float newDig = Math.abs(sumOfOtherDealCompensatedGridCurrentAs_(slaveSideUnitId_())) + dealGridCurrentA;
					if (log.isInfoEnabled()) log.info("dig : " + JsonObjectUtil.getFloat(slaveSideUnitData_(), "dcdc", "param", "dig") + " -> " + newDig);
					// Set up the current value in the parameter
					// 電流値をパラメタに仕込んで
					JsonObject params = new JsonObject().put("gridCurrentA", newDig);
					// Issue command to change the current value
					// 電流値の変更を命令
					// If successful, fail if dcdc/failAfterWarmUp = true in the DEAL object
					// 成功した場合に DEAL オブジェクト中に dcdc/failAfterWarmUp = true があったら fail させる
					controlSlaveSideUnitDcdc_("current", params, res -> testFeature_failIfNeed_(res, "dcdc", "failAfterWarmUp", completionHandler));
				}
			} else {
				ErrorUtil.reportAndFail(vertx_, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.WARN, "no dealGridCurrentA value in deal : " + deal_, completionHandler);
			}
		} else {
			if (log.isInfoEnabled()) log.info("already warmed up");
			completionHandler.handle(Future.succeededFuture());
		}
	}

	/**
	 * Put the interchange information in the "warmUp complete" state
	 * @param completionHandler the completion handler
	 *          
	 * 融通情報を warmUp 完了状態にする.
	 * @param completionHandler the completion handler
	 */
	private void warmUpDeal_(Handler<AsyncResult<Void>> completionHandler) {
		if (!Deal.isWarmedUp(deal_)) {
			DealUtil.warmUp(vertx_, deal_, referenceDateTimeString_(), res -> ErrorExceptionUtil.reportIfNeedAndHandle(vertx_, res, completionHandler));
		} else {
			completionHandler.handle(Future.succeededFuture());
		}
	}

	/**
	 * In the DEAL object, record the interchange current value after compensation has been performed
	 * @param completionHandler the completion handler
	 *          
	 * コンペンセイション実行後の融通電流値を DEAL オブジェクトに記録する
	 * @param completionHandler the completion handler
	 */
	private void saveCompensatedGridCurrentA_(Handler<AsyncResult<Void>> completionHandler) {
		// Acquire the device control state of units on both sides
		// 両側ユニットのデバイス制御状態を取得する
		Future<Void> dischargeFuture = Future.future();
		Future<Void> chargeFuture = Future.future();
		updateUnitDcdcStatus_(dischargeUnitId_, dischargeFuture);
		updateUnitDcdcStatus_(chargeUnitId_, chargeFuture);
		CompositeFuture.<Void, Void>all(dischargeFuture, chargeFuture).setHandler(ar -> {
			if (ar.succeeded()) {
				// Obtain grid current measurements for units at both ends
				// 両端ユニットのグリッド電流測定値を取得する
				Float dischargeUnitIg = JsonObjectUtil.getFloat(dischargeUnitData_, "dcdc", "meter", "ig");
				Float chargeUnitIg = JsonObjectUtil.getFloat(chargeUnitData_, "dcdc", "meter", "ig");
				if (dischargeUnitIg != null && chargeUnitIg != null) {
					if (log.isInfoEnabled()) log.info("discharge unit ig : " + dischargeUnitIg + ", charge unit ig : " + chargeUnitIg);
					// Add together the interchange currents of the other interchanges presently participating at each end unit
					// 両端ユニットそれぞれ現在参加中の他の融通の融通電流を合計し
					float dischargeUnitSumOfOtherDealCompensatedGridCurrentA = sumOfOtherDealCompensatedGridCurrentAs_(dischargeUnitId_);
					float chargeUnitSumOfOtherDealCompensatedGridCurrentA = sumOfOtherDealCompensatedGridCurrentAs_(chargeUnitId_);
					// Subtract the sum of the interchange currents of the other interchanges from the present grid currents of each end unit
					// 両端ユニットそれぞれ現在のグリッド電流から他の融通の融通電流の合計を引くと
					float dischargeUnitCompensatedGridCurrentA = dischargeUnitIg - dischargeUnitSumOfOtherDealCompensatedGridCurrentA;
					float chargeUnitCompensatedGridCurrentA = chargeUnitIg - chargeUnitSumOfOtherDealCompensatedGridCurrentA;
					// This will become the interchange current at this interchange, so record it in the DEAL object
					// それが当該融通における融通電流になるので DEAL オブジェクトに記録する
					deal_.put("dischargeUnitCompensatedGridCurrentA", dischargeUnitCompensatedGridCurrentA);
					deal_.put("chargeUnitCompensatedGridCurrentA", chargeUnitCompensatedGridCurrentA);
					if (log.isInfoEnabled()) log.info("discharge unit compensated deal ig : " + dischargeUnitCompensatedGridCurrentA + ", charge unit compensated deal ig : " + chargeUnitCompensatedGridCurrentA);
					completionHandler.handle(Future.succeededFuture());
				} else {
					ErrorUtil.reportAndFail(vertx_, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.WARN, "no dcdc.meter.ig value in discharge and/or charge unit data : " + dischargeUnitData_ + ", " + chargeUnitData_, completionHandler);
				}
			} else {
				completionHandler.handle(Future.failedFuture(ar.cause()));
			}
		});
	}

	/**
	 * Perform current compensation.
	 * @param completionHandler the completion handler
	 *          
	 * 電流コンペンセイションを実行する.
	 * @param completionHandler the completion handler
	 */
	private void doCompensate_(Handler<AsyncResult<Void>> completionHandler) {
		// Fail if dcdc/failBeforeCompensate = true in the DEAL object
		// DEAL オブジェクト中に dcdc/failBeforeCompensate = true があったら fail させる
		if (testFeature_failIfNeed_("dcdc", "failBeforeCompensate", completionHandler)) return;
		// Various preparations
		// もろもろ準備
		Integer limitOfTrials = PolicyKeeping.cache().getInteger("gridMaster", "currentCompensation", "limitOfTrials");
		Float driftAllowanceA = PolicyKeeping.cache().getFloat("gridMaster", "currentCompensation", "driftAllowanceA");
		if (limitOfTrials != null && driftAllowanceA != null) {
			Float compensationTargetVoltageReferenceGridCurrentA = Deal.compensationTargetVoltageReferenceGridCurrentA(deal_);
			if (compensationTargetVoltageReferenceGridCurrentA != null) {
				if (log.isInfoEnabled()) log.info("compensationTargetVoltageReferenceGridCurrentA : " + compensationTargetVoltageReferenceGridCurrentA);
				if (DDCon.Mode.VOLTAGE_REFERENCE == masterSideUnitDDConMode_()) {
					// If the voltage reference side is a voltage reference → i.e., if one of these interchanges is a voltage reference
					// 電圧リファレンス側が電圧リファレンスなら → つまり当該融通の一方が電圧リファレンスなら
					if (masterSide_.equals(referenceSide_)) {
						// If the voltage reference side is the reference side → compensation is required
						// 電圧リファレンス側がリファレンス側なら → コンペンセイションは必要
						if ("dischargeUnit".equals(referenceSide_)) {
							// The discharging side is the reference side (i.e., the discharging side is simultaneously the voltage reference), so
							// 送電側がリファレンス側なので ( つまり同時に送電側が電圧リファレンスなので )
							// perform current compensation by designating the unit on the discharging side as the voltage reference, and the unit on the charging side as the adjuster unit.
							// 送電側ユニットを電圧リファレンスに, 受電側ユニットを調整ユニットに指定して電流コンペンセイションを実行
							// If successful, fail if dcdc/failAfterCompensate = true in the DEAL object
							// 成功した場合で DEAL オブジェクト中に dcdc/failAfterCompensate = true があったら fail させる
							new CurrentCompensationExecutor_(limitOfTrials, driftAllowanceA, compensationTargetVoltageReferenceGridCurrentA, dischargeUnitId_, chargeUnitId_, false).execute_(res -> testFeature_failIfNeed_(res, "dcdc", "failAfterCompensate", completionHandler));
						} else {
							// The charging side is the reference side (i.e., the charging side is simultaneously the voltage reference), so
							// 受電側がリファレンス側なので ( つまり同時に受電側が電圧リファレンスなので )
							// perform current compensation by designating the unit on the charging side as the voltage reference, and the unit on the discharging side as the adjuster unit.
							// 受電側ユニットを電圧リファレンスに, 送電側ユニットを調整ユニットに指定して電流コンペンセイションを実行
							// If successful, fail if dcdc/failAfterCompensate = true in the DEAL object
							// 成功した場合で DEAL オブジェクト中に dcdc/failAfterCompensate = true があったら fail させる
							new CurrentCompensationExecutor_(limitOfTrials, driftAllowanceA, compensationTargetVoltageReferenceGridCurrentA, chargeUnitId_, dischargeUnitId_, true).execute_(res -> testFeature_failIfNeed_(res, "dcdc", "failAfterCompensate", completionHandler));
						}
					} else {
						// Since one of these interchanges is a voltage reference and
						// 当該融通の一方が電圧リファレンス, かつ
						// the non-voltage-reference side is the reference side,
						// 電圧リファレンス側じゃない方がリファレンス側なら
						// the result of specifying the current on the non-voltage-reference side is "positive", so this is OK (i.e., compensation processing is not required)
						// 電圧リファレンス側じゃない方で電流を指定した結果が "正" なのでこれで OK ( つまりコンペンセイション処理は不要 )
						// Fail if dcdc/failAfterCompensate = true in the DEAL object
						// DEAL オブジェクト中に dcdc/failAfterCompensate = true があったら fail させる
						if (testFeature_failIfNeed_("dcdc", "failAfterCompensate", completionHandler)) return;
						completionHandler.handle(Future.succeededFuture());
					}
				} else {
					// If the voltage reference side is not a voltage reference → i.e., if the voltage reference is somewhere else
					// 電圧リファレンス側が電圧リファレンスでないなら → つまり電圧リファレンスは別のところにある
					JsonObject masterDeal = masterDeal_();
					// FInd the master deal...
					// master deal を見つけて...
					if (masterDeal != null) {
						if ("dischargeUnit".equals(referenceSide_)) {
							// The discharging side is the reference side, so
							// 送電側がリファレンス側なので
							// perform current compensation by designating the unit on the master deal voltage reference side as the voltage reference, and the unit on the charging side as the adjuster unit
							// master deal の電圧リファレンス側ユニットを電圧リファレンスに, 受電側ユニットを調整ユニットに指定して電流コンペンセイションを実行
							// If successful, fail if dcdc/failAfterCompensate = true in the DEAL object
							// 成功した場合で DEAL オブジェクト中に dcdc/failAfterCompensate = true があったら fail させる
							new CurrentCompensationExecutor_(limitOfTrials, driftAllowanceA, compensationTargetVoltageReferenceGridCurrentA, Deal.masterSideUnitId(masterDeal, masterSide_), chargeUnitId_, false).execute_(res -> testFeature_failIfNeed_(res, "dcdc", "failAfterCompensate", completionHandler));
						} else {
							// The charging side is the reference side, so
							// 受電側がリファレンス側なので
							// perform current compensation by designating the unit on the master deal voltage reference side as the voltage reference, and the unit on the discharging side as the adjuster unit
							// master deal の電圧リファレンス側ユニットを電圧リファレンスに, 送電側ユニットを調整ユニットに指定して電流コンペンセイションを実行
							// If successful, fail if dcdc/failAfterCompensate = true in the DEAL object
							// 成功した場合で DEAL オブジェクト中に dcdc/failAfterCompensate = true があったら fail させる
							new CurrentCompensationExecutor_(limitOfTrials, driftAllowanceA, compensationTargetVoltageReferenceGridCurrentA, Deal.masterSideUnitId(masterDeal, masterSide_), dischargeUnitId_, true).execute_(res -> testFeature_failIfNeed_(res, "dcdc", "failAfterCompensate", completionHandler));
						}
					} else {
						// There can't be no master deal, so raise a GLOBAL ERROR
						// master deal がないはずがないので GLOBAL ERROR
						ErrorUtil.reportAndFail(vertx_, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.ERROR, "no master deal found", completionHandler);
					}
				}
			} else {
				ErrorUtil.reportAndFail(vertx_, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.WARN, "no compensationTargetVoltageReferenceGridCurrentA value in deal : " + deal_, completionHandler);
			}
		} else {
			ErrorUtil.reportAndFail(vertx_, Error.Category.USER, Error.Extent.LOCAL, Error.Level.ERROR, "data deficiency; limitOfTrials : " + limitOfTrials + ", driftAllowanceA : " + driftAllowanceA + " in POLICY.gridMaster.currentCompensation values", completionHandler);
		}
	}
	/**
	 * A class that performs current compensation processing.
	 * @author OES Project
	 *          
	 * 電流コンペンセイション処理を実行するクラス.
	 * @author OES Project
	 */
	private class CurrentCompensationExecutor_ {
		private int limitOfTrials_;
		private float driftAllowanceA_;
		private float targetValue_;
		private String voltageReferenceUnitId_;
		private String adjusterUnitId_;
		private boolean minus_;
		private boolean toFail_ = false;
		/**
		 * Create an instance.
		 * @param limitOfTrials number of trial attempts
		 * @param driftAllowanceA tolerance [A]
		 * @param targetValue target value of grid current in voltage reference unit [A]
		 * @param voltageReferenceUnitId ID of voltage reference unit
		 * @param adjusterUnitId ID of adjuster unit
		 * @param minus plus/minus indicator used when adjusting the specified value
		 * TODO: Isn't the minus parameter unnecessary?
		 *       Is the adjuster unit determined by the discharging side or the charging side?
		 *       And is the voltage reference unit determined by the discharging side or the charging side?
		 *          
		 * インスタンスを生成する.
		 * @param limitOfTrials 試行回数
		 * @param driftAllowanceA 誤差 [A]
		 * @param targetValue 電圧リファレンスユニットでのグリッド電流の目標値 [A]
		 * @param voltageReferenceUnitId 電圧リファレンスユニットの ID
		 * @param adjusterUnitId 調整ユニットの ID
		 * @param minus 指定値を調整する際のプラスマイナス
		 * TODO : minus パラメタは不要なのでは ?
		 *        調整ユニットが送電側か受電側かで決まる ?
		 *        それとも電圧リファレンスユニットが送電側か受電側かで決まる ?
		 */
		private CurrentCompensationExecutor_(int limitOfTrials, float driftAllowanceA, float targetValue, String voltageReferenceUnitId, String adjusterUnitId, boolean minus) {
			limitOfTrials_ = limitOfTrials;
			driftAllowanceA_ = driftAllowanceA;
			targetValue_ = targetValue;
			voltageReferenceUnitId_ = voltageReferenceUnitId;
			adjusterUnitId_ = adjusterUnitId;
			minus_ = minus;
			if (limitOfTrials_ < 0) {
				// for test (If a negative value is specified, make it positive, repeat the test this number of times, then fail)
				// for test ( マイナス値を指定するとその絶対値回数リトライした挙句失敗するようになる )
				limitOfTrials_ = - limitOfTrials_;
				toFail_ = true;
			}
			if (log.isDebugEnabled()) log.debug("limit of trials : " + limitOfTrials_ + ", voltage reference unit : " + voltageReferenceUnitId_ + ", adjuster unit : " + adjusterUnitId_ + ", target value : " + targetValue_ + ", drift allowance : " + driftAllowanceA_ + ", minus : " + minus_ + ", to fail : " + toFail_);
		}
		/**
		 * Perform current compensation processing.
		 * @param completionHandler the completion handler
		 *          
		 * 電流コンペンセイション処理を実行する.
		 * @param completionHandler the completion handler
		 */
		private void execute_(Handler<AsyncResult<Void>> completionHandler) {
			if (log.isDebugEnabled()) log.debug("limitOfTrials_ : " + limitOfTrials_);
			// Refresh the device status of the voltage reference unit
			// 電圧リファレンスユニットのデバイス状態をリフレッシュ
			updateUnitDcdcStatus_(voltageReferenceUnitId_, resVoltageReferenceUnitDcdcStatus -> {
				if (resVoltageReferenceUnitDcdcStatus.succeeded()) {
					Float currentValue = DealExecution.unitDataCache.getFloat(voltageReferenceUnitId_, "dcdc", "meter", "ig");
					if (currentValue != null) {
						if (log.isDebugEnabled()) log.debug("voltage reference unit ig : " + currentValue + ", target : " + targetValue_ + ", allowance : " + driftAllowanceA_);
						// Calculate the difference between the measured grid current of the voltage reference unit and its target value
						// 電圧リファレンスユニットのグリッド電流の測定値と目標値の差を算出
						float diff = Math.abs(currentValue - targetValue_);
						if (log.isDebugEnabled()) log.debug("Math.abs(ig - target) : " + diff + ", allowance : " + driftAllowanceA_);
						if (diff <= driftAllowanceA_ && !toFail_) {
							// Success if the difference is within tolerable limits (and failure is not specified by the test function)
							// 差が誤差範囲以下なら ( そしてテスト機能で失敗を指定されていなければ ) 成功
							if (log.isDebugEnabled()) log.debug("OK ( " + diff + " <= " + driftAllowanceA_ + " )");
							completionHandler.handle(Future.succeededFuture());
						} else {
							// Fail if the difference is outside tolerable limits (or if failure is specified by the test function)
							//  差が誤差範囲を超えていたら ( あるいはテスト機能で失敗を指定されていれば )
							if (log.isDebugEnabled()) log.debug("NG ( " + diff + " <= " + driftAllowanceA_ + " )");
							if (0 < limitOfTrials_--) {
								// Retry up to the specified number of times
								// リトライ回数内であればリトライ
								Float dig = DealExecution.unitDataCache.getFloat(adjusterUnitId_, "dcdc", "param", "dig");
								if (dig != null) {
									if (log.isDebugEnabled()) log.debug("adjuster unit dig : " + dig);
									// Adjust the specified value of the grid current in the adjuster unit
									// 調整ユニットのグリッド電流の指定値を調節
									if (minus_) {
										dig -= currentValue - targetValue_;
									} else {
										dig += currentValue - targetValue_;
									}
									if (log.isDebugEnabled()) log.debug("adjuster unit new dig : " + dig);
									// Set up a new grid current value parameter
									// 新しいグリッド電流値をパラメタに仕込んで
									JsonObject params = new JsonObject().put("gridCurrentA", dig);
									// Issue command to change the current value in the adjuster unit
									// 調整ユニットに電流値の変更を命令
									controlDcdc_(adjusterUnitId_, "current", params, resControlDcdc -> {
										if (resControlDcdc.succeeded()) {
											// Retry
											// 再試行
											execute_(completionHandler);
										} else {
											completionHandler.handle(resControlDcdc);
										}
									});
								} else {
									ErrorUtil.reportAndFail(vertx_, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.WARN, "no dcdc.param.dig value in adjuster unit data : " + DealExecution.unitDataCache.getJsonObject(adjusterUnitId_), completionHandler);
								}
							} else {
								// Fail if the specified number of retries has been reached
								// リトライ回数に達したら失敗
								ErrorUtil.reportAndFail(vertx_, Error.Category.HARDWARE, Error.Extent.GLOBAL, Error.Level.WARN, "current compensation failed", completionHandler);
							}
						}
					} else {
						ErrorUtil.reportAndFail(vertx_, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.WARN, "no dcdc.meter.ig value in voltage reference unit data : " + DealExecution.unitDataCache.getJsonObject(voltageReferenceUnitId_), completionHandler);
					}
				} else {
					completionHandler.handle(Future.failedFuture(resVoltageReferenceUnitDcdcStatus.cause()));
				}
			});
		}
	}

}
