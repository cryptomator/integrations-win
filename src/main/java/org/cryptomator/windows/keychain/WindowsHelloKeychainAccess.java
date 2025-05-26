package org.cryptomator.windows.keychain;

import org.cryptomator.integrations.common.LocalizedDisplayName;
import org.cryptomator.integrations.common.OperatingSystem;
import org.cryptomator.integrations.common.Priority;
import org.cryptomator.integrations.keychain.KeychainAccessProvider;
import org.cryptomator.windows.common.Localization;

/**
 * Windows implementation for the {@link KeychainAccessProvider} based on the <a href="https://en.wikipedia.org/wiki/Data_Protection_API">data protection API</a>.
 * The storage locations to check for encrypted data can be set with the JVM property {@value WINDOWS_HELLO_KEYCHAIN_PATHS_PROPERTY} with the paths seperated with the character defined in the JVM property path.separator.
 */
@Priority(1001)
@OperatingSystem(OperatingSystem.Value.WINDOWS)
@LocalizedDisplayName(bundle = "WinIntegrationsBundle", key ="org.cryptomator.windows.keychain.displayWindowsHelloName")
public final class WindowsHelloKeychainAccess extends WindowsKeychainAccessBase {

	private static final String WINDOWS_HELLO_KEYCHAIN_PATHS_PROPERTY = "cryptomator.integrationsWin.windowsHelloKeychainPaths";
	private static final String WINDOWS_HELLO_KEY_ID_PROPERTY = "cryptomator.integrationsWin.windowsHelloKeyId";

	@SuppressWarnings("unused") // default constructor required by ServiceLoader
	public WindowsHelloKeychainAccess() {
		super(new FileKeychain(WINDOWS_HELLO_KEYCHAIN_PATHS_PROPERTY),
				new WindowsHello(System.getProperty(WINDOWS_HELLO_KEY_ID_PROPERTY, "org.cryptomator.integrations-win")));
	}
}
