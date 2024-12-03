package org.cryptomator.windows.keychain;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.cryptomator.integrations.keychain.KeychainAccessException;
import org.cryptomator.integrations.keychain.KeychainAccessProvider;
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
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;

abstract sealed class WindowsFileKeychainAccess implements KeychainAccessProvider permits WindowsHelloKeychainAccess, WindowsProtectedKeychainAccess {

	private final static Logger LOG = LoggerFactory.getLogger(WindowsFileKeychainAccess.class);
	private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

	protected final boolean keychainPathPresent;
	private final List<Path> keychainPaths;

	private Map<String, KeychainEntry> keychainEntries;

	WindowsFileKeychainAccess(String keychainPathsProperty) {
		var paths = parsePaths(System.getProperty(keychainPathsProperty, ""), System.getProperty("path.separator"));
		this.keychainPaths = paths;
		this.keychainPathPresent = !paths.isEmpty();
	}

	//testing
	WindowsFileKeychainAccess(List<Path> keychainPaths) {
		this.keychainPaths = keychainPaths;
		this.keychainPathPresent = !keychainPaths.isEmpty();
	}

	abstract byte[] encrypt(byte[] passphrase, byte[] salt);

	abstract byte[] decrypt(byte[] ciphertext, byte[] salt);

	@Override
	public final void storePassphrase(String key, String displayName, CharSequence passphrase, boolean ignored) throws KeychainAccessException {
		keychainEntries = loadKeychainEntriesIfNeeded();
		ByteBuffer buf = UTF_8.encode(CharBuffer.wrap(passphrase));
		byte[] cleartext = new byte[buf.remaining()];
		try {
			buf.get(cleartext);
			var salt = Util.generateSalt();
			var ciphertext = encrypt(cleartext, salt);
			keychainEntries.put(key, new KeychainEntry(ciphertext, salt));
		} finally {
			Arrays.fill(buf.array(), (byte) 0x00);
			Arrays.fill(cleartext, (byte) 0x00);
		}
		saveKeychainEntries();
	}

	@Override
	public final char[] loadPassphrase(String key) throws KeychainAccessException {
		keychainEntries = loadKeychainEntriesIfNeeded();
		KeychainEntry entry = keychainEntries.get(key);
		if (entry == null) {
			return null;
		}
		byte[] cleartext = {};
		CharBuffer intermediate = null;
		try {
			cleartext = decrypt(entry.ciphertext(), entry.salt());
			if (cleartext == null) {
				return null;
			}
			intermediate = UTF_8.decode(ByteBuffer.wrap(cleartext));
			char[] passphrase = new char[intermediate.remaining()];
			intermediate.get(passphrase);
			return passphrase;
		} finally {
			Arrays.fill(cleartext, (byte) 0x00);
			if (intermediate != null) {
				Arrays.fill(intermediate.array(), (char) 0x00);
			}
		}
	}

	@Override
	public final void deletePassphrase(String key) throws KeychainAccessException {
		keychainEntries = loadKeychainEntriesIfNeeded();
		keychainEntries.remove(key);
		saveKeychainEntries();
	}

	@Override
	public final void changePassphrase(String key, String displayName, CharSequence passphrase) throws KeychainAccessException {
		keychainEntries = loadKeychainEntriesIfNeeded();
		if (keychainEntries.remove(key) != null) {
			storePassphrase(key, displayName, passphrase);
		}
	}

	//Note: We are trying out all keychainPaths to see, if we have to migrate an old keychain file to a new location
	synchronized Map<String, KeychainEntry> loadKeychainEntriesIfNeeded() throws KeychainAccessException {
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
		return keychainEntries;
	}

	//visible for testing
	Optional<Map<String, KeychainEntry>> loadKeychainEntries(Path keychainPath) throws KeychainAccessException {
		LOG.debug("Loading keychain from {}", keychainPath);
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

	void saveKeychainEntries() throws KeychainAccessException {
		var keychainFile = keychainPaths.getFirst(); //Note: we are always storing the keychain to the first entry to use the 'newest' keychain path and thus migrate old data
		LOG.debug("Writing keychain to {}", keychainFile);
		try (OutputStream out = Files.newOutputStream(keychainFile, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING); //
			 Writer writer = new OutputStreamWriter(out, UTF_8)) {
			JSON_MAPPER.writeValue(writer, keychainEntries);
		} catch (IOException e) {
			throw new KeychainAccessException("Could not write keychain to path " + keychainFile, e);
		}
	}

	static List<Path> parsePaths(String listOfPaths, String pathSeparator) {
		return Arrays.stream(listOfPaths.split(pathSeparator))
				.filter(Predicate.not(String::isEmpty))
				.map(s -> {
					try {
						return Path.of(s);
					} catch (InvalidPathException e) {
						LOG.info("Ignoring string {} for keychain file path: Cannot be converted to a path.", s);
						return null;
					}
				})
				.filter(Objects::nonNull)
				.map(Util::resolveHomeDir)
				.collect(Collectors.toList());
	}
}
