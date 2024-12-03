package org.cryptomator.windows.keychain;

import org.cryptomator.integrations.common.OperatingSystem;
import org.cryptomator.integrations.common.Priority;
import org.cryptomator.integrations.keychain.KeychainAccessException;
import org.cryptomator.integrations.keychain.KeychainAccessProvider;
import org.cryptomator.windows.common.Localization;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Windows implementation for the {@link KeychainAccessProvider} based on the <a href="https://en.wikipedia.org/wiki/Data_Protection_API">data protection API</a>.
 * The storage locations to check for encrypted data can be set with the JVM property {@value WINDOWS_HELLO_KEYCHAIN_PATHS_PROPERTY} with the paths seperated with the character defined in the JVM property path.separator.
 */
@Priority(1001)
@OperatingSystem(OperatingSystem.Value.WINDOWS)
public final class WindowsHelloKeychainAccess extends WindowsFileKeychainAccess {

	private static final String WINDOWS_HELLO_KEYCHAIN_PATHS_PROPERTY = "cryptomator.integrationsWin.windowsHelloKeychainPaths";

	private final WindowsHello windowsHello;


	@SuppressWarnings("unused") // default constructor required by ServiceLoader
	public WindowsHelloKeychainAccess() {
		this(WINDOWS_HELLO_KEYCHAIN_PATHS_PROPERTY, new WindowsHello());
	}

	// visible for testing
	WindowsHelloKeychainAccess(String keychainPathsProp, WindowsHello winHello) {
		super(keychainPathsProp);
		this.windowsHello = winHello;
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
		return windowsHello.setEncryptionKey(passphrase, salt);
	}

	@Override
	byte[] decrypt(byte[] ciphertext, byte[] salt) {
		return windowsHello.getEncryptionKey(ciphertext, salt);
	}
}
