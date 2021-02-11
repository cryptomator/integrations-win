package org.cryptomator.integrations.uiappearance;

import org.cryptomator.windows.uiappearance.WinUiAppearanceProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class WinUiAppearanceProviderTest {
	private WinUiAppearanceProvider appearanceProvider;

	@BeforeEach
	public void setup() {
		this.appearanceProvider = new WinUiAppearanceProvider();
	}

	@Test
	public void testGettingTheCurrentTheme(){
		System.out.println(appearanceProvider.getSystemTheme().toString());
	}

	@Test
	@DisplayName("get current system theme")
	public void testGetSystemTheme() {
		Theme myTheme = appearanceProvider.getSystemTheme();
		Assertions.assertNotNull(myTheme);
	}

	@Test
	@DisplayName("add theme listener, wait 10s, remove theme listener")
	@Disabled
	public void testAddAndRemoveListener() throws UiAppearanceException {
		UiAppearanceListener listener = theme -> System.out.println(theme.toString());
		appearanceProvider.addListener(listener);
		try {
			Thread.sleep(10_000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		appearanceProvider.removeListener(listener);
	}

}
