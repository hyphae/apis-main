package jp.co.sony.csl.dcoes.apis.main.app.gridmaster.deal_execution;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

import java.util.List;

import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.ServiceAddress;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.ReplyFailureUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

public class DealDisposition extends AbstractDealExecution {
	// private static final Logger log =
	// LoggerFactory.getLogger(DealDisposition.class);

	public DealDisposition(Vertx vertx, JsonObject policy, JsonObject deal, List<JsonObject> otherDeals) {
		super(vertx, policy, deal, otherDeals);
	}

	public DealDisposition(AbstractDealExecution other) {
		super(other);
	}

	@Override
	protected void doExecute(Handler<AsyncResult<Void>> onComplete) {
		disposeDeal_(onComplete);
	}

	private void disposeDeal_(Handler<AsyncResult<Void>> onComplete) {
		vertx_.eventBus().<JsonObject>request(ServiceAddress.Mediator.dealDisposition(), dealId_,
				repDealDisposition -> {
					if (repDealDisposition.succeeded()) {
						onComplete.handle(Future.succeededFuture());
					} else {
						if (ReplyFailureUtil.isRecipientFailure(repDealDisposition)) {
							onComplete.handle(Future.failedFuture(repDealDisposition.cause()));
						} else {
							ErrorUtil.reportAndFail(vertx_, Error.Category.FRAMEWORK, Error.Extent.GLOBAL,
									Error.Level.ERROR, "Communication failed on EventBus", repDealDisposition.cause(),
									onComplete);
						}
					}
				});
	}

}
