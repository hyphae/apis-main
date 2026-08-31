package jp.co.sony.csl.dcoes.apis.main.error.handling;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

public abstract class AbstractErrorsHandling {

	protected Vertx vertx_;
	protected JsonObject policy_;
	protected JsonArray errors_;
	protected JsonArray logMessages_;

	public AbstractErrorsHandling(Vertx vertx, JsonObject policy, JsonArray errors) {
		vertx_ = vertx;
		policy_ = policy;
		errors_ = errors;
		logMessages_ = logMessages_(errors_);
	}

	public void handle(Handler<AsyncResult<Void>> completionHandler) {
		doHandle(completionHandler);
	}

	private JsonArray logMessages_(JsonArray errors) {
		JsonArray result = new JsonArray();
		for (Object anError : errors) {
			if (anError instanceof JsonObject) {
				String aLogMessage = Error.logMessage((JsonObject) anError);
				if (!result.contains(aLogMessage)) {
					result.add(aLogMessage);
				}
			} else {
				ErrorUtil.report(vertx_, Error.Category.LOGIC, Error.Extent.LOCAL, Error.Level.ERROR,
						"error object is not an instance of JsonObject : " + anError);
			}
		}
		return result;
	}

	protected abstract void doHandle(Handler<AsyncResult<Void>> completionHandler);

}
