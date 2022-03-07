package org.cryptomator.windows.uiappearance;

import org.cryptomator.integrations.common.OperatingSystem;
import org.cryptomator.integrations.common.Priority;
import org.cryptomator.integrations.uiappearance.Theme;
import org.cryptomator.integrations.uiappearance.UiAppearanceListener;
import org.cryptomator.integrations.uiappearance.UiAppearanceProvider;

import java.util.ArrayList;
import java.util.Collection;

@Priority(1000)
@OperatingSystem(OperatingSystem.Value.WINDOWS)
public class WinUiAppearanceProvider implements UiAppearanceProvider {

	private final WinAppearance winAppearance;
	private final Collection<UiAppearanceListener> registeredListeners;
	private volatile Thread appearanceObserver;

	public WinUiAppearanceProvider() {
		this.winAppearance = new WinAppearance();
		this.registeredListeners = new ArrayList<>();
	}

	@Override
	public Theme getSystemTheme() {
		return winAppearance.getSystemTheme();
	}

	@Override
	public void adjustToTheme(Theme theme) {
		// no-op
	}

	@Override
	public synchronized void addListener(UiAppearanceListener listener) {
		var wasEmpty = registeredListeners.isEmpty();
		registeredListeners.add(listener);
		if (wasEmpty) {
			assert this.appearanceObserver == null;
			this.appearanceObserver = winAppearance.startObserving(this::systemAppearanceChanged);
		}
	}

	@Override
	public synchronized void removeListener(UiAppearanceListener listener) {
		registeredListeners.remove(listener);
		if (appearanceObserver != null && registeredListeners.isEmpty()) {
			this.appearanceObserver.interrupt();
			this.appearanceObserver = null;
		}
	}

	// notification hub called from single observer
	private void systemAppearanceChanged(Theme theme) {
		for (var listener : registeredListeners) {
			listener.systemAppearanceChanged(theme);
		}
	}
}
