package jp.co.sony.csl.dcoes.apis.main.app.gridmaster.deal_execution;

import io.vertx.core.AsyncResult;
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
import jp.co.sony.csl.dcoes.apis.main.evaluation.safety.GridBranchCurrentCapacity;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorExceptionUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;
import jp.co.sony.csl.dcoes.apis.main.util.Policy;

/**
 * Launch the first voltage reference.
 * @author OES Project
 *          
 * 最初の電圧リファレンスを起動する.
 * @author OES Project
 */
public class DealActivation extends AbstractDealExecution {
	private static final Logger log = LoggerFactory.getLogger(DealActivation.class);

	private boolean isFirstDeal_ = false;
	private JsonObject currentMasterDeal_;
	private String currentVoltageReferenceUnitId_;
	private Float operationGridVoltageV_;
	private boolean willMoveVoltageReference_ = false;
	private DDCon.Mode oldVoltageReferenceNewMode_;
	private float oldVoltageReferenceGridCurrentA_ = 0F;
	private DDCon.Mode oldMode_;
	private DDCon.Mode newMode_;
	private Float dealGridCurrentA_;

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
	public DealActivation(Vertx vertx, JsonObject policy, JsonObject deal, List<JsonObject> otherDeals) {
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
	public DealActivation(AbstractDealExecution other) {
		super(other);
	}

	@Override protected void doExecute(Handler<AsyncResult<Void>> completionHandler) {
		// Decide whether to treat this as a control target or discard
		// 制御対象として扱って良いか捨てるか判断する
		String dealValidationError = validateDeal_();
		if (dealValidationError == null) {
			// Decide if the unit on the voltage reference side can be attached
			// 電圧リファレンス側ユニットをつけて良いか判断する
			if (canActivate_()) {
				// Perform various preparations
				// いろいろ準備する
				prepareActivate_(resPrepare -> {
					if (resPrepare.succeeded()) {
						// Attach the unit on the voltage reference side
						// 電圧リファレンス側のユニットをつける
						activateDcdc_(resActivateDcdc -> {
							if (resActivateDcdc.succeeded()) {
								// Put the DEAL object in the "activate" state
								// DEAL オブジェクトを activate 状態にする
								activateDeal_(resActivateDeal -> {
									if (resActivateDeal.succeeded()) {
										// Drop the original Master Deal flag if necessary
										// 必要に応じて元の Master Deal のフラグを落とす
										moveMasterDeal_(resMoveMasterDeal -> {
											if (resMoveMasterDeal.succeeded()) {
												if (isFirstDeal_) {
													// If this is the first interchange, proceed to the voltage ramp-up process
													// 最初の融通なら電圧ランプアップ処理に移行する
													new DealRampingUp(this).execute(completionHandler);
												} else {
													// For the second and subsequent interchanges, proceed to the current compensation process
													// 二番目以降の融通なら電流コンペンセイション処理に移行する
													new DealCompensation(this).execute(completionHandler);
												}
											} else {
												completionHandler.handle(resMoveMasterDeal);
											}
										});
									} else {
										completionHandler.handle(resActivateDeal);
									}
								});
							} else {
								completionHandler.handle(resActivateDcdc);
							}
						});
					} else {
						completionHandler.handle(resPrepare);
					}
				});
			} else {
				// Do nothing unless conditions are right for attaching the unit on the voltage reference side
				// 電圧リファレンス側ユニットをつけて良い状況じゃなければ何もしない
				completionHandler.handle(Future.succeededFuture());
			}
		} else {
			// If discarding, proceed to the interchange discard process
			// 捨てる場合は融通破棄処理に移行する
			new DealDispositionWithAbortReason(this, dealValidationError).execute(completionHandler);
		}
	}

	/**
	 * Decide whether or not to formally adopt an interchange as a control target.
	 * If NG, return the reason.
	 * @return If NG, a character string explaining the reason why, or {@code null} if accepted
	 *          
	 * 融通を制御対象として正式に採用するか否か判定する.
	 * NG なら理由を返す.
	 * @return NG なら NG 理由の文字列. OK なら {@code null}
	 */
	private String validateDeal_() {
		// Check the reset count limit
		// リセット回数制限をチェック
		String error = checkNumberOfResets_();
		if (error == null) {
			// Check the direction of the interchange
			// 融通方向をチェック
			error = checkDealDirection_();
		}
		if (error == null) {
			// Check the current capacity
			// 電流容量をチェック
			error = checkCurrentCapacity_();
		}
		return error;
	}
	/**
	 * Check the number of times a DEAL object has been reset.
	 * Return an error string if the upper limit is exceeded.
	 * @return an error string if the limit has been exceeded, or {@code null} if accepted
	 *          
	 * DEAL オブジェクトのリセット回数をチェックする.
	 * 上限を超えていたらエラー文字列を返す.
	 * @return Ng ならエラー文字列. OK なら {@code null}
	 */
	private String checkNumberOfResets_() {
		// Maximum number of resets: POLICY.gridMaster.deal.resetLimit
		// リセット上限回数 : POLICY.gridMaster.deal.resetLimit
		Integer resetLimit_ = PolicyKeeping.cache().getInteger("gridMaster", "deal", "resetLimit");
		if (resetLimit_ != null) {
			if (resetLimit_ <= Deal.numberOfResets(deal_)) {
				String msg = "deal reset limit exceeded : " + resetLimit_;
				ErrorUtil.report(vertx_, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.WARN, msg);
				return msg;
			}
		} else {
			String msg = "data deficiency; POLICY.gridMaster.deal.resetLimit : " + PolicyKeeping.cache().getValue("gridMaster", "deal", "resetLimit");
			ErrorUtil.report(vertx_, Error.Category.USER, Error.Extent.LOCAL, Error.Level.ERROR, msg);
			return msg;
		}
		return null;
	}
	/**
	 * Check the direction of an interchange.
	 * Return an error string if NG.
	 * @return An error string if NG, or {@code null} if successful
	 *          
	 * 融通の方向をチェックする.
	 * NG ならエラー文字列を返す.
	 * @return NG ならエラー文字列. OK なら {@code null}
	 */
	private String checkDealDirection_() {
		for (JsonObject aDeal : otherDeals_) {
			// For all other interchanges
			// 他の融通全てについて
			if (Deal.masterSideUnitMustBeActive(aDeal)) {
				// For interchanges where at least one unit is operating
				// 少なくとも一方のユニットが起動している融通について
				if (Deal.isChargeUnit(aDeal, dischargeUnitId_)) {
					// NG if the charging side of this interchange is also the discharging side
					// その融通の受電側がこの融通の送電側なら NG
					String msg = "other charge side deal found on discharge unit : " + dischargeUnitId_ + " ; other deal : " + aDeal;
					if (log.isInfoEnabled()) log.info(msg);
					return msg;
				}
				if (Deal.isDischargeUnit(aDeal, chargeUnitId_)) {
					// NG if the discharging side of this interchange is also the charging side
					// その融通の送電側がこの融通の受電側なら NG
					String msg = "other discharge side deal found on charge unit : " + chargeUnitId_ + " ; other deal : " + aDeal;
					if (log.isInfoEnabled()) log.info(msg);
					return msg;
				}
			}
		}
		return null;
	}
	/**
	 * Check the current capacity.
	 * Return an error string if NG.
	 * Judgement is made based on {@link GridBranchCurrentCapacity#checkNewDeal(Vertx, JsonObject, JsonObject, List)}.
	 * @return An error string if NG, or {@code null} if accepted
	 *          
	 * 電流容量をチェックする.
	 * NG ならエラー文字列を返す.
	 * 判定は {@link GridBranchCurrentCapacity#checkNewDeal(Vertx, JsonObject, JsonObject, List)} で.
	 * @return NG ならエラー文字列. OK なら {@code null}
	 */
	private String checkCurrentCapacity_() {
		return GridBranchCurrentCapacity.checkNewDeal(vertx_, policy_, deal_, otherDeals_);
	}

	private boolean canActivate_() {
		// Determine whether or not this is the first interchange
		// 最初の融通かどうかを判定しておく
		isFirstDeal_ = isFirstDeal_();
		return (isFirstDeal_) ? canActivateFirstDeal_() : canActivateNonFirstDeal_();
	}
	private boolean isFirstDeal_() {
		for (JsonObject aDeal : otherDeals_) {
			if (Deal.masterSideUnitMustBeActive(aDeal)) {
				// False if any other interchange has already been started
				// 他に一つでも起動済みの融通があったら false
				return false;
			}
		}
		return true;
	}
	/**
	 * Determine if the first interchange can be launched.
	 * @return {@code true} if yes
	 *          
	 * 最初の融通を起動して良いか判定する.
	 * @return OK なら {@code true}
	 */
	private boolean canActivateFirstDeal_() {
		Float gridVoltageV = JsonObjectUtil.getFloat(masterSideUnitData_(), "dcdc", "meter", "vg");
		if (gridVoltageV != null) {
			Float minOperationGridVoltageV = PolicyKeeping.cache().getFloat("operationGridVoltageVRange", "min");
			Float gridVoltageSeparationV = PolicyKeeping.cache().getFloat("gridVoltageSeparationV");
			Float gridUvloMaskV = PolicyKeeping.cache().getFloat("gridUvloMaskV");
			if (minOperationGridVoltageV != null && gridVoltageSeparationV != null && gridUvloMaskV != null) {
				float gridVoltageMaxV = minOperationGridVoltageV + (gridVoltageSeparationV * 2F);
				if (gridVoltageV <= gridVoltageMaxV) {
					// Present grid voltage is no larger than POLICY.operationGridVoltageVRange.min + POLICY.gridVoltageSeparationV × 2
					// 現在のグリッド電圧が POLICY.operationGridVoltageVRange.min + POLICY.gridVoltageSeparationV x 2 以下で
					float maskMinV = minOperationGridVoltageV - gridUvloMaskV;
					float maskMaxV = minOperationGridVoltageV + gridUvloMaskV;
					if (gridVoltageV < maskMinV || maskMaxV < gridVoltageV) {
						// OK if less than POLICY.operationGridVoltageVRange.min - POLICY.gridUvloMaskV or greater than POLICY.operationGridVoltageVRange.min + POLICY.gridUvloMaskV
						// POLICY.operationGridVoltageVRange.min - POLICY.gridUvloMaskV 未満 または POLICY.operationGridVoltageVRange.min + POLICY.gridUvloMaskV を超えていたら OK
						return true;
					} else {
						// NG if in the range from POLICY.operationGridVoltageVRange.min - POLICY.gridUvloMaskV to POLICY.operationGridVoltageVRange.min + POLICY.gridUvloMaskV
						// POLICY.operationGridVoltageVRange.min - POLICY.gridUvloMaskV 以上 POLICY.operationGridVoltageVRange.min + POLICY.gridUvloMaskV 以下なら NG
						if (log.isInfoEnabled()) log.info("can not start voltage reference yet ( grid voltage : " + gridVoltageV + ", should be lower than : " + maskMinV + " or greater than : " + maskMaxV + " ) ...");
					}
				} else {
					// GLOBAL ERROR if the present grid voltage exceeds POLICY.operationGridVoltageVRange.min + POLICY.gridVoltageSeparationV × 2
					// 現在のグリッド電圧が POLICY.operationGridVoltageVRange.min + POLICY.gridVoltageSeparationV x 2 を超えていたら GLOBAL ERROR
					ErrorUtil.report(vertx_, Error.Category.HARDWARE, Error.Extent.GLOBAL, Error.Level.ERROR, "invalid grid voltage : " + gridVoltageV + ", should be lower than or equal to : " + gridVoltageMaxV);
				}
			} else {
				ErrorUtil.report(vertx_, Error.Category.USER, Error.Extent.LOCAL, Error.Level.ERROR, "data deficiency ; POLICY.operationGridVoltageVRange.min : " + minOperationGridVoltageV + "POLICY.gridVoltageSeparationV : " + gridVoltageSeparationV + ", POLICY.gridUvloMaskV : " + gridUvloMaskV);
			}
		} else {
			ErrorUtil.report(vertx_, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.WARN, "no dcdc.meter.vg value in master side unit data : " + masterSideUnitData_());
		}
		return false;
	}
	/**
	 * Determine if the second and subsequent interchanges can be launched.
	 * @return {@code true} if yes
	 *          
	 * 二番目以降の融通を起動して良いか判定する.
	 * @return OK なら {@code true}
	 */
	private boolean canActivateNonFirstDeal_() {
		for (JsonObject aDeal : otherDeals_) {
			if (Deal.isStarted(aDeal) && !Deal.isDeactivated(aDeal)) {
				// OK if there is at least one other interchange that has been started and not deactivated
				// 他の融通の中で「start 済みで deactivate していない」ものが一つでもあれば OK
				return true;
			}
		}
		if (log.isInfoEnabled()) log.info("can not start master side unit yet ( other deal should be ramping up ) ...");
		// NG if there are none --- either this is the first interchange or voltage ramp-up is still in progress
		// 一つもなければ「最初の融通がまだ電圧ランプアップ中」のはずなので NG
		return false;
	}

	/**
	 * Perform various preparations for start-up.
	 * @param completionHandler the completion handler
	 *          
	 * 起動に向けていろいろ準備する.
	 * @param completionHandler the completion handler
	 */
	private void prepareActivate_(Handler<AsyncResult<Void>> completionHandler) {
		// Update the control state of the voltage reference to record the data required for current compensation
		// 電流コンペンセイションに必要なデータを記録しておくため電圧リファレンスの制御状態を最新にする
		prepareVoltageReference_(resPrepareVoltageReference -> {
			if (resPrepareVoltageReference.succeeded()) {
				// Perform judgments and preparations necessary for switching around the voltage reference side, moving the voltage reference, etc.
				// 電圧リファレンス側を反転したり電圧リファレンスを移動したりするための判定や準備をする
				prepareFlipAndMove_();
				// Prepare the mode of the controlled unit
				// 制御対象ユニットのモードを準備する
				prepareMode_();
				if (willMoveVoltageReference_) {
					// If the voltage reference is to be moved,
					// 電圧リファレンスを移動するなら
					// determine the grid voltage setting at the destination
					// 移動先でのグリッド電圧の設定値を決める
					String voltageReferenceTakeOverDvg = Policy.voltageReferenceTakeOverDvg(policy_);
					if ("theoretical".equals(voltageReferenceTakeOverDvg)) {
						// Record the setting (dvg) at the source unit
						// 移動元ユニットでの設定値 ( dvg ) を記録しておく
						operationGridVoltageV_ = DealExecution.unitDataCache.getFloat(currentVoltageReferenceUnitId_, "dcdc", "vdis", "dvg");
						if (operationGridVoltageV_ == null) {
							ErrorUtil.reportAndFail(vertx_, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.WARN, "no dcdc.vdis.dvg value in voltage reference unit data : " + DealExecution.unitDataCache.getJsonObject(currentVoltageReferenceUnitId_), completionHandler);
							return;
						}
					}
					// No problem if null is sent during non-theoretical (i.e., actual) control -- the measured value (vg) will be specified at the other end.
					// theoretical でなければ ( つまり actual なら ) 制御時に null を送っておけば勝手に向こうで測定値 ( vg ) を指定してくれるのでヨシ!
				}
				dealGridCurrentA_ = Deal.dealGridCurrentA(deal_);
				if (dealGridCurrentA_ != null) {
					if (isFirstDeal_ || (willMoveVoltageReference_ && DDCon.Mode.WAIT == masterSideUnitDDConMode_())) {
						// When the first interchange or voltage reference movement occurs, and the voltage reference side is in a WAIT state (i.e., WAIT → VR control)
						// 最初の融通または電圧リファレンス移動が起きしかも電圧リファレンス側が WAIT である ( つまり WAIT → VR の制御となる ) 場合
						float result = 0;
						if ("dischargeUnit".equals(masterSide_)) {
							result = - dealGridCurrentA_;
						} else {
							result = dealGridCurrentA_;
						}
						// The current compensation target value is the interchange current itself (the negative sign differs between the discharging and charging sides)
						// 電流コンペンセイションのターゲット値は融通電流そのもの ( 送電側か受電側で負号は異なる )
						deal_.put("compensationTargetVoltageReferenceGridCurrentA", result);
						if (log.isInfoEnabled()) log.info("compensationTargetVoltageReferenceGridCurrentA ( +/- dealGridCurrentA ) : " + result);
						completionHandler.handle(Future.succeededFuture());
					} else {
						// If this is neither the first interchange nor subject to WAIT → VR control
						// 最初の融通ではなく WAIT → VR の制御でもない場合
						final String targetVoltageReferenceUnitId = (willMoveVoltageReference_) ? masterSideUnitId_() : currentVoltageReferenceUnitId_;
						if (targetVoltageReferenceUnitId != null) {
							if (targetVoltageReferenceUnitId.equals(masterSideUnitId_())) {
								// If the voltage reference moves
								// 電圧リファレンスが移動してくる場合
								Float masterSideGridCurrentA = JsonObjectUtil.getFloat(masterSideUnitData_(), "dcdc", "meter", "ig");
								if (masterSideGridCurrentA != null) {
									float result = masterSideGridCurrentA;
									if ("dischargeUnit".equals(masterSide_)) {
										result -= dealGridCurrentA_;
									} else {
										result += dealGridCurrentA_;
									}
									// The current compensation target value is obtained by adding the present ig value of the unit on the voltage reference side of this interchange to the interchange current (the negative sign differs between the discharging and charging sides)
									// 電流コンペンセイションのターゲット値は当該融通の電圧リファレンス側ユニットの現在の ig 値に融通電流 ( 送電側か受電側で負号は異なる ) を加算したもの
									deal_.put("compensationTargetVoltageReferenceGridCurrentA", result);
									if (log.isInfoEnabled()) log.info("compensationTargetVoltageReferenceGridCurrentA ( masterSideGridCurrentA +/- dealGridCurrentA ) : " + masterSideGridCurrentA + " , " + dealGridCurrentA_ + " , " + result);
									completionHandler.handle(Future.succeededFuture());
								} else {
									ErrorUtil.reportAndFail(vertx_, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.WARN, "no dcdc.meter.ig value in master side unit data : " + masterSideUnitData_(), completionHandler);
								}
							} else {
								// If the voltage reference does not move
								// 電圧リファレンスが移動してこない場合
								Float targetVoltageReferenceUnitGridCurrentA = DealExecution.unitDataCache.getFloat(targetVoltageReferenceUnitId, "dcdc", "meter", "ig");
								if (targetVoltageReferenceUnitGridCurrentA != null) {
									float result = targetVoltageReferenceUnitGridCurrentA;
									// The current compensation target value is just the present ig value of the present voltage reference unit
									// 電流コンペンセイションのターゲット値は現在の電圧リファレンスユニットの現在の ig 値そのもの
									deal_.put("compensationTargetVoltageReferenceGridCurrentA", result);
									if (log.isInfoEnabled()) log.info("compensationTargetVoltageReferenceGridCurrentA ( targetVoltageReferenceUnitGridCurrentA ) : " + result);
									completionHandler.handle(Future.succeededFuture());
								} else {
									ErrorUtil.reportAndFail(vertx_, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.WARN, "no dcdc.meter.ig value in voltage reference unit data : " + DealExecution.unitDataCache.getJsonObject(targetVoltageReferenceUnitId), completionHandler);
								}
							}
						} else {
							ErrorUtil.reportAndFail(vertx_, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.ERROR, "no voltage reference found", completionHandler);
						}
					}
				} else {
					ErrorUtil.reportAndFail(vertx_, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.WARN, "no dealGridCurrentA value in deal : " + deal_, completionHandler);
				}
			} else {
				completionHandler.handle(resPrepareVoltageReference);
			}
		});
	}
	/**
	 * Update the control state of the voltage reference.
	 * @param completionHandler the completion handler
	 *          
	 * 電圧リファレンスの制御状態を最新にする.
	 * @param completionHandler the completion handler
	 */
	private void prepareVoltageReference_(Handler<AsyncResult<Void>> completionHandler) {
		// Find the unit ID of the voltage reference
		// 電圧リファレンスのユニット ID を探しておく
		currentVoltageReferenceUnitId_ = DealExecution.voltageReferenceUnitId();
		if (currentVoltageReferenceUnitId_ != null) {
			// If a voltage reference already exists in the cluster
			// クラスタ上にすでに電圧リファレンスが存在する場合
			if (log.isInfoEnabled()) log.info("voltage reference unit : " + currentVoltageReferenceUnitId_);
			if (Deal.isInvolved(deal_, currentVoltageReferenceUnitId_)) {
				// If either of these interchanges is already a voltage reference
				// この融通のどちらかがすでに電圧リファレンスの場合
				if (log.isDebugEnabled()) log.debug("no need to update voltage reference unit status");
				// No need to do anything because the data of both units was updated at the start of interchange processing
				// 融通処理の最初に両ユニットのデータを更新したので何もする必要がない
				completionHandler.handle(Future.succeededFuture());
			} else {
				// If neither unit of this interchange is a voltage reference
				// この融通の両ユニットとも電圧リファレンスではない場合
				if (log.isDebugEnabled()) log.debug("update voltage reference unit status...");
				// Update the cached device control state of the voltage reference unit
				// 電圧リファレンスユニットのデバイス制御状態のキャッシュを更新しておく
				updateUnitDcdcStatus_(currentVoltageReferenceUnitId_, completionHandler);
			}
		} else {
			// If there is no voltage reference anywhere yet
			// まだどこにも電圧リファレンスが存在しない場合
			if (log.isInfoEnabled()) log.info("no voltage reference unit found");
			// No need to do anything
			// なにもする必要がない
			completionHandler.handle(Future.succeededFuture());
		}
	}
	/**
	 * Perform judgments and preparations necessary for switching around the voltage reference side, moving the voltage reference, etc.
	 *          
	 * 電圧リファレンス側を反転したり電圧リファレンスを移動したりするための判定や準備をする.
	 */
	private void prepareFlipAndMove_() {
		List<String> largeCapacityUnitIds = Policy.largeCapacityUnitIds(policy_);
		if (largeCapacityUnitIds != null && !largeCapacityUnitIds.isEmpty()) {
			// If there is a large capacity unit setting
			// 大容量ユニットの設定がある場合
			if (isFirstDeal_) {
				// If this is the first interchange
				// 最初の融通の場合
				if (!largeCapacityUnitIds.contains(masterSideUnitId_()) && largeCapacityUnitIds.contains(slaveSideUnitId_())) {
					// If either unit is a large capacity unit
					// どちらかのユニットが大容量ユニットの場合
					// Decide whether or not to switch around the voltage reference side
					// 電圧リファレンス側を反転させるかどうか判定する
					if (tryToFlipMasterSide_()) {
						if (log.isInfoEnabled()) log.info("master side was flipped : " + masterSide_);
					} else {
						if (log.isWarnEnabled()) log.warn("could not flip master side");
					}
				}
			} else {
				// For the second and subsequent interchanges
				// 二番目以降の融通の場合
				if (currentVoltageReferenceUnitId_ != null && !largeCapacityUnitIds.contains(currentVoltageReferenceUnitId_)) {
					// If the present voltage reference is not a large capacity unit
					// 現在の電圧リファレンスが大容量ユニットではない場合
					if (log.isInfoEnabled()) log.info("voltage reference unit is not a large capacity unit");
					if (largeCapacityUnitIds.contains(masterSideUnitId_())) {
						// If the unit on the voltage reference side of this interchange is a large capacity unit
						// この融通の電圧リファレンス側ユニットが大容量ユニットの場合
						// The voltage reference should be moved
						// 電圧リファレンスの移動が発生します
						willMoveVoltageReference_ = true;
						if (log.isInfoEnabled()) log.info("will move voltage reference");
					} else if (largeCapacityUnitIds.contains(slaveSideUnitId_())) {
						// If the unit on the voltage reference side of this interchange is not a large capacity unit
						// この融通の電圧リファレンス側ユニットが大容量ユニットではなく
						// but the unit not on the voltage reference side of this interchange is
						// この融通の電圧リファレンス側ではない方のユニットが大容量ユニットの場合
						// Decide whether or not to switch around the voltage reference side
						// 電圧リファレンス側を反転させるかどうか判定する
						if (tryToFlipMasterSide_()) {
							if (log.isInfoEnabled()) log.info("master side was flipped : " + masterSide_);
							// Even if they can be switched
							// 反転できた場合にも
							// The voltage reference should be moved
							// 電圧リファレンスの移動が発生します
							willMoveVoltageReference_ = true;
							if (log.isInfoEnabled()) log.info("will move voltage reference");
						} else {
							if (log.isWarnEnabled()) log.warn("could not flip master side");
						}
					}
				} else {
					// If there is not yet any voltage reference, or if the present voltage reference is a large capacity unit,
					// まだ電圧リファレンスが存在しない, もしくは現在の電圧リファレンスが大容量ユニットである場合
					// do nothing
					// なにもしない
					if (log.isInfoEnabled()) log.info("no voltage reference unit or voltage reference unit is a large capacity unit");
				}
				if (willMoveVoltageReference_) {
					// If the voltage reference is to be moved
					// 電圧リファレンスの移動が発生することになった場合
					// Remember the present master deal
					// 現在の master deal を覚えておく
					currentMasterDeal_ = masterDeal_();
					// Decide on a new mode for the present voltage reference (i.e. the mode to apply after moving the voltage reference)
					// 現在の電圧リファレンスの新しいモード ( 電圧リファレンス移動後のモード ) を決めておく
					for (JsonObject aDeal : otherDeals_(currentVoltageReferenceUnitId_)) {
						// For all other interchanges
						// 他の融通全てについて
						if (oldVoltageReferenceNewMode_ == null || Deal.slaveSideUnitMustBeActive(aDeal)) {
							// If there is no candidate yet, or if the unit on the non-voltage-reference side is active (the result is the same, but may occur when the units at both ends are active)
							// まだ候補が出ていない若くは電圧リファレンスでない側が点いている場合 ( これ結果は同じだけど "両端ユニットが点いている場合" なのかもしれない )
							// → Prioritize interchanges for which control has already started, but if there are none, begin with an interchange that has not yet started
							// → もう制御が始まっている融通が優先だけど無ければそうでない融通から判定する ということ
							if (Deal.isDischargeUnit(aDeal, currentVoltageReferenceUnitId_)) {
								// DISCHARGE if the present voltage reference is the unit on the discharging side
								// 現在の電圧リファレンスが送電側ユニットの場合 DISCHARGE
								oldVoltageReferenceNewMode_ = DDCon.Mode.DISCHARGE;
							} else {
								// CHARGE if the present voltage reference is the unit on charging side
								// 現在の電圧リファレンスが受電側ユニットの場合 CHARGE
								oldVoltageReferenceNewMode_ = DDCon.Mode.CHARGE;
							}
						}
					}
					Float currentVoltageReferenceIg = DealExecution.unitDataCache.getFloat(currentVoltageReferenceUnitId_, "dcdc", "meter", "ig");
					if (oldVoltageReferenceNewMode_ != null && currentVoltageReferenceIg != null) {
						// If a new mode is determined for the present voltage reference, record the grid current readings for this unit
						// 現在の電圧リファレンスの新しいモードが決まっていたらそのユニットでのグリッド電流測定値を記録しておく
						oldVoltageReferenceGridCurrentA_ = currentVoltageReferenceIg;
						if (log.isInfoEnabled()) log.info("old voltage reference will be : " + oldVoltageReferenceNewMode_ + " ( " + oldVoltageReferenceGridCurrentA_ + " )");
					} else {
						if (log.isWarnEnabled()) log.warn("failed to move voltage reference ; oldVoltageReferenceNewMode_ : " + oldVoltageReferenceNewMode_ + ", oldVoltageReferenceGridCurrentA_ : " + oldVoltageReferenceGridCurrentA_);
					}
				}
			}
		}
	}
	/**
	 * Perform mode preparation.
	 *          
	 * モードを準備する.
	 */
	private void prepareMode_() {
		// First record the present mode
		// まず現在のモードを記録する
		oldMode_ = masterSideUnitDDConMode_();
		if (isFirstDeal_ || willMoveVoltageReference_) {
			// VOLTAGE_REFERENCE if this is the first interchange or the voltage reference has to be moved
			// 最初の融通か電圧リファレンスの移動が発生する場合は VOLTAGE_REFERENCE
			newMode_ = DDCon.Mode.VOLTAGE_REFERENCE;
		} else {
			if (DDCon.Mode.WAIT == oldMode_) {
				// If currently in WAIT mode
				// 現在 WAIT なら
				// DISCHARGE if this is the discharging side, or CHARGE if this is the charging side
				// 送電側なら DISCHARGE 受電側なら CHARGE
				newMode_ = ("dischargeUnit".equals(masterSide_)) ? DDCon.Mode.DISCHARGE : DDCon.Mode.CHARGE;
			} else {
				// Leave unchanged if neither applies (should be DISCHARGE or CHARGE)
				// どちらでもない ( DISCHARGE か CHARGE のはず ) ならそのまま
				newMode_ = oldMode_;
			}
		}
		if (log.isInfoEnabled()) log.info("old mode : " + oldMode_ + ", new mode : " + newMode_);
	}

	/**
	 * Control the unit on the voltage reference side.
	 * @param completionHandler the completion handler
	 *          
	 * 電圧リファレンス側のユニットを制御する.
	 * @param completionHandler the completion handler
	 */
	private void activateDcdc_(Handler<AsyncResult<Void>> completionHandler) {
		// Fail if dcdc/failBeforeActivate = true in a DEAL object
		// DEAL オブジェクト中に dcdc/failBeforeActivate = true があったら fail させる
		if (testFeature_failIfNeed_("dcdc", "failBeforeActivate", completionHandler)) return;
		if (willMoveVoltageReference_) {
			// If moving the voltage reference
			// 電圧リファレンスを移動する場合
			if (log.isInfoEnabled()) log.info("move voltage reference");
			// Send voltageReferenceWillHandOver to the source unit
			// 移動元ユニットに voltageReferenceWillHandOver
			controlDcdc_(currentVoltageReferenceUnitId_, "voltageReferenceWillHandOver", null, resWillHandOver -> {
				if (resWillHandOver.succeeded()) {
					// Set up operationGridVoltageV_ determined in prepareActivate_() as a new dvg in the parameters...
					// prepareActivate_() で決めた operationGridVoltageV_ を新しい dvg としてパラメタに仕込んで...
					JsonObject params = new JsonObject().put("gridVoltageV", operationGridVoltageV_);
					// Set the destination unit to voltageReferenceWillTakeOver
					// 移動先ユニットに voltageReferenceWillTakeOver
					controlMasterSideUnitDcdc_("voltageReferenceWillTakeOver", params, resWillTakeOver -> {
						if (resWillTakeOver.succeeded()) {
							if (log.isInfoEnabled()) log.info("new voltage reference started, retire old voltage reference");
							// Set up oldVoltageReferenceNewMode_ and oldVoltageReferenceGridCurrentA_ determined in prepareActivate_() as parameters...
							// prepareActivate_() で決めた oldVoltageReferenceNewMode_ と oldVoltageReferenceGridCurrentA_ をパラメタに仕込んで...
							params.clear().put("mode", oldVoltageReferenceNewMode_.name()).put("gridCurrentA", Math.abs(oldVoltageReferenceGridCurrentA_));
							// Set the destination unit to voltageReferenceDidHandOver
							// 移動元ユニットに voltageReferenceDidHandOver
							controlDcdc_(currentVoltageReferenceUnitId_, "voltageReferenceDidHandOver", params, resDidHandOver -> {
								if (resDidHandOver.succeeded()) {
									// Set the destination unit to voltageReferenceDidTakeOver
									// 移動先ユニットに voltageReferenceDidTakeOver
									// Fail if dcdc/failBeforeActivate = true in the DEAL object on success
									// 成功した場合に DEAL オブジェクト中に dcdc/failBeforeActivate = true があったら fail させる
									controlMasterSideUnitDcdc_("voltageReferenceDidTakeOver", null, res -> testFeature_failIfNeed_(res, "dcdc", "failAfterActivate", completionHandler));
								} else {
									completionHandler.handle(resDidHandOver);
								}
							});
						} else {
							completionHandler.handle(resWillTakeOver);
						}
					});
				} else {
					completionHandler.handle(resWillHandOver);
				}
			});
		} else {
			// If the voltage reference is not moved
			// 電圧リファレンスを移動しない場合
			if (DDCon.Mode.VOLTAGE_REFERENCE == oldMode_) {
				// If this was originally a voltage reference
				// もともと電圧リファレンスだった場合
				if (log.isInfoEnabled()) log.info("no need to control voltage reference");
				// Fail if dcdc/failAfterActivate = true in the DEAL object
				// DEAL オブジェクト中に dcdc/failAfterActivate = true があったら fail させる
				if (testFeature_failIfNeed_("dcdc", "failAfterActivate", completionHandler)) return;
				// No need to do anything
				// 何もする必要なし
				completionHandler.handle(Future.succeededFuture());
			} else if (DDCon.Mode.WAIT == oldMode_) {
				// If the source unit was in WAIT mode
				// 元が WAIT だった場合
				if (log.isInfoEnabled()) log.info("start master side unit");
				// Set up the interchange current value of this interchange in the parameter
				// 当該融通の融通電流値をパラメタに仕込んで
				JsonObject params = new JsonObject().put("gridCurrentA", dealGridCurrentA_);
				// Command the unit on the voltage reference side to switch to newMode_ as determined by prepareMode_()
				// 電圧リファレンス側ユニットに prepareMode_() で決めた newMode_ を命令
				// If successful, fail if dcdc/failAfterActivate = true in the DEAL object
				// 成功した場合に DEAL オブジェクト中に dcdc/failAfterActivate = true があったら fail させる
				controlMasterSideUnitDcdc_(newMode_.name(), params, res -> testFeature_failIfNeed_(res, "dcdc", "failAfterActivate", completionHandler));
			} else {
				// If this is neither a voltage reference nor in WAIT mode (should be DISCHARGE or CHARGE)
				// もともとが電圧リファレンスでも WAIT でもない ( DISCHARGE か CHARGE のはず ) 場合
				// Calculate the new current value
				// 新しい電流値を算出し
				float newDig = Math.abs(sumOfOtherDealCompensatedGridCurrentAs_(masterSideUnitId_())) + dealGridCurrentA_;
				if (log.isInfoEnabled()) log.info("dig : " + JsonObjectUtil.getFloat(masterSideUnitData_(), "dcdc", "param", "dig") + " -> " + newDig);
				// Set up the new grid current value parameter
				// 新しい電流値をパラメタに仕込んで
				JsonObject params = new JsonObject().put("gridCurrentA", newDig);
				// Issue command to change the current value in the unit on the voltage reference side
				// 電圧リファレンス側ユニットに電流値の変更を命令
				// If successful, fail if dcdc/failAfterActivate = true in the DEAL object
				// 成功した場合に DEAL オブジェクト中に dcdc/failAfterActivate = true があったら fail させる
				controlMasterSideUnitDcdc_("current", params, res -> testFeature_failIfNeed_(res, "dcdc", "failAfterActivate", completionHandler));
			}
		}
	}

	/**
	 * Move the master deal marker
	 * @param completionHandler the completion handler
	 *          
	 * master deal の目印を移動する.
	 * @param completionHandler the completion handler
	 */
	private void moveMasterDeal_(Handler<AsyncResult<Void>> completionHandler) {
		if (willMoveVoltageReference_) {
			// If the voltage reference is moved
			// 電圧リファレンスを移動した場合
			// Remove the master deal marker from the source DEAL object
			// 移動元の DEAL オブジェクトから master deal の印を外す
			DealUtil.isMaster(vertx_, currentMasterDeal_, false, res -> ErrorExceptionUtil.reportIfNeedAndHandle(vertx_, res, completionHandler));
		} else {
			completionHandler.handle(Future.succeededFuture());
		}
	}

	/**
	 * Put the interchange information in the "activate complete" state
	 * @param completionHandler the completion handler
	 *          
	 * 融通情報を activate 完了状態にする.
	 * @param completionHandler the completion handler
	 */
	private void activateDeal_(Handler<AsyncResult<Void>> completionHandler) {
		DealUtil.activate(vertx_, deal_, referenceDateTimeString_(), resActivateDeal -> {
			if (resActivateDeal.succeeded()) {
				// If the first interchange or a voltage reference is moved
				// 最初の融通か電圧リファレンスを移動した場合
				// Make this DEAL object the master deal
				// 当該 DEAL オブジェクトを master deal にする
				DealUtil.isMaster(vertx_, deal_, isFirstDeal_ || willMoveVoltageReference_, resIsMaster -> ErrorExceptionUtil.reportIfNeedAndHandle(vertx_, resIsMaster, completionHandler));
			} else {
				ErrorExceptionUtil.reportIfNeedAndFail(vertx_, resActivateDeal.cause(), completionHandler);
			}
		});
	}

}
