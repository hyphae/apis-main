package jp.co.sony.csl.dcoes.apis.main.factory;

import jp.co.sony.csl.dcoes.apis.main.app.controller.DataAcquisition;
import jp.co.sony.csl.dcoes.apis.main.app.controller.DataResponding;
import jp.co.sony.csl.dcoes.apis.main.app.controller.DeviceControlling;

/**
 * An interface to the factories associated with a controller sevice.
 * Has the following three implementations:
 * - {@link jp.co.sony.csl.dcoes.apis.main.factory.impl.dcdc.emulator.DcdcEmulatorControllerFactory DcdcEmulatorControllerFactory}
 * - {@link jp.co.sony.csl.dcoes.apis.main.factory.impl.dcdc.v1.DcdcV1ControllerFactory DcdcV1ControllerFactory}
 * - {@link jp.co.sony.csl.dcoes.apis.main.factory.impl.dcdc.v2.DcdcV2ControllerFactory DcdcV2ControllerFactory}
 * @author OES Project
 *          
 * Controller サービスまわりのファクトリのインタフェイス.
 * 実装は以下の 3 つ.
 * - {@link jp.co.sony.csl.dcoes.apis.main.factory.impl.dcdc.emulator.DcdcEmulatorControllerFactory DcdcEmulatorControllerFactory}
 * - {@link jp.co.sony.csl.dcoes.apis.main.factory.impl.dcdc.v1.DcdcV1ControllerFactory DcdcV1ControllerFactory}
 * - {@link jp.co.sony.csl.dcoes.apis.main.factory.impl.dcdc.v2.DcdcV2ControllerFactory DcdcV2ControllerFactory}
 * @author OES Project
 */
public interface ControllerFactory {

	/**
	 * Create a real object for a data acquisition service.
	 * @return an instance of the {@link DataAcquisition} subclass
	 *          
	 * データ取得サービスの実オブジェクトを生成する.
	 * @return {@link DataAcquisition} サブクラスのインスタンス
	 */
	DataAcquisition createDataAcquisition();
	/**
	 * Create a real object for a data response service.
	 * @return an instance of the {@link DataResponding} subclass
	 *          
	 * データ応答サービスの実オブジェクトを生成する.
	 * @return {@link DataResponding} サブクラスのインスタンス
	 */
	DataResponding createDataResponding();
	/**
	 * Create a real object for a device control service.
	 * @return an instance of the {@link DeviceControlling} subclass
	 *          
	 * デバイス制御サービスの実オブジェクトを生成する.
	 * @return {@link DeviceControlling} サブクラスのインスタンス
	 */
	DeviceControlling createDeviceControlling();

}
