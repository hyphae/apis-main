package jp.co.sony.csl.dcoes.apis.main.app.mediator;

import java.util.List;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
//import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import jp.co.sony.csl.dcoes.apis.common.Deal;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.ServiceAddress;
//import jp.co.sony.csl.dcoes.apis.common.util.vertx.LocalExclusiveLock;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.ReplyFailureUtil;
import jp.co.sony.csl.dcoes.apis.main.app.PolicyKeeping;
import jp.co.sony.csl.dcoes.apis.main.app.controller.DataAcquisition;
import jp.co.sony.csl.dcoes.apis.main.app.mediator.util.DealNeedToStopUtil;
import jp.co.sony.csl.dcoes.apis.main.app.mediator.util.DealUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ApisConfig;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorExceptionUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

/**
 * A Verticle that manages interchange information.
 * Launched from the {@link Mediator} Verticle.
 * Registers, destroys and fetches interchange information.
 * Manages interchange stop requests from units participating in an interchange.
 * @author OES Project
 *          
 * 融通情報を管理する Verticle.
 * {@link Mediator} Verticle から起動される.
 * 融通情報の登録と破棄および取得.
 * 融通参加ユニットからの融通停止依頼の管理.
 * @author OES Project
 */
public class DealManagement extends AbstractVerticle {
	private static final Logger log = LoggerFactory.getLogger(DealManagement.class);

//	private static final LocalExclusiveLock exclusiveLock_ = new LocalExclusiveLock(DealManagement.class.getName());
//	public static void acquirePrivilegedExclusiveLock(Vertx vertx, Handler<AsyncResult<LocalExclusiveLock.Lock>> completionHandler) {
//		exclusiveLock_.acquire(vertx, true, completionHandler);
//	}
//	public static void acquireExclusiveLock(Vertx vertx, Handler<AsyncResult<LocalExclusiveLock.Lock>> completionHandler) {
//		exclusiveLock_.acquire(vertx, completionHandler);
//	}
//	public static void resetExclusiveLock(Vertx vertx) {
//		exclusiveLock_.reset(vertx);
//	}

	/**
	 * Called at startup.
	 * Launches the {@link io.vertx.core.eventbus.EventBus} service.
	 * @param startFuture {@inheritDoc}
	 * @throws Exception {@inheritDoc}
	 *          
	 * 起動時に呼び出される.
	 * {@link io.vertx.core.eventbus.EventBus} サービスを起動する.
	 * @param startFuture {@inheritDoc}
	 * @throws Exception {@inheritDoc}
	 */
	@Override public void start(Future<Void> startFuture) throws Exception {
		startDealsService_(resDeals -> {
			if (resDeals.succeeded()) {
				startDealCreationService_(resDealCreation -> {
					if (resDealCreation.succeeded()) {
						startDealDispositionService_(resDealDisposition -> {
							if (resDealDisposition.succeeded()) {
								startDealNeedToStopService_(resDealNeedToStop -> {
									if (resDealNeedToStop.succeeded()) {
										startResetLocalService_(resResetLocal -> {
											if (resResetLocal.succeeded()) {
												startResetAllService_(resResetAll -> {
													if (resResetAll.succeeded()) {
														if (log.isTraceEnabled()) log.trace("started : " + deploymentID());
														startFuture.complete();
													} else {
														startFuture.fail(resResetAll.cause());
													}
												});
											} else {
												startFuture.fail(resResetLocal.cause());
											}
										});
									} else {
										startFuture.fail(resDealNeedToStop.cause());
									}
								});
							} else {
								startFuture.fail(resDealDisposition.cause());
							}
						});
					} else {
						startFuture.fail(resDealCreation.cause());
					}
				});
			} else {
				startFuture.fail(resDeals.cause());
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

	/**
	 * Launch the {@link io.vertx.core.eventbus.EventBus} service.
	 * Address: {@link ServiceAddress.Mediator#deals()}
	 * Scope: global
	 * Function: Get a list of interchange information.
	 * Message body: none
	 * Message header: none
	 * Response: a list of interchange information managed in shared memory [{@link JsonArray}].
	 *           Fails if an error occurs.
	 * @param completionHandler the completion handler
	 *          
	 * {@link io.vertx.core.eventbus.EventBus} サービス起動.
	 * アドレス : {@link ServiceAddress.Mediator#deals()}
	 * 範囲 : グローバル
	 * 処理 : 融通情報リストを取得する.
	 * メッセージボディ : なし
	 * メッセージヘッダ : なし
	 * レスポンス : 共有メモリに管理されている融通情報リスト [{@link JsonArray}].
	 * 　　　　　   エラーが起きたら fail.
	 * @param completionHandler the completion handler
	 */
	private void startDealsService_(Handler<AsyncResult<Void>> completionHandler) {
		vertx.eventBus().<Void>consumer(ServiceAddress.Mediator.deals(), req -> {
			DealUtil.all(vertx, resAll -> {
				if (resAll.succeeded()) {
					List<JsonObject> deals = resAll.result();
					req.reply(new JsonArray(deals));
				} else {
					ErrorExceptionUtil.reportIfNeedAndFail(vertx, resAll.cause(), req);
				}
			});
		}).completionHandler(completionHandler);
	}

	/**
	 * Launch the {@link io.vertx.core.eventbus.EventBus} service.
	 * Address: {@link ServiceAddress.Mediator#dealCreation()}
	 * Scope: global
	 * Function: Register interchange information.
	 * Message body: Interchange information [{@link JsonObject}]
	 * Message header: none
	 * Response: A created list of interchange information [{@link JsonObject}].
	 *           Fails if unsuccessful.
	 *           Fails if an error occurs.
	 * @param completionHandler the completion handler
	 *          
	 * {@link io.vertx.core.eventbus.EventBus} サービス起動.
	 * アドレス : {@link ServiceAddress.Mediator#dealCreation()}
	 * 範囲 : グローバル
	 * 処理 : 融通情報を登録する.
	 * メッセージボディ : 融通情報 [{@link JsonObject}]
	 * メッセージヘッダ : なし
	 * レスポンス : 作成された融通情報 [{@link JsonObject}].
	 * 　　　　　   失敗したら fail.
	 * 　　　　　   エラーが起きたら fail.
	 * @param completionHandler the completion handler
	 */
	private void startDealCreationService_(Handler<AsyncResult<Void>> completionHandler) {
		vertx.eventBus().<JsonObject>consumer(ServiceAddress.Mediator.dealCreation(), req -> {
			JsonObject deal = req.body();
			if (deal != null) {
				String requestUnitId = Deal.requestUnitId(deal);
				String acceptUnitId = Deal.acceptUnitId(deal);
				if (requestUnitId != null && acceptUnitId != null) {
					if (PolicyKeeping.isMember(requestUnitId) && PolicyKeeping.isMember(acceptUnitId)) {
						vertx.eventBus().<Boolean>send(ServiceAddress.GridMaster.errorTesting(), null, repGlobalErrors -> {
							if (repGlobalErrors.succeeded()) {
								Boolean hasGlobalErrors = repGlobalErrors.result().body();
								if (hasGlobalErrors != null && hasGlobalErrors) {
									// Do not register if there is a global error
									// グローバルエラーがあったら登録しない
									String msg = "global error exists";
									if (log.isInfoEnabled()) log.info(msg);
									req.fail(-1, msg);
								} else {
									Future<Message<Boolean>> requestUnitFuture = Future.future();
									Future<Message<Boolean>> acceptUnitFuture = Future.future();
									vertx.eventBus().<Boolean>send(ServiceAddress.User.errorTesting(requestUnitId), null, requestUnitFuture);
									vertx.eventBus().<Boolean>send(ServiceAddress.User.errorTesting(acceptUnitId), null, acceptUnitFuture);
									CompositeFuture.<Message<Boolean>, Message<Boolean>>all(requestUnitFuture, acceptUnitFuture).setHandler(ar -> {
										if (ar.succeeded()) {
											Boolean hasRequestUnitErrors = ar.result().<Message<Boolean>>resultAt(0).body();
											Boolean hasAcceptUnitErrors = ar.result().<Message<Boolean>>resultAt(1).body();
											JsonArray errors = new JsonArray();
											if (hasRequestUnitErrors != null && hasRequestUnitErrors) {
												String msg = "local error exists on request unit : " + requestUnitId;
												if (log.isInfoEnabled()) log.info(msg);
												errors.add(msg);
											}
											if (hasAcceptUnitErrors != null && hasAcceptUnitErrors) {
												String msg = "local error exists on accept unit : " + acceptUnitId;
												if (log.isInfoEnabled()) log.info(msg);
												errors.add(msg);
											}
											if (errors.isEmpty()) {
												// Register if neither of the units at both ends have local errors
												// 両端ユニットどちらもローカルエラーがなければ登録する
												createDeal_(deal, resCreate -> {
													if (resCreate.succeeded()) {
														req.reply(resCreate.result());
													} else {
														req.fail(-1, resCreate.cause().getMessage());
													}
												});
											} else {
												// Do not register if either of the units at both ends has a local error
												// 両端ユニットどちらか一方でもローカルエラーがあったら登録しない
												req.fail(-1, errors.encode());
											}
										} else {
											if (ReplyFailureUtil.isRecipientFailure(ar.cause())) {
												req.fail(-1, ar.cause().getMessage());
											} else {
												ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR, "Communication failed on EventBus", ar.cause(), req);
											}
										}
									});
								}
							} else {
								if (ReplyFailureUtil.isRecipientFailure(repGlobalErrors)) {
									req.fail(-1, repGlobalErrors.cause().getMessage());
								} else if (ReplyFailureUtil.isNoHandlers(repGlobalErrors)) {
									ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, "Communication failed on EventBus", repGlobalErrors.cause(), req);
								} else if (ReplyFailureUtil.isTimeout(repGlobalErrors)) {
									ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.WARN, "Communication failed on EventBus", repGlobalErrors.cause(), req);
								} else {
									ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR, "Communication failed on EventBus", repGlobalErrors.cause(), req);
								}
							}
						});
					} else {
						// Do not register if either one is not a cluster member
						// どちらか一方でもクラスタメンバでなければ登録しない
						StringBuilder msg = new StringBuilder();
						if (!PolicyKeeping.isMember(requestUnitId)) {
							msg.append(requestUnitId);
						}
						if (!PolicyKeeping.isMember(acceptUnitId)) {
							if (0 < msg.length()) msg.append(" & ");
							msg.append(acceptUnitId);
						}
						msg.insert(0, "deal received with illegal unit(s) : ").append(" ; deal : ").append(deal);
						ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, msg.toString(), req);
					}
				} else {
					ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, "no requestUnitId and/or acceptUnitId in deal : " + deal, req);
				}
			} else {
				ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, "deal is null", req);
			}
		}).completionHandler(completionHandler);
	}
	/**
	 * Save the interchange information in shared memory
	 * Receive a DEAL object registered by the {@link AsyncResult#result()} method of completionHandler.
	 * @param deal a DEAL object
	 * @param completionHandler the completion handler
	 *          
	 * 融通情報を共有メモリに保存する
	 * completionHandler の {@link AsyncResult#result()} で登録された DEAL オブジェクトを受け取る.
	 * @param deal DEAL オブジェクト
	 * @param completionHandler the completion handler
	 */
	private void createDeal_(JsonObject deal, Handler<AsyncResult<JsonObject>> completionHandler) {
		@SuppressWarnings("unused") String dealId = Deal.generateDealId(deal);
		String requestUnitId = Deal.requestUnitId(deal);
		String acceptUnitId = Deal.acceptUnitId(deal);
		String unitId1_, unitId2_;
		if (requestUnitId != null && acceptUnitId != null) {
			// Try to establish some kind of rules for the ordering of resource allocations so that deadlocks do not occur
			// なんとなくデッドロックが起きないようにリソース確保順序をルール化してみている
			// TODO: Perhaps deadlocks won't occur even if we don't do this, because we give up when an acquisition failure occurs
			// TODO : たぶんこんなことしなくてもデッドロック起きない. なぜなら確保失敗で諦めるから
			if (requestUnitId.compareTo(acceptUnitId) < 0) {
				unitId1_ = requestUnitId;
				unitId2_ = acceptUnitId;
			} else {
				unitId2_ = requestUnitId;
				unitId1_ = acceptUnitId;
			}
			// TODO: Maybe we could do two at the same time?
			// TODO : たぶん二つ同時にやってよいのではないかと...
			acquireInterlock_(unitId1_, deal, resAcquire1 -> {
				if (resAcquire1.succeeded()) {
					acquireInterlock_(unitId2_, deal, resAcquire2 -> {
						if (resAcquire2.succeeded()) {
							// Save information when an interlock has been acquired for the units at both ends
							// 両端ユニットのインタロックが確保できたら保存する
							deal.put("createDateTime", DataAcquisition.cache.getString("time"));
							DealUtil.add(vertx, deal, resAdd -> {
								if (resAdd.succeeded()) {
									completionHandler.handle(Future.succeededFuture(deal));
								} else {
									ErrorExceptionUtil.reportIfNeed(vertx, resAdd.cause());
									releaseInterlock_(unitId2_, deal, resRelease2 -> {
										releaseInterlock_(unitId1_, deal, resRelease1 -> {
											if (resRelease2.succeeded() && resRelease1.succeeded()) {
												completionHandler.handle(Future.failedFuture(resAdd.cause()));
											} else if (resRelease1.failed()) {
												completionHandler.handle(Future.failedFuture(resRelease1.cause()));
											} else {
												completionHandler.handle(Future.failedFuture(resRelease2.cause()));
											}
										});
									});
								}
							});
						} else {
							releaseInterlock_(unitId1_, deal, resRelease1 -> {
								if (resRelease1.succeeded()) {
									completionHandler.handle(Future.failedFuture(resAcquire2.cause()));
								} else {
									completionHandler.handle(Future.failedFuture(resRelease1.cause()));
								}
							});
						}
					});
				} else {
					completionHandler.handle(Future.failedFuture(resAcquire1.cause()));
				}
			});
		} else {
			ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, "no requestUnitId and/or acceptUnitId in deal : " + deal, completionHandler);
		}
	}
	private void acquireInterlock_(String unitId, JsonObject deal, Handler<AsyncResult<Void>> completionHandler) {
		if (unitId != null) {
			DeliveryOptions acquireOptions = new DeliveryOptions().addHeader("command", "acquire");
			vertx.eventBus().<Void>send(ServiceAddress.Mediator.dealInterlocking(unitId), deal, acquireOptions, rep -> {
				if (rep.succeeded()) {
					completionHandler.handle(Future.succeededFuture());
				} else {
					if (ReplyFailureUtil.isRecipientFailure(rep)) {
						completionHandler.handle(Future.failedFuture(rep.cause()));
					} else {
						ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR, "Communication failed on EventBus", rep.cause(), completionHandler);
					}
				}
			});
		} else {
			completionHandler.handle(Future.failedFuture("no unitId"));
		}
	}
	private void releaseInterlock_(String unitId, JsonObject deal, Handler<AsyncResult<Void>> completionHandler) {
		if (unitId != null) {
			DeliveryOptions releaseOptions = new DeliveryOptions().addHeader("command", "release");
			vertx.eventBus().<Void>send(ServiceAddress.Mediator.dealInterlocking(unitId), deal, releaseOptions, rep -> {
				if (rep.succeeded()) {
					completionHandler.handle(Future.succeededFuture());
				} else {
					if (ReplyFailureUtil.isRecipientFailure(rep)) {
						completionHandler.handle(Future.failedFuture(rep.cause()));
					} else {
						ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR, "Communication failed on EventBus", rep.cause(), completionHandler);
					}
				}
			});
		} else {
			completionHandler.handle(Future.succeededFuture());
		}
	}

	/**
	 * Launch the {@link io.vertx.core.eventbus.EventBus} service.
	 * Address: {@link ServiceAddress.Mediator#dealDisposition()}
	 * Scope: local
	 * Function: Delete interchange information
	 * Message body: interchange ID [{@link String}]
	 * Message header: none
	 * Response: The deleted interchange information [{@link JsonObject}].
	 *           Fails if an error occurs.
	 * @param completionHandler the completion handler
	 *          
	 * {@link io.vertx.core.eventbus.EventBus} サービス起動.
	 * アドレス : {@link ServiceAddress.Mediator#dealDisposition()}
	 * 範囲 : ローカル
	 * 処理 : 融通情報を削除する.
	 * メッセージボディ : 融通 ID [{@link String}]
	 * メッセージヘッダ : なし
	 * レスポンス : 削除された融通情報 [{@link JsonObject}].
	 * 　　　　　   エラーが起きたら fail.
	 * @param completionHandler the completion handler
	 */
	private void startDealDispositionService_(Handler<AsyncResult<Void>> completionHandler) {
		vertx.eventBus().<String>localConsumer(ServiceAddress.Mediator.dealDisposition(), req -> {
			String dealId = req.body();
			if (dealId != null) {
				disposeDeal_(dealId, resDispose -> {
					if (resDispose.succeeded()) {
						req.reply(resDispose.result());
					} else {
						req.fail(-1, resDispose.cause().getMessage());
					}
				});
			} else {
				ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR, "dealId is null", req);
			}
		}).completionHandler(completionHandler);
	}
	private void disposeDeal_(String dealId, Handler<AsyncResult<JsonObject>> completionHandler) {
		DealNeedToStopUtil.remove(vertx, dealId, resRemoveNeedToStop -> {
			if (resRemoveNeedToStop.succeeded()) {
				// nop
			} else {
				ErrorExceptionUtil.reportIfNeed(vertx, resRemoveNeedToStop.cause());
			}
		});
		DealUtil.get(vertx, dealId, resGet -> {
			if (resGet.succeeded()) {
				JsonObject deal = resGet.result();
				vertx.eventBus().publish(ServiceAddress.Mediator.dealLogging(), deal);
				String requestUnitId = Deal.requestUnitId(deal);
				String acceptUnitId = Deal.acceptUnitId(deal);
				String unitId1_, unitId2_;
				if (requestUnitId != null && acceptUnitId != null) {
					// Try to establish some kind of rules for the ordering of resource allocations so that deadlocks do not occur
					// なんとなくデッドロックが起きないようにリソース確保順序をルール化してみている
					// TODO: Perhaps deadlocks won't occur even if we don't do this, because we give up when an acquisition failure occurs
					// TODO : たぶんこんなことしなくてもデッドロック起きない. なぜなら確保失敗で諦めるから
					if (requestUnitId.compareTo(acceptUnitId) < 0) {
						unitId1_ = requestUnitId;
						unitId2_ = acceptUnitId;
					} else {
						unitId2_ = requestUnitId;
						unitId1_ = acceptUnitId;
					}
					DealUtil.remove(vertx, dealId, resRemove -> {
						if (resRemove.succeeded()) {
							// Release in the reverse order of allocation
							// 獲得と逆の順序で開放する
							// TODO: Maybe we could do two at the same time?
							// TODO : たぶん二つ同時にやってよいのではないかと...
							releaseInterlock_(unitId2_, deal, resRelease2 -> {
								releaseInterlock_(unitId1_, deal, resRelease1 -> {
									if (resRelease2.succeeded() && resRelease1.succeeded()) {
										completionHandler.handle(Future.succeededFuture(deal));
									} else if (resRelease1.failed()) {
										completionHandler.handle(Future.failedFuture(resRelease1.cause()));
									} else {
										completionHandler.handle(Future.failedFuture(resRelease2.cause()));
									}
								});
							});
						} else {
							ErrorExceptionUtil.reportIfNeedAndFail(vertx, resRemove.cause(), completionHandler);
						}
					});
				} else {
					completionHandler.handle(Future.failedFuture("no requestUnitId and/or acceptUnitId in deal : " + deal));
				}
			} else {
				ErrorExceptionUtil.reportIfNeedAndFail(vertx, resGet.cause(), completionHandler);
			}
		});
	}

	/**
	 * Launch the {@link io.vertx.core.eventbus.EventBus} service.
	 * Address: {@link ServiceAddress.Mediator#dealNeedToStop()}
	 * Scope: global
	 * Function: Send an interchange stop request.
	 * Message body: The stop request [{@link JsonObject}]
	 *                - {@code "dealId"}: the interchange ID
	 *                - {@code "reasons"}: a list of reasons [{@link JsonArray}]
	 * Message header: none
	 * Response: interchange ID [{@link String}]
	 *           Fails if an error occurs.
	 * @param completionHandler the completion handler
	 *          
	 * {@link io.vertx.core.eventbus.EventBus} サービス起動.
	 * アドレス : {@link ServiceAddress.Mediator#dealNeedToStop()}
	 * 範囲 : グローバル
	 * 処理 : 融通停止要求を送信する.
	 * メッセージボディ : 停止要求 [{@link JsonObject}]
	 * 　　　　　　　　   - {@code "dealId"} : 融通 ID
	 * 　　　　　　　　   - {@code "reasons"} : 理由リスト [{@link JsonArray}]
	 * メッセージヘッダ : なし
	 * レスポンス : 融通 ID [{@link String}]
	 * 　　　　　   エラーが起きたら fail.
	 * @param completionHandler the completion handler
	 */
	private void startDealNeedToStopService_(Handler<AsyncResult<Void>> completionHandler) {
		vertx.eventBus().<JsonObject>consumer(ServiceAddress.Mediator.dealNeedToStop(), req -> {
			JsonObject message = req.body();
			String dealId = message.getString("dealId");
			if (dealId != null) {
				JsonArray reasons = message.getJsonArray("reasons");
				needToStopDeal_(dealId, reasons, resNeedToStop -> {
					if (resNeedToStop.succeeded()) {
						req.reply(dealId);
					} else {
						req.fail(-1, resNeedToStop.cause().getMessage());
					}
				});
			} else {
				ErrorUtil.reportAndFail(vertx, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.WARN, "dealId is null", req);
			}
		}).completionHandler(completionHandler);
	}
	private void needToStopDeal_(String dealId, JsonArray reasons, Handler<AsyncResult<Void>> completionHandler) {
		DealUtil.get(vertx, dealId, true, resGet -> {
			if (resGet.succeeded()) {
				JsonObject deal = resGet.result();
				if (deal != null) {
					DealNeedToStopUtil.add(vertx, dealId, reasons, resNeedToStop -> ErrorExceptionUtil.reportIfNeedAndHandle(vertx, resNeedToStop, completionHandler));
				} else {
					completionHandler.handle(Future.succeededFuture());
				}
			} else {
				ErrorExceptionUtil.reportIfNeedAndFail(vertx, resGet.cause(), completionHandler);
			}
		});
	}

	/**
	 * Launch the {@link io.vertx.core.eventbus.EventBus} service.
	 * Address: {@link ServiceAddress#resetLocal()}
	 * Scope: local
	 * Function: reset this unit.
	 * Message body: none
	 * Message header: none
	 * Response: this unit's ID [{@link String}].
	 * 　　　　　Fails if an error occurs.
	 * @param completionHandler the completion handler
	 *          
	 * {@link io.vertx.core.eventbus.EventBus} サービス起動.
	 * アドレス : {@link ServiceAddress#resetLocal()}
	 * 範囲 : ローカル
	 * 処理 : 自ユニットをリセットする.
	 * メッセージボディ : なし
	 * メッセージヘッダ : なし
	 * レスポンス : 自ユニットの ID [{@link String}].
	 * 　　　　　   エラーが起きたら fail.
	 * @param completionHandler the completion handler
	 */
	private void startResetLocalService_(Handler<AsyncResult<Void>> completionHandler) {
		vertx.eventBus().<Void>localConsumer(ServiceAddress.resetLocal(), req -> {
			doReset_(req);
		}).completionHandler(completionHandler);
	}
	/**
	 * Launch the {@link io.vertx.core.eventbus.EventBus} service.
	 * Address: {@link ServiceAddress#resetAll()}
	 * Scope: global
	 * Function: Reset all units and all programs that participate in a cluster.
	 * Message body: none
	 * Message header: none
	 * Response: this unit's ID [{@link String}].
	 * 　　　　　Fails if an error occurs.
	 * @param completionHandler the completion handler
	 *          
	 * {@link io.vertx.core.eventbus.EventBus} サービス起動.
	 * アドレス : {@link ServiceAddress#resetAll()}
	 * 範囲 : グローバル
	 * 処理 : 全ユニットおよびクラスタに参加する全プログラムをリセットする.
	 * メッセージボディ : なし
	 * メッセージヘッダ : なし
	 * レスポンス : 自ユニットの ID [{@link String}].
	 * 　　　　　   エラーが起きたら fail.
	 * @param completionHandler the completion handler
	 */
	private void startResetAllService_(Handler<AsyncResult<Void>> completionHandler) {
		vertx.eventBus().<Void>consumer(ServiceAddress.resetAll(), req -> {
			doReset_(req);
		}).completionHandler(completionHandler);
	}
	private void doReset_(Message<?> message) {
//		resetExclusiveLock(vertx);
		DealUtil.resetExclusiveLock(vertx);
		message.reply(ApisConfig.unitId());
	}

}
