package org.cryptomator.integrations.autostart;

import org.cryptomator.windows.autostart.WindowsAutoStart;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ServiceLoader;

public class AutoStartProviderTest {

	@Test
	@DisplayName("WindowsAutoStart can be loaded")
	public void testLoadWindowsProtectedKeychainAccess() {
		var loadedProviders = ServiceLoader.load(AutoStartProvider.class);
		var windowsAutoStartProvider = loadedProviders.stream()
				.filter(provider -> provider.type().equals(WindowsAutoStart.class))
				.map(ServiceLoader.Provider::get)
				.findAny();
		Assertions.assertTrue(windowsAutoStartProivder.isPresent());
	}
}
