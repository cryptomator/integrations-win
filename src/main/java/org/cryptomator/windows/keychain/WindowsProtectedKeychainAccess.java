package org.cryptomator.windows.keychain;

import org.cryptomator.integrations.common.DisplayName;
import org.cryptomator.integrations.common.LocalizedDisplayName;
import org.cryptomator.integrations.common.OperatingSystem;
import org.cryptomator.integrations.common.Priority;
import org.cryptomator.integrations.keychain.KeychainAccessProvider;
import org.cryptomator.windows.common.Localization;

/**
 * Windows implementation for the {@link KeychainAccessProvider} based on the <a href="https://en.wikipedia.org/wiki/Data_Protection_API">data protection API</a>.
 * The storage locations to check for encrypted data can be set with the JVM property {@value KEYCHAIN_PATHS_PROPERTY} with the paths seperated with the character defined in the JVM property path.separator.
 */
@Priority(1000)
@OperatingSystem(OperatingSystem.Value.WINDOWS)
@LocalizedDisplayName(bundle = "WinIntegrationsBundle", key = "org.cryptomator.windows.keychain.displayName")
public final class WindowsProtectedKeychainAccess extends WindowsKeychainAccessBase {

	private static final String KEYCHAIN_PATHS_PROPERTY = "cryptomator.integrationsWin.keychainPaths";

	//no-arg constructuor required for ServiceLoader
	public WindowsProtectedKeychainAccess() {
		super(new FileKeychain(KEYCHAIN_PATHS_PROPERTY),new WinDataProtection());
	}

}
