package jp.co.sony.csl.dcoes.apis.main.app.gridmaster.deal_execution;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import jp.co.sony.csl.dcoes.apis.common.Deal;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.util.DateTimeUtil;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectUtil;
import jp.co.sony.csl.dcoes.apis.main.app.PolicyKeeping;
import jp.co.sony.csl.dcoes.apis.main.app.controller.util.DDCon;
import jp.co.sony.csl.dcoes.apis.main.app.mediator.util.DealUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorExceptionUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

/**
 * Wait for the grid voltage to rise.
 * @author OES Project
 *          
 * グリッド電圧が上がるのを待つ.
 * @author OES Project
 */
public class DealRampingUp extends AbstractDealExecution {
	private static final Logger log = LoggerFactory.getLogger(DealRampingUp.class);

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
	public DealRampingUp(Vertx vertx, JsonObject policy, JsonObject deal, List<JsonObject> otherDeals) {
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
	public DealRampingUp(AbstractDealExecution other) {
		super(other);
	}

	@Override protected void doExecute(Handler<AsyncResult<Void>> completionHandler) {
		if (DDCon.Mode.VOLTAGE_REFERENCE == masterSideUnitDDConMode_()) {
			if (DDCon.Mode.WAIT == slaveSideUnitDDConMode_()) {
				Float gridVoltageV = JsonObjectUtil.getFloat(masterSideUnitData_(), "dcdc", "meter", "vg");
				Float targetGridVoltageV = JsonObjectUtil.getFloat(masterSideUnitData_(), "dcdc", "vdis", "dvg");
				if (log.isInfoEnabled()) log.info("vg : " + gridVoltageV + " ; target : " + targetGridVoltageV);
				if (gridVoltageV != null && targetGridVoltageV != null) {
					Float gridVoltageAllowanceV = PolicyKeeping.cache().getFloat("gridVoltageAllowanceV");
					if (gridVoltageAllowanceV != null) {
						float gridVoltageMinV = targetGridVoltageV - gridVoltageAllowanceV;
						if (gridVoltageMinV <= gridVoltageV) {
							// When the measured grid voltage exceeds the specified value minus the error tolerance (POLICY.gridVoltageAllowanceV )
							// グリッド電圧測定値が 指定値 - 誤差許容値 ( POLICY.gridVoltageAllowanceV ) 以上になったら
							// Put the interchange information in the "rampUp complete" state
							// 融通情報を "rampUp" 完了状態にし
							rampUpDeal_(resRampUpDeal -> {
								if (resRampUpDeal.succeeded()) {
									// Proceed to the voltage reference privilege acquisition process
									// 電圧リファレンス権限獲得処理に移行する
									new DealMasterAuthorization(this).execute(completionHandler);
								} else {
									completionHandler.handle(resRampUpDeal);
								}
							});
						} else {
							LocalDateTime currentDateTime = DateTimeUtil.toLocalDateTime(referenceDateTimeString_());
							LocalDateTime activateDateTime = JsonObjectUtil.getLocalDateTime(deal_, "activateDateTime");
							Duration duration = Duration.between(activateDateTime, currentDateTime);
							long durationMsec = duration.toMillis();
							Long timeoutMsec = PolicyKeeping.cache().getLong("controller", "dcdc", "voltageReference", "rampUp", "first", "timeoutMsec");
							if (timeoutMsec != null) {
								if (durationMsec < timeoutMsec) {
									// If the time since the voltage reference was started is less than the timeout duration (POLICY.controller.dcdc.voltageReference.rampUp.first.timeoutMsec )
									// 電圧リファレンスを起動してからの時間がタイムアウト時間 ( POLICY.controller.dcdc.voltageReference.rampUp.first.timeoutMsec ) 未満なら
									if (log.isInfoEnabled()) log.info("ramping up ...");
									// Carry on waiting
									// 引き続き待つ
									completionHandler.handle(Future.succeededFuture());
								} else {
									// Timeout
									// タイムアウト
									ErrorUtil.reportAndFail(vertx_, Error.Category.HARDWARE, Error.Extent.GLOBAL, Error.Level.ERROR, "ramping up timed out; activateDateTime : " + Deal.activateDateTime(deal_) + ", currentDateTime : " + referenceDateTimeString_(), completionHandler);
								}
							} else {
								ErrorUtil.reportAndFail(vertx_, Error.Category.USER, Error.Extent.LOCAL, Error.Level.ERROR, "data deficiency; POLICY.controller.dcdc.voltageReference.rampUp.first.timeoutMsec : " + timeoutMsec, completionHandler);
							}
						}
					} else {
						ErrorUtil.reportAndFail(vertx_, Error.Category.USER, Error.Extent.LOCAL, Error.Level.ERROR, "data deficiency; POLICY.gridVoltageAllowanceV : " + gridVoltageAllowanceV, completionHandler);
					}
				} else {
					ErrorUtil.reportAndFail(vertx_, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.WARN, "no dcdc.meter.vg and/or dcdc.vdis.dvg value in unit data : " + masterSideUnitData_(), completionHandler);
				}
			} else {
				ErrorUtil.reportAndFail(vertx_, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.WARN, "invalid slave side unit status; unit : " + slaveSideUnitId_() + ", mode : " + slaveSideUnitDDConMode_(), completionHandler);
			}
		} else {
			ErrorUtil.reportAndFail(vertx_, Error.Category.LOGIC, Error.Extent.GLOBAL, Error.Level.WARN, "invalid master side unit status; unit : " + masterSideUnitId_() + ", mode : " + masterSideUnitDDConMode_(), completionHandler);
		}
	}

	private void rampUpDeal_(Handler<AsyncResult<Void>> completionHandler) {
		DealUtil.rampUp(vertx_, deal_, referenceDateTimeString_(), resRampUp -> ErrorExceptionUtil.reportIfNeedAndHandle(vertx_, resRampUp, completionHandler));
	}

}
