package org.cryptomator.windows.keychain;

import org.cryptomator.integrations.keychain.KeychainAccessException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class FileKeychainTest {

	private static final String CONTENT = """
			{
			  "cryptomator-device-p12": {
			    "ciphertext": "Zm9vYmFy",
			    "salt": "c2FsdHlTYWx0"
			  },
			  "äd3": {
			    "ciphertext": "aUFtQVdhbHJ1cw==",
			    "salt": "c3VybGF3"
			  }
			}
			""";

	@TempDir
	Path keychainFileDir;
	List<Path> keychainPaths;
	FileKeychain fileKeychain;

	@BeforeEach
	public void beforeEach() {
		keychainPaths = List.of(keychainFileDir.resolve("keychain.json"), //
				keychainFileDir.resolve("keychain_2.json"), //
				keychainFileDir.resolve("keychain_3.json"));
		fileKeychain = new FileKeychain(List.of());
	}

	@Test
	public void puttin() throws KeychainAccessException {
		var fileKeychainSpy = spy(fileKeychain);
		Mockito.doNothing().when(fileKeychainSpy).load();
		Mockito.doNothing().when(fileKeychainSpy).save();

		var result = fileKeychainSpy.put("test3000", new KeychainEntry(new byte[] {}, new byte[] {}));

		var ordering = Mockito.inOrder(fileKeychainSpy);
		ordering.verify(fileKeychainSpy).load();
		ordering.verify(fileKeychainSpy).save();
		Assertions.assertNull(result);
	}

	@Test
	public void gettin() throws KeychainAccessException {
		var fileKeychainSpy = spy(fileKeychain);
		Mockito.doNothing().when(fileKeychainSpy).load();

		var result = fileKeychainSpy.get("test3000");

		verify(fileKeychainSpy).load();
		verify(fileKeychainSpy, never()).save();
		Assertions.assertNull(result);
	}

	@Test
	public void removin() throws KeychainAccessException {
		var fileKeychainSpy = spy(fileKeychain);
		Mockito.doNothing().when(fileKeychainSpy).load();
		Mockito.doNothing().when(fileKeychainSpy).save();

		var result = fileKeychainSpy.remove("test3000");

		var ordering = Mockito.inOrder(fileKeychainSpy);
		ordering.verify(fileKeychainSpy).load();
		ordering.verify(fileKeychainSpy).save();
		Assertions.assertNull(result);
	}

	@Test
	public void changin() throws KeychainAccessException {
		var fileKeychainSpy = spy(fileKeychain);
		Mockito.doNothing().when(fileKeychainSpy).load();
		Mockito.doNothing().when(fileKeychainSpy).save();

		var result = fileKeychainSpy.change("test3000", new KeychainEntry(new byte[] {}, new byte[] {}));

		var ordering = Mockito.inOrder(fileKeychainSpy);
		ordering.verify(fileKeychainSpy).load();
		ordering.verify(fileKeychainSpy).save();
		Assertions.assertNull(result);
	}

	@Test
	public void youOnlyLoadOnce() throws KeychainAccessException {
		var fileKeychainSpy = spy(fileKeychain);
		Mockito.doNothing().when(fileKeychainSpy).loadInternal();
		fileKeychainSpy.load();
		fileKeychainSpy.load();
		verify(fileKeychainSpy).loadInternal();
	}

	@Test
	public void loadThrowsOnEmptyPaths() {
		var fileKeychain = new FileKeychain(List.of());
		Assertions.assertThrows(KeychainAccessException.class, fileKeychain::load);
		Assertions.assertThrows(KeychainAccessException.class, fileKeychain::loadInternal);
	}

	@Test
	public void loadInternalUsesFirstFittingPath() throws KeychainAccessException {
		var fileKeychain = spy(new FileKeychain(keychainPaths));

		when(fileKeychain.parse(keychainPaths.get(0))).thenReturn(Optional.empty());
		when(fileKeychain.parse(keychainPaths.get(1))).thenReturn(Optional.of(Map.of()));

		fileKeychain.loadInternal();
		verify(fileKeychain).parse(keychainPaths.get(0));
		verify(fileKeychain).parse(keychainPaths.get(1));
		verify(fileKeychain, never()).parse(keychainPaths.get(2));
	}

	@Test
	public void saveUsesFirstPath() throws KeychainAccessException {
		var fileKeychain = new FileKeychain(keychainPaths);
		fileKeychain.save();
		Assertions.assertTrue(Files.exists(keychainPaths.get(0)));
		Assertions.assertTrue(Files.notExists(keychainPaths.get(1)));
		Assertions.assertTrue(Files.notExists(keychainPaths.get(2)));
	}

	@Test
	public void saveThrowsKeychainExceptionOnIOExcpetion() {
		var fileKeychain = new FileKeychain(List.of(keychainFileDir.resolve("foo/bar")));
		var exception = Assertions.assertThrows(KeychainAccessException.class, fileKeychain::save);
		Assertions.assertInstanceOf(IOException.class, exception.getCause());
	}

	@Test
	public void parseSuccess() throws KeychainAccessException, IOException {
		var keychainFile = keychainFileDir.resolve("realJson.json");
		Files.writeString(keychainFile, CONTENT, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);

		var result = fileKeychain.parse(keychainFile);

		Assertions.assertTrue(result.isPresent());
		Assertions.assertEquals(2, result.get().size());
	}

	@Test
	public void parseWrongJson() throws KeychainAccessException, IOException {
		var keychainFile = keychainFileDir.resolve("realJson.json");
		Files.writeString(keychainFile, CONTENT.substring(0, 20), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);

		var result = fileKeychain.parse(keychainFile);

		Assertions.assertTrue(result.isEmpty());
	}

	@Test
	public void parseNotExisting() throws KeychainAccessException, IOException {
		var keychainFile = keychainFileDir.resolve("realJson.json");
		var result = fileKeychain.parse(keychainFile);
		Assertions.assertTrue(result.isEmpty());
	}

	@Nested
	public class ParsePaths {
		@Test
		@DisplayName("String is split with path separator")
		public void testParsePaths() {
			String paths = "C:\\foo\\bar;bar\\kuz";
			var result = FileKeychain.parsePaths(paths, ";");
			Assertions.assertEquals(2, result.size());
			Assertions.assertTrue(result.contains(Path.of("C:\\foo\\bar")));
			Assertions.assertTrue(result.contains(Path.of("bar\\kuz")));
		}

		@Test
		@DisplayName("Empty string returns empty list")
		public void testParsePathsEmpty() {
			var result = FileKeychain.parsePaths("", ";");
			Assertions.assertEquals(0, result.size());
		}

		@Test
		@DisplayName("Strings starting with ~ are resolved to user home")
		public void testParsePathsUserHome() {
			var userHome = Path.of(System.getProperty("user.home"));
			var result = FileKeychain.parsePaths("this\\~\\not;~\\foo\\bar", ";");
			Assertions.assertEquals(2, result.size());
			Assertions.assertTrue(result.contains(Path.of("this\\~\\not")));
			Assertions.assertTrue(result.contains(userHome.resolve("foo\\bar")));
		}

		@Test
		@DisplayName("Invalid paths are ignored")
		public void testParseInvalidPaths() {
			var result = FileKeychain.parsePaths("ax?|*;C:\\foo", ";");
			Assertions.assertEquals(1, result.size());
			Assertions.assertTrue(result.contains(Path.of("C:\\foo")));
		}

	}

}
