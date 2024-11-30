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
 * The storage locations to check for encrypted data can be set with the JVM property {@value KEYCHAIN_PATHS_PROPERTY} with the paths seperated with the character defined in the JVM property path.separator.
 */
@Priority(1000)
@OperatingSystem(OperatingSystem.Value.WINDOWS)
public class WindowsProtectedKeychainAccess implements KeychainAccessProvider {

	private static final String KEYCHAIN_PATHS_PROPERTY = "cryptomator.integrationsWin.keychainPaths";

	private final List<Path> keychainPaths;
	private final WinDataProtection dataProtection;
	private Map<String, KeychainEntry> keychainEntries;

	@SuppressWarnings("unused") // default constructor required by ServiceLoader
	public WindowsProtectedKeychainAccess() {
		this(readKeychainPathsFromEnv(), new WinDataProtection());
	}

	// visible for testing
	WindowsProtectedKeychainAccess(List<Path> keychainPaths, WinDataProtection dataProtection) {
		this.keychainPaths = keychainPaths;
		this.dataProtection = dataProtection;
	}

	private static List<Path> readKeychainPathsFromEnv() {
		var keychainPaths = System.getProperty(KEYCHAIN_PATHS_PROPERTY, "");
		return parsePaths(keychainPaths, System.getProperty("path.separator"));
	}

	// visible for testing
	static List<Path> parsePaths(String listOfPaths, String pathSeparator) {
		return Arrays.stream(listOfPaths.split(pathSeparator))
				.filter(Predicate.not(String::isEmpty))
				.map(Path::of)
				.map(Util::resolveHomeDir)
				.collect(Collectors.toList());
	}

	@Override
	public String displayName() {
		return Localization.get().getString("org.cryptomator.windows.keychain.displayName");
	}

	@Override
	public void storePassphrase(String key, String displayName, CharSequence passphrase, boolean ignored) throws KeychainAccessException {
		keychainEntries = Util.loadKeychainEntriesIfNeeded(keychainPaths, keychainEntries);
		ByteBuffer buf = UTF_8.encode(CharBuffer.wrap(passphrase));
		byte[] cleartext = new byte[buf.remaining()];
		buf.get(cleartext);
		var salt = Util.generateSalt();
		var ciphertext = dataProtection.protect(cleartext, salt);
		Arrays.fill(buf.array(), (byte) 0x00);
		Arrays.fill(cleartext, (byte) 0x00);
		keychainEntries.put(key, new KeychainEntry(ciphertext, salt));
		Util.saveKeychainEntries(keychainPaths, keychainEntries);
	}

	@Override
	public char[] loadPassphrase(String key) throws KeychainAccessException {
		keychainEntries = Util.loadKeychainEntriesIfNeeded(keychainPaths, keychainEntries);
		KeychainEntry entry = keychainEntries.get(key);
		if (entry == null) {
			return null;
		}
		byte[] cleartext = dataProtection.unprotect(entry.ciphertext(), entry.salt());
		if (cleartext == null) {
			return null;
		}
		CharBuffer buf = UTF_8.decode(ByteBuffer.wrap(cleartext));
		char[] passphrase = new char[buf.remaining()];
		buf.get(passphrase);
		Arrays.fill(cleartext, (byte) 0x00);
		Arrays.fill(buf.array(), (char) 0x00);
		return passphrase;
	}

	@Override
	public void deletePassphrase(String key) throws KeychainAccessException {
		keychainEntries = Util.loadKeychainEntriesIfNeeded(keychainPaths, keychainEntries);
		keychainEntries.remove(key);
		Util.saveKeychainEntries(keychainPaths, keychainEntries);
	}

	@Override
	public void changePassphrase(String key, String displayName, CharSequence passphrase) throws KeychainAccessException {
		keychainEntries = Util.loadKeychainEntriesIfNeeded(keychainPaths, keychainEntries);
		if (keychainEntries.remove(key) != null) {
			storePassphrase(key, passphrase);
		}
	}

	@Override
	public boolean isSupported() {
		return !keychainPaths.isEmpty();
	}

	@Override
	public boolean isLocked() {
		return false;
	}

}
