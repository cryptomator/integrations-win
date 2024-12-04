package org.cryptomator.windows.keychain;

import org.cryptomator.integrations.keychain.KeychainAccessException;
import org.cryptomator.integrations.keychain.KeychainAccessProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class WindowsProtectedKeychainAccessIntegrationTest {
	static Path keychainFile;

	@BeforeAll
	public static void setup(@TempDir Path tmpDir) {
		keychainFile = tmpDir.resolve("keychain.tmp");
		System.setProperty("cryptomator.integrationsWin.keychainPaths", keychainFile.toString());
	}

	@Test
	@DisplayName("WindowsProtectedKeychainAccess can be loaded")
	public void testLoadWindowsProtectedKeychainAccess() {
		var found = KeychainAccessProvider.get().anyMatch(s -> s instanceof WindowsProtectedKeychainAccess);
		Assertions.assertTrue(found);
	}

	@Nested
	@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
	public class FunctionalTests {

		WindowsProtectedKeychainAccess keychainAccess;

		@BeforeEach
		public void beforeEach(@TempDir Path tmpDir) {
			keychainAccess = (WindowsProtectedKeychainAccess) KeychainAccessProvider.get().filter(s -> s instanceof WindowsProtectedKeychainAccess).findFirst().get();
		}

		@Test
		@DisplayName("The DataProtection Keychain cannot be locked")
		@Order(1)
		public void cannotBeLocked() {
			Assertions.assertFalse(keychainAccess.isLocked());
		}

		@Test
		@DisplayName("The DataProtection API works, although the file does not exists")
		@Order(2)
		public void notExistingFile() throws KeychainAccessException, IOException {
			Assertions.assertNull(keychainAccess.loadPassphrase("ozelot"));
			Assertions.assertTrue(Files.notExists(keychainFile));
		}

		@Test
		@DisplayName("A credential can be stored")
		@Order(3)
		public void testStoringCredential() throws KeychainAccessException, IOException {
			keychainAccess.storePassphrase("ozelot", "oZeLoT", "abc", false);
			var passphrase = keychainAccess.loadPassphrase("ozelot");
			Assertions.assertEquals("abc", String.valueOf(passphrase));
		}

		@Test
		@DisplayName("A credential can be removed")
		@Order(4)
		public void testRemovingCredential() throws KeychainAccessException, IOException {
			Assertions.assertNotNull(keychainAccess.loadPassphrase("ozelot"));
			keychainAccess.deletePassphrase("ozelot");
			Assertions.assertNull(keychainAccess.loadPassphrase("ozelot"));
		}

		@Test
		@DisplayName("Not existing credential can be \"removed\"")
		@Order(5)
		public void testRemovingCredentialNotExisting() throws KeychainAccessException, IOException {
			keychainAccess.deletePassphrase("ozelot");
			Assertions.assertNull(keychainAccess.loadPassphrase("ozelot"));
		}

		@Test
		@DisplayName("Not existing credential can be \"changed\"")
		@Order(6)
		public void testChangingCredentialNotExisting() throws KeychainAccessException, IOException {
			keychainAccess.changePassphrase("ozelot", "oZeLoT", "qwe");
			Assertions.assertNull(keychainAccess.loadPassphrase("ozelot"));
		}

		@Test
		@DisplayName("Existing credential can be changed")
		@Order(7)
		public void testChangingCredential() throws KeychainAccessException, IOException {
			keychainAccess.storePassphrase("ozelot", "oZeLoT", "abc", false);
			keychainAccess.changePassphrase("ozelot", "oZeLoT", "qwe");
			var passphrase = keychainAccess.loadPassphrase("ozelot");
			Assertions.assertEquals("qwe", String.valueOf(passphrase));
		}
	}

}
