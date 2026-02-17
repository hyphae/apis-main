package jp.co.sony.csl.dcoes.apis.main.error.action;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import jp.co.sony.csl.dcoes.apis.common.Deal;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.ServiceAddress;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectUtil;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.ReplyFailureUtil;
import jp.co.sony.csl.dcoes.apis.main.app.controller.DataAcquisition;
import jp.co.sony.csl.dcoes.apis.main.app.mediator.util.DealUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorExceptionUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

public class Scram extends AbstractErrorAction {
	private static final Logger log = LoggerFactory.getLogger(Scram.class);

	public static final Long DEFAULT_SCRAM_VOLTAGE_REFERENCE_DELAY_MSEC = 5000L;

	public Scram(Vertx vertx, JsonObject policy, JsonArray logMessages) {
		super(vertx, policy, logMessages);
	}

	@Override
	protected void doAction(Handler<AsyncResult<Void>> completionHandler) {
		DeliveryOptions options = new DeliveryOptions();
		if (log.isInfoEnabled())
			log.info("publishing SCRAM message to all units ( excluding voltage reference ) ...");
		DeliveryOptions excludeVoltageReferenceOptions = new DeliveryOptions(options)
				.addHeader("excludeVoltageReference", Boolean.TRUE.toString());
		vertx_.eventBus().publish(ServiceAddress.Controller.scram(), null, excludeVoltageReferenceOptions);
		vertx_.setTimer(JsonObjectUtil.getLong(policy_, DEFAULT_SCRAM_VOLTAGE_REFERENCE_DELAY_MSEC, "controller",
				"scramVoltageReferenceDelayMsec"), h -> {
					if (log.isInfoEnabled())
						log.info("publishing SCRAM message to all units ( including voltage reference ) ...");
					DeliveryOptions includeVoltageReferenceOptions = new DeliveryOptions(options)
							.addHeader("excludeVoltageReference", Boolean.FALSE.toString());
					vertx_.eventBus().publish(ServiceAddress.Controller.scram(), null, includeVoltageReferenceOptions);
					if (log.isInfoEnabled())
						log.info("SCRAM all deals ...");
					DealUtil.all(vertx_, resAll -> {
						if (resAll.succeeded()) {
							List<JsonObject> deals = resAll.result();
							new DealScramming_(deals).doLoop_(completionHandler);
						} else {
							ErrorExceptionUtil.reportIfNeedAndFail(vertx_, resAll.cause(), completionHandler);
						}
					});
				});
	}

	private class DealScramming_ {
		private List<JsonObject> dealsForLoop_;

		private DealScramming_(List<JsonObject> deals) {
			dealsForLoop_ = new ArrayList<JsonObject>(deals);
		}

		private void doLoop_(Handler<AsyncResult<Void>> completionHandler) {
			if (dealsForLoop_.isEmpty()) {
				if (log.isInfoEnabled())
					log.info("done");
				completionHandler.handle(Future.succeededFuture());
			} else {
				JsonObject aDeal = dealsForLoop_.remove(0);
				DealUtil.scram(vertx_, aDeal, DataAcquisition.cache.getString("time"), logMessages_.encode(),
						resScram -> {
							if (resScram.succeeded()) {
								vertx_.eventBus().request(ServiceAddress.Mediator.dealDisposition(), Deal.dealId(aDeal),
										repDealDisposition -> {
											if (repDealDisposition.succeeded()) {
											} else {
												if (ReplyFailureUtil.isRecipientFailure(repDealDisposition)) {
												} else {
													ErrorUtil.report(vertx_, Error.Category.FRAMEWORK,
															Error.Extent.LOCAL, Error.Level.ERROR,
															"Communication failed on EventBus",
															repDealDisposition.cause());
												}
											}
											doLoop_(completionHandler);
										});
							} else {
								ErrorExceptionUtil.reportIfNeed(vertx_, resScram.cause());
								doLoop_(completionHandler);
							}
						});
			}
		}
	}

}
