package jp.co.sony.csl.dcoes.apis.main.factory;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jp.co.sony.csl.dcoes.apis.main.factory.impl.dcdc.emulator.DcdcEmulatorFactory;
import jp.co.sony.csl.dcoes.apis.main.factory.impl.dcdc.v1.DcdcV1Factory;
import jp.co.sony.csl.dcoes.apis.main.factory.impl.dcdc.v2.DcdcV2Factory;
import jp.co.sony.csl.dcoes.apis.main.util.ApisConfig;

public abstract class Factory {
	private static final Logger log = LoggerFactory.getLogger(Factory.class);

	private ControllerFactory controllerFactory_;

	protected Factory() {
		controllerFactory_ = createControllerFactory();
	}

	public ControllerFactory controllerFactory() {
		return controllerFactory_;
	}

	protected abstract ControllerFactory createControllerFactory();

	private static Factory instance_;

	public static Factory factory() {
		return instance_;
	}

	public static void initialize(Handler<AsyncResult<Void>> completionHandler) {
		if (ApisConfig.systemType() != null) {
			switch (ApisConfig.systemType()) {
				case "dcdc_emulator":
					instance_ = new DcdcEmulatorFactory();
					break;
				case "dcdc_v1":
					instance_ = new DcdcV1Factory();
					break;
				case "dcdc_v2":
					instance_ = new DcdcV2Factory();
					break;
			}
		}
		if (instance_ != null) {
			if (log.isInfoEnabled())
				log.info("initialized");
			completionHandler.handle(Future.succeededFuture());
		} else {
			completionHandler
					.handle(Future.failedFuture("initialize failed ; CONFIG.systemType : " + ApisConfig.systemType()));
		}
	}

}
