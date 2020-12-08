package org.cryptomator.windows.uiappearance;

import org.cryptomator.integrations.uiappearance.Theme;
import org.cryptomator.integrations.uiappearance.UiAppearanceException;
import org.cryptomator.integrations.uiappearance.UiAppearanceListener;
import org.cryptomator.integrations.uiappearance.UiAppearanceProvider;

import java.util.HashMap;
import java.util.Map;

public class WinUiAppearanceProvider implements UiAppearanceProvider {

	private final WinAppearance winAppearance;
	private final Map<UiAppearanceListener, Long> registeredObservers;

	public WinUiAppearanceProvider() {
		this.winAppearance = new WinAppearance();
		this.registeredObservers = new HashMap<>();
	}

	@Override
	public Theme getSystemTheme() {
		return winAppearance.getSystemTheme();
	}

	@Override
	public void adjustToTheme(Theme theme) {
		switch (theme) {
			case LIGHT:
				winAppearance.setToLight();
				break;
			case DARK:
				winAppearance.setToDark();
				break;
		}	}

	@Override
	public void addListener(UiAppearanceListener listener) throws UiAppearanceException {
		var observer = winAppearance.registerObserverWithListener(() -> {
			listener.systemAppearanceChanged(getSystemTheme());
		});
		if (observer == 0) {
			throw new UiAppearanceException("Failed to register appearance observer.");
		} else {
			registeredObservers.put(listener, observer);
		}
	}

	@Override
	public void removeListener(UiAppearanceListener listener) throws UiAppearanceException {
		var observer = registeredObservers.remove(listener);
		if (observer != null) {
			winAppearance.deregisterObserver(observer);
		}
	}
}
