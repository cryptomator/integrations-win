package org.cryptomator.integrations.uiappearance;

import org.cryptomator.windows.uiappearance.WinUiAppearanceProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ServiceLoader;

public class UiAppearanceProviderTest {

	@Test
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
