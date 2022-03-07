package org.cryptomator.windows.uiappearance;

import org.cryptomator.integrations.uiappearance.UiAppearanceProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class UiAppearanceProviderTest {

	@Test
	@DisplayName("WinUiAppearanceProvider can be loaded")
	public void testLoadWinUiAppearanceProvider() {
		var provider = UiAppearanceProvider.get();
		Assertions.assertTrue(provider.isPresent());
		Assertions.assertInstanceOf(WinUiAppearanceProvider.class, provider.get());
	}

}
