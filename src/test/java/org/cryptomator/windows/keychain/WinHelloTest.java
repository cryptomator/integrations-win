package org.cryptomator.windows.keychain;

import org.cryptomator.integrations.keychain.KeychainAccessException;
import org.cryptomator.integrations.keychain.KeychainAccessProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

@EnabledOnOs(OS.WINDOWS)
@DisabledIfEnvironmentVariable(named = "CI", matches = ".*")
class WinHelloTest {

	static WindowsProtectedKeychainAccess keychain;

	@BeforeAll
	public static void setup(@TempDir Path tmpDir) {
		Path keychainPath = tmpDir.resolve("keychain.tmp");
		System.setProperty("cryptomator.integrationsWin.keychainPaths", keychainPath.toString());
		keychain = (WindowsProtectedKeychainAccess) KeychainAccessProvider.get().findAny().get();

	}

	@Test
	@DisplayName("Test Windows Hello Authentication")
	@Disabled
	public void testStoreAndLoadWithAuth() throws KeychainAccessException {
		String storedPw = "secretPassword";
		// Test with authentication required
		keychain.storePassphrase("securePassword", null, storedPw, true);
		String loadedPw = new String(keychain.loadPassphrase("securePassword"));
		Assertions.assertEquals(storedPw, loadedPw);
	}
}