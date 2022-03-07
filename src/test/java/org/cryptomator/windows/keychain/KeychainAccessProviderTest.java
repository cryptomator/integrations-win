package org.cryptomator.windows.keychain;

import org.cryptomator.integrations.keychain.KeychainAccessProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class KeychainAccessProviderTest {

	@Test
	@DisplayName("WindowsProtectedKeychainAccess can be loaded")
	public void testLoadWindowsProtectedKeychainAccess() {
		var windowsKeychainAccessProvider = KeychainAccessProvider.get().findAny();
		Assertions.assertTrue(windowsKeychainAccessProvider.isPresent());
		Assertions.assertInstanceOf(WindowsProtectedKeychainAccess.class, windowsKeychainAccessProvider.get());
	}

}
