package org.cryptomator.integrations.uiappearance;

import org.cryptomator.windows.uiappearance.WinUiAppearanceProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ServiceLoader;

public class UiAppearanceProviderTest {
	private WinUiAppearanceProvider applicationUiAppearance;

	/*
	@Test
	public void testGetKeyFromRegistry() throws UiAppearanceException {
		Theme myTheme = applicationUiAppearance.getSystemTheme();
		Assertions.assertEquals(Theme.DARK, myTheme);
	}
	*/

	@Test
	public void testSetThemeToLight(){
		applicationUiAppearance = new WinUiAppearanceProvider();

		applicationUiAppearance.adjustToTheme(Theme.LIGHT);
		Theme newTheme = applicationUiAppearance.getSystemTheme();
		Assertions.assertEquals(Theme.LIGHT, newTheme);
	}

	@Test
	public void testSetThemeToDark(){
		applicationUiAppearance = new WinUiAppearanceProvider();

		applicationUiAppearance.adjustToTheme(Theme.DARK);
		Theme newTheme = applicationUiAppearance.getSystemTheme();
		Assertions.assertEquals(Theme.DARK, newTheme);
	}


	@Test //doesn't work propably yet.
	@DisplayName("WinUiAppearanceProvider can be loaded")
	public void testLoadWinUiAppearanceProvider() {
		var loadedProviders = ServiceLoader.load(UiAppearanceProvider.class);
		var provider = loadedProviders.stream()
				.filter(p -> p.type().equals(WinUiAppearanceProvider.class))
				.map(ServiceLoader.Provider::get)
				.findAny();
		Assertions.assertTrue(provider.isPresent());
	}

}