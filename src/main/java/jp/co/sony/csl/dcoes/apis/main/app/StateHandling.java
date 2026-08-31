package jp.co.sony.csl.dcoes.apis.main.app;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vertx.core.Promise;
import io.vertx.core.shareddata.AsyncMap;
import java.io.File;
import jp.co.sony.csl.dcoes.apis.common.Error;
import jp.co.sony.csl.dcoes.apis.common.ServiceAddress;
import jp.co.sony.csl.dcoes.apis.common.util.StringUtil;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.EncryptedClusterWideMapUtil;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.FileSystemUtil;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectUtil;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.VertxConfig;
import jp.co.sony.csl.dcoes.apis.main.util.ApisConfig;
import jp.co.sony.csl.dcoes.apis.main.util.ErrorUtil;

public class StateHandling extends AbstractVerticle {
	private static final Logger LOGGER = LoggerFactory.getLogger(StateHandling.class);

	private static final JsonObjectUtil.DefaultString DEFAULT_FILE_FORMAT = new JsonObjectUtil.DefaultString(
			StringUtil.TMPDIR + "/apis/state/%s");

	private static final String MAP_NAME = StateHandling.class.getName();
	private static final String PATH_FORMAT;

	static {
		String s = VertxConfig.config.getString(DEFAULT_FILE_FORMAT, "stateFileFormat");
		PATH_FORMAT = StringUtil.fixFilePath(s);
	}

	private static String operationMode = null;
	private static boolean started = false;
	private static boolean stopping = false;

	@Override
	public void start(Promise<Void> startPromise) throws Exception {
		init(resInit -> {
			if (resInit.succeeded()) {
				startGlobalOperationModeService(resGlobalOperationMode -> {
					if (resGlobalOperationMode.succeeded()) {
						startLocalOperationModeService(resLocalOperationMode -> {
							if (resLocalOperationMode.succeeded()) {
								LOGGER.trace("started : {}", deploymentID());
								startPromise.complete();
							} else {
								startPromise.fail(resLocalOperationMode.cause());
							}
						});
					} else {
						startPromise.fail(resGlobalOperationMode.cause());
					}
				});
			} else {
				startPromise.fail(resInit.cause());
			}
		});
	}

	@Override
	public void stop() throws Exception {
		LOGGER.trace("stopped : {}", deploymentID());
	}

	private void init(Handler<AsyncResult<Void>> onComplete) {
		readFromFile(vertx, "operationMode", res -> {
			if (res.succeeded()) {
				String result = res.result();
				if (result != null && !"heteronomous".equals(result) && !"stop".equals(result)) {
					ErrorUtil.report(vertx, Error.Category.USER, Error.Extent.LOCAL, Error.Level.WARN,
							"local operationMode '" + result + "' not supported, default to null ( follow global )");
					result = null;
				}
				operationMode = result;
				onComplete.handle(Future.succeededFuture());
			} else {
				onComplete.handle(Future.failedFuture(res.cause()));
			}
		});
	}

	private void startGlobalOperationModeService(Handler<AsyncResult<Void>> onComplete) {
		vertx.eventBus().<String>consumer(ServiceAddress.operationMode(), req -> {
			String command = req.headers().get("command");
			if ("set".equals(command)) {
				String value = req.body();
				if (value != null && !"autonomous".equals(value) && !"heteronomous".equals(value)
						&& !"stop".equals(value) && !"manual".equals(value)) {
					ErrorUtil.report(vertx, Error.Category.USER, Error.Extent.LOCAL, Error.Level.WARN,
							"global operationMode '" + value + "' not supported, default to null ( follow policy )");
					value = null;
				}
				String result = value;
				setToClusterWideMap(vertx, "operationMode", result, r -> {
					if (r.succeeded()) {
						LOGGER.info("global operationMode set to : {}", result);
						req.reply(ApisConfig.unitId());
					} else {
						req.fail(-1, r.cause().getMessage());
					}
				});
			} else {
				globalOperationMode(vertx, r -> {
					if (r.succeeded()) {
						req.reply(r.result());
					} else {
						req.fail(-1, r.cause().getMessage());
					}
				});
			}
		}).completionHandler(onComplete);
	}

	private void startLocalOperationModeService(Handler<AsyncResult<Void>> onComplete) {
		vertx.eventBus().<String>consumer(ServiceAddress.User.operationMode(ApisConfig.unitId()), req -> {
			String command = req.headers().get("command");
			if ("set".equals(command)) {
				String value = req.body();
				if (value != null && !"heteronomous".equals(value) && !"stop".equals(value)) {
					ErrorUtil.report(vertx, Error.Category.USER, Error.Extent.LOCAL, Error.Level.WARN,
							"local operationMode '" + value + "' not supported, default to null ( follow global )");
					value = null;
				}
				String result = value;
				operationMode = result;
				writeToFileUsingKey(vertx, "operationMode", result, r -> {
					if (r.succeeded()) {
						LOGGER.info("local operationMode set to : {}", result);
						req.reply(ApisConfig.unitId());
					} else {
						req.fail(-1, r.cause().getMessage());
					}
				});
			} else {
				localOperationMode(vertx, r -> {
					if (r.succeeded()) {
						req.reply(r.result());
					} else {
						req.fail(-1, r.cause().getMessage());
					}
				});
			}
		}).completionHandler(onComplete);
	}

	public static void globalOperationMode(Vertx vertx, Handler<AsyncResult<String>> onComplete) {
		getFromClusterWideMap(vertx, "operationMode", res -> {
			if (res.succeeded()) {
				String result = res.result();
				if (result != null && !"autonomous".equals(result) && !"heteronomous".equals(result)
						&& !"stop".equals(result) && !"manual".equals(result)) {
					ErrorUtil.report(vertx, Error.Category.USER, Error.Extent.LOCAL, Error.Level.WARN,
							"global operationMode '" + result + "' not supported, follow policy");
					result = null;
				}
				if (result == null) {
					result = PolicyKeeping.cache().getString("operationMode");
					if (result != null && !"autonomous".equals(result) && !"heteronomous".equals(result)
							&& !"stop".equals(result) && !"manual".equals(result)) {
						ErrorUtil.report(vertx, Error.Category.USER, Error.Extent.LOCAL, Error.Level.WARN,
								"policy operationMode '" + result + "' not supported, default to null");
						result = null;
					}
				}
				if (result == null) {
					ErrorUtil.report(vertx, Error.Category.USER, Error.Extent.LOCAL, Error.Level.WARN,
							"global operationMode is null, default to 'stop'");
					result = "stop";
				}
				onComplete.handle(Future.succeededFuture(result));
			} else {
				onComplete.handle(Future.failedFuture(res.cause()));
			}
		});
	}

	public static void localOperationMode(Vertx vertx, Handler<AsyncResult<String>> onComplete) {
		String result = operationMode;
		if (result != null && !"heteronomous".equals(result) && !"stop".equals(result)) {
			ErrorUtil.report(vertx, Error.Category.USER, Error.Extent.LOCAL, Error.Level.WARN,
					"local operationMode '" + result + "' not supported, default to null");
			result = null;
		}
		onComplete.handle(Future.succeededFuture(result));
	}

	public static void operationModes(Vertx vertx, Handler<AsyncResult<JsonObject>> onComplete) {
		Promise<String> globalPromise = Promise.promise();
		Promise<String> localPromise = Promise.promise();
		globalOperationMode(vertx, globalPromise);
		localOperationMode(vertx, localPromise);
		Future.all(globalPromise.future(), localPromise.future()).onComplete(ar -> {
			if (ar.succeeded()) {
				String global = ar.result().resultAt(0);
				String local = ar.result().resultAt(1);
				String effective = null;

				if (local == null) {
					effective = global;
				} else if ("autonomous".equals(global)) {
					effective = local;
				} else if ("heteronomous".equals(global)) {
					effective = local;
				} else if ("stop".equals(global)) {
					effective = global;
				} else if ("manual".equals(global)) {
					effective = global;
				}

				if (effective == null) {
					ErrorUtil.report(vertx, Error.Category.USER, Error.Extent.LOCAL, Error.Level.WARN,
							"illegal operationModes; global : " + global + ", local : " + local + "; use 'stop'");
					effective = "stop";
				}

				JsonObject result = new JsonObject()
						.put("global", global)
						.put("local", local)
						.put("effective", effective);

				onComplete.handle(Future.succeededFuture(result));
			} else {
				onComplete.handle(Future.failedFuture(ar.cause()));
			}
		});
	}

	public static void operationMode(Vertx vertx, Handler<AsyncResult<String>> onComplete) {
		operationModes(vertx, res -> {
			if (res.succeeded()) {
				onComplete.handle(Future.succeededFuture(res.result().getString("effective")));
			} else {
				onComplete.handle(Future.failedFuture(res.cause()));
			}
		});
	}

	public static void setStarted() {
		started = true;
		LOGGER.info("started");
	}

	public static void setStopping() {
		LOGGER.info("stopping");
		stopping = true;
	}

	public static boolean isStarted() {
		return started;
	}

	public static boolean isStopping() {
		return stopping;
	}

	public static boolean isInOperation() {
		return started && !stopping;
	}

	private static void setToClusterWideMap(Vertx vertx, String key, String value,
			Handler<AsyncResult<Void>> onComplete) {
		EncryptedClusterWideMapUtil.<String, String>getEncryptedClusterWideMap(vertx, MAP_NAME, resMap -> {
			if (resMap.succeeded()) {
				AsyncMap<String, String> map = resMap.result();
				if (value != null) {
					map.put(key, value, resPut -> {
						if (resPut.succeeded()) {
							onComplete.handle(Future.succeededFuture());
						} else {
							ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL,
									Error.Level.ERROR, "Communication failed on SharedData", resPut.cause(),
									onComplete);
						}
					});
				} else {
					map.remove(key, resRemove -> {
						if (resRemove.succeeded()) {
							onComplete.handle(Future.succeededFuture());
						} else {
							ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL,
									Error.Level.ERROR, "Communication failed on SharedData", resRemove.cause(),
									onComplete);
						}
					});
				}
			} else {
				ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR,
						"Communication failed on SharedData", resMap.cause(), onComplete);
			}
		});
	}

	private static void getFromClusterWideMap(Vertx vertx, String key, Handler<AsyncResult<String>> onComplete) {
		EncryptedClusterWideMapUtil.<String, String>getEncryptedClusterWideMap(vertx, MAP_NAME, resMap -> {
			if (resMap.succeeded()) {
				AsyncMap<String, String> map = resMap.result();
				map.get(key, resGet -> {
					if (resGet.succeeded()) {
						onComplete.handle(Future.succeededFuture(resGet.result()));
					} else {
						ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR,
								"Communication failed on SharedData", resGet.cause(), onComplete);
					}
				});
			} else {
				ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.ERROR,
						"Communication failed on SharedData", resMap.cause(), onComplete);
			}
		});
	}

	private static void writeToFileDirect(Vertx vertx, String path, String value,
			Handler<AsyncResult<Void>> onComplete) {
		vertx.fileSystem().writeFile(path, Buffer.buffer(value), resWriteFile -> {
			if (resWriteFile.succeeded()) {
				onComplete.handle(Future.succeededFuture());
			} else {
				ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.FATAL,
						"Operation failed on File System", resWriteFile.cause(), onComplete);
			}
		});
	}

	private static void writeToFileUsingKey(Vertx vertx, String key, String value,
			Handler<AsyncResult<Void>> onComplete) {
		String path = String.format(PATH_FORMAT, key);
		if (value != null) {
			vertx.fileSystem().exists(path, resExists -> {
				if (resExists.succeeded()) {
					if (resExists.result()) {
						writeToFileDirect(vertx, path, value, onComplete);
					} else {
						String dir = new File(path).getParent();
						FileSystemUtil.ensureDirectory(vertx, dir, resEnsureDir -> {
							if (resEnsureDir.succeeded()) {
								vertx.fileSystem().createFile(path, resCreate -> {
									if (resCreate.succeeded()) {
										writeToFileDirect(vertx, path, value, onComplete);
									} else {
										vertx.fileSystem().exists(path, resExistsAgain -> {
											if (resExistsAgain.succeeded()) {
												if (resExistsAgain.result()) {
													writeToFileDirect(vertx, path, value, onComplete);
												} else {
													ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK,
															Error.Extent.LOCAL, Error.Level.FATAL,
															"Operation failed on File System", resCreate.cause(),
															onComplete);
												}
											} else {
												ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK,
														Error.Extent.LOCAL, Error.Level.FATAL,
														"Operation failed on File System", resExistsAgain.cause(),
														onComplete);
											}
										});
									}
								});
							} else {
								ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL,
										Error.Level.FATAL, "Operation failed on File System", resEnsureDir.cause(),
										onComplete);
							}
						});
					}
				} else {
					ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.FATAL,
							"Operation failed on File System", resExists.cause(), onComplete);
				}
			});
		} else {
			vertx.fileSystem().exists(path, resExists -> {
				if (resExists.succeeded()) {
					if (resExists.result()) {
						vertx.fileSystem().delete(path, resDelete -> {
							if (resDelete.succeeded()) {
								onComplete.handle(Future.succeededFuture());
							} else {
								vertx.fileSystem().exists(path, resExistsAgain -> {
									if (resExistsAgain.succeeded()) {
										if (resExistsAgain.result()) {
											ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL,
													Error.Level.FATAL, "Operation failed on File System",
													resDelete.cause(), onComplete);
										} else {
											onComplete.handle(Future.succeededFuture());
										}
									} else {
										ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL,
												Error.Level.FATAL, "Operation failed on File System",
												resExistsAgain.cause(), onComplete);
									}
								});
							}
						});
					} else {
						onComplete.handle(Future.succeededFuture());
					}
				} else {
					ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.FATAL,
							"Operation failed on File System", resExists.cause(), onComplete);
				}
			});
		}
	}

	private static void readFromFile(Vertx vertx, String key, Handler<AsyncResult<String>> onComplete) {
		String path = String.format(PATH_FORMAT, key);
		vertx.fileSystem().exists(path, resExists -> {
			if (resExists.succeeded()) {
				if (resExists.result()) {
					vertx.fileSystem().readFile(path, resReadFile -> {
						if (resReadFile.succeeded()) {
							String result = String.valueOf(resReadFile.result()).trim();
							onComplete.handle(Future.succeededFuture(result));
						} else {
							ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL,
									Error.Level.FATAL, "Operation failed on File System", resReadFile.cause(),
									onComplete);
						}
					});
				} else {
					onComplete.handle(Future.succeededFuture());
				}
			} else {
				ErrorUtil.reportAndFail(vertx, Error.Category.FRAMEWORK, Error.Extent.LOCAL, Error.Level.FATAL,
						"Operation failed on File System", resExists.cause(), onComplete);
			}
		});
	}
}
