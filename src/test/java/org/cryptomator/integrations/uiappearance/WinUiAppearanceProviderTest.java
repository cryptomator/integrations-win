package org.cryptomator.integrations.uiappearance;

import org.cryptomator.windows.uiappearance.WinUiAppearanceProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
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
	public void testRegisterAndUnregisterObserver() throws UiAppearanceException {
		UiAppearanceListener listener = theme -> System.out.println(theme.toString());
		appearanceProvider.addListener(listener);
		try {
			Thread.sleep(15000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		appearanceProvider.removeListener(listener);
	}

}
