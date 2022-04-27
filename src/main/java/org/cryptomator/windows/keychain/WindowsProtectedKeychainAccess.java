package org.cryptomator.windows.keychain;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
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
import java.lang.reflect.Type;
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

@Priority(1000)
@OperatingSystem(OperatingSystem.Value.WINDOWS)
public class WindowsProtectedKeychainAccess implements KeychainAccessProvider {

	private static final Logger LOG = LoggerFactory.getLogger(WindowsProtectedKeychainAccess.class);
	private static final String PATH_LIST_SEP = ":";
	private static final Path USER_HOME_REL = Path.of("~");
	private static final Path USER_HOME = Path.of(System.getProperty("user.home"));
	private static final Gson GSON = new GsonBuilder() //
			.setPrettyPrinting() //
			.registerTypeHierarchyAdapter(byte[].class, new ByteArrayJsonAdapter()) //
			.disableHtmlEscaping() //
			.create();

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
		String rawPaths = System.getProperty("cryptomator.keychainPath");
		if (rawPaths == null) {
			return List.of();
		} else {
			return Arrays.stream(rawPaths.split(PATH_LIST_SEP))
					.filter(Predicate.not(String::isEmpty))
					.map(Path::of)
					.map(WindowsProtectedKeychainAccess::resolveHomeDir)
					.collect(Collectors.toList());
		}
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
		KeychainEntry entry = new KeychainEntry();
		entry.salt = generateSalt();
		entry.ciphertext = dataProtection.protect(cleartext, entry.salt);
		Arrays.fill(buf.array(), (byte) 0x00);
		Arrays.fill(cleartext, (byte) 0x00);
		keychainEntries.put(key, entry);
		saveKeychainEntries();
	}

	@Override
	public char[] loadPassphrase(String key) throws KeychainAccessException {
		loadKeychainEntriesIfNeeded();
		KeychainEntry entry = keychainEntries.get(key);
		if (entry == null) {
			return null;
		}
		byte[] cleartext = dataProtection.unprotect(entry.ciphertext, entry.salt);
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

	private Optional<Map<String, KeychainEntry>> loadKeychainEntries(Path keychainPath) throws KeychainAccessException {
		LOG.debug("Attempting to load keychain from {}", keychainPath);
		Type type = new TypeToken<Map<String, KeychainEntry>>() {
		}.getType();
		try (InputStream in = Files.newInputStream(keychainPath, StandardOpenOption.READ); //
			 Reader reader = new InputStreamReader(in, UTF_8)) {
			return Optional.of(GSON.fromJson(reader, type));
		} catch (NoSuchFileException | JsonParseException e) {
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
			GSON.toJson(keychainEntries, writer);
		} catch (IOException e) {
			throw new KeychainAccessException("Could not read keychain from path " + keychainPath, e);
		}
	}

}
