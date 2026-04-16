package jp.co.sony.csl.dcoes.apis.main.app.gridmaster.deal_execution;

import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import jp.co.sony.csl.dcoes.apis.common.Deal;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.ServiceAddress;
import jp.co.sony.csl.dcoes.apis.common.util.NumberUtil;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectUtil;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.ReplyFailureUtil;
import jp.co.sony.csl.dcoes.apis.main.app.controller.util.DDCon;
import jp.co.sony.csl.dcoes.apis.main.app.gridmaster.main_loop.DealExecution;
import jp.co.sony.csl.dcoes.apis.main.app.mediator.util.DealUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ApisConfig;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorExceptionUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;
import jp.co.sony.csl.dcoes.apis.main.util.Policy;

/**
 * An object representing a specific interchange process
 * Created and called by {@link jp.co.sony.csl.dcoes.apis.main.app.gridmaster.main_loop.DealExecution DealExecution}.
 * @author OES Project
 *          
 * 具体的な融通処理の親玉.
 * {@link jp.co.sony.csl.dcoes.apis.main.app.gridmaster.main_loop.DealExecution DealExecution} で生成され呼ばれる.
 * @author OES Project
 */
public abstract class AbstractDealExecution {
	private static final Logger log = LoggerFactory.getLogger(AbstractDealExecution.class);

	private boolean initialized_ = false;

	protected Vertx vertx_;
	protected JsonObject policy_;
	protected JsonObject deal_;
	protected List<JsonObject> otherDeals_;

	protected String dealId_;
	protected String dischargeUnitId_;
	protected String chargeUnitId_;
	protected JsonObject dischargeUnitData_;
	protected JsonObject chargeUnitData_;

	protected String masterSide_;
	protected String referenceSide_;

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
	public AbstractDealExecution(Vertx vertx, JsonObject policy, JsonObject deal, List<JsonObject> otherDeals) {
		vertx_ = vertx;
		policy_ = policy;
		deal_ = deal;
		otherDeals_ = otherDeals;
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
	public AbstractDealExecution(AbstractDealExecution other) {
		initialized_ = true;

		vertx_ = other.vertx_;
		policy_ = other.policy_;
		deal_ = other.deal_;
		otherDeals_ = other.otherDeals_;

		dealId_ = other.dealId_;
		dischargeUnitId_ = other.dischargeUnitId_;
		chargeUnitId_ = other.chargeUnitId_;
		dischargeUnitData_ = other.dischargeUnitData_;
		chargeUnitData_ = other.chargeUnitData_;

		masterSide_ = other.masterSide_;
		referenceSide_ = other.referenceSide_;
	}

	protected String masterSideUnitId_() {
		return ("dischargeUnit".equals(masterSide_)) ? dischargeUnitId_ : chargeUnitId_;
	}
	protected String slaveSideUnitId_() {
		return ("dischargeUnit".equals(masterSide_)) ? chargeUnitId_ : dischargeUnitId_;
	}
	protected JsonObject masterSideUnitData_() {
		return ("dischargeUnit".equals(masterSide_)) ? dischargeUnitData_ : chargeUnitData_;
	}
	protected JsonObject slaveSideUnitData_() {
		return ("dischargeUnit".equals(masterSide_)) ? chargeUnitData_ : dischargeUnitData_;
	}

	protected DDCon.Mode masterSideUnitDDConMode_() {
		return DDCon.modeFromCode(JsonObjectUtil.getString(masterSideUnitData_(), "dcdc", "status", "status"));
	}
	protected DDCon.Mode slaveSideUnitDDConMode_() {
		return DDCon.modeFromCode(JsonObjectUtil.getString(slaveSideUnitData_(), "dcdc", "status", "status"));
	}

	protected DDCon.Mode dischargeUnitDDConMode_() {
		return DDCon.modeFromCode(JsonObjectUtil.getString(dischargeUnitData_, "dcdc", "status", "status"));
	}
	protected DDCon.Mode chargeUnitDDConMode_() {
		return DDCon.modeFromCode(JsonObjectUtil.getString(chargeUnitData_, "dcdc", "status", "status"));
	}

	protected String referenceUnitId_() {
		return ("dischargeUnit".equals(referenceSide_)) ? dischargeUnitId_ : chargeUnitId_;
	}
	protected JsonObject referenceUnitData_() {
		return ("dischargeUnit".equals(referenceSide_)) ? dischargeUnitData_ : chargeUnitData_;
	}
	protected String referenceDateTimeString_() {
		return ("dischargeUnit".equals(referenceSide_)) ? JsonObjectUtil.getString(dischargeUnitData_, "time") : JsonObjectUtil.getString(chargeUnitData_, "time");
	}
	protected Float referenceUnitWb_() {
		return ("dischargeUnit".equals(referenceSide_)) ? NumberUtil.negativeValue(JsonObjectUtil.getFloat(dischargeUnitData_, "dcdc", "meter", "wb")) : JsonObjectUtil.getFloat(chargeUnitData_, "dcdc", "meter", "wb");
	}
//	protected Float referenceUnitIg_() {
//		return ("dischargeUnit".equals(referenceSide_)) ? NumberUtil.negativeValue(JsonObjectUtil.getFloat(dischargeUnitData_, "dcdc", "meter", "ig")) : JsonObjectUtil.getFloat(chargeUnitData_, "dcdc", "meter", "ig");
//	}

	/**
	 * Fetch the Master Deal.
	 * If it does not exist, return {@code null}
	 * @return the DEAL object corresponding to the Master Deal
	 *          
	 * Master Deal を取得する.
	 * なければ {@code null}
	 * @return Master Deal である DEAL オブジェクト
	 */
	protected JsonObject masterDeal_() {
		if (Deal.isMaster(deal_)) return deal_;
		for (JsonObject aDeal : otherDeals_) {
			if (Deal.isMaster(aDeal)) return aDeal;
		}
		return null;
	}

	/**
	 * Fetch a list of DEAL objects in which the unit specified by {@code unitId} participates, except for the DEAL object to be processed.
	 * @param unitId the unit ID
	 * @return a list of DEAL objects in which the unit specified by {@code unitId} participates, except for the DEAL object to be processed
	 *          
	 * {@code unitId} で指定したユニットが参加している DEAL のうち処理対象の DEAL を除くリストを取得する.
	 * @param unitId ユニット ID
	 * @return {@code unitId} で指定したユニットが参加している DEAL のうち処理対象の DEAL を除くリスト
	 */
	protected List<JsonObject> otherDeals_(String unitId) {
		List<JsonObject> result = new ArrayList<>();
		for (JsonObject aDeal : otherDeals_) {
			if (Deal.isInvolved(aDeal, unitId)) {
				result.add(aDeal);
			}
		}
		return result;
	}
	/**
	 * Fetch the total value of the interchange currents of DEAL objects in which the unit specified by {@code unitId} participates, except for the DEAL object to be processed.
	 * @param unitId the unit ID
	 * @return the total value [A] of the interchange currents of DEAL objects in which the unit specified by {@code unitId} participates, except for the DEAL object to be processed
	 *          
	 * {@code unitId} で指定したユニットが参加している DEAL のうち処理対象の DEAL を除く融通電流の合計値を取得する.
	 * @param unitId ユニット ID
	 * @return {@code unitId} で指定したユニットが参加している DEAL のうち処理対象の DEAL を除く融通電流の合計値 [A]
	 */
	protected float sumOfOtherDealCompensatedGridCurrentAs_(String unitId) {
		float result = 0F;
		for (JsonObject aDeal : otherDeals_(unitId)) {
			if (Deal.bothSideUnitsMustBeActive(aDeal)) {
				Float value = Deal.compensatedGridCurrentA(aDeal, unitId);
				if (value != null) {
					result += value;
				}
			}
		}
		return result;
	}

	/**
	 * Decide whether or not this is a good time to reverse the assignment of the voltage reference to the discharging side or the charging side.
	 * Reversal is OK unless the {@link Deal#isTransitionalState(JsonObject) interchange is in a transient state}.
	 * @return {@code true} if reversal is possible
	 *          
	 * 送電側と受電側のどちらに電圧リファレンスを立てるかを反転させて良いタイミングか否かを判定する.
	 * {@link Deal#isTransitionalState(JsonObject) 融通が過渡状態} でなければ反転 OK.
	 * @return 反転可なら {@code true}
	 */
	protected boolean canFlipMasterSide_() {
//		if (Deal.isTransitionalState(deal_)) {
//			return false;
//		}
		for (JsonObject aDeal : otherDeals_) {
			if (Deal.isTransitionalState(aDeal)) {
				return false;
			}
		}
		return true;
	}
	/**
	 * Inverts the assignment of a voltage reference between the discharging side and the charging side.
	 *          
	 * 送電側と受電側のどちらに電圧リファレンスを立てるかを反転させる.
	 */
	protected void flipMasterSide_() {
		masterSide_ = ("dischargeUnit".equals(masterSide_)) ? "chargeUnit" : "dischargeUnit";
	}
	/**
	 * If possible, flip the voltage reference assignment between the discharging side and the charging side.
	 * @return {@code true} if the reversal is successful
	 *          
	 * 可能であれば送電側と受電側のどちらに電圧リファレンスを立てるかを反転させる.
	 * @return 反転成功なら {@code true}
	 */
	protected boolean tryToFlipMasterSide_() {
		boolean result = canFlipMasterSide_();
		if (result) {
			flipMasterSide_();
		}
		return result;
	}

	////

	protected void controlMasterSideUnitDcdc_(String command, JsonObject params, Handler<AsyncResult<Void>> completionHandler) {
		controlDcdc_(masterSideUnitId_(), command, params, completionHandler);
	}
	protected void controlSlaveSideUnitDcdc_(String command, JsonObject params, Handler<AsyncResult<Void>> completionHandler) {
		controlDcdc_(slaveSideUnitId_(), command, params, completionHandler);
	}
	protected void controlDcdc_(String unitId, String command, JsonObject params, Handler<AsyncResult<Void>> completionHandler) {
		JsonObject operation = new JsonObject().put("command", command);
		if (params != null) {
			operation.put("params", params);
		}
		// Send the ID of this unit for GridMaster interlock
		// GridMaster インタロックのため自ユニットの ID を送る
		DeliveryOptions options = new DeliveryOptions().addHeader("gridMasterUnitId", ApisConfig.unitId());
		vertx_.eventBus().<JsonObject>send(ServiceAddress.Controller.deviceControlling(unitId), operation, options, rep -> {
			if (rep.succeeded()) {
				// Merge various resulting device control states
				// 結果のデバイス制御状態をもろもろマージする
				mergeUnitDcdc_(unitId, rep.result().body());
				completionHandler.handle(Future.succeededFuture());
			} else {
				if (ReplyFailureUtil.isRecipientFailure(rep)) {
					completionHandler.handle(Future.failedFuture(rep.cause()));
				} else {
					ErrorUtil.reportAndFail(vertx_, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR, "Communication failed on EventBus", rep.cause(), completionHandler);
				}
			}
		});
	}

	/**
	 * Update the cache by reacquiring the device control state of the unit specified by {@code unitId}.
	 * Receive the results with the {@link AsyncResult#result()} method of completionHandler.
	 * @param unitId ID of the unit to be reacquired
	 * @param completionHandler the completion handler
	 *          
	 * {@code unitId} で指定したユニットのデバイス制御状態を取得しなおしキャッシュを更新する.
	 * completionHandler の {@link AsyncResult#result()} で結果を受け取る.
	 * @param unitId 取得しなおすユニットの ID
	 * @param completionHandler the completion handler
	 */
	protected void updateUnitDcdcStatus_(String unitId, Handler<AsyncResult<Void>> completionHandler) {
		DeliveryOptions options = new DeliveryOptions();
		options.addHeader("gridMasterUnitId", ApisConfig.unitId());
		options.addHeader("urgent", "true");
		vertx_.eventBus().<JsonObject>send(ServiceAddress.Controller.unitDeviceStatus(unitId), null, options, rep -> {
			if (rep.succeeded()) {
				mergeUnitDcdc_(unitId, rep.result().body());
				completionHandler.handle(Future.succeededFuture());
			} else {
				if (ReplyFailureUtil.isRecipientFailure(rep)) {
					completionHandler.handle(Future.failedFuture(rep.cause()));
				} else {
					ErrorUtil.reportAndFail(vertx_, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR, "Communication failed on EventBus", rep.cause(), completionHandler);
				}
			}
		});
	}

	/**
	 * Merge the device control state of the unit specified by {@code unitId} into various caches.
	 * @param unitId the unit ID
	 * @param dcdc the device control state
	 *          
	 * {@code unitId} で指定したユニットのデバイス制御状態を各種キャッシュにマージする.
	 * @param unitId ユニット ID
	 * @param dcdc デバイス制御状態
	 */
	private void mergeUnitDcdc_(String unitId, JsonObject dcdc) {
		// Main cache for interchange processing
		// 融通処理のメインキャッシュ
		DealExecution.unitDataCache.mergeIn(dcdc, unitId, "dcdc");
		if (unitId.equals(dischargeUnitId_)) {
			// Discharge unit cache
			// 送電側キャッシュ
			if (dischargeUnitData_ != null) {
				JsonObjectUtil.mergeIn(dischargeUnitData_, dcdc, "dcdc");
			} else {
				dischargeUnitData_ = new JsonObject().put("dcdc", dcdc);
			}
		} else if (unitId.equals(chargeUnitId_)) {
			// Charge unit cache
			// 受電側キャッシュ
			if (chargeUnitData_ != null) {
				JsonObjectUtil.mergeIn(chargeUnitData_, dcdc, "dcdc");
			} else {
				chargeUnitData_ = new JsonObject().put("dcdc", dcdc);
			}
		}
	}

	////

	/**
	 * Actual processing contents.
	 * Implemented in subclasses.
	 * @param completionHandler the completion handler
	 *          
	 * 実際の処理の内容.
	 * サブクラスで実装する.
	 * @param completionHandler the completion handler
	 */
	protected abstract void doExecute(Handler<AsyncResult<Void>> completionHandler);

	/**
	 * Perform interchange processing.
	 * The actual processing is performed by the {@link #doExecute (Handler)} of subclasses.
	 * @param completionHandler the completion handler
	 *          
	 * 融通処理を実行する.
	 * 実際の処理はサブクラスの {@link #doExecute(Handler)} で実行する.
	 * @param completionHandler the completion handler
	 */
	public void execute(Handler<AsyncResult<Void>> completionHandler) {
		if (!initialized_) {
			// Start with initialization
			// まず初期化する
			dealId_ = Deal.dealId(deal_);
			if (log.isInfoEnabled()) log.info("dealId : " + dealId_);
			// First, retrieve the DEAL object from shared memory
			// まず共有メモリから DEAL オブジェクトを取り出す
			DealUtil.get(vertx_, dealId_, res -> {
				if (res.succeeded()) {
					// Substitute the contents of a locally retained DEAL object
					// ローカルに保持している DEAL オブジェクトの中身を置き換える
					deal_.clear().mergeIn(res.result());
					// Generate various internal states
					// 各種内部状態を生成する
					dischargeUnitId_ = Deal.dischargeUnitId(deal_);
					chargeUnitId_ = Deal.chargeUnitId(deal_);
					if (log.isInfoEnabled()) log.info("dischargeUnitId : " + dischargeUnitId_ + ", chargeUnitId : " + chargeUnitId_);
					// Fetch the raw unit data of both units
					// 両ユニットのユニットデータをナマ取得する
					Future<JsonObject> dischargeUnitDataFuture = Future.future();
					Future<JsonObject> chargeUnitDataFuture = Future.future();
					unitData_(dischargeUnitId_, dischargeUnitDataFuture);
					unitData_(chargeUnitId_, chargeUnitDataFuture);
					CompositeFuture.<JsonObject, JsonObject>all(dischargeUnitDataFuture, chargeUnitDataFuture).setHandler(ar -> {
						if (ar.succeeded()) {
							dischargeUnitData_ = ar.result().resultAt(0);
							chargeUnitData_ = ar.result().resultAt(1);
							// Update the main cache for interchange processing with the acquired unit data
							// 融通処理のメインキャッシュを取得したユニットデータで更新する
							DealExecution.unitDataCache.mergeIn(dischargeUnitData_, dischargeUnitId_);
							DealExecution.unitDataCache.mergeIn(chargeUnitData_, chargeUnitId_);
							masterSide_ = masterSide_();
							if (log.isInfoEnabled()) log.info("master side : " + masterSide_);
							referenceSide_ = Policy.dealReferenceSide(policy_);
							if (log.isInfoEnabled()) log.info("reference side : " + referenceSide_);
							// Finally perform the actual processing
							// やっと実処理
							doExecute(completionHandler);
						} else {
							completionHandler.handle(Future.failedFuture(ar.cause()));
						}
					});
				} else {
					ErrorExceptionUtil.reportIfNeedAndFail(vertx_, res.cause(), completionHandler);
				}
			});
		} else {
			// Actual processing performed straight away without requiring initialization
			// 初期化不要でいきなり実処理
			doExecute(completionHandler);
		}
	}
	/**
	 * Find out if the voltage reference is on the discharging side or the charging side.
	 * @return {@code dischargeUnit}: discharging side
	 *         {@code chargeUnit}: charging side
	 *          
	 * 送電側と受電側のどちらが電圧リファレンス側なのかを取得する.
	 * @return {@code dischargeUnit} : 送電側
	 *         {@code chargeUnit} : 受電側
	 */
	private String masterSide_() {
		List<JsonObject> deals = new ArrayList<>(otherDeals_);
		deals.add(deal_);
		return DealExecution.masterSide(vertx_, policy_, deals);
	}
	/**
	 * Fetch raw data from the unit specified by {@code unitId}
	 * Receive the results with the {@link AsyncResult#result()} method of completionHandler.
	 * @param unitId the unit ID
	 * @param completionHandler the completion handler
	 *          
	 * {@code unitId} で指定したユニットのユニットデータをユニットからナマ取得する.
	 * completionHandler の {@link AsyncResult#result()} で結果を受け取る.
	 * @param unitId ユニット ID
	 * @param completionHandler the completion handler
	 */
	private void unitData_(String unitId, Handler<AsyncResult<JsonObject>> completionHandler) {
		DeliveryOptions options = new DeliveryOptions();
		options.addHeader("gridMasterUnitId", ApisConfig.unitId());
		options.addHeader("urgent", "true");
		vertx_.eventBus().<JsonObject>send(ServiceAddress.Controller.unitData(unitId), null, options, rep -> {
			if (rep.succeeded()) {
				completionHandler.handle(Future.succeededFuture(rep.result().body()));
			} else {
				if (ReplyFailureUtil.isRecipientFailure(rep)) {
					completionHandler.handle(Future.failedFuture(rep.cause()));
				} else {
					ErrorUtil.reportAndFail(vertx_, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR, "Communication failed on EventBus", rep.cause(), completionHandler);
				}
			}
		});
	}

	////

	/**
	 * Test function.
	 * Find out if {@code true} is included in the {@code category} → {@code name} hierarchy within a DEAL object.
	 * If so, fail the {@code completionHandler}.
	 * @param category a string representing the test category
	 * @param name a string representing the name of the test
	 * @param completionHandler the completion handler to handle failed tests
	 * @return {@code true} if {@code true} is included in the {@code category} → {@code name} hierarchy of the DEAL object
	 *          
	 * テスト用の機能.
	 * DEAL オブジェクト中に {@code category} → {@code name} の階層で {@code true} が含まれているかを取得する.
	 * また含まれていた場合は {@code completionHandler} を fail させる.
	 * @param category テストカテゴリ文字列
	 * @param name テスト名文字列
	 * @param completionHandler fail 対象 completion handler
	 * @return DEAL オブジェクト中に {@code category} → {@code name} の階層で {@code true} が含まれていたら {@code true}
	 */
	protected boolean testFeature_failIfNeed_(String category, String name, Handler<AsyncResult<Void>> completionHandler) {
		if (JsonObjectUtil.getBoolean(deal_, Boolean.FALSE, "testFeature", category, name)) {
			ErrorExceptionUtil.logAndFail(Error.Category.USER, Error.Extent.GLOBAL, Error.Level.WARN, "TEST FEATURE : " + category + " / " + name, completionHandler);
			return true;
		}
		return false;
	}
	/**
	 * Test function.
	 * Fail the {@code completionHandler} if {@code res} is successful and {@code true} is included in the {@code category} → {@code name} hierarchy of a DEAL object.
	 * @param res The result of the processing performed before an error was generated in the test function. If the result is "failed", do nothing
	 * @param category a string representing the test category
	 * @param name a string representing the name of the test
	 * @param completionHandler the completion handler to handle failed tests
	 *          
	 * テスト用の機能.
	 * {@code res} が成功しておりかつ DEAL オブジェクト中に {@code category} → {@code name} の階層で {@code true} が含まれている場合 {@code completionHandler} を fail させる.
	 * @param res テスト機能でエラーを発生させる前に実行された処理の結果. これが failed なら何もしない
	 * @param category テストカテゴリ文字列
	 * @param name テスト名文字列
	 * @param completionHandler fail 対象 completion handler
	 */
	protected void testFeature_failIfNeed_(AsyncResult<Void> res, String category, String name, Handler<AsyncResult<Void>> completionHandler) {
		if (res.succeeded() && testFeature_failIfNeed_(category, name, completionHandler)) return;
		completionHandler.handle(res);
	}

}
