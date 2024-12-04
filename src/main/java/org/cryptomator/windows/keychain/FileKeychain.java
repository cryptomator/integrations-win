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

/**
 * A file-based keychain. It's content is a utf-8 encoded JSON object.
 */
class FileKeychain implements WindowsKeychainAccessBase.Keychain {

	private final static Logger LOG = LoggerFactory.getLogger(FileKeychain.class);
	private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

	private final List<Path> keychainPaths;

	private Map<String, KeychainEntry> cache;
	private volatile boolean loaded;

	FileKeychain(String keychainPathsProperty) {
		keychainPaths = parsePaths(System.getProperty(keychainPathsProperty, ""), System.getProperty("path.separator"));
		cache = new HashMap<>();
	}

	//testing
	FileKeychain(List<Path> paths) {
		keychainPaths = paths;
		cache = new HashMap<>();
	}

	synchronized void load() throws KeychainAccessException {
		if (!loaded) {
			loadInternal();
			loaded = true;
		}
	}

	//for testing
	void loadInternal() throws KeychainAccessException {
		if (keychainPaths.isEmpty()) {
			throw new KeychainAccessException("No path specified to store keychain");
		}
		//Note: We are trying out all keychainPaths to see, if we have to migrate an old keychain file to a new location
		boolean useExisting = false;
		for (Path keychainPath : keychainPaths) {
			Optional<Map<String, KeychainEntry>> maybeKeychain = parse(keychainPath);
			if (maybeKeychain.isPresent()) {
				cache = maybeKeychain.get();
				useExisting = true;
				break;
			}
		}
		if (!useExisting) {
			LOG.debug("Keychain file not found or not parsable. Using new keychain.");
		}

	}

	//visible for testing
	Optional<Map<String, KeychainEntry>> parse(Path keychainPath) throws KeychainAccessException {
		LOG.debug("Loading keychain from {}", keychainPath);
		TypeReference<Map<String, KeychainEntry>> type = new TypeReference<>() {
		};
		try (InputStream in = Files.newInputStream(keychainPath, StandardOpenOption.READ); //
			 Reader reader = new InputStreamReader(in, UTF_8)) {
			return Optional.ofNullable(JSON_MAPPER.readValue(reader, type));
		} catch (NoSuchFileException e) {
			return Optional.empty();
		} catch (JacksonException je) {
			LOG.warn("Ignoring existing keychain file {}: Parsing failed.", keychainPath);
			return Optional.empty();
		} catch (IOException e) {
			//TODO: we could ignore this
			throw new KeychainAccessException("Failed to read keychain from path " + keychainPath, e);
		}
	}

	//visible for testing
	void save() throws KeychainAccessException {
		var keychainFile = keychainPaths.getFirst(); //Note: we are always storing the keychain to the first entry to use the 'newest' keychain path and thus migrate old data
		LOG.debug("Writing keychain to {}", keychainFile);
		try (OutputStream out = Files.newOutputStream(keychainFile, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING); //
			 Writer writer = new OutputStreamWriter(out, UTF_8)) {
			JSON_MAPPER.writeValue(writer, cache);
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
				.toList();
	}

	@Override
	public KeychainEntry put(String id, KeychainEntry value) throws KeychainAccessException {
		load();
		var result = cache.put(id, value);
		save();
		return result;
	}

	@Override
	public KeychainEntry get(String id) throws KeychainAccessException {
		load();
		return cache.get(id);
	}

	@Override
	public KeychainEntry remove(String id) throws KeychainAccessException {
		load();
		var result = cache.remove(id);
		save();
		return result;
	}

	@Override
	public KeychainEntry change(String id, KeychainEntry newEntry) throws KeychainAccessException {
		load();
		var result = cache.computeIfPresent(id, (_, _) -> newEntry);
		save();
		return result;
	}
}
