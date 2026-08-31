package jp.co.sony.csl.dcoes.apis.main.evaluation.scenario;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

import java.util.List;

import jp.co.sony.csl.dcoes.apis.main.evaluation.scenario.impl.SimpleScenarioEvaluationImpl;

public class ScenarioEvaluation {

	private ScenarioEvaluation() {
	}

	public interface Impl {
		void checkStatus(Vertx vertx, JsonObject scenario, JsonObject unitData,
				Handler<AsyncResult<JsonObject>> completionHandler);

		void treatRequest(Vertx vertx, JsonObject scenario, JsonObject unitData, JsonObject request,
				Handler<AsyncResult<JsonObject>> completionHandler);

		void chooseAccept(Vertx vertx, JsonObject scenario, JsonObject unitData, JsonObject request,
				List<JsonObject> accepts, Handler<AsyncResult<JsonObject>> completionHandler);
	}

	private static final Impl instance_ = new SimpleScenarioEvaluationImpl();

	public static void checkStatus(Vertx vertx, JsonObject scenario, JsonObject unitData,
			Handler<AsyncResult<JsonObject>> completionHandler) {
		instance_.checkStatus(vertx, scenario, unitData, completionHandler);
	}

	public static void treatRequest(Vertx vertx, JsonObject scenario, JsonObject unitData, JsonObject request,
			Handler<AsyncResult<JsonObject>> completionHandler) {
		instance_.treatRequest(vertx, scenario, unitData, request, completionHandler);
	}

	public static void chooseAccept(Vertx vertx, JsonObject scenario, JsonObject unitData, JsonObject request,
			List<JsonObject> accepts, Handler<AsyncResult<JsonObject>> completionHandler) {
		instance_.chooseAccept(vertx, scenario, unitData, request, accepts, completionHandler);
	}

}
