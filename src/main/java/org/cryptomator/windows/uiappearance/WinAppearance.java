package org.cryptomator.windows.uiappearance;

import org.cryptomator.integrations.uiappearance.Theme;
import org.cryptomator.integrations.uiappearance.UiAppearanceException;
import org.cryptomator.integrations.uiappearance.UiAppearanceListener;
import org.cryptomator.windows.common.NativeLibLoader;

import java.util.HashMap;
import java.util.Map;

class WinAppearance{

	private final Map<UiAppearanceListener, Long> registeredObservers;

	public WinAppearance() {
		this.registeredObservers = new HashMap<>();
	}

	public Theme getSystemTheme(){
		int userTheme = Native.INSTANCE.getCurrentTheme();
		switch (userTheme) {
			case 0:
				return Theme.DARK;
			case 1:
				return Theme.LIGHT;
			default: return Theme.LIGHT;
		}
	}

	public void adjustToTheme(Theme theme){

	}

	long registerObserverWithListener(WinAppearanceListener listener) throws UiAppearanceException{
		long observerPtr = Native.INSTANCE.registerObserverWithListener(listener);
		return observerPtr;
	}

	void deregisterObserver(long observer) {
		Native.INSTANCE.deregisterObserver(observer);
	}

	void setToLight(){
		Native.INSTANCE.setToLight();
		//SendMessageTimeout(HWND_BROADCAST, WM_SETTINGCHANGE, NULL, NULL,
		//    SMTO_NORMAL, aShortTimeoutInMilliseconds, NULL);
	}

	void setToDark(){
		Native.INSTANCE.setToDark();
	}

	// initialization-on-demand pattern, as loading the .dll is an expensive operation
	private static class Native {
		static final WinAppearance.Native INSTANCE = new WinAppearance.Native();

		private Native() { NativeLibLoader.loadLib();		}

		public native int getCurrentTheme();

		public native long registerObserverWithListener(WinAppearanceListener listener);

		public native long deregisterObserver(long observer);

		public native void setToLight();

		public native void setToDark();

	}
}
