package jp.co.sony.csl.dcoes.apis.main.app.gridmaster.deal_execution;

import java.util.List;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import jp.co.sony.csl.dcoes.apis.main.app.mediator.util.DealUtil;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorExceptionUtil;

/**
 * Delete interchange information while writing abnormal termination information.
 * @author OES Project
 *          
 * 異常終了情報を書き込みつつ融通情報を削除する.
 * @author OES Project
 */
public class DealDispositionWithAbortReason extends DealDisposition {
//	private static final Logger log = LoggerFactory.getLogger(DealDispositionWithAbortReason.class);

	private String abortReason_;

	/**
	 * Create an instance.
	 * @param vertx a vertx object
	 * @param policy a POLICY object. To prevent changes from taking effect while running, a copy is passed at startup to {@link jp.co.sony.csl.dcoes.apis.main.app.gridmaster.main_loop.DealExecution DealExecution}.
	 * @param deal the DEAL object to be processed
	 * @param otherDeals a list of other DEAL objects that exist at the same time
	 * @param abortReason the reason for abnormal termination
	 *          
	 * インスタンスを生成する.
	 * @param vertx vertx オブジェクト
	 * @param policy POLICY オブジェクト. 処理中に変更されても影響しないように {@link jp.co.sony.csl.dcoes.apis.main.app.gridmaster.main_loop.DealExecution DealExecution} 開始時にコピーしたものが渡される.
	 * @param deal 処理対象 DEAL オブジェクト
	 * @param otherDeals 同時に存在している他の DEAL オブジェクトのリスト
	 * @param abortReason 異常終了の理由
	 */
	public DealDispositionWithAbortReason(Vertx vertx, JsonObject policy, JsonObject deal, List<JsonObject> otherDeals, String abortReason) {
		super(vertx, policy, deal, otherDeals);
		abortReason_ = abortReason;
	}
	/**
	 * Create an instance.
	 * Initialization is not required because the internal state of another {@link AbstractDealExecution} is inherited as-is.
	 * @param other another abstractdealexecution object
	 * @param abortReason the reason for abnormal termination
	 *          
	 * インスタンスを生成する.
	 * 他の {@link AbstractDealExecution} の内部状態をそのまま受け継ぐため初期化不要.
	 * @param other 他の abstractdealexecution オブジェクト
	 * @param abortReason 異常終了の理由
	 */
	public DealDispositionWithAbortReason(AbstractDealExecution other, String abortReason) {
		super(other);
		abortReason_ = abortReason;
	}

	@Override protected void doExecute(Handler<AsyncResult<Void>> completionHandler) {
		abortDeal_(resAbort-> {
			if (resAbort.succeeded()) {
				super.doExecute(completionHandler);
			} else {
				completionHandler.handle(resAbort);
			}
		});
	}

	private void abortDeal_(Handler<AsyncResult<Void>> completionHandler) {
		DealUtil.abort(vertx_, deal_, referenceDateTimeString_(), abortReason_, resAbort -> ErrorExceptionUtil.reportIfNeedAndHandle(vertx_, resAbort, completionHandler));
	}

}
