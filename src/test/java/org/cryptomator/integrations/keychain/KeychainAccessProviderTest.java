package org.cryptomator.integrations.keychain;

import org.cryptomator.windows.keychain.WindowsProtectedKeychainAccess;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ServiceLoader;

public class KeychainAccessProviderTest {

	@Test
	@DisplayName("WindowsProtectedKeychainAccess can be loaded")
	public void testLoadWindowsProtectedKeychainAccess() {
		var loadedProviders = ServiceLoader.load(KeychainAccessProvider.class);
		var windowsKeychainAccessProvider = loadedProviders.stream()
				.filter(provider -> provider.type().equals(WindowsProtectedKeychainAccess.class))
				.map(ServiceLoader.Provider::get)
				.findAny();
		Assertions.assertTrue(windowsKeychainAccessProvider.isPresent());
	}

}
