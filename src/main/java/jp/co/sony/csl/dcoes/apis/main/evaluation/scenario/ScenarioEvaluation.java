package jp.co.sony.csl.dcoes.apis.main.evaluation.scenario;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

import java.util.List;

import jp.co.sony.csl.dcoes.apis.main.evaluation.scenario.impl.SimpleScenarioEvaluationImpl;

/**
 * Entry point for SCENARIO evaluation.
 * The only implementation is {@link SimpleScenarioEvaluationImpl}.
 * @author OES Project
 *          
 * SCENARIO 評価の入り口.
 * 実装は {@link SimpleScenarioEvaluationImpl} のみ.
 * @author OES Project
 */
public class ScenarioEvaluation {

	private ScenarioEvaluation() { }

	/**
	 * Interface for implementation.
	 * @author OES Project
	 *          
	 * 実装のためのインタフェイス.
	 * @author OES Project
	 */
	public interface Impl {
		/**
		 * Decide whether or not to issue an interchange request based on the SCENARIO and the status of this unit.
		 * Receive the interchange request with the {@link AsyncResult#result()} method of completionHandler.
		 * Return {@code null} if not in a state where a request should be issued.
		 * @param vertx a vertx object
		 * @param scenario a subset of the SCENARIO objects in a particular time zone
		 * @param unitData the unit data of this unit
		 * @param completionHandler the completion handler
		 *          
		 * SCENARIO と自ユニットの状態から融通リクエストを発するかどうか判断する.
		 * completionHandler の {@link AsyncResult#result()} で融通リクエストを受け取る.
		 * リクエストを発する状態でなければ {@code null} が返る.
		 * @param vertx vertx オブジェクト
		 * @param scenario SCENARIO オブジェクト中の特定の時間帯のサブセット
		 * @param unitData 自ユニットのユニットデータ
		 * @param completionHandler the completion handler
		 */
		void checkStatus(Vertx vertx, JsonObject scenario, JsonObject unitData, Handler<AsyncResult<JsonObject>> completionHandler);
		/**
		 * Based on the SCENARIO and the status of this unit, decide whether or not to accept an interchange request received from another unit.
		 * Receive the interchange "accept" response with the {@link AsyncResult#result()} method of completionHandler.
		 * Return {@code null} if not in a state where "accept" response should be returned.
		 * @param vertx a vertx object
		 * @param scenario a subset of the SCENARIO objects in a particular time zone
		 * @param unitData the unit data of this unit
		 * @param request interchange request received from another unit
		 * @param completionHandler the completion handler
		 *          
		 * 他ユニットから受信した融通リクエストに対し SCENARIO と自ユニットの状態からアクセプトを返すかどうか判断する.
		 * completionHandler の {@link AsyncResult#result()} で融通アクセプトを受け取る.
		 * アクセプトを返す状態でなければ {@code null} が返る.
		 * @param vertx vertx オブジェクト
		 * @param scenario SCENARIO オブジェクト中の特定の時間帯のサブセット
		 * @param unitData 自ユニットのユニットデータ
		 * @param request 他ユニットから受信した融通リクエスト
		 * @param completionHandler the completion handler
		 */
		void treatRequest(Vertx vertx, JsonObject scenario, JsonObject unitData, JsonObject request, Handler<AsyncResult<JsonObject>> completionHandler);
		/**
		 * From a group of "accept" responses returned from other units, select one "accept" response based on the SCENARIO and the status of this unit.
		 * Receive the selected "accept" response with the {@link AsyncResult#result()} method of completionHandler.
		 * Return {@code null} if not in a state where an interchange should be created.
		 * @param vertx a vertx object
		 * @param scenario a subset of the SCENARIO objects in a particular time zone
		 * @param unitData the unit data of this unit
		 * @param request the interchange request issued by this unit
		 * @param accepts a group of "accept" responses returned from other units
		 * @param completionHandler the completion handler
		 *          
		 * 他ユニットが返したアクセプト群に対し SCENARIO と自ユニットの状態からアクセプトを一つ選択する.
		 * completionHandler の {@link AsyncResult#result()} で選択したアクセプトを受け取る.
		 * 融通を作成しようとする状態でなければ {@code null} が返る.
		 * @param vertx vertx オブジェクト
		 * @param scenario SCENARIO オブジェクト中の特定の時間帯のサブセット
		 * @param unitData 自ユニットのユニットデータ
		 * @param request 自ユニットが発した融通リクエスト
		 * @param accepts 他ユニットから返ってきたアクセプト群
		 * @param completionHandler the completion handler
		 */
		void chooseAccept(Vertx vertx, JsonObject scenario, JsonObject unitData, JsonObject request, List<JsonObject> accepts, Handler<AsyncResult<JsonObject>> completionHandler);
	}

	private static final Impl instance_ = new SimpleScenarioEvaluationImpl();

	/**
	 * Call the {@link Impl#checkStatus(Vertx, JsonObject, JsonObject, Handler)} method of an implementation object.
	 * @param vertx a vertx object
	 * @param scenario a subset of the SCENARIO objects in a particular time zone
	 * @param unitData the unit data of this unit
	 * @param completionHandler the completion handler
	 *          
	 * 実装オブジェクトの {@link Impl#checkStatus(Vertx, JsonObject, JsonObject, Handler)} を呼ぶ.
	 * @param vertx vertx オブジェクト
	 * @param scenario SCENARIO オブジェクト中の特定の時間帯のサブセット
	 * @param unitData 自ユニットのユニットデータ
	 * @param completionHandler the completion handler
	 */
	public static void checkStatus(Vertx vertx, JsonObject scenario, JsonObject unitData, Handler<AsyncResult<JsonObject>> completionHandler) {
		instance_.checkStatus(vertx, scenario, unitData, completionHandler);
	}
	/**
	 * Call the {@link Impl#treatRequest(Vertx, JsonObject, JsonObject, JsonObject, Handler)} method of an implementation object.
	 * @param vertx a vertx object
	 * @param scenario a subset of the SCENARIO objects in a particular time zone
	 * @param unitData the unit data of this unit
	 * @param request interchange request received from another unit
	 * @param completionHandler the completion handler
	 *          
	 * 実装オブジェクトの {@link Impl#treatRequest(Vertx, JsonObject, JsonObject, JsonObject, Handler)} を呼ぶ.
	 * @param vertx vertx オブジェクト
	 * @param scenario SCENARIO オブジェクト中の特定の時間帯のサブセット
	 * @param unitData 自ユニットのユニットデータ
	 * @param request 他ユニットから受信した融通リクエスト
	 * @param completionHandler the completion handler
	 */
	public static void treatRequest(Vertx vertx, JsonObject scenario, JsonObject unitData, JsonObject request, Handler<AsyncResult<JsonObject>> completionHandler) {
		instance_.treatRequest(vertx, scenario, unitData, request, completionHandler);
	}
	/**
	 * Call the {@link Impl#chooseAccept(Vertx, JsonObject, JsonObject, JsonObject, List, Handler)} method of an implementation object.
	 * @param vertx a vertx object
	 * @param scenario a subset of the SCENARIO objects in a particular time zone
	 * @param unitData the unit data of this unit
	 * @param request the interchange request issued by this unit
	 * @param accepts a group of "accept" responses returned from other units
	 * @param completionHandler the completion handler
	 *          
	 * 実装オブジェクトの {@link Impl#chooseAccept(Vertx, JsonObject, JsonObject, JsonObject, List, Handler)} を呼ぶ.
	 * @param vertx vertx オブジェクト
	 * @param scenario SCENARIO オブジェクト中の特定の時間帯のサブセット
	 * @param unitData 自ユニットのユニットデータ
	 * @param request 自ユニットが発した融通リクエスト
	 * @param accepts 他ユニットから返ってきたアクセプト群
	 * @param completionHandler the completion handler
	 */
	public static void chooseAccept(Vertx vertx, JsonObject scenario, JsonObject unitData, JsonObject request, List<JsonObject> accepts, Handler<AsyncResult<JsonObject>> completionHandler) {
		instance_.chooseAccept(vertx, scenario, unitData, request, accepts, completionHandler);
	}

}
