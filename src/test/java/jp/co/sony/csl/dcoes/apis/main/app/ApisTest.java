package jp.co.sony.csl.dcoes.apis.main.app;

import io.vertx.ext.unit.TestContext;
import jp.co.sony.csl.dcoes.apis.main.factory.Factory;
import jp.co.sony.csl.dcoes.apis.main.test.AbstractApisTest;

import org.junit.Test;

/**
 * Tests the entire APIS.
 * @author OES Project
 *          
 * APIS 全体のテスト.
 * @author OES Project
 */
public class ApisTest extends AbstractApisTest {

	/**
	 * Supports deploy and undeploy operations.
	 * @param context a testcontext object
	 *          
	 *  deploy と undeploy が行える.
	 * @param context testcontext オブジェクト
	 */
	@Test
	public void testDeployAndUnDeploy(TestContext context) {
		Factory.initialize(context.asyncAssertSuccess(v -> {
			vertx.deployVerticle(Apis.class.getName(), context.asyncAssertSuccess(deploymentID -> {
				vertx.undeploy(deploymentID, context.asyncAssertSuccess());
			}));
		}));
	}

}
