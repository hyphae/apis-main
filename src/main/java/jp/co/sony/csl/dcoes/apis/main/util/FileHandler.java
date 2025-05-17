package jp.co.sony.csl.dcoes.apis.main.util;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.file.FileSystem;
import io.vertx.core.json.JsonObject;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.JsonObjectUtil;

public final class FileHandler {
    private FileHandler() {
        // prevent instantiation
    }
    public static void readLocalFile(Handler<AsyncResult<JsonObject>> completionHandler, FileSystem fileSystem, String localFilePath) {
        fileSystem.readFile(localFilePath, resFile -> {
            if (resFile.succeeded()) {
                JsonObjectUtil.toJsonObject(resFile.result(), resToJsonObject -> {
                    if (resToJsonObject.succeeded()) {
                        JsonObject jsonObject = resToJsonObject.result();
                        completionHandler.handle(Future.succeededFuture(jsonObject));
                    } else {
                        completionHandler.handle(resToJsonObject);
                    }
                });
            } else {
                completionHandler.handle(Future.failedFuture(resFile.cause()));
            }
        });
    }
}
