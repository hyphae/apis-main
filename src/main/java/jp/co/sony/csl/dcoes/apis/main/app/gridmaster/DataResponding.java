package jp.co.sony.csl.dcoes.apis.main.app.gridmaster;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import jp.co.sony.csl.dcoes.apis.common.ServiceAddress;
import jp.co.sony.csl.dcoes.apis.main.app.PolicyKeeping;

public class DataResponding extends AbstractVerticle {
	private static final Logger log = LoggerFactory.getLogger(DataResponding.class);

	@Override
	public void start(Promise<Void> startPromise) throws Exception {
		startUnitIdsService_(resUnitIds -> {
			if (resUnitIds.succeeded()) {
				startUnitDatasService_(resUnitDatas -> {
					if (resUnitDatas.succeeded()) {
						startPromise.complete();
					} else {
						startPromise.fail(resUnitDatas.cause());
					}
				});
			} else {
				startPromise.fail(resUnitIds.cause());
			}
		});
	}

	@Override
	public void stop() throws Exception {
	}

	private void startUnitIdsService_(Handler<AsyncResult<Void>> onComplete) {
		vertx.eventBus().<Void>consumer(ServiceAddress.GridMaster.unitIds(), req -> {
			JsonArray result = null;
			List<String> memberUnitIds = PolicyKeeping.memberUnitIds();
			if (memberUnitIds != null) {
				result = new JsonArray(memberUnitIds);
			} else {
				result = new JsonArray();
			}
			req.reply(result);
		}).completionHandler(onComplete);
	}

	private void startUnitDatasService_(Handler<AsyncResult<Void>> onComplete) {
		vertx.eventBus().<Void>consumer(ServiceAddress.GridMaster.unitDatas(), req -> {
			if (!DataCollection.cache.isNull()) {
				if (log.isDebugEnabled())
					log.debug("size of cache : " + DataCollection.cache.jsonObject().size());
			} else {
				if (log.isDebugEnabled())
					log.debug("cache is null");
			}
			req.reply(DataCollection.cache.jsonObject());
		}).completionHandler(onComplete);
	}

}
