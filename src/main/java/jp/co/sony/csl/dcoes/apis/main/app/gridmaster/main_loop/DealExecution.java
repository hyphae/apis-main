package jp.co.sony.csl.dcoes.apis.main.app.gridmaster.main_loop;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jp.co.sony.csl.dcoes.apis.common.Deal;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.ServiceAddress;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectUtil;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectWrapper;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.ReplyFailureUtil;
import jp.co.sony.csl.dcoes.apis.main.app.PolicyKeeping;
import jp.co.sony.csl.dcoes.apis.main.app.StateHandling;
import jp.co.sony.csl.dcoes.apis.main.app.controller.util.DDCon;
import jp.co.sony.csl.dcoes.apis.main.app.gridmaster.ErrorCollection;
import jp.co.sony.csl.dcoes.apis.main.app.gridmaster.deal_execution.AbstractDealExecution;
import jp.co.sony.csl.dcoes.apis.main.app.gridmaster.deal_execution.DealAbortion;
import jp.co.sony.csl.dcoes.apis.main.app.gridmaster.deal_execution.DealActivation;
import jp.co.sony.csl.dcoes.apis.main.app.gridmaster.deal_execution.DealCompensation;
import jp.co.sony.csl.dcoes.apis.main.app.gridmaster.deal_execution.DealCumulation;
import jp.co.sony.csl.dcoes.apis.main.app.gridmaster.deal_execution.DealDeactivation;
import jp.co.sony.csl.dcoes.apis.main.app.gridmaster.deal_execution.DealDisposition;
import jp.co.sony.csl.dcoes.apis.main.app.gridmaster.deal_execution.DealMasterAuthorization;
import jp.co.sony.csl.dcoes.apis.main.app.gridmaster.deal_execution.DealRampingUp;
import jp.co.sony.csl.dcoes.apis.main.app.mediator.util.DealNeedToStopUtil;
import jp.co.sony.csl.dcoes.apis.main.app.mediator.util.DealUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorExceptionUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;
import jp.co.sony.csl.dcoes.apis.main.util.Policy;

/**
 * Perform interchange control.
 * Called periodically from {@link jp.co.sony.csl.dcoes.apis.main.app.gridmaster.MainLoop}.
 * The following processes are executed in sequence.
 * 1. Collect unit data
 * 2. Acquire interchange information
 * 3. Transfer the interchange stop request to the interchange information
 * 4. Check the global interchange mode
 * 5. Sort the interchange information into a suitable order
 * 6. Extract and control the interchange information in sequence
 * 7. Optimize the grid voltage
 * Specific interchange processing is performed in the following classes according to the interchange status.
 * - {@link DealActivation}: Launch the first voltage reference
 * - {@link DealRampingUp}: Wait for the grid voltage to rise
 * - {@link DealMasterAuthorization}: Perform voltage reference privilege acquisition operations
 * - {@link DealCompensation}: Perform current compensation operations
 * - {@link DealCumulation}: Sum up the available interchange power
 * - {@link DealDeactivation}: End an interchange. Stop when the end condition has been reached
 * - {@link DealDisposition}: Delete the interchange information
 * - {@link DealAbortion}: Abnormal termination of an interchange
 * - {@link jp.co.sony.csl.dcoes.apis.main.app.gridmaster.deal_execution.DealDispositionWithAbortReason}: Delete interchange information while writing abnormal termination information
 * @author OES Project
 *          
 * 融通制御を実行する.
 * {@link jp.co.sony.csl.dcoes.apis.main.app.gridmaster.MainLoop} から定期的に呼ばれる.
 * 以下の処理を順次実行する.
 * 1. ユニットデータを収集する
 * 2. 融通情報を取得する
 * 3. 融通停止要求を融通情報に転記する
 * 4. グローバル融通モードを確認する
 * 5. 融通情報を適切な順序に並べ替える
 * 6. 順番に融通情報を取り出し制御する
 * 7. グリッド電圧を最適化する
 * 具体的な融通処理は融通の状態の応じて以下のクラスで実行する.
 * - {@link DealActivation} : 最初の電圧リファレンスを起動する
 * - {@link DealRampingUp} : グリッド電圧が上がるのを待つ
 * - {@link DealMasterAuthorization} : 電圧リファレンス権限取得動作を実行する
 * - {@link DealCompensation} : 電流コンペンセイション動作を実行する
 * - {@link DealCumulation} : 融通電力量を積算する
 * - {@link DealDeactivation} : 融通を終了する. 終了条件に到達したら停止する
 * - {@link DealDisposition} : 融通情報を削除する
 * - {@link DealAbortion} : 融通を異常終了する
 * - {@link jp.co.sony.csl.dcoes.apis.main.app.gridmaster.deal_execution.DealDispositionWithAbortReason} : 異常終了情報を書き込みつつ融通情報を削除する
 * @author OES Project
 */
public class DealExecution {
	private static final Logger log = LoggerFactory.getLogger(DealExecution.class);

	/**
	 * A cache that retains unit data for all units.
	 * The cache is used only for interchange processing.
	 * Independent of {@link jp.co.sony.csl.dcoes.apis.main.app.gridmaster.DataCollection#cache}.
	 *          
	 * 全ユニットのユニットデータを保持しておくキャッシュ.
	 * 融通処理にだけ使用するキャッシュ.
	 * {@link jp.co.sony.csl.dcoes.apis.main.app.gridmaster.DataCollection#cache} とは独立.
	 */
	public static final JsonObjectWrapper unitDataCache = new JsonObjectWrapper();

	private static long lastDealExecutionMillis_ = 0L;

	private DealExecution() { }

	/**
	 * Processing called from {@link jp.co.sony.csl.dcoes.apis.main.app.gridmaster.MainLoop}.
	 * The following processes are executed in sequence.
	 * 1. Collect unit data
	 * 2. Acquire interchange information
	 * 3. Transfer the interchange stop request to the interchange information
	 * 4. Check the global interchange mode
	 * 5. Sort the interchange information into a suitable order
	 * 6. Extract and control the interchange information in sequence
	 * 7. Optimize the grid voltage
	 * Specific interchange processing is performed in the following classes according to the interchange status.
	 * - {@link DealActivation}: Launch the first voltage reference
	 * - {@link DealRampingUp}: Wait for the grid voltage to rise
	 * - {@link DealMasterAuthorization}: Perform voltage reference privilege acquisition operations
	 * - {@link DealCompensation}: Perform current compensation operations
	 * - {@link DealCumulation}: Sum up the available interchange power
	 * - {@link DealDeactivation}: End an interchange. Stop when the end condition has been reached
	 * - {@link DealDisposition}: Delete the interchange information
	 * - {@link DealAbortion}: Abnormal termination of an interchange
	 * - {@link jp.co.sony.csl.dcoes.apis.main.app.gridmaster.deal_execution.DealDispositionWithAbortReason}: Delete interchange information while writing abnormal termination information
	 * Save {@link #lastDealExecutionMillis_} as a mechanism to prevent problems caused by the {@link jp.co.sony.csl.dcoes.apis.main.app.gridmaster.DataCollection} data collection process from colliding with the data collection performed here.
	 * @param vertx a vertx object
	 * @param completionHandler the completion handler
	 *          
	 * {@link jp.co.sony.csl.dcoes.apis.main.app.gridmaster.MainLoop} から呼ばれる処理.
	 * 以下の処理を順次実行する.
	 * 1. ユニットデータを収集する
	 * 2. 融通情報を取得する
	 * 3. 融通停止要求を融通情報に転記する
	 * 4. グローバル融通モードを確認する
	 * 5. 融通情報を適切な順序に並べ替える
	 * 6. 順番に融通情報を取り出し制御する
	 * 7. グリッド電圧を最適化する
	 * 具体的な融通処理は融通の状態の応じて以下のクラスで実行する.
	 * - {@link DealActivation} : 最初の電圧リファレンスを起動する
	 * - {@link DealRampingUp} : グリッド電圧が上がるのを待つ
	 * - {@link DealMasterAuthorization} : 電圧リファレンス権限取得動作を実行する
	 * - {@link DealCompensation} : 電流コンペンセイション動作を実行する
	 * - {@link DealCumulation} : 融通電力量を積算する
	 * - {@link DealDeactivation} : 融通を終了する. 終了条件に到達したら停止する
	 * - {@link DealDisposition} : 融通情報を削除する
	 * - {@link DealAbortion} : 融通を異常終了する
	 * - {@link jp.co.sony.csl.dcoes.apis.main.app.gridmaster.deal_execution.DealDispositionWithAbortReason} : 異常終了情報を書き込みつつ融通情報を削除する
	 * {@link jp.co.sony.csl.dcoes.apis.main.app.gridmaster.DataCollection} のデータ収集処理とここでのデータ収集が重なってしまう問題を防ぐための仕組みで {@link #lastDealExecutionMillis_} を保存するため一段かませている.
	 * @param vertx vertx オブジェクト
	 * @param completionHandler the completion handler
	 */
	public static void execute(Vertx vertx, Handler<AsyncResult<Void>> completionHandler) {
		execute_(vertx, res -> {
			// Save the time at which the interchange processing was completed
			// 融通処理が終わった時刻を保存しておく
			lastDealExecutionMillis_ = System.currentTimeMillis();
			completionHandler.handle(res);
		});
	}
	private static void execute_(Vertx vertx, Handler<AsyncResult<Void>> completionHandler) {
		// Collect unit data
		// ユニットデータを収集する
		getUnitData_(vertx, resGetUnitData -> {
			if (resGetUnitData.succeeded()) {
				// Retrieve the DEAL object from shared memory
				// 共有メモリから DEAL オブジェクトを全部取り出す
				DealUtil.all(vertx, resAll -> {
					if (resAll.succeeded()) {
						List<JsonObject> deals = resAll.result();
						if (log.isDebugEnabled()) log.debug("deals : " + deals);
						if (!deals.isEmpty()) {
							// Copy the received interchange stop request to the DEAL object
							// 受信した融通停止要求を DEAL オブジェクトに転記する
							DealNeedToStopUtil.copyToDeals(vertx, deals, resCopyNeedToStop -> {
								if (resCopyNeedToStop.succeeded()) {
									// Check the global interchange mode
									// グローバル融通モードを確認
									StateHandling.globalOperationMode(vertx, resOperationMode -> {
										if (resOperationMode.succeeded()) {
											String result = resOperationMode.result();
											if ("stop".equals(result) || "manual".equals(result)) {
												// If the interchange is in "stop" or "manual" mode → Raise an error
												// stop または manual なのに融通がある → エラーにする
												ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.ERROR, "operationMode is '" + result + "' but deal exists", completionHandler);
											} else {
												// If a master deal must exist in this state, check that one exists
												// master deal が存在していなければならない状況なら存在していることを確認する
												if (checkMasterDealExistence_(deals)) {
													// Acquire a POLICY object to avoid changes taking effect while processing
													// 処理中に変わっても影響しないよう POLICY オブジェクトを確保し
													JsonObject policy = PolicyKeeping.cache().jsonObject();
													// Rearrange the interchange processing sequence into a suitable order
													// 融通の処理順を適切に並べ直し
													sortDeals_(vertx, policy, deals);
													if (log.isDebugEnabled()) log.debug("sorted deals : " + deals);
													// START INTERCHANGE PROCESSING
													// 融通処理開始！
													new DealExecution_(vertx, policy, deals).doLoop_(completionHandler);
												} else {
													// If a master deal must exist in this state, raise an error if this is not so
													// master deal が存在していなければならない状況なのに存在していなかったらエラー
													ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.ERROR, "no master deal found", completionHandler);
												}
											}
										} else {
											completionHandler.handle(Future.failedFuture(resOperationMode.cause()));
										}
									});
								} else {
									ErrorExceptionUtil.reportIfNeedAndFail(vertx, resCopyNeedToStop.cause(), completionHandler);
								}
							});
						} else {
							completionHandler.handle(Future.succeededFuture());
						}
					} else {
						ErrorExceptionUtil.reportIfNeedAndFail(vertx, resAll.cause(), completionHandler);
					}
				});
			} else {
				completionHandler.handle(resGetUnitData);
			}
		});
	}

	/**
	 * Collect unit data.
	 * @param vertx a vertx object
	 * @param completionHandler the completion handler
	 *          
	 * ユニットデータを収集する.
	 * @param vertx vertx オブジェクト
	 * @param completionHandler the completion handler
	 */
	private static void getUnitData_(Vertx vertx, Handler<AsyncResult<Void>> completionHandler) {
		// Send the time at which the last interchange processing was completed
		// 前回の融通処理が終わった時刻を送る
		// If data collection is performed after this time, the cached value is returned instead of collecting data again.
		// この時刻より後にデータ収集が行われていれば新たにデータ収集しなおすことなくキャッシュ値が返ってくる
		vertx.eventBus().<JsonObject>send(ServiceAddress.GridMaster.urgentUnitDatas(), lastDealExecutionMillis_, rep -> {
			if (rep.succeeded()) {
				// Keep in cache
				// キャッシュしておく
				unitDataCache.setJsonObject(rep.result().body());
				completionHandler.handle(Future.succeededFuture());
			} else {
				if (ReplyFailureUtil.isRecipientFailure(rep)) {
					completionHandler.handle(Future.failedFuture(rep.cause()));
				} else {
					ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.GLOBAL, Error.Level.ERROR, "Communication failed on EventBus", rep.cause(), completionHandler);
				}
			}
		});
	}
	private static boolean checkMasterDealExistence_(List<JsonObject> deals) {
		boolean noActiveDeal = true;
		for (JsonObject aDeal : deals) {
			if (Deal.masterSideUnitMustBeActive(aDeal)) {
				noActiveDeal = false;
				break;
			}
		}
		if (noActiveDeal) {
			// OK if there are no interchanges running
			// 動いている融通がなければ OK
			return true;
		}
		// If there is an interchange running
		// 動いている融通があったら
		for (JsonObject aDeal : deals) {
			if (Deal.isMaster(aDeal)) {
				// OK if there is a master deal
				// master deal があれば OK
				return true;
			}
		}
		// NG if there is an interchange running but no master deal
		// 動いている融通があるのに master deal がないのは NG
		return false;
	}
	/**
	 * Sort multiple interchanges into a suitable order for processing.
	 * @param vertx a vertx object
	 * @param policy a POLICY object
	 * @param deals a list of DEAL objects
	 *          
	 * 複数の融通を適切な順序で処理されるよう並び替える.
	 * @param vertx vertx オブジェクト
	 * @param policy POLICY オブジェクト
	 * @param deals DEAL オブジェクトのリスト
	 */
	private static void sortDeals_(Vertx vertx, JsonObject policy, List<JsonObject> deals) {
		if (1 < deals.size()) {
			List<JsonObject> sorted = new ArrayList<>(deals);
			List<String> largeCapacityUnitIds = Policy.largeCapacityUnitIds(policy);
			if (largeCapacityUnitIds != null && !largeCapacityUnitIds.isEmpty()) {
				// Prioritize large capacity units
				// 大容量ユニット優先
				String masterSidePolicy = masterSide(vertx, policy, deals);
				for (JsonObject aDeal : deals) {
					if (!Deal.isMaster(aDeal)) {
						if (largeCapacityUnitIds.contains(Deal.slaveSideUnitId(aDeal, masterSidePolicy))) {
							// The large capacity unit is not a master deal and is not on the voltage reference side
							// master deal じゃない & 電圧リファレンス側じゃないユニットが大容量ユニットである
							if (log.isDebugEnabled()) log.debug("move non-master deal with large capacity slave side unit to first ; deal : " + Deal.dealId(aDeal));
							// → Move to the beginning
							// → 先頭に移動
							// 　Reason: We want to make this a voltage reference if possible, so move it up. We want to make the voltage reference side into a voltage reference (complicated), so give priority to the lower one
							// 　理由 : できれば電圧リファレンスをさせたいので上に持っていく. 電圧リファレンス側を電圧リファレンスにしたい ( ややこしい ) ので下のやつを優先させる
							sorted.remove(aDeal);
							sorted.add(0, aDeal);
						}
					}
				}
				for (JsonObject aDeal : deals) {
					if (!Deal.isMaster(aDeal)) {
						if (largeCapacityUnitIds.contains(Deal.masterSideUnitId(aDeal, masterSidePolicy))) {
							// The large capacity unit is not a master deal and is on the voltage reference side
							// master deal じゃない & 電圧リファレンス側ユニットが大容量ユニットである
							if (log.isDebugEnabled()) log.debug("move non-master deal with large capacity master side unit to first ; deal : " + Deal.dealId(aDeal));
							// → Also move to the beginning
							// → さらに先頭に移動
							// 　Reason: We want to make this a voltage reference if possible, so move it up. We want to make the voltage reference side into a voltage reference (complicated), so prioritize over the upper one
							// 　理由 : できれば電圧リファレンスをさせたいので上に持っていく. 電圧リファレンス側を電圧リファレンスにしたい ( ややこしい ) ので上のやつより優先させる
							sorted.remove(aDeal);
							sorted.add(0, aDeal);
						}
					}
				}
			}
			// Prioritize an interchange that is in the middle of ramping up the voltage
			// 電圧ランプアップ中の融通はもっと優先
			// The ordinary master deal comes last
			// 普通の master deal は最後
			for (JsonObject aDeal : deals) {
				if (Deal.isMaster(aDeal)) {
					if (Deal.isActivated(aDeal) && !Deal.isStarted(aDeal)) {
						// Launching with master deal
						// master deal で起動中
						// → Voltage ramp-up is definitely in progress
						// → 電圧ランプアップ中に違いない
						if (log.isDebugEnabled()) log.debug("move ramping up master deal to first ; deal : " + Deal.dealId(aDeal));
						// → Also move to the beginning
						// → さらに先頭に移動
						// 　Reason: When ramp-up has finished, we want to continue by starting another interchange. So we want to perform this processing first
						// 　理由 : ランプアップが終わった際には引き続いて他の融通を開始させたい. なので最初に処理させたい
						sorted.remove(aDeal);
						sorted.add(0, aDeal);
					} else {
						// A master deal that is flowing normally
						// 普通に融通している master deal
						if (log.isDebugEnabled()) log.debug("move master deal to last ; deal : " + Deal.dealId(aDeal));
						// → Move to the very end
						// → 一番最後に移動
						// 　Reason: We want to process this last because the voltage reference might be moved
						// 　理由 : 電圧リファレンスの移動が発生する可能性があるため最後に処理させたい
						sorted.remove(aDeal);
						sorted.add(aDeal);
					}
					break;
				}
			}
			// The one that has a stop request has the highest priority
			// 停止要求を持ったやつは最優先
			for (JsonObject aDeal : deals) {
				if (Deal.isNeedToStop(aDeal)) {
					if (log.isDebugEnabled()) log.debug("move need-to-stop deal to first ; deal : " + Deal.dealId(aDeal));
					sorted.remove(aDeal);
					sorted.add(0, aDeal);
				}
			}
			deals.clear();
			deals.addAll(sorted);
		}
	}

	////

	/**
	 * Get the ID of the unit responsible for the voltage reference.
	 * This is determined based on data collected during interchange processing.
	 * The decision is made based on a different set of data to that used in {@link jp.co.sony.csl.dcoes.apis.main.app.gridmaster.DataCollection#voltageReferenceUnitId()}.
	 * @return the ID of the voltage reference unit, if it exists.
	 *         If not, return {@code null}.
	 *         Also return {@code null} if the data has not yet been cached.
	 *          
	 * 電圧リファレンスを担っているユニットの ID を取得する.
	 * 融通処理で収集したデータを元に決定する.
	 * {@link jp.co.sony.csl.dcoes.apis.main.app.gridmaster.DataCollection#voltageReferenceUnitId()} とは別のデータで判定.
	 * @return 電圧リファレンスがある場合そのユニットの ID.
	 *         なければ {@code null}.
	 *         データがまだキャッシュされていない場合も {@code null}.
	 */
	public static String voltageReferenceUnitId() {
		JsonObject unitData = unitDataCache.jsonObject();
		if (unitData != null) {
			for (String aUnitId : unitData.fieldNames()) {
				JsonObject aUnitData = unitData.getJsonObject(aUnitId);
				DDCon.Mode aMode = DDCon.modeFromCode(JsonObjectUtil.getString(aUnitData, "dcdc", "status", "status"));
				if (DDCon.Mode.VOLTAGE_REFERENCE == aMode) {
					if (log.isInfoEnabled()) log.info("voltage reference unit : " + aUnitId);
					return aUnitId;
				}
			}
		} else {
			if (log.isWarnEnabled()) log.warn("no unit data");
		}
		if (log.isInfoEnabled()) log.info("no voltage reference unit found");
		return null;
	}

	/**
	 * Find out which side of an interchange pair sets the voltage reference.
	 * The default is the POLICY.gridMaster.voltageReferenceSide setting value, but this could be reversed by the functions of a large capacity unit, so a decision is made based on where the voltage reference actually exists.
	 * @param vertx a vertx object
	 * @param policy a POLICY object
	 * @param deals a list of DEAL objects
	 * @return dischargeUnit: The unit on the discharging side
	 *         chargeUnit: The unit on the charging side
	 * @see Policy#masterSide(JsonObject)
	 *          
	 * 融通ペアのどちら側で電圧リファレンスを立てるかを取得する.
	 * デフォルトは POLICY.gridMaster.voltageReferenceSide の設定値であるが大容量ユニット機能により逆転する可能性があるため実際の電圧リファレンスの存在場所から判定する.
	 * @param vertx vertx オブジェクト
	 * @param policy POLICY オブジェクト
	 * @param deals DEAL オブジェクトのリスト
	 * @return dischargeUnit : 送電側
	 *         chargeUnit : 受電側
	 * @see Policy#masterSide(JsonObject)
	 */
	public static String masterSide(Vertx vertx, JsonObject policy, List<JsonObject> deals) {
		// If a master deal is found, determine which of the units in this deal is the voltage reference
		// master deal を見つけたらそのどちらのユニットが電圧リファレンスかで判定する
		for (JsonObject aDeal : deals) {
			if (Deal.isMaster(aDeal)) {
				String voltageReferenceUnitId = voltageReferenceUnitId();
				if (voltageReferenceUnitId != null) {
					if (Deal.isDischargeUnit(aDeal, voltageReferenceUnitId)) {
						return "dischargeUnit";
					} else if (Deal.isChargeUnit(aDeal, voltageReferenceUnitId)) {
						return "chargeUnit";
					} else {
						ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.ERROR, "voltage reference unit found : " + voltageReferenceUnitId + " ; but not in master deal : " + aDeal);
					}
				} else {
					ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.ERROR, "no voltage reference unit found ; master deal : " + aDeal);
				}
				break;
			}
		}
		// The default is POLICY.gridMaster.voltageReferenceSide
		// デフォルトは POLICY.gridMaster.voltageReferenceSide
		return Policy.masterSide(policy);
	}

	////

	/**
	 * A class that performs interchange control.
	 * @author OES Project
	 *          
	 * 融通制御を実行するクラス.
	 * @author OES Project
	 */
	private static class DealExecution_ {
		private Vertx vertx_;
		private JsonObject policy_;
		private List<JsonObject> deals_;
		private List<JsonObject> dealsForLoop_;
		private List<String> activeDealIdsBeforeLoop_;
		/**
		 * Make an instance.
		 * @param vertx a vertx object
		 * @param policy a POLICY object
		 * @param deals a list of DEAL objects
		 *          
		 * インスタンスを作成する.
		 * @param vertx vertx オブジェクト
		 * @param policy POLICY オブジェクト
		 * @param deals DEAL オブジェクトのリスト
		 */
		private DealExecution_(Vertx vertx, JsonObject policy, List<JsonObject> deals) {
			vertx_ = vertx;
			policy_ = policy;
			deals_ = deals;
			dealsForLoop_ = new ArrayList<JsonObject>(deals_);
			activeDealIdsBeforeLoop_ = activeDealIds_();
		}
		private void doLoop_(Handler<AsyncResult<Void>> completionHandler) {
			if (ErrorCollection.hasErrors()) {
//   			If a GLOBAL ERROR has been raised, we probably don't need to continue with the interchange processing
//				GLOBAL ERROR が出ているのなら融通処理を続ける必要はないだろう
				String msg = "global error exists";
				if (log.isInfoEnabled()) log.info(msg);
				completionHandler.handle(Future.failedFuture(msg));
			} else if (dealsForLoop_.isEmpty()) {
				// Interchange loop has ended
				// 融通ループが終わった
				if (Policy.gridVoltageOptimizationEnabled(policy_)) {
					// If POLICY.gridMaster.gridVoltageOptimization.enabled is true, perform grid voltage optimIzation processing
					// POLICY.gridMaster.gridVoltageOptimization.enabled が true ならグリッド電圧最適化処理を行う
					doGridVoltageOptimization_(vertx_, completionHandler);
				} else {
					completionHandler.handle(Future.succeededFuture());
				}
			} else {
				// Extract one DEAL object
				// DEAL オブジェクトを一つ取り出し
				JsonObject aDeal = dealsForLoop_.remove(0);
				List<JsonObject> otherDeals = new ArrayList<JsonObject>(deals_);
				otherDeals.remove(aDeal);
				// Allocate processing according to the state of this DEAL object
				// DEAL オブジェクトの状態に応じた処理を割り当て
				final AbstractDealExecution exec;
				if (Deal.isDeactivated(aDeal)) {
					// Perform deletion processing if finished
					// 終了済みなら削除処理
					exec = new DealDisposition(vertx_, policy_, aDeal, otherDeals);
				} else if (Deal.isStopped(aDeal)) {
					// If stopped, end processing
					// 停止済みなら終了処理
					exec = new DealDeactivation(vertx_, policy_, aDeal, otherDeals);
				} else if (Deal.isNeedToStop(aDeal)) {
					// Perform abnormal termination processing if there is a stop request
					// 停止要求を持っていれば異常終了処理
					exec = new DealAbortion(vertx_, policy_, aDeal, otherDeals, Deal.needToStopReasons(aDeal).encode());
				} else if (Deal.isStarted(aDeal)) {
					// Perform summing process if started
					// 開始済みなら積算処理
					exec = new DealCumulation(vertx_, policy_, aDeal, otherDeals);
				} else if (Deal.isActivated(aDeal)) {
					// If started
					// 起動済みなら
					if (Deal.isMaster(aDeal)) {
						// If this is a master
						// マスタなら
						if (Deal.isRampedUp(aDeal)) {
							// If the voltage has been ramped up, perform the voltage reference privilege acquisition process
							// 電圧ランプアップ済みなら電圧リファレンス権限取得処理
							exec = new DealMasterAuthorization(vertx_, policy_, aDeal, otherDeals);
						} else {
							// If the voltage is still ramping up, perform the voltage ramp-up stand-by process
							// 電圧ランプアップ中なら電圧ランプアップ待ち処理
							exec = new DealRampingUp(vertx_, policy_, aDeal, otherDeals);
						}
					} else {
						// If this is not a master, perform current compensation processing
						// マスタじゃなければ電流コンペンセイション処理
						exec = new DealCompensation(vertx_, policy_, aDeal, otherDeals);
					}
				} else {
					// Since this is brand new, perform start-up processing
					// まっさらなので起動処理
					exec = new DealActivation(vertx_, policy_, aDeal, otherDeals);
				}
				// Execute processing
				// 処理を実行する
				exec.execute(resExec -> {
					if (resExec.succeeded()) {
						// If there is no problem, proceed to the next DEAL object
						// 問題なければ次の DEAL オブジェクトに進む
						doLoop_(completionHandler);
					} else {
						// If a problem has occurred, perform abnormal termination of the DEAL object
						// 問題が起きたらその DEAL オブジェクトを異常終了処理する
						new DealAbortion(exec, resExec.cause().getMessage()).execute(resAbort -> {
							if (resAbort.succeeded()) {
								// OK if abnormal termination is successful
								// 異常終了が成功したら OK
							} else {
								// If the abnormal termination fails
								// 異常終了が失敗したら
								String msg = "deal abortion failed";
								// Perform error processing by sending a local error to the units at both ends
								// 両端ユニットに対しローカルエラーを送りつけエラー処理させる
								// → Forcibly stop at the worst timeout
								// → 最悪タイムアウトで強制的に止まる
								ErrorUtil.report(vertx_, Deal.chargeUnitId(aDeal), Error.Category.HARDWARE, Error.Extent.LOCAL, Error.Level.ERROR, msg);
								ErrorUtil.report(vertx_, Deal.dischargeUnitId(aDeal), Error.Category.HARDWARE, Error.Extent.LOCAL, Error.Level.ERROR, msg);
							}
							// Proceed to the next DEAL object
							// 次の DEAL オブジェクトに進む
							doLoop_(completionHandler);
						});
					}
				});
			}
		}
		/**
		 * Get a list of working interchange IDs.
		 * Used to judge whether or not there are any interchanges whose state has changed after performing an entire round of interchange processing.
		 * (Required in order to perform grid voltage optimization processing if changes have occurred)
		 * @return a list of the IDs of working interchanges
		 *          
		 * 動いている融通の ID のリストを取得する.
		 * 融通処理ひとまわしの前後で動いている融通の状態が変わったかを判定するために使う.
		 * ( 変わってたらグリッド電圧最適化処理を実行するため )
		 * @return 動いている融通の ID のリスト
		 */
		private List<String> activeDealIds_() {
			List<String> result = new ArrayList<>();
			for (JsonObject aDeal : deals_) {
				if (Deal.bothSideUnitsMustBeActive(aDeal)) {
					result.add(Deal.dealId(aDeal));
				}
			}
			return result;
		}
		/**
		 * Perform grid voltage optimization processing.
		 * @param vertx a vertx object
		 * @param completionHandler the completion handler
		 *          
		 * グリッド電圧最適化処理を実行する.
		 * @param vertx vertx オブジェクト
		 * @param completionHandler the completion handler
		 */
		private void doGridVoltageOptimization_(Vertx vertx, Handler<AsyncResult<Void>> completionHandler) {
			boolean shouldDoGridVoltageOptimization = true;
			List<String> activeDealIdsAfterLoop = activeDealIds_();
			if (activeDealIdsBeforeLoop_.size() == activeDealIdsAfterLoop.size()) {
				Collections.sort(activeDealIdsBeforeLoop_);
				Collections.sort(activeDealIdsAfterLoop);
				if (activeDealIdsBeforeLoop_.equals(activeDealIdsAfterLoop)) {
					// No need to do this if the state of an interchange operating before and after a single round of processing is the same
					// 融通処理ひと回しの前後で動いている融通の状態が同じなら実行する必要なし
					shouldDoGridVoltageOptimization = false;
				}
			}
			if (shouldDoGridVoltageOptimization) {
				GridVoltageOptimization.execute(vertx, deals_, completionHandler);
			} else {
				completionHandler.handle(Future.succeededFuture());
			}
		}
	}

}
