package org.cryptomator.windows.keychain;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.cryptomator.integrations.keychain.KeychainAccessException;
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
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static java.nio.charset.StandardCharsets.UTF_8;

public class Util {
	private static final Logger LOG = LoggerFactory.getLogger(Util.class);
	private static final Path USER_HOME_REL = Path.of("~");
	private static final Path USER_HOME = Path.of(System.getProperty("user.home"));
	private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

	static Path resolveHomeDir(Path path) {
		if (path.startsWith(USER_HOME_REL)) {
			return USER_HOME.resolve(USER_HOME_REL.relativize(path));
		} else {
			return path;
		}
	}

	static byte[] generateSalt() {
		byte[] result = new byte[2 * Long.BYTES];
		UUID uuid = UUID.randomUUID();
		ByteBuffer buf = ByteBuffer.wrap(result);
		buf.putLong(uuid.getMostSignificantBits());
		buf.putLong(uuid.getLeastSignificantBits());
		return result;
	}

	static Map<String, KeychainEntry> loadKeychainEntriesIfNeeded(List<Path> keychainPaths, Map<String, KeychainEntry> keychainEntries) throws KeychainAccessException {
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
	static Optional<Map<String, KeychainEntry>> loadKeychainEntries(Path keychainPath) throws KeychainAccessException {
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

	static void saveKeychainEntries(List<Path> keychainPaths, Map<String, KeychainEntry> keychainEntries) throws KeychainAccessException {
		if (keychainPaths.isEmpty()) {
			throw new IllegalStateException("Can't save keychain if no keychain path is specified.");
		}
		saveKeychainEntries(keychainPaths.get(0), keychainEntries);
	}

	static void saveKeychainEntries(Path keychainPath, Map<String, KeychainEntry> keychainEntries) throws KeychainAccessException {
		try (OutputStream out = Files.newOutputStream(keychainPath, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING); //
			 Writer writer = new OutputStreamWriter(out, UTF_8)) {
			JSON_MAPPER.writeValue(writer, keychainEntries);
		} catch (IOException e) {
			throw new KeychainAccessException("Could not read keychain from path " + keychainPath, e);
		}
	}
}
