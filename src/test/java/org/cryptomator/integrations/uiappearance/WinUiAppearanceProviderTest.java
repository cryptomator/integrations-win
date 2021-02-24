package org.cryptomator.integrations.uiappearance;

import org.cryptomator.windows.uiappearance.WinUiAppearanceProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

@EnabledOnOs(OS.WINDOWS)
@DisabledIfEnvironmentVariable(named = "CI", matches = ".*")
public class WinUiAppearanceProviderTest {

	private WinUiAppearanceProvider appearanceProvider;

	@BeforeEach
	public void setup() {
		this.appearanceProvider = new WinUiAppearanceProvider();
	}

	@Test
	@DisplayName("get current system theme")
	public void testGetSystemTheme() {
		Theme myTheme = appearanceProvider.getSystemTheme();

		Assertions.assertNotNull(myTheme);
		System.out.println("current theme: " + myTheme.name());
	}

	@Test
	@DisplayName("add theme listener, wait 10s, remove theme listener")
	@Disabled
	public void testAddAndRemoveListener() {
		UiAppearanceListener listener = theme -> System.out.println(theme.toString());
		appearanceProvider.addListener(listener);
		try {
			Thread.sleep(10_000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		appearanceProvider.removeListener(listener);
	}

	@Test
	@DisplayName("test removing a non-registered listener is a no-op")
	public void testRemoveListenerIfNoneIsRegistered() {
		UiAppearanceListener listener = theme -> System.out.println(theme.toString());
		appearanceProvider.removeListener(listener);
	}

}
