package jp.co.sony.csl.dcoes.apis.main.error.handling;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jp.co.sony.csl.dcoes.apis.main.app.StateHandling;
import jp.co.sony.csl.dcoes.apis.main.error.action.AskAndWaitForStopDeals;
import jp.co.sony.csl.dcoes.apis.main.error.action.DeactivateGridMaster;
import jp.co.sony.csl.dcoes.apis.main.error.action.ShutdownLocal;
import jp.co.sony.csl.dcoes.apis.main.error.action.StopLocal;

/**
 * An actual class for error handling.
 * - Scope: {@link jp.co.sony.csl.dcoes.apis.common.Error.Extent#LOCAL}
 * - Type: all
 * - Severity: {@link jp.co.sony.csl.dcoes.apis.common.Error.Level#FATAL}
 * - Processing details:
 *   1. Request stoppage of interchange
 *   2. Stop devices
 *   3. Change operating state to "stopped".
 *   4. Stop GridMaster
 *   5. Shut down
 * @author OES Project
 *          
 * エラー対応の実クラス.
 * - 範囲 : {@link jp.co.sony.csl.dcoes.apis.common.Error.Extent#LOCAL}
 * - 種類 : すべて
 * - 深刻さ : {@link jp.co.sony.csl.dcoes.apis.common.Error.Level#FATAL}
 * - 処理内容 :
 *   1. 融通停止依頼
 *   2. デバイス停止
 *   3. 動作モードを停止中に変更
 *   4. GridMaster 停止
 *   5. シャットダウン
 * @author OES Project
 */
public class LocalAnyFatalsHandling extends AbstractErrorsHandling {

	/**
	 * Create an instance.
	 * @param vertx a vertx object
	 * @param policy a POLICY object. To prevent changes from taking effect while running, a copy is passed at {@link jp.co.sony.csl.dcoes.apis.main.app.user.ErrorHandling} or {@link jp.co.sony.csl.dcoes.apis.main.app.gridmaster.main_loop.ErrorHandling}.
	 * @param errors a list of errors to be handled
	 *          
	 * インスタンスを生成する.
	 * @param vertx vertx オブジェクト
	 * @param policy POLICY オブジェクト. 処理中に変更されても影響しないように {@link jp.co.sony.csl.dcoes.apis.main.app.user.ErrorHandling} あるいは {@link jp.co.sony.csl.dcoes.apis.main.app.gridmaster.main_loop.ErrorHandling} でコピーしたものが渡される.
	 * @param errors 処理対象のエラーのリスト
	 */
	public LocalAnyFatalsHandling(Vertx vertx, JsonObject policy, JsonArray errors) {
		super(vertx, policy, errors);
	}

	/**
	 * {@inheritDoc}
	 * 1. Request stoppage of interchange
	 * 2. Stop devices
	 * 3. Change operating state to "stopped".
	 * 4. Stop GridMaster
	 * 5. Shut down
	 *          
	 * {@inheritDoc}
	 * 1. 融通停止依頼
	 * 2. デバイス停止
	 * 3. 動作モードを停止中に変更
	 * 4. GridMaster 停止
	 * 5. シャットダウン
	 */
	@Override protected void doHandle(Handler<AsyncResult<Void>> completionHandler) {
		new AskAndWaitForStopDeals(vertx_, policy_, logMessages_).action(r -> {
			new StopLocal(vertx_, policy_, logMessages_).action(rr -> {
				StateHandling.setStopping();
				new DeactivateGridMaster(vertx_, policy_, logMessages_).action(rrr -> {
					new ShutdownLocal(vertx_, policy_, logMessages_).action(completionHandler);
				});
			});
		});
	}

}
