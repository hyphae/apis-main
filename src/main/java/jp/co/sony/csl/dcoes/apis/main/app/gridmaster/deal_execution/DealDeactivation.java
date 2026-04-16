package jp.co.sony.csl.dcoes.apis.main.app.gridmaster.deal_execution;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;

import jp.co.sony.csl.dcoes.apis.common.Deal;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectUtil;
import jp.co.sony.csl.dcoes.apis.main.app.controller.util.DDCon;
import jp.co.sony.csl.dcoes.apis.main.app.gridmaster.main_loop.DealExecution;
import jp.co.sony.csl.dcoes.apis.main.app.mediator.util.DealUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorExceptionUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;
import jp.co.sony.csl.dcoes.apis.main.util.Policy;

/**
 * End an interchange.
 * @author OES Project
 *          
 * 融通を終了する.
 * @author OES Project
 */
public class DealDeactivation extends AbstractDealExecution {
	private static final Logger log = LoggerFactory.getLogger(DealDeactivation.class);

	private boolean isMaster_;
	private Float operationGridVoltageV_;
	private JsonObject newMasterDeal_;
	private String newVoltageReferenceUnitId_;
	private boolean masterSideWillBeFlipped_ = false;

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
	public DealDeactivation(Vertx vertx, JsonObject policy, JsonObject deal, List<JsonObject> otherDeals) {
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
	public DealDeactivation(AbstractDealExecution other) {
		super(other);
	}

	@Override protected void doExecute(Handler<AsyncResult<Void>> completionHandler) {
		if (DDCon.Mode.WAIT != masterSideUnitDDConMode_()) {
			prepareDeactivate_(resPrepare -> {
				if (resPrepare.succeeded()) {
					// Stop the unit on the voltage reference side
					// 電圧リファレンス側のユニットを止める
					deactivateDcdc_(resDeactivateDcdc -> {
						if (resDeactivateDcdc.succeeded()) {
							// Drop the original Master Deal flag if necessary
							// 必要に応じて元の Master Deal のフラグを落とす
							moveMasterDeal_(resMoveMasterDeal -> {
								if (resMoveMasterDeal.succeeded()) {
									// Put the DEAL object in the "deactivate" state
									// DEAL オブジェクトを deactivate 状態にする
									deactivateDeal_(resDeactivateDeal -> {
										if (resDeactivateDeal.succeeded()) {
											// Proceed to the interchange information deletion process
											// 融通情報削除処理に移行する
											new DealDisposition(this).doExecute(completionHandler);
										} else {
											completionHandler.handle(resDeactivateDeal);
										}
									});
								} else {
									completionHandler.handle(resMoveMasterDeal);
								}
							});
						} else {
							completionHandler.handle(resDeactivateDcdc);
						}
					});
				} else {
					completionHandler.handle(resPrepare);
				}
			});
		} else {
			deactivateDeal_(resDeactivateDeal -> {
				if (resDeactivateDeal.succeeded()) {
					new DealDisposition(this).doExecute(completionHandler);
				} else {
					completionHandler.handle(resDeactivateDeal);
				}
			});
		}
	}

	/**
	 * Perform various preparations for deactivation.
	 * @param completionHandler the completion handler
	 *          
	 * 停止に向けていろいろ準備する.
	 * @param completionHandler the completion handler
	 */
	private void prepareDeactivate_(Handler<AsyncResult<Void>> completionHandler) {
		isMaster_ = Deal.isMaster(deal_);
		if (isMaster_) {
			// If this is the master deal, choose another interchange to be designated in its place
			// master deal なら別の融通を master deal にする必要があるので決める
			newMasterDeal_ = newMasterDeal_();
			if (newMasterDeal_ != null) {
				if (log.isInfoEnabled()) log.info("need to move master deal; new master deal : " + newMasterDeal_);
				// Since the voltage reference might move
				// 電圧リファレンスを移動するかもしれないので
				// determine the grid voltage setting at the destination
				// 移動先でのグリッド電圧の設定値を決める
				String voltageReferenceTakeOverDvg = Policy.voltageReferenceTakeOverDvg(policy_);
				if ("theoretical".equals(voltageReferenceTakeOverDvg)) {
					// Record the setting (dvg) at the source unit
					// 移動元ユニットでの設定値 ( dvg ) を記録しておく
					operationGridVoltageV_ = JsonObjectUtil.getFloat(masterSideUnitData_(), "dcdc", "vdis", "dvg");
					if (operationGridVoltageV_ == null) {
						ErrorUtil.reportAndFail(vertx_, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.WARN, "no dcdc.vdis.dvg value in voltage reference unit data : " + masterSideUnitData_(), completionHandler);
						return;
					}
				}
				// No problem if null is sent during non-theoretical (i.e., actual) control -- the measured value (vg) will be specified at the other end.
				// theoretical でなければ ( つまり actual なら ) 制御時に null を送っておけば勝手に向こうで測定値 ( vg ) を指定してくれるのでヨシ!
			}
		}
		completionHandler.handle(Future.succeededFuture());
	}
	/**
	 * Choose the next master deal.
	 * @return the DEAL object that will be the next master deal
	 *          
	 * 次の master deal を決める.
	 * @return 次に master deal になる DEAL オブジェクト
	 */
	private JsonObject newMasterDeal_() {
		boolean canFlipMasterSide = canFlipMasterSide_();
		List<String> largeCapacityUnitIds = Policy.largeCapacityUnitIds(policy_);
		String masterDealSelectionStrategy = Policy.masterDealSelectionStrategy(policy_);
		if ("hoge".equals(masterDealSelectionStrategy)) {
			// This value is never returned
			// こんな値は返ってこない
			// For now, we will always enter the "else" branch; this is just dummy code
			// いまのところ必ず else に入るためのダミー
			return null;
		} else {
			// If "newestDeal"
			// "newestDeal" なら
			JsonObject result = null;
			JsonObject result_ = null;
			JsonObject result__ = null;
			LocalDateTime newestActivateDateTime = null;
			LocalDateTime newestActivateDateTime_ = null;
			LocalDateTime newestActivateDateTime__ = null;
			for (JsonObject aDeal : otherDeals_) {
				                                              // Cannot choose a DEAL that is not running
				if (Deal.masterSideUnitMustBeActive(aDeal)) { // 動いてない DEAL は対象外
					LocalDateTime anActivateDateTime = JsonObjectUtil.getLocalDateTime(aDeal, "activateDateTime");
					String aDealMasterSideUnitId = Deal.masterSideUnitId(aDeal, masterSide_);
					DDCon.Mode aDealMasterSideUnitDDConMode = DDCon.modeFromCode(DealExecution.unitDataCache.getString(aDealMasterSideUnitId, "dcdc", "status", "status"));
					if (DDCon.Mode.VOLTAGE_REFERENCE == aDealMasterSideUnitDDConMode) {
						// Assign top priority to the selection of an interchange in which the voltage reference participates → Avoid moving the voltage reference
						// 電圧リファレンスが参加している融通を最優先で選ぶ → 電圧リファレンスの移動を避けるため
						if (newestActivateDateTime == null || anActivateDateTime.isAfter(newestActivateDateTime)) {
							// If newer
							// より新しければ
							newestActivateDateTime = anActivateDateTime;
							result = aDeal;
						}
					}
					if (result == null && largeCapacityUnitIds != null && !largeCapacityUnitIds.isEmpty()) {
						// If we haven't yet found a top priority candidate
						// 最優先で選ばれているものがまだ見つかってなければ
						if (newestActivateDateTime_ == null || anActivateDateTime.isAfter(newestActivateDateTime_)) {
							// If newer
							// より新しければ
							if (largeCapacityUnitIds.contains(Deal.masterSideUnitId(aDeal, masterSide_))) {
								// If the unit on the voltage reference side is a large capacity unit
								// 電圧リファレンス側ユニットが大容量ユニットならば
								newestActivateDateTime_ = anActivateDateTime;
								result_ = aDeal;
							} else if (largeCapacityUnitIds.contains(Deal.slaveSideUnitId(aDeal, masterSide_))) {
								// If the unit on the non-voltage-reference side is a large capacity unit
								// 電圧リファレンス側じゃ無いユニットが大容量ユニットなら
								if (canFlipMasterSide) {
									// If the voltage reference side can be switched
									// 電圧リファレンス側を反転してもよい状況なら
									newestActivateDateTime_ = anActivateDateTime;
									result_ = aDeal;
								}
							}
						}
					}
					if (result == null && result_ == null) {
						// If neither a top priority nor a second priority candidate has yet been found
						// 最優先も次優先もまだ見つかってなければ
						if (newestActivateDateTime__ == null || anActivateDateTime.isAfter(newestActivateDateTime__)) {
							// Simply choose a new one
							// 単により新しいものを選ぶ
							newestActivateDateTime__ = anActivateDateTime;
							result__ = aDeal;
						}
					}
				}
			}
			if (result != null) {
				// Top priority candidate found
				// 最優先が見つかった
				newVoltageReferenceUnitId_ = Deal.masterSideUnitId(result, masterSide_);
				return result;
			} else if (result_ != null) {
				// Second priority candidate found
				// 次優先が見つかった
				if (largeCapacityUnitIds.contains(Deal.masterSideUnitId(result_, masterSide_))) {
					// Don't switch
					// 反転しない
					newVoltageReferenceUnitId_ = Deal.masterSideUnitId(result_, masterSide_);
				} else {
					// Switch
					// 反転する
					newVoltageReferenceUnitId_ = Deal.slaveSideUnitId(result_, masterSide_);
					masterSideWillBeFlipped_ = true;
					if (log.isInfoEnabled()) log.info("master side will be flipped");
				}
				return result_;
			} else if (result__ != null) {
				// Neither a top priority nor a second priority candidate was found
				// 最優先も次優先も見つからなかった
				if (masterSide_.equals(Policy.masterSide(policy_))) {
					// If the policy of the present voltage reference is the same as POLICY
					// 現在の電圧リファレンス側方針が POLICY と同じなら
					newVoltageReferenceUnitId_ = Deal.masterSideUnitId(result__, masterSide_);
				} else {
					// If the policy of the present voltage reference is the opposite of POLICY
					// 現在の電圧リファレンス側方針が POLICY と反対なら
					if (canFlipMasterSide) {
						// If the voltage reference sides can be switched → Switch them back
						// 電圧リファレンス側を反転してもよい状況なら → 反転を戻す
						newVoltageReferenceUnitId_ = Deal.slaveSideUnitId(result__, masterSide_);
						masterSideWillBeFlipped_ = true;
						if (log.isInfoEnabled()) log.info("master side will be flipped");
					} else {
						// The switching cannot be reversed, so leave them as they are
						// 反転は戻せないのでそのまま
						newVoltageReferenceUnitId_ = Deal.masterSideUnitId(result__, masterSide_);
						if (log.isInfoEnabled()) log.info("could not flip master side");
					}
				}
				return result__;
			}
			return null;
		}
	}

	private void deactivateDcdc_(Handler<AsyncResult<Void>> completionHandler) {
		// Fail if dcdc/failBeforeDeactivate = true in the DEAL object
		// DEAL オブジェクト中に dcdc/failBeforeDeactivate = true があったら fail させる
		if (testFeature_failIfNeed_("dcdc", "failBeforeDeactivate", completionHandler)) return;
		if (isMaster_) {
			// This is the master deal
			// master deal である
			if (newMasterDeal_ != null) {
				// The master deal is to be moved → In other words, there is another running interchange
				// master deal の移動が発生する → つまり他に実行中の融通があるということ
				if (log.isInfoEnabled()) log.info("new voltage reference unit : " + newVoltageReferenceUnitId_);
				if (masterSideUnitId_().equals(newVoltageReferenceUnitId_)) {
					// No need to move the voltage reference
					// 電圧リファレンスの移動は不要
					if (log.isInfoEnabled()) log.info("no need to move voltage reference");
					// Update the cached device control state of the voltage reference unit
					// 電圧リファレンスユニットのデバイス制御状態のキャッシュを更新しておく
					// Fail if dcdc/failAfterDeactivate = true in the DEAL object
					// DEAL オブジェクト中に dcdc/failAfterDeactivate = true があったら fail させる
					updateUnitDcdcStatus_(masterSideUnitId_(), res -> testFeature_failIfNeed_(res, "dcdc", "failAfterDeactivate", completionHandler)); // update cache for master side unit ...
				} else {
					// The voltage reference has to be moved
					// 電圧リファレンスの移動が必要
					if (canStopDcdc_()) {
						// The device can be stopped
						// デバイスを停止できる
						if (log.isInfoEnabled()) log.info("move voltage reference");
						// Send voltageReferenceWillHandOver to the source unit
						// 移動元ユニットに voltageReferenceWillHandOver
						controlMasterSideUnitDcdc_("voltageReferenceWillHandOver", null, resWillHandOver -> {
							if (resWillHandOver.succeeded()) {
								// Set up operationGridVoltageV_ determined in prepareDeactivate_() as a new dvg in the parameters...
								// prepareDeactivate_() で決めた operationGridVoltageV_ を新しい dvg としてパラメタに仕込んで...
								JsonObject params = new JsonObject().put("gridVoltageV", operationGridVoltageV_);
								// Set the destination unit to voltageReferenceWillTakeOver
								// 移動先ユニットに voltageReferenceWillTakeOver
								controlDcdc_(newVoltageReferenceUnitId_, "voltageReferenceWillTakeOver", params, resWillTakeOver -> {
									if (resWillTakeOver.succeeded()) {
										if (log.isInfoEnabled()) log.info("new voltage reference started, stop old voltage reference");
										// Set the destination unit to voltageReferenceDidHandOver
										// 移動元ユニットに voltageReferenceDidHandOver
										controlMasterSideUnitDcdc_("voltageReferenceDidHandOver", null, resDidHandOver -> {
											if (resDidHandOver.succeeded()) {
												// Set the destination unit to voltageReferenceDidTakeOver
												// 移動先ユニットに voltageReferenceDidTakeOver
												// If successful, fail if dcdc/failAfterDeactivate = true in the DEAL object
												// 成功した場合に DEAL オブジェクト中に dcdc/failAfterDeactivate = true があったら fail させる
												controlDcdc_(newVoltageReferenceUnitId_, "voltageReferenceDidTakeOver", null, res -> testFeature_failIfNeed_(res, "dcdc", "failAfterDeactivate", completionHandler));
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
						// This is the master deal, we want to move the master deal, and the voltage reference has to be moved
						// master deal であり master deal を移動しようとしており電圧リファレンスの移動が必要である
						// In other words, the voltage reference side should not be participating in any other interchanges (if it is, then the master deal should have been chosen so that there is no need to move the voltage reference)
						// つまり電圧リファレンス側は他の融通に参加していないはず ( 他の融通に参加しているなら電圧リファレンスを移動しないですむように master deal を決めたはず )
						// However, the device cannot be stopped
						// それなのにデバイスを止められない
						// This shouldn't happen, so raise a GLOBAL ERROR
						// そんなはずがないので GLOBAL ERROR
						ErrorUtil.reportAndFail(vertx_, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.ERROR, "isMaster_ == true && newMasterDeal_ != null && masterSideUnitId_() != newMasterDealMasterSideUnitId && canStopDcdc_() == false ; deal : " + deal_, completionHandler);
					}
				}
			} else {
				// The master deal is not moved → In other words, there are no other running interchanges
				// master deal の移動が発生しない → つまり他に実行中の融通はないということ
				if (log.isInfoEnabled()) log.info("stop voltage reference");
				// Stop the device
				// デバイスを止めちゃう
				// If successful, fail if dcdc/failAfterDeactivate = true in the DEAL object
				// 成功した場合に DEAL オブジェクト中に dcdc/failAfterDeactivate = true があったら fail させる
				controlMasterSideUnitDcdc_(DDCon.Mode.WAIT.name(), null, res -> testFeature_failIfNeed_(res, "dcdc", "failAfterDeactivate", completionHandler));
			}
		} else {
			// This is not the master deal
			// master deal ではない
			if (DDCon.Mode.VOLTAGE_REFERENCE == masterSideUnitDDConMode_()) {
				// If it was a voltage reference
				// 電圧リファレンスだった場合
				if (canStopDcdc_()) {
					// If this is not the master deal but is a voltage reference, then there ought to be at least one other participating interchange (one of which is the master deal)
					// master deal ではないのに電圧リファレンスだということは他にも融通に参加している ( そのどれかが master deal である ) はず
					// So this device should not be stopped
					// なのでデバイスは止められないはず
					// But it is being stopped
					// それなのにデバイスを止められる
					// This shouldn't happen, so raise a GLOBAL ERROR
					// そんなはずがないので GLOBAL ERROR
					ErrorUtil.reportAndFail(vertx_, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.ERROR, "isMaster_ == false && masterSideUnitDDConMode_() == VR && canStopDcdc_() == true ; deal : " + deal_, completionHandler);
				} else {
					// Leave as voltage reference
					// 電圧リファレンスのまま放置
					if (log.isInfoEnabled()) log.info("no need to control voltage reference");
					// Update the cached device control state of the voltage reference unit
					// 電圧リファレンスユニットのデバイス制御状態のキャッシュを更新しておく
					// Fail if dcdc/failAfterDeactivate = true in the DEAL object
					// DEAL オブジェクト中に dcdc/failAfterDeactivate = true があったら fail させる
					updateUnitDcdcStatus_(masterSideUnitId_(), res -> testFeature_failIfNeed_(res, "dcdc", "failAfterDeactivate", completionHandler)); // update cache for master side unit ...
				}
			} else {
				// If this is not a voltage reference (either CHARGE or DISCHARGE mode)
				// 電圧リファレンスではない ( CHARGE または DISCHARGE である ) 場合
				if (canStopDcdc_()) {
					// Then the device can be stopped
					// デバイスを止めてよい
					if (log.isInfoEnabled()) log.info("stop master side unit");
					// Stop the device
					// デバイスを止めちゃう
					// If successful, fail if dcdc/failAfterDeactivate = true in the DEAL object
					// 成功した場合に DEAL オブジェクト中に dcdc/failAfterDeactivate = true があったら fail させる
					controlMasterSideUnitDcdc_(DDCon.Mode.WAIT.name(), null, res -> testFeature_failIfNeed_(res, "dcdc", "failAfterDeactivate", completionHandler));
				} else {
					// Change the grid current value without stopping
					// 止めないでグリッド電流値を変更する
					// Calculate the new current value
					// 新しい電流値を算出し
					float newDig = Math.abs(sumOfOtherDealCompensatedGridCurrentAs_(masterSideUnitId_()));
					if (log.isInfoEnabled()) log.info("dig : " + JsonObjectUtil.getFloat(masterSideUnitData_(), "dcdc", "param", "dig") + " -> " + newDig);
					// Set up the new grid current value parameter
					// 新しい電流値をパラメタに仕込んで
					JsonObject params = new JsonObject().put("gridCurrentA", newDig);
					// Issue command to change the current value in the unit on the voltage reference side
					// 電圧リファレンス側ユニットに電流値の変更を命令
					// If successful, fail if dcdc/failAfterDeactivate = true in the DEAL object
					// 成功した場合に DEAL オブジェクト中に dcdc/failAfterDeactivate = true があったら fail させる
					controlMasterSideUnitDcdc_("current", params, res -> testFeature_failIfNeed_(res, "dcdc", "failAfterDeactivate", completionHandler));
				}
			}
		}
	}
	/**
	 * Find out if a device can be stopped.
	 * @return {@code true} if yes
	 *          
	 * デバイスを止めてよいかどうか調べる.
	 * @return 止めてよければ {@code true}
	 */
	private boolean canStopDcdc_() {
		for (JsonObject aDeal : otherDeals_(masterSideUnitId_())) {
			if (Deal.masterSideUnitMustBeActive(aDeal)) {
				// It cannot be stopped if there are one or more other active interchanges participating in the units on the voltage reference side
				// 電圧リファレンス側ユニットが参加する他の融通が一つでも動いていたら止められない
				return false;
			}
		}
		return true;
	}

	/**
	 * Move the master deal marker
	 * @param completionHandler the completion handler
	 *          
	 * master deal の目印を移動する.
	 * @param completionHandler the completion handler
	 */
	private void moveMasterDeal_(Handler<AsyncResult<Void>> completionHandler) {
		if (newMasterDeal_ != null) {
			if (masterSideWillBeFlipped_) {
				// Switch
				// 反転
				flipMasterSide_();
				if (log.isInfoEnabled()) log.info("master side was flipped : " + masterSide_);
			}
			DealUtil.isMaster(vertx_, newMasterDeal_, true, res -> ErrorExceptionUtil.reportIfNeedAndHandle(vertx_, res, completionHandler));
		} else {
			completionHandler.handle(Future.succeededFuture());
		}
	}

	/**
	 * Put the interchange information in the "deactivate" state
	 * @param completionHandler the completion handler
	 *          
	 * 融通情報を deactivate 状態にする.
	 * @param completionHandler the completion handler
	 */
	private void deactivateDeal_(Handler<AsyncResult<Void>> completionHandler) {
		if (!Deal.isDeactivated(deal_)) {
			DealUtil.deactivate(vertx_, deal_, referenceDateTimeString_(), resDeactivate -> ErrorExceptionUtil.reportIfNeedAndHandle(vertx_, resDeactivate, completionHandler));
		} else {
			// Since deactivation is already in progress, pass through this inconsistency
			// 止める方向なので不整合はスルーする
			if (log.isInfoEnabled()) log.info("already deactivated");
			completionHandler.handle(Future.succeededFuture());
		}
	}

}
