package org.cryptomator.windows.keychain;

import org.cryptomator.integrations.keychain.KeychainAccessException;
import org.cryptomator.integrations.keychain.KeychainAccessProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public class KeychainAccessProviderTest {

	@BeforeAll
	public static void setup(@TempDir Path tmpDir) {
		Path keychainPath = tmpDir.resolve("keychain.tmp");
		System.setProperty("cryptomator.integrationsWin.keychainPaths", keychainPath.toString());
	}

	@Test
	@DisplayName("WindowsProtectedKeychainAccess can be loaded")
	public void testLoadWindowsProtectedKeychainAccess() {
		Assertions.assertTrue(Objects.nonNull(System.getProperty("cryptomator.integrationsWin.keychainPaths")));

		var windowsKeychainAccessProvider = KeychainAccessProvider.get().findAny();
		Assertions.assertTrue(windowsKeychainAccessProvider.isPresent());
		Assertions.assertInstanceOf(WindowsProtectedKeychainAccess.class, windowsKeychainAccessProvider.get());
	}

	@Nested
	public class LoadKeyChainEntries {

		Path keychainPath;
		WindowsProtectedKeychainAccess keychainAccess;

		@BeforeEach
		public void init() throws IOException {
			keychainPath = Path.of(System.getProperty("cryptomator.integrationsWin.keychainPaths"));
			Files.write(keychainPath, new byte[] {});
			keychainAccess = (WindowsProtectedKeychainAccess) KeychainAccessProvider.get().findAny().get();
		}

		@Test
		public void testNonExistingFileReturnsEmpty() throws KeychainAccessException, IOException {
			Files.delete(keychainPath);
			var result = keychainAccess.loadKeychainEntries(keychainPath);

			Assertions.assertTrue(result.isEmpty());
		}

		@Test
		public void testEmptyFileReturnsEmpty() throws KeychainAccessException {
			var result = keychainAccess.loadKeychainEntries(keychainPath);

			Assertions.assertTrue(result.isEmpty());
		}
	}

}
