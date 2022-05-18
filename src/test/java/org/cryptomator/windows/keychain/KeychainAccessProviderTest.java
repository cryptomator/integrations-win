package org.cryptomator.windows.keychain;

import org.cryptomator.integrations.keychain.KeychainAccessProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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

}
