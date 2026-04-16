package jp.co.sony.csl.dcoes.apis.main.app.util;

import io.vertx.core.DeploymentOptions;
import io.vertx.ext.unit.TestContext;
import jp.co.sony.csl.dcoes.apis.main.test.AbstractApisTest;
import jp.co.sony.csl.dcoes.apis.main.util.Starter;

import org.junit.Test;

/**
 * Starter test.
 * @author OES Project
 *          
 * Starter のテスト.
 * @author OES Project
 */
public class StarterTest extends AbstractApisTest {

	/**
	 * Supports deploy and undeploy operations.
	 * @param context a testcontext object
	 *          
	 *  deploy と undeploy が行える.
	 * @param context testcontext オブジェクト
	 */
	@Test
	public void testDeployAndUnDeploy(TestContext context) {
		DeploymentOptions options = new DeploymentOptions().setConfig(config);
		vertx.deployVerticle(Starter.class.getName(), options, context.asyncAssertSuccess(deploymentID -> {
			vertx.undeploy(deploymentID, context.asyncAssertSuccess());
		}));
	}

}
