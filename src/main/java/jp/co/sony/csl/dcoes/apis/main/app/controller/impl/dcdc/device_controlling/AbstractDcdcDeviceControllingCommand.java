package jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.device_controlling;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import jp.co.sony.csl.dcoes.apis.main.app.controller.DataAcquisition;
import jp.co.sony.csl.dcoes.apis.main.app.controller.DeviceControlling;
import jp.co.sony.csl.dcoes.apis.main.app.controller.impl.dcdc.DcdcDeviceControlling;

/**
 * A device control object for DCDC systems.
 * Used from {@link DcdcDeviceControlling}.
 * @author OES Project
 *          
 * DCDC システム向けデバイス制御の親玉.
 * {@link DcdcDeviceControlling} から使用される.
 * @author OES Project
 */
public abstract class AbstractDcdcDeviceControllingCommand {
	private static final Logger log = LoggerFactory.getLogger(AbstractDcdcDeviceControllingCommand.class);

	protected Vertx vertx_;
	protected DcdcDeviceControlling controller_;
	protected JsonObject params_;

	/**
	 * Create an instance.
	 * @param vertx a vertx object
	 * @param controller an object that actually sends commands to the device
	 * @param params control parameters
	 *          
	 * インスタンスを生成する.
	 * @param vertx vertx オブジェクト
	 * @param controller 実際にデバイスに命令を送信するオブジェクト
	 * @param params 制御パラメタ
	 */
	public AbstractDcdcDeviceControllingCommand(Vertx vertx, DcdcDeviceControlling controller, JsonObject params) {
		vertx_ = vertx;
		controller_ = controller;
		params_ = params;
	}

	/**
	 * Perform control operations.
	 * The device control state after performing these operations is received by the {@link AsyncResult#result()} method of completionHandler.
	 * The actual processing is implemented by the {@link #doExecute (Handler)} of subclasses.
	 * @param completionHandler the completion handler
	 *          
	 * 制御を実行する.
	 * completionHandler の {@link AsyncResult#result()} で実行後のデバイス制御状態を受け取る.
	 * 実際の処理はサブクラスの {@link #doExecute(Handler)} で実装する.
	 * @param completionHandler the completion handler
	 */
	public final void execute(Handler<AsyncResult<JsonObject>> completionHandler) {
		if (log.isInfoEnabled()) log.info(getClass().getSimpleName() + ".execute()");
		if (startIgnoreDynamicSafetyCheck()) {
			// Start skipping dynamic safety checks where necessary
			// 必要に応じて動的安全性チェックのスキップを開始する
			DeviceControlling.ignoreDynamicSafetyCheck(true);
		}
		// Process the actual implementation
		// 実実装を処理する
		doExecute(res -> {
			if (log.isInfoEnabled()) {
				log.info(getClass().getSimpleName() + ".execute(); res.succeeded() : " + res.succeeded());
				if (res.succeeded()) {
					log.info(getClass().getSimpleName() + ".execute(); res.result() : " + res.result());
				} else {
					log.info(getClass().getSimpleName() + ".execute(); res.cause() : " + res.cause());
				}
			}
			if (res.succeeded()) {
				if (stopIgnoreDynamicSafetyCheck()) {
					// Stop skipping dynamic safety checks
					// 必要に応じて動的安全性チェックのスキップを終了する
					DeviceControlling.ignoreDynamicSafetyCheck(false);
				}
			}
			completionHandler.handle(res);
		});
	}

	/**
	 * Get the flag that indicates whether or not to start skipping dynamic safety checks while executing this process.
	 * If true, set the {@link DeviceControlling} dynamic safety check skip flag before execution.
	 * @return flag
	 *          
	 * この処理の実行と同時に動的安全性チェックのスキップを開始するか否かのフラグを取得する.
	 * true なら実行前に {@link DeviceControlling} の動的安全性チェックスキップフラグを立てる.
	 * @return フラグ
	 */
	protected abstract boolean startIgnoreDynamicSafetyCheck();
	/**
	 * Get the flag that indicates whether or not to stop skipping dynamic safety checks while executing this process.
	 * If true, clear the {@link DeviceControlling} dynamic safety check skip flag after execution.
	 * @return flag
	 *          
	 * この処理の実行と同時に動的安全性チェックのスキップを終了するか否かのフラグを取得する.
	 * true なら実行後に {@link DeviceControlling} の動的安全性チェックスキップフラグを落とす.
	 * @return フラグ
	 */
	protected abstract boolean stopIgnoreDynamicSafetyCheck();
	/**
	 * Specific processing.
	 * Implemented in subclasses.
	 * @param completionHandler the completion handler
	 *          
	 * 具体的な処理.
	 * サブクラスで実装する.
	 * @param completionHandler the completion handler
	 */
	protected abstract void doExecute(Handler<AsyncResult<JsonObject>> completionHandler);

	protected void succeeded(Handler<AsyncResult<JsonObject>> completionHandler) {
		completionHandler.handle(Future.succeededFuture(DataAcquisition.cache.getJsonObject("dcdc")));
	}

}
