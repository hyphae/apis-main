package jp.co.sony.csl.dcoes.apis.main.app.gridmaster.main_loop;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.main.app.PolicyKeeping;
import jp.co.sony.csl.dcoes.apis.main.app.gridmaster.ErrorCollection;
import jp.co.sony.csl.dcoes.apis.main.error.handling.AbstractErrorsHandling;
import jp.co.sony.csl.dcoes.apis.main.error.handling.GlobalAnyFatalsHandling;
import jp.co.sony.csl.dcoes.apis.main.error.handling.GlobalFrameworkErrorsHandling;
import jp.co.sony.csl.dcoes.apis.main.error.handling.GlobalHardwareErrorsHandling;
import jp.co.sony.csl.dcoes.apis.main.error.handling.GlobalLogicErrorsHandling;
import jp.co.sony.csl.dcoes.apis.main.error.handling.GlobalUserErrorsHandling;

/**
 * Handle errors.
 * Called periodically from {@link jp.co.sony.csl.dcoes.apis.main.app.gridmaster.MainLoop}.
 * Check for errors.
 * If an error exists, create and run a suitable response process according to its type, scope and severity.
 * @author OES Project
 *          
 * エラーに対応する.
 * {@link jp.co.sony.csl.dcoes.apis.main.app.gridmaster.MainLoop} から定期的に呼ばれる.
 * エラーの有無を確認する.
 * エラーが存在していたらその種類と範囲と深刻さごとに適切な対応処理を作成して実行させる.
 * @author OES Project
 */
public class ErrorHandling {
	private static final Logger log = LoggerFactory.getLogger(ErrorHandling.class);

	private ErrorHandling() { }

	/**
	 * Processing called from {@link jp.co.sony.csl.dcoes.apis.main.app.gridmaster.MainLoop}.
	 * For local errors that have been received and saved, create and run a suitable error processing object for each type and scope.
	 * @param vertx a vertx object
	 * @param completionHandler the completion handler
	 *          
	 * {@link jp.co.sony.csl.dcoes.apis.main.app.gridmaster.MainLoop} から呼ばれる処理.
	 * 受け取って保存してあるローカルエラーに対して種類と範囲ごとに対応するエラー処理オブジェクトを作成し実行する.
	 * @param vertx vertx オブジェクト
	 * @param completionHandler the completion handler
	 */
	public static void execute(Vertx vertx, Handler<AsyncResult<Void>> completionHandler) {
		// Fetch the POLICY (because it might change during processing)
		// POLICY を確保し ( 処理中に変更される可能性があるので )
		JsonObject policy = PolicyKeeping.cache().jsonObject();
		new HandleErrors_(vertx, policy).doLoop_(r -> {
			// When the processing is finished, mark it as finished
			// 処理が終わったら終わったマークをつける
			ErrorCollection.errorHandled();
			completionHandler.handle(r);
		});
	}

	////

	/**
	 * Error handling class.
	 * Loops through the error types.
	 * @author OES Project
	 *          
	 * エラー処理クラス.
	 * エラーの種類をループする.
	 * @author OES Project
	 */
	private static class HandleErrors_ {
		private Vertx vertx_;
		private JsonObject policy_;
		private List<Error.Category> categoriesForLoop_;
		/**
		 * Create an instance.
		 * @param vertx a vertx object
		 * @param policy a POLICY object
		 *          
		 * インスタンス生成.
		 * @param vertx vertx オブジェクト
		 * @param policy POLICY オブジェクト
		 */
		private HandleErrors_(Vertx vertx, JsonObject policy) {
			vertx_ = vertx;
			policy_ = policy;
			categoriesForLoop_ = new ArrayList<Error.Category>(Arrays.asList(Error.Category.values()));
		}
		private void doLoop_(Handler<AsyncResult<Void>> completionHandler) {
			if (categoriesForLoop_.isEmpty()) {
				completionHandler.handle(Future.succeededFuture());
			} else {
				Error.Category aCategory = categoriesForLoop_.remove(0);
				new HandleErrorsByCategory_(policy_, aCategory).doLoop_(r -> {
					doLoop_(completionHandler);
				});
			}
		}
		/**
		 * Error handling class.
		 * Created by specifying the error type.
		 * Loop through the error severity.
		 * @author OES Project
		 *          
		 * エラー処理クラス.
		 * エラー種類を指定されて誕生する.
		 * エラーの深刻さをループする.
		 * @author OES Project
		 */
		private class HandleErrorsByCategory_ {
			private JsonObject policy_;
			private Error.Category category_;
			private List<Error.Level> levelsForLoop_;
			private HandleErrorsByCategory_(JsonObject policy, Error.Category category) {
				policy_ = policy;
				category_ = category;
				levelsForLoop_ = new ArrayList<Error.Level>(Arrays.asList(Error.Level.values()));
			}
			private void doLoop_(Handler<AsyncResult<Void>> completionHandler) {
				if (levelsForLoop_.isEmpty()) {
					completionHandler.handle(Future.succeededFuture());
				} else {
					Error.Level aLevel = levelsForLoop_.remove(0);
					JsonArray errors = ErrorCollection.cache.removeJsonArray(category_.name(), aLevel.name());
					if (errors != null && 0 < errors.size()) {
						if (log.isInfoEnabled()) log.info("[" + category_ + ':' + aLevel + "] : " + errors);
						AbstractErrorsHandling handler = null;
						switch (aLevel) {
						case WARN:
							// Warning level errors do nothing
							// 警告レベルは何もしない
							log.fatal("#### should never happen; category : " + category_ + ", level : " + aLevel);
							break;
						case ERROR:
							// Failure level errors are handled by creating a processing object according to the type of error
							// 障害レベルなら種類に応じて処理オブジェクトを作りお任せする
							switch (category_) {
							case HARDWARE:
								handler = new GlobalHardwareErrorsHandling(vertx_, policy_, errors);
								break;
							case FRAMEWORK:
								handler = new GlobalFrameworkErrorsHandling(vertx_, policy_, errors);
								break;
							case LOGIC:
								handler = new GlobalLogicErrorsHandling(vertx_, policy_, errors);
								break;
							case USER:
								handler = new GlobalUserErrorsHandling(vertx_, policy_, errors);
								break;
							case UNKNOWN:
								log.fatal("#### should never happen; category : " + category_ + ", level : " + aLevel);
								break;
							}
							break;
						case FATAL:
						case UNKNOWN:
							// Fatal level errors or errors whose level is unknown for some reason are handled by running the FATAL (shutdown) process regardless of the type of error
							// 致命的レベルまたは何らかの理由で不明レベルなら種類にかかわらず FATAL な処理 ( シャットダウン ) を実行する
							handler = new GlobalAnyFatalsHandling(vertx_, policy_, errors);
							break;
						}
						if (handler != null) {
							handler.handle(resHandle -> {
								doLoop_(completionHandler);
							});
						} else {
							doLoop_(completionHandler);
						}
					} else {
						doLoop_(completionHandler);
					}
				}
			}
		}
	}

}
