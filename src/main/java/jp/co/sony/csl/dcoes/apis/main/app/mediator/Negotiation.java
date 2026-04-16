package jp.co.sony.csl.dcoes.apis.main.app.mediator;

import java.util.ArrayList;
import java.util.List;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.ServiceAddress;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.ReplyFailureUtil;
import jp.co.sony.csl.dcoes.apis.main.app.PolicyKeeping;
import jp.co.sony.csl.dcoes.apis.main.util.ApisConfig;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

/**
 * A Verticle that performs interchange negotiation.
 * 1. Broadcast a request issued by this unit to all other units and wait for them to return "accept" responses
 * 2. Pass these responses to the User service to receive appropriate "accept" responses
 * 3. Combine the original request with the selected response to create interchange information, and request that it is registered
 * @author OES Project
 *          
 * 融通交渉を実行する Verticle.
 * 1. 自ユニットから発せられたリクエストを全ユニットにブロードキャストしアクセプトが返ってくるのを待つ
 * 2. 返ってきたアクセプト群を User サービスに渡して適切なアクセプトを受け取る
 * 3. 元のリクエストと選ばれたアクセプトを合わせて融通情報を作成し登録を依頼する
 * @author OES Project
 */
public class Negotiation extends AbstractVerticle {
	private static final Logger log = LoggerFactory.getLogger(Negotiation.class);

	/**
	 * Default time to wait for responses after issuing a request [ms].
	 * Value: {@value}.
	 *          
	 * リクエストを発してからレスポンスを待つ時間のデフォルト値 [ms].
	 * 値は {@value}.
	 */
	private static final Long DEFAULT_NEGOTIATION_TIMEOUT_MSEC = 2000L;

	private JsonObject request_;

	/**
	 * Create an instance.
	 * @param request an interchange request
	 *          
	 * インスタンスを生成する.
	 * @param request 融通リクエスト
	 */
	public Negotiation(JsonObject request) {
		request_ = request;
	}

	/**
	 * Called at startup.
	 * Launches the {@link io.vertx.core.eventbus.EventBus} service.
	 * Publishes interchange requests and the addresses of launched services.
	 * After waiting for a fixed period of time, processes the returned responses and undeploys itself.
	 * @param startFuture {@inheritDoc}
	 * @throws Exception {@inheritDoc}
	 *          
	 * 起動時に呼び出される.
	 * {@link io.vertx.core.eventbus.EventBus} サービスを起動する.
	 * 起動したサービスのアドレスと融通リクエストを publish する.
	 * 一定時間待った後返ってきたレスポンス群を処理し自身を undeploy する.
	 * @param startFuture {@inheritDoc}
	 * @throws Exception {@inheritDoc}
	 */
	@Override public void start(Future<Void> startFuture) throws Exception {
		startAcceptService_(resAccept -> {
			if (resAccept.succeeded()) {
				if (log.isTraceEnabled()) log.trace("started : " + deploymentID());
				startFuture.complete();
			} else {
				startFuture.fail(resAccept.cause());
			}
		});
	}

	/**
	 * Called when stopped.
	 * @throws Exception {@inheritDoc}
	 *          
	 * 停止時に呼び出される.
	 * @throws Exception {@inheritDoc}
	 */
	@Override public void stop() throws Exception {
		if (log.isTraceEnabled()) log.trace("stopped : " + deploymentID());
	}

	////

	private String negotiationId_() {
		return deploymentID();
	}

	private void startAcceptService_(Handler<AsyncResult<Void>> completionHandler) {
		// Use this unit's deploymentID as a disposable reply address
		// 使い捨ての返信用アドレスとして自身の deploymentID を使う
		String replyAddress = deploymentID();
		List<JsonObject> accepts = new ArrayList<>();
		// Open up an EventBus connection with a disposable address
		// 使い捨てのアドレスで EventBus の口を開く
		MessageConsumer<JsonObject> acceptConsumer = vertx.eventBus().<JsonObject>consumer(replyAddress);
		// Register a receiver process
		// 受信処理を登録する
		acceptConsumer.handler(rep -> {
			// When a message is received
			// メッセージを受け取ったら
			JsonObject anAccept = rep.body();
			// Check the unit ID to see whether or not it is a member defined in POLICY
			// ユニット ID を確認して POLICY で定義されているメンバかどうか確認する
			String unitId = anAccept.getString("unitId");
			boolean isMember = PolicyKeeping.isMember(unitId);
			if (!isMember) {
				ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, "accept received from illegal unit : " + unitId + " ; accept : " + anAccept);
			} else {
				accepts.add(anAccept);
				// TODO: Why not quit immediately after collecting as many members as specified in gridmaster.DataCollection?
				// TODO : gridmaster.DataCollection のようにメンバの数だけ集まったら即座に終了するのはどうでしょう
			}
		}).exceptionHandler(t -> {
			acceptConsumer.unregister(resUnregister -> {
				if (resUnregister.succeeded()) {
					// nop
				} else {
					ErrorUtil.report(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR, resUnregister.cause());
				}
				ErrorUtil.report(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR, t);
				vertx.undeploy(deploymentID());
			});
		}).completionHandler(res -> {
			// When finished opening the connection (eventBus address has finished propagating)
			// 口を開け終わった ( EventBus アドレスの伝搬が終わった ) ら
			if (res.succeeded()) {
				if (log.isDebugEnabled()) log.debug("request : " + request_);
				// Prepare a disposable address for the reply
				// 返信用の使い捨てアドレスを仕込んで
				DeliveryOptions options = new DeliveryOptions().addHeader("replyAddress", replyAddress);
				// Publish an interchange request
				// 融通リクエストを publish する
				vertx.eventBus().publish(ServiceAddress.Mediator.externalRequest(), request_, options);
				Long negotiationTimeoutMsec = PolicyKeeping.cache().getLong(DEFAULT_NEGOTIATION_TIMEOUT_MSEC, "mediator", "negotiationTimeoutMsec");
				// Set a timeout
				// タイムアウトを仕込む
				// The timeout duration is {@code POLICY.mediator.negotiationTimeoutMsec} (default: {@link #DEFAULT_NEGOTIATION_TIMEOUT_MSEC}).
				// 待ち時間は {@code POLICY.mediator.negotiationTimeoutMsec} ( デフォルト値 {@link #DEFAULT_NEGOTIATION_TIMEOUT_MSEC} ).
				vertx.setTimer(negotiationTimeoutMsec, t -> {
					// If a timeout occurs
					// タイムアウトしたら
					// Close the connection
					// 開いた口を閉じる
					acceptConsumer.unregister(resUnregister -> {
						if (resUnregister.succeeded()) {
							// Handle the "accept" responses
							// アクセプト群を処理し
							doTreatAccepts_(accepts, resTreat -> {
								// Call undeploy() for this verticle when finished
								// 終わったら自身を undeploy () する
								vertx.undeploy(deploymentID());
							});
						} else {
							ErrorUtil.report(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR, resUnregister.cause());
							vertx.undeploy(deploymentID());
						}
					});
				});
				// This completes the start() method for this Verticle
				// ここまででこの Verticle の start() が完了する
				completionHandler.handle(Future.succeededFuture());
			} else {
				ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR, res.cause(), completionHandler);
			}
		});
	}

	private void doTreatAccepts_(List<JsonObject> accepts, Handler<AsyncResult<Void>> completionHandler) {
		if (log.isDebugEnabled()) log.debug("accepts received : " + accepts);
		if (accepts != null && ! accepts.isEmpty()) {
			JsonObject values = new JsonObject().put("request", request_).put("accepts", new JsonArray(accepts));
			vertx.eventBus().<JsonObject>send(ServiceAddress.User.mediatorAccepts(), values, repAccept -> {
				if (repAccept.succeeded()) {
					JsonObject accept = repAccept.result().body();
					if (accept != null) {
						Integer requestAmountWh = request_.getInteger("amountWh");
						Integer acceptAmountWh = accept.getInteger("amountWh");
						Integer dealAmountMinWh = PolicyKeeping.cache().getInteger("mediator", "deal", "amountMinWh");
						Integer dealAmountMaxWh = PolicyKeeping.cache().getInteger("mediator", "deal", "amountMaxWh");
						Integer dealAmountUnitWh = PolicyKeeping.cache().getInteger("mediator", "deal", "amountUnitWh");
						if (requestAmountWh != null && acceptAmountWh != null && dealAmountMinWh != null && dealAmountMaxWh != null && dealAmountUnitWh != null) {
							// Adopt the interchange power specified in the request or in the "accept" response, whichever is the smaller
							// リクエストとアクセプトの融通電力量指定の小さい方を採用する
							int dealAmountWh = (requestAmountWh < acceptAmountWh) ? requestAmountWh : acceptAmountWh;
							// Limit to the maximum value of the interchange power in POLICY
							// POLICY の融通電力最大値で制限する
							dealAmountWh = (dealAmountMaxWh < dealAmountWh) ? dealAmountMaxWh : dealAmountWh;
							// Quantize in the smallest unit of the interchange power in POLICY
							// POLICY の融通電力量の最小単位で量子化する
							dealAmountWh = (dealAmountWh / dealAmountUnitWh) * dealAmountUnitWh;
							// Set to zero if less than the smallest unit of the interchange power in POLICY (interchange is not established)
							// POLICY の融通電力最小値未満なら 0 にする ( 融通が成立しない )
							dealAmountWh = (dealAmountWh < dealAmountMinWh) ? 0 : dealAmountWh;
							if (0 < dealAmountWh) {
								Float requestDealGridCurrentA = request_.getFloat("dealGridCurrentA");
								Float acceptDealGridCurrentA = accept.getFloat("dealGridCurrentA");
								if (requestDealGridCurrentA != null && acceptDealGridCurrentA != null && 0 < requestDealGridCurrentA && 0 < acceptDealGridCurrentA) {
									String acceptUnitId = accept.getString("unitId");
									JsonObject deal = new JsonObject();
									deal.put("unitId", ApisConfig.unitId());
									deal.put("negotiationId", negotiationId_());
									deal.put("requestUnitId", ApisConfig.unitId());
									deal.put("acceptUnitId", acceptUnitId);
									deal.put("requestDateTime", request_.getString("dateTime"));
									deal.put("acceptDateTime", accept.getString("dateTime"));
									deal.put("requestPointPerWh", request_.getFloat("pointPerWh"));
									deal.put("acceptPointPerWh", accept.getFloat("pointPerWh"));
									deal.put("requestDealGridCurrentA", requestDealGridCurrentA);
									deal.put("acceptDealGridCurrentA", acceptDealGridCurrentA);
									String type = request_.getString("type");
									deal.put("type", type);
									if ("charge".equals(type)) {
										deal.put("chargeUnitId", ApisConfig.unitId());
										deal.put("dischargeUnitId", acceptUnitId);
										deal.put("pointPerWh", request_.getFloat("pointPerWh")); // charge side
										deal.put("chargeUnitEfficientGridVoltageV", request_.getFloat("efficientGridVoltageV"));
										deal.put("dischargeUnitEfficientGridVoltageV", accept.getFloat("efficientGridVoltageV"));
									} else if ("discharge".equals(type)) {
										deal.put("chargeUnitId", acceptUnitId);
										deal.put("dischargeUnitId", ApisConfig.unitId());
										deal.put("pointPerWh", accept.getFloat("pointPerWh")); // charge side
										deal.put("chargeUnitEfficientGridVoltageV", accept.getFloat("efficientGridVoltageV"));
										deal.put("dischargeUnitEfficientGridVoltageV", request_.getFloat("efficientGridVoltageV"));
									} else {
										ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, "unknown type : " + type);
									}
									// Set the interchange grid current to the value specified in the request or the value specified in the "accept" response, whichever is smaller
									// リクエストの融通グリッド電流指定とアクセプトの融通グリッド電流指定の小さい方を融通グリッド電流値とする
									Float dealGridCurrentA = (requestDealGridCurrentA < acceptDealGridCurrentA) ? requestDealGridCurrentA : acceptDealGridCurrentA;
									deal.put("dealGridCurrentA", dealGridCurrentA);
									deal.put("requestAmountWh", requestAmountWh);
									deal.put("acceptAmountWh", acceptAmountWh);
									deal.put("dealAmountWh", dealAmountWh);
									if (request_.getString("pairUnitId") != null) deal.put("requestPairUnitId", request_.getString("pairUnitId"));
									if (accept.getString("pairUnitId") != null) deal.put("acceptPairUnitId", accept.getString("pairUnitId"));
									// Request registration of an interchange
									// 融通の登録を依頼する
									vertx.eventBus().send(ServiceAddress.Mediator.dealCreation(), deal);
								} else {
									ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, "illegal values; requestDealGridCurrentA : " + requestDealGridCurrentA + ", acceptDealGridCurrentA : " + acceptDealGridCurrentA);
								}
							} else {
								if (log.isDebugEnabled()) log.debug("negotiated amount : " + acceptAmountWh + " ; less than dealAmountMinWh : " + dealAmountMinWh);
							}
						} else {
							ErrorUtil.report(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, "data deficiency; requestAmountWh : " + requestAmountWh + ", acceptAmountWh : " + acceptAmountWh + ", dealAmountMinWh : " + dealAmountMinWh + ", dealAmountMaxWh : " + dealAmountMaxWh + ", dealAmountUnitWh : " + dealAmountUnitWh);
						}
					} else {
						if (log.isInfoEnabled()) log.info("no accept chosen");
					}
					completionHandler.handle(Future.succeededFuture());
				} else {
					if (ReplyFailureUtil.isRecipientFailure(repAccept)) {
						completionHandler.handle(Future.failedFuture(repAccept.cause()));
					} else if (ReplyFailureUtil.isTimeout(repAccept)) {
						ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.WARN, "Communication failed on EventBus", repAccept.cause(), completionHandler);
					} else {
						ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR, "Communication failed on EventBus", repAccept.cause(), completionHandler);
					}
				}
			});
		} else {
			completionHandler.handle(Future.succeededFuture());
		}
	}

}
