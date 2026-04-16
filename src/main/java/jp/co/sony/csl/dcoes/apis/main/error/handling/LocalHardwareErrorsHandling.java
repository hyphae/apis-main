package jp.co.sony.csl.dcoes.apis.main.error.handling;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jp.co.sony.csl.dcoes.apis.main.error.action.AskAndWaitForStopDeals;
import jp.co.sony.csl.dcoes.apis.main.error.action.StopLocal;

/**
 * An actual class for error handling.
 * - Scope: {@link jp.co.sony.csl.dcoes.apis.common.Error.Extent#LOCAL}
 * - Type: {@link jp.co.sony.csl.dcoes.apis.common.Error.Category#HARDWARE}
 * - Severity: {@link jp.co.sony.csl.dcoes.apis.common.Error.Level#ERROR}
 * - Processing details:
 *   1. Request stoppage of interchange
 *   2. Stop devices
 * @author OES Project
 *          
 * エラー対応の実クラス.
 * - 範囲 : {@link jp.co.sony.csl.dcoes.apis.common.Error.Extent#LOCAL}
 * - 種類 : {@link jp.co.sony.csl.dcoes.apis.common.Error.Category#HARDWARE}
 * - 深刻さ : {@link jp.co.sony.csl.dcoes.apis.common.Error.Level#ERROR}
 * - 処理内容 :
 *   1. 融通停止依頼
 *   2. デバイス停止
 * @author OES Project
 */
public class LocalHardwareErrorsHandling extends AbstractErrorsHandling {

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
	public LocalHardwareErrorsHandling(Vertx vertx, JsonObject policy, JsonArray errors) {
		super(vertx, policy, errors);
	}

	/**
	 * {@inheritDoc}
	 * 1. Request stoppage of interchange
	 * 2. Stop devices
	 *          
	 * {@inheritDoc}
	 * 1. 融通停止依頼
	 * 2. デバイス停止
	 */
	@Override protected void doHandle(Handler<AsyncResult<Void>> completionHandler) {
		new AskAndWaitForStopDeals(vertx_, policy_, logMessages_).action(r -> {
			new StopLocal(vertx_, policy_, logMessages_).action(completionHandler);
		});
	}

}
