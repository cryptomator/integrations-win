package org.cryptomator.windows.uiappearance;

import org.cryptomator.integrations.uiappearance.Theme;
import org.cryptomator.integrations.uiappearance.UiAppearanceException;
import org.cryptomator.integrations.uiappearance.UiAppearanceListener;
import org.cryptomator.windows.common.NativeLibLoader;

class WinAppearance {

	public Theme getSystemTheme() {
		int userTheme = Native.INSTANCE.getCurrentTheme();
		switch (userTheme) {
			case 0:
				return Theme.DARK;
			case 1:
				return Theme.LIGHT;
			default:
				return Theme.LIGHT;
		}
	}

	Thread startObserving(UiAppearanceListener listener) throws UiAppearanceException {
		Thread observer = new Thread(() -> {
			Theme theme = getSystemTheme();
			while (!Thread.interrupted()) {
				Native.INSTANCE.waitForNextThemeChange();
				Theme newTheme = getSystemTheme();
				if (newTheme != theme) {
					listener.systemAppearanceChanged(newTheme);
					theme = newTheme;
				}
			}
		}, "AppearanceObserver");
		observer.setDaemon(true);
		observer.start();
		return observer;
	}

	// initialization-on-demand pattern, as loading the .dll is an expensive operation
	private static class Native {
		static final WinAppearance.Native INSTANCE = new WinAppearance.Native();

		private Native() {
			NativeLibLoader.loadLib();
		}

		public native int getCurrentTheme();

		// blocks until changed
		public native void waitForNextThemeChange();
	}
}
