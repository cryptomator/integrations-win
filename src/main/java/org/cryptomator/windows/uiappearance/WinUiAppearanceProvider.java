package org.cryptomator.windows.uiappearance;

import org.cryptomator.integrations.uiappearance.Theme;
import org.cryptomator.integrations.uiappearance.UiAppearanceException;
import org.cryptomator.integrations.uiappearance.UiAppearanceListener;
import org.cryptomator.integrations.uiappearance.UiAppearanceProvider;

import java.util.ArrayList;
import java.util.Collection;

public class WinUiAppearanceProvider implements UiAppearanceProvider, UiAppearanceListener {

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
	public synchronized void addListener(UiAppearanceListener listener) throws UiAppearanceException {
		var wasEmpty = registeredListeners.isEmpty();
		registeredListeners.add(listener);
		if (wasEmpty) {
			assert this.appearanceObserver == null;
			this.appearanceObserver = winAppearance.startObserving(this);
		}
	}

	@Override
	public synchronized void removeListener(UiAppearanceListener listener) {
		registeredListeners.remove(listener);
		if (registeredListeners.isEmpty()) {
			this.appearanceObserver.interrupt();
			this.appearanceObserver = null;
		}
	}

	//called from native code, to notify all observes of latest change
	@Override
	public void systemAppearanceChanged(Theme theme) {
		for (var listener : registeredListeners) {
			listener.systemAppearanceChanged(theme);
		}
	}
}
