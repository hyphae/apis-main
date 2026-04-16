package jp.co.sony.csl.dcoes.apis.main.app.gridmaster;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.List;

import jp.co.sony.csl.dcoes.apis.common.ServiceAddress;
import jp.co.sony.csl.dcoes.apis.main.app.PolicyKeeping;

/**
 * A Verticle that provides the unit data of all units.
 * Launched from {@link GridMaster}.
 * Returns the unit data retained in {@link DataCollection} in response to an external unit data acquisition request.
 * @author OES Project
 *          
 * 全ユニットのユニットデータを提供する Verticle.
 * {@link GridMaster} から起動される.
 * 外部からのユニットデータ取得要求に対し {@link DataCollection} で保持しているユニットデータを返す.
 * @author OES Project
 */
public class DataResponding extends AbstractVerticle {
	private static final Logger log = LoggerFactory.getLogger(DataResponding.class);

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
		startUnitIdsService_(resUnitIds -> {
			if (resUnitIds.succeeded()) {
				startUnitDatasService_(resUnitDatas -> {
					if (resUnitDatas.succeeded()) {
						if (log.isTraceEnabled()) log.trace("started : " + deploymentID());
						startFuture.complete();
					} else {
						startFuture.fail(resUnitDatas.cause());
					}
				});
			} else {
				startFuture.fail(resUnitIds.cause());
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
	 * Address: {@link ServiceAddress.GridMaster#unitIds()}
	 * Scope: global
	 * Function: Acquire a list of all unit IDs.
	 *           The ID list is obtained from POLICY.
	 * Message body: none
	 * Message header: none
	 * Response: A list of the IDs of all units [{@link JsonArray}].
	 *           Fails if an error occurs.
	 * @param completionHandler the completion handler
	 *          
	 * {@link io.vertx.core.eventbus.EventBus} サービス起動.
	 * アドレス : {@link ServiceAddress.GridMaster#unitIds()}
	 * 範囲 : グローバル
	 * 処理 : 全ユニットの ID リストを取得する.
	 * 　　   ID リストは POLICY から取得する.
	 * メッセージボディ : なし
	 * メッセージヘッダ : なし
	 * レスポンス : 全ユニットの ID リスト [{@link JsonArray}].
	 * 　　　　　   エラーが起きたら fail.
	 * @param completionHandler the completion handler
	 */
	private void startUnitIdsService_(Handler<AsyncResult<Void>> completionHandler) {
		vertx.eventBus().<Void>consumer(ServiceAddress.GridMaster.unitIds(), req -> {
			JsonArray result = null;
			List<String> memberUnitIds = PolicyKeeping.memberUnitIds();
			if (memberUnitIds != null) {
				result = new JsonArray(memberUnitIds);
			} else {
				result = new JsonArray();
			}
			req.reply(result);
		}).completionHandler(completionHandler);
	}

	/**
	 * Launch the {@link io.vertx.core.eventbus.EventBus} service.
	 * Address: {@link ServiceAddress.GridMaster#unitDatas()}
	 * Scope: global
	 * Function: Acquire the unit data of all units.
	 *           Return cached values that are periodically refreshed by GridMaster.
	 * Message body: none
	 * Message header: none
	 * Response: The unit data for all units [{@link io.vertx.core.json.JsonObject JsonObject}].
	 *           Fails if an error occurs.
	 * @param completionHandler the completion handler
	 *          
	 * {@link io.vertx.core.eventbus.EventBus} サービス起動.
	 * アドレス : {@link ServiceAddress.GridMaster#unitDatas()}
	 * 範囲 : グローバル
	 * 処理 : 全ユニットのユニットデータを取得する.
	 * 　　   GridMaster が定期的にリフレッシュしているキャッシュ値を返す.
	 * メッセージボディ : なし
	 * メッセージヘッダ : なし
	 * レスポンス : 全ユニットのユニットデータ [{@link io.vertx.core.json.JsonObject JsonObject}].
	 * 　　　　　   エラーが起きたら fail.
	 * @param completionHandler the completion handler
	 */
	private void startUnitDatasService_(Handler<AsyncResult<Void>> completionHandler) {
		vertx.eventBus().<Void>consumer(ServiceAddress.GridMaster.unitDatas(), req -> {
			if (!DataCollection.cache.isNull()) {
				if (log.isDebugEnabled()) log.debug("size of cache : " + DataCollection.cache.jsonObject().size());
			} else {
				if (log.isDebugEnabled()) log.debug("cache is null");
			}
			req.reply(DataCollection.cache.jsonObject());
		}).completionHandler(completionHandler);
	}

}
