package jp.co.sony.csl.dcoes.apis.main.app.gridmaster.deal_execution;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

public class DealDeactivation extends AbstractDealExecution {
	private static final Logger log = LoggerFactory.getLogger(DealDeactivation.class);

	private boolean isMaster_;
	private Float operationGridVoltageV_;
	private JsonObject newMasterDeal_;
	private String newVoltageReferenceUnitId_;
	private boolean masterSideWillBeFlipped_ = false;

	public DealDeactivation(Vertx vertx, JsonObject policy, JsonObject deal, List<JsonObject> otherDeals) {
		super(vertx, policy, deal, otherDeals);
	}

	public DealDeactivation(AbstractDealExecution other) {
		super(other);
	}

	@Override
	protected void doExecute(Handler<AsyncResult<Void>> onComplete) {
		if (DDCon.Mode.WAIT != masterSideUnitDDConMode_()) {
			prepareDeactivate_(resPrepare -> {
				if (resPrepare.succeeded()) {
					deactivateDcdc_(resDeactivateDcdc -> {
						if (resDeactivateDcdc.succeeded()) {
							moveMasterDeal_(resMoveMasterDeal -> {
								if (resMoveMasterDeal.succeeded()) {
									deactivateDeal_(resDeactivateDeal -> {
										if (resDeactivateDeal.succeeded()) {
											new DealDisposition(this).doExecute(onComplete);
										} else {
											onComplete.handle(resDeactivateDeal);
										}
									});
								} else {
									onComplete.handle(resMoveMasterDeal);
								}
							});
						} else {
							onComplete.handle(resDeactivateDcdc);
						}
					});
				} else {
					onComplete.handle(resPrepare);
				}
			});
		} else {
			deactivateDeal_(resDeactivateDeal -> {
				if (resDeactivateDeal.succeeded()) {
					new DealDisposition(this).doExecute(onComplete);
				} else {
					onComplete.handle(resDeactivateDeal);
				}
			});
		}
	}

	private void prepareDeactivate_(Handler<AsyncResult<Void>> onComplete) {
		isMaster_ = Deal.isMaster(deal_);
		if (isMaster_) {
			newMasterDeal_ = newMasterDeal_();
			if (newMasterDeal_ != null) {
				if (log.isInfoEnabled())
					log.info("need to move master deal; new master deal : " + newMasterDeal_);
				String voltageReferenceTakeOverDvg = Policy.voltageReferenceTakeOverDvg(policy_);
				if ("theoretical".equals(voltageReferenceTakeOverDvg)) {
					operationGridVoltageV_ = JsonObjectUtil.getFloat(masterSideUnitData_(), "dcdc", "vdis", "dvg");
					if (operationGridVoltageV_ == null) {
						ErrorUtil.reportAndFail(vertx_, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.WARN,
								"no dcdc.vdis.dvg value in voltage reference unit data : " + masterSideUnitData_(),
								onComplete);
						return;
					}
				}
				// No problem if null is sent during non-theoretical (i.e., actual) control --
				// the measured value (vg) will be specified at the other end.
				// theoretical でなければ ( つまり actual なら ) 制御時に null を送っておけば勝手に向こうで測定値 ( vg )
				// を指定してくれるのでヨシ!
			}
		}
		onComplete.handle(Future.succeededFuture());
	}

	private JsonObject newMasterDeal_() {
		boolean canFlipMasterSide = canFlipMasterSide_();
		List<String> largeCapacityUnitIds = Policy.largeCapacityUnitIds(policy_);
		String masterDealSelectionStrategy = Policy.masterDealSelectionStrategy(policy_);
		if ("hoge".equals(masterDealSelectionStrategy)) {
			return null;
		} else {
			JsonObject result = null;
			JsonObject result_ = null;
			JsonObject result__ = null;
			LocalDateTime newestActivateDateTime = null;
			LocalDateTime newestActivateDateTime_ = null;
			LocalDateTime newestActivateDateTime__ = null;
			for (JsonObject aDeal : otherDeals_) {
				if (Deal.masterSideUnitMustBeActive(aDeal)) {
					LocalDateTime anActivateDateTime = JsonObjectUtil.getLocalDateTime(aDeal, "activateDateTime");
					String aDealMasterSideUnitId = Deal.masterSideUnitId(aDeal, masterSide_);
					DDCon.Mode aDealMasterSideUnitDDConMode = DDCon.modeFromCode(
							DealExecution.unitDataCache.getString(aDealMasterSideUnitId, "dcdc", "status", "status"));
					if (DDCon.Mode.VOLTAGE_REFERENCE == aDealMasterSideUnitDDConMode) {
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
					if (log.isInfoEnabled())
						log.info("master side will be flipped");
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
						if (log.isInfoEnabled())
							log.info("master side will be flipped");
					} else {
						// The switching cannot be reversed, so leave them as they are
						// 反転は戻せないのでそのまま
						newVoltageReferenceUnitId_ = Deal.masterSideUnitId(result__, masterSide_);
						if (log.isInfoEnabled())
							log.info("could not flip master side");
					}
				}
				return result__;
			}
			return null;
		}
	}

	private void deactivateDcdc_(Handler<AsyncResult<Void>> onComplete) {
		if (testFeature_failIfNeed_("dcdc", "failBeforeDeactivate", onComplete))
			return;
		if (isMaster_) {
			// This is the master deal
			// master deal である
			if (newMasterDeal_ != null) {
				if (log.isInfoEnabled())
					log.info("new voltage reference unit : " + newVoltageReferenceUnitId_);
				if (masterSideUnitId_().equals(newVoltageReferenceUnitId_)) {
					if (log.isInfoEnabled())
						log.info("no need to move voltage reference");
					updateUnitDcdcStatus_(masterSideUnitId_(),
							res -> testFeature_failIfNeed_(res, "dcdc", "failAfterDeactivate", onComplete)); // update
																												// cache
																												// for
																												// master
																												// side
																												// unit
																												// ...
				} else {
					if (canStopDcdc_()) {
						// The device can be stopped
						// デバイスを停止できる
						if (log.isInfoEnabled())
							log.info("move voltage reference");
						// Send voltageReferenceWillHandOver to the source unit
						// 移動元ユニットに voltageReferenceWillHandOver
						controlMasterSideUnitDcdc_("voltageReferenceWillHandOver", null, resWillHandOver -> {
							if (resWillHandOver.succeeded()) {
								// Set up operationGridVoltageV_ determined in prepareDeactivate_() as a new dvg
								// in the parameters...
								// prepareDeactivate_() で決めた operationGridVoltageV_ を新しい dvg としてパラメタに仕込んで...
								JsonObject params = new JsonObject().put("gridVoltageV", operationGridVoltageV_);
								// Set the destination unit to voltageReferenceWillTakeOver
								// 移動先ユニットに voltageReferenceWillTakeOver
								controlDcdc_(newVoltageReferenceUnitId_, "voltageReferenceWillTakeOver", params,
										resWillTakeOver -> {
											if (resWillTakeOver.succeeded()) {
												if (log.isInfoEnabled())
													log.info(
															"new voltage reference started, stop old voltage reference");
												// Set the destination unit to voltageReferenceDidHandOver
												// 移動元ユニットに voltageReferenceDidHandOver
												controlMasterSideUnitDcdc_("voltageReferenceDidHandOver", null,
														resDidHandOver -> {
															if (resDidHandOver.succeeded()) {
																controlDcdc_(newVoltageReferenceUnitId_,
																		"voltageReferenceDidTakeOver", null,
																		res -> testFeature_failIfNeed_(res, "dcdc",
																				"failAfterDeactivate", onComplete));
															} else {
																onComplete.handle(resDidHandOver);
															}
														});
											} else {
												onComplete.handle(resWillTakeOver);
											}
										});
							} else {
								onComplete.handle(resWillHandOver);
							}
						});
					} else {
						// This is the master deal, we want to move the master deal, and the voltage
						// reference has to be moved
						// master deal であり master deal を移動しようとしており電圧リファレンスの移動が必要である
						// In other words, the voltage reference side should not be participating in any
						// other interchanges (if it is, then the master deal should have been chosen so
						// that there is no need to move the voltage reference)
						// つまり電圧リファレンス側は他の融通に参加していないはず ( 他の融通に参加しているなら電圧リファレンスを移動しないですむように master deal
						// を決めたはず )
						// However, the device cannot be stopped
						// それなのにデバイスを止められない
						// This shouldn't happen, so raise a GLOBAL ERROR
						// そんなはずがないので GLOBAL ERROR
						ErrorUtil.reportAndFail(vertx_, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.ERROR,
								"isMaster_ == true && newMasterDeal_ != null && masterSideUnitId_() != newMasterDealMasterSideUnitId && canStopDcdc_() == false ; deal : "
										+ deal_,
								onComplete);
					}
				}
			} else {
				if (log.isInfoEnabled())
					log.info("stop voltage reference");
				controlMasterSideUnitDcdc_(DDCon.Mode.WAIT.name(), null,
						res -> testFeature_failIfNeed_(res, "dcdc", "failAfterDeactivate", onComplete));
			}
		} else {
			// This is not the master deal
			// master deal ではない
			if (DDCon.Mode.VOLTAGE_REFERENCE == masterSideUnitDDConMode_()) {
				// If it was a voltage reference
				// 電圧リファレンスだった場合
				if (canStopDcdc_()) {
					// If this is not the master deal but is a voltage reference, then there ought
					// to be at least one other participating interchange (one of which is the
					// master deal)
					// master deal ではないのに電圧リファレンスだということは他にも融通に参加している ( そのどれかが master deal である ) はず
					// So this device should not be stopped
					// なのでデバイスは止められないはず
					// But it is being stopped
					// それなのにデバイスを止められる
					// This shouldn't happen, so raise a GLOBAL ERROR
					// そんなはずがないので GLOBAL ERROR
					ErrorUtil.reportAndFail(vertx_, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.ERROR,
							"isMaster_ == false && masterSideUnitDDConMode_() == VR && canStopDcdc_() == true ; deal : "
									+ deal_,
							onComplete);
				} else {
					// Leave as voltage reference
					// 電圧リファレンスのまま放置
					if (log.isInfoEnabled())
						log.info("no need to control voltage reference");
					// Update the cached device control state of the voltage reference unit
					// 電圧リファレンスユニットのデバイス制御状態のキャッシュを更新しておく
					// Fail if dcdc/failAfterDeactivate = true in the DEAL object
					// DEAL オブジェクト中に dcdc/failAfterDeactivate = true があったら fail させる
					updateUnitDcdcStatus_(masterSideUnitId_(),
							res -> testFeature_failIfNeed_(res, "dcdc", "failAfterDeactivate", onComplete)); // update
																												// cache
																												// for
																												// master
																												// side
																												// unit
																												// ...
				}
			} else {
				// If this is not a voltage reference (either CHARGE or DISCHARGE mode)
				// 電圧リファレンスではない ( CHARGE または DISCHARGE である ) 場合
				if (canStopDcdc_()) {
					// Then the device can be stopped
					// デバイスを止めてよい
					if (log.isInfoEnabled())
						log.info("stop master side unit");
					controlMasterSideUnitDcdc_(DDCon.Mode.WAIT.name(), null,
							res -> testFeature_failIfNeed_(res, "dcdc", "failAfterDeactivate", onComplete));
				} else {
					// Change the grid current value without stopping
					// 止めないでグリッド電流値を変更する
					// Calculate the new current value
					// 新しい電流値を算出し
					float newDig = Math.abs(sumOfOtherDealCompensatedGridCurrentAs_(masterSideUnitId_()));
					if (log.isInfoEnabled())
						log.info("dig : " + JsonObjectUtil.getFloat(masterSideUnitData_(), "dcdc", "param", "dig")
								+ " -> " + newDig);
					JsonObject params = new JsonObject().put("gridCurrentA", newDig);
					controlMasterSideUnitDcdc_("current", params,
							res -> testFeature_failIfNeed_(res, "dcdc", "failAfterDeactivate", onComplete));
				}
			}
		}
	}

	private boolean canStopDcdc_() {
		for (JsonObject aDeal : otherDeals_(masterSideUnitId_())) {
			if (Deal.masterSideUnitMustBeActive(aDeal)) {
				return false;
			}
		}
		return true;
	}

	private void moveMasterDeal_(Handler<AsyncResult<Void>> onComplete) {
		if (newMasterDeal_ != null) {
			if (masterSideWillBeFlipped_) {
				flipMasterSide_();
			}
			DealUtil.isMaster(vertx_, newMasterDeal_, true,
					res -> ErrorExceptionUtil.reportIfNeedAndHandle(vertx_, res, onComplete));
		} else {
			onComplete.handle(Future.succeededFuture());
		}
	}

	private void deactivateDeal_(Handler<AsyncResult<Void>> onComplete) {
		if (!Deal.isDeactivated(deal_)) {
			DealUtil.deactivate(vertx_, deal_, referenceDateTimeString_(),
					resDeactivate -> ErrorExceptionUtil.reportIfNeedAndHandle(vertx_, resDeactivate, onComplete));
		} else {
			// Since deactivation is already in progress, pass through this inconsistency
			// 止める方向なので不整合はスルーする
			if (log.isInfoEnabled())
				log.info("already deactivated");
			onComplete.handle(Future.succeededFuture());
		}
	}

}
