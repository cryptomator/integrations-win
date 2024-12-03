package org.cryptomator.windows.keychain;

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
public final class WindowsProtectedKeychainAccess extends WindowsFileKeychainAccess {

	private static final String KEYCHAIN_PATHS_PROPERTY = "cryptomator.integrationsWin.keychainPaths";

	private final WinDataProtection dataProtection;

	@SuppressWarnings("unused") // default constructor required by ServiceLoader
	public WindowsProtectedKeychainAccess() {
		this(KEYCHAIN_PATHS_PROPERTY, new WinDataProtection());
	}

	// visible for testing
	WindowsProtectedKeychainAccess(String keychainPathsProp, WinDataProtection dataProtection) {
		super(keychainPathsProp);
		this.dataProtection = dataProtection;
	}

	@Override
	public String displayName() {
		return Localization.get().getString("org.cryptomator.windows.keychain.displayName");
	}

	@Override
	public boolean isSupported() {
		return super.keychainPathPresent;
	}

	@Override
	public boolean isLocked() {
		return false;
	}

	@Override
	byte[] encrypt(byte[] passphrase, byte[] salt) {
		return dataProtection.protect(passphrase, salt);
	}

	@Override
	byte[] decrypt(byte[] ciphertext, byte[] salt) {
		return dataProtection.unprotect(ciphertext, salt);
	}
}
