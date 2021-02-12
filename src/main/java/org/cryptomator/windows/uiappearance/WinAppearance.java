package org.cryptomator.windows.uiappearance;

import org.cryptomator.integrations.uiappearance.Theme;
import org.cryptomator.integrations.uiappearance.UiAppearanceException;
import org.cryptomator.integrations.uiappearance.UiAppearanceListener;
import org.cryptomator.windows.common.NativeLibLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

class WinAppearance {

	private static final Logger LOG = LoggerFactory.getLogger(WinAppearance.class);
	private static final Theme DEFAULT_THEME = Theme.LIGHT;

	public Theme getSystemTheme() {
		try {
			return getSystemThemeInternal();
		} catch (IllegalStateException e) {
			LOG.warn("Failed to determine system theme", e);
			return DEFAULT_THEME;
		}
	}

	private Theme getSystemThemeInternal() throws IllegalStateException {
		// TODO refactor using switch expressions, once we upgraded to JDK 14+
		switch (Native.INSTANCE.getCurrentTheme()) {
			case 0:
				return Theme.DARK;
			case 1:
				return Theme.LIGHT;
			default:
				return DEFAULT_THEME;
		}
	}

	public Thread startObserving(Consumer<Theme> listener) {
		Thread observer = new Thread(() -> {
			try {
				notifyOnThemeChange(listener);
			} catch (IllegalStateException e) {
				LOG.warn("Failed to observe system theme", e);
			}
		}, "AppearanceObserver");
		observer.setDaemon(true);
		observer.start();
		return observer;
	}

	private void notifyOnThemeChange(Consumer<Theme> listener) throws IllegalStateException {
		Theme currentTheme = getSystemThemeInternal();
		while (!Thread.interrupted()) {
			Native.INSTANCE.waitForNextThemeChange();
			Theme newTheme = getSystemThemeInternal();
			if (newTheme != currentTheme) {
				listener.accept(newTheme);
				currentTheme = newTheme;
			}
		}
	}

	// initialization-on-demand pattern, as loading the .dll is an expensive operation
	private static class Native {
		static final WinAppearance.Native INSTANCE = new WinAppearance.Native();

		private Native() {
			NativeLibLoader.loadLib();
		}

		public native int getCurrentTheme() throws IllegalStateException;

		// blocks until changed
		public native void waitForNextThemeChange() throws IllegalStateException;
	}
}
