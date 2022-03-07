package org.cryptomator.windows.uiappearance;

import org.cryptomator.integrations.uiappearance.UiAppearanceProvider;
import org.cryptomator.windows.uiappearance.WinUiAppearanceProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ServiceLoader;

public class UiAppearanceProviderTest {

	@Test
	@DisplayName("WinUiAppearanceProvider can be loaded")
	public void testLoadWinUiAppearanceProvider() {
		var provider = UiAppearanceProvider.get();
		Assertions.assertTrue(provider.isPresent());
		Assertions.assertInstanceOf(WinUiAppearanceProvider.class, provider.get());
	}

}
