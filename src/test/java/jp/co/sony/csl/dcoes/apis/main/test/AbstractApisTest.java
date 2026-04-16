package jp.co.sony.csl.dcoes.apis.main.test;

import static org.junit.Assert.assertTrue;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

import java.net.URL;
import java.util.concurrent.CompletableFuture;

import jp.co.sony.csl.dcoes.apis.common.util.EncryptionUtil;
import jp.co.sony.csl.dcoes.apis.common.util.vertx.VertxConfig;

import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public abstract class AbstractApisTest {

	public static final String APIS_CONFIG_JSON = "sample-config.json";

	protected Vertx vertx;
	protected JsonObject config;

	public AbstractApisTest() {
		super();
	}

	@Before
	public void before(TestContext context) {
		VertxOptions options = new VertxOptions();
		CompletableFuture<Vertx> cf = new CompletableFuture<Vertx>();
		config = setupAndReadConfigFile_();
		VertxConfig.config.setJsonObject(config);
		EncryptionUtil.initialize(r -> {
			if (r.succeeded()) {
				Vertx.clusteredVertx(options, rr -> {
					if (rr.succeeded()) {
						VertxConfig.config.setJsonObject(config);
						cf.complete(rr.result());
					} else {
						cf.completeExceptionally(rr.cause());
					}
				});
				try {
					vertx = cf.get();
				} catch (Exception e) {
					context.fail(e);
				}
			} else {
				context.fail(r.cause());
			}
		});
	}

	@After
	public void after(TestContext context) {
		vertx.close(context.asyncAssertSuccess());
	}

	private JsonObject setupAndReadConfigFile_() {
		try {
			URL configUrl = ClassLoader.getSystemClassLoader().getResource(APIS_CONFIG_JSON);
			byte[] baConfig = IOUtils.toByteArray(configUrl.openStream());
			assertTrue(baConfig.length > 0);
			return new JsonObject(Buffer.buffer(baConfig));
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

}
