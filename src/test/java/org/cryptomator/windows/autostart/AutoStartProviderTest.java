package org.cryptomator.windows.autostart;

import org.cryptomator.integrations.autostart.AutoStartProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class AutoStartProviderTest {

	@Test
	@DisplayName("WindowsAutoStart can be loaded")
	public void testLoadWindowsAutoStart() {
		var windowsAutoStartProvider = AutoStartProvider.get();
		Assertions.assertTrue(windowsAutoStartProvider.isPresent());
		Assertions.assertInstanceOf(WindowsAutoStart.class, windowsAutoStartProvider.get());
	}
}
