package org.cryptomator.windows.keychain;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.cryptomator.integrations.common.OperatingSystem;
import org.cryptomator.integrations.common.Priority;
import org.cryptomator.integrations.keychain.KeychainAccessException;
import org.cryptomator.integrations.keychain.KeychainAccessProvider;
import org.cryptomator.windows.common.Localization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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
	private static final Logger LOG = LoggerFactory.getLogger(WindowsProtectedKeychainAccess.class);
	private static final Path USER_HOME_REL = Path.of("~");
	private static final Path USER_HOME = Path.of(System.getProperty("user.home"));
	private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

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
				.map(WindowsProtectedKeychainAccess::resolveHomeDir)
				.collect(Collectors.toList());
	}

	private static Path resolveHomeDir(Path path) {
		if (path.startsWith(USER_HOME_REL)) {
			return USER_HOME.resolve(USER_HOME_REL.relativize(path));
		} else {
			return path;
		}
	}

	@Override
	public String displayName() {
		return Localization.get().getString("org.cryptomator.windows.keychain.displayName");
	}

	@Override
	public void storePassphrase(String key, String displayName, CharSequence passphrase) throws KeychainAccessException {
		loadKeychainEntriesIfNeeded();
		ByteBuffer buf = UTF_8.encode(CharBuffer.wrap(passphrase));
		byte[] cleartext = new byte[buf.remaining()];
		buf.get(cleartext);
		var salt = generateSalt();
		var ciphertext = dataProtection.protect(cleartext, salt);
		Arrays.fill(buf.array(), (byte) 0x00);
		Arrays.fill(cleartext, (byte) 0x00);
		keychainEntries.put(key, new KeychainEntry(ciphertext, salt));
		saveKeychainEntries();
	}

	@Override
	public char[] loadPassphrase(String key) throws KeychainAccessException {
		loadKeychainEntriesIfNeeded();
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
		loadKeychainEntriesIfNeeded();
		keychainEntries.remove(key);
		saveKeychainEntries();
	}

	@Override
	public void changePassphrase(String key, String displayName, CharSequence passphrase) throws KeychainAccessException {
		loadKeychainEntriesIfNeeded();
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

	private byte[] generateSalt() {
		byte[] result = new byte[2 * Long.BYTES];
		UUID uuid = UUID.randomUUID();
		ByteBuffer buf = ByteBuffer.wrap(result);
		buf.putLong(uuid.getMostSignificantBits());
		buf.putLong(uuid.getLeastSignificantBits());
		return result;
	}

	private void loadKeychainEntriesIfNeeded() throws KeychainAccessException {
		if (keychainEntries == null) {
			for (Path keychainPath : keychainPaths) {
				Optional<Map<String, KeychainEntry>> keychain = loadKeychainEntries(keychainPath);
				if (keychain.isPresent()) {
					keychainEntries = keychain.get();
					break;
				}
			}
		}
		if (keychainEntries == null) {
			LOG.info("Unable to load existing keychain file, creating new keychain.");
			keychainEntries = new HashMap<>();
		}
	}

	//visible for testing
	Optional<Map<String, KeychainEntry>> loadKeychainEntries(Path keychainPath) throws KeychainAccessException {
		LOG.debug("Attempting to load keychain from {}", keychainPath);
		TypeReference<Map<String, KeychainEntry>> type = new TypeReference<>() {
		};
		try (InputStream in = Files.newInputStream(keychainPath, StandardOpenOption.READ); //
			 Reader reader = new InputStreamReader(in, UTF_8)) {
			return Optional.ofNullable(JSON_MAPPER.readValue(reader, type));
		} catch (NoSuchFileException e) {
			return Optional.empty();
		} catch (JacksonException je) {
			LOG.warn("Unable to parse keychain file, overwriting existing one.");
			return Optional.empty();
		} catch (IOException e) {
			throw new KeychainAccessException("Could not read keychain from path " + keychainPath, e);
		}
	}

	private void saveKeychainEntries() throws KeychainAccessException {
		if (keychainPaths.isEmpty()) {
			throw new IllegalStateException("Can't save keychain if no keychain path is specified.");
		}
		saveKeychainEntries(keychainPaths.get(0));
	}

	private void saveKeychainEntries(Path keychainPath) throws KeychainAccessException {
		try (OutputStream out = Files.newOutputStream(keychainPath, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING); //
			 Writer writer = new OutputStreamWriter(out, UTF_8)) {
			JSON_MAPPER.writeValue(writer, keychainEntries);
		} catch (IOException e) {
			throw new KeychainAccessException("Could not read keychain from path " + keychainPath, e);
		}
	}

}
