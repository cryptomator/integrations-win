package org.cryptomator.windows.uiappearance;

import org.cryptomator.integrations.uiappearance.Theme;
import org.cryptomator.integrations.uiappearance.UiAppearanceException;
import org.cryptomator.integrations.uiappearance.UiAppearanceListener;
import org.cryptomator.windows.common.NativeLibLoader;

import java.util.HashMap;
import java.util.Map;

class WinAppearance {

	private final Map<UiAppearanceListener, Long> registeredObservers;
	private Thread observerThread;

	public WinAppearance() {
		this.registeredObservers = new HashMap<>();
	}

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

	void startObserving(WinAppearanceListener listener) throws UiAppearanceException {
		if (Native.INSTANCE.prepareObserving(listener) != 0){
			throw new UiAppearanceException("failed to prepeare Observer"); //TODO act on return message and write proper Exception
		};
		observerThread = new Thread(Native.INSTANCE::observe, "AppearanceObserver");
		observerThread.setDaemon(true);
		//observerThread.start(); //terminates, but no Java Call. Message Box ("theme Changed") appears only with a message Box in stopObserveing
		observerThread.run(); //calls java Method, but stopObserving does not terminate the programm
	}

	void stopObserving() {
		//observerThread.stop(); //is deprecated, unsafe and should not be used. (I don't think it would work)
		System.out.println("stopping");
		Native.INSTANCE.stopObserving();
	}

	void setToLight() {
		Native.INSTANCE.setToLight();
	}

	void setToDark() {
		Native.INSTANCE.setToDark();
	}

	// initialization-on-demand pattern, as loading the .dll is an expensive operation
	private static class Native {
		static final WinAppearance.Native INSTANCE = new WinAppearance.Native();

		private Native() {
			NativeLibLoader.loadLib();
		}

		public native int getCurrentTheme();

		public native void setToLight();

		public native void setToDark();

		public native int prepareObserving(WinAppearanceListener listener);

		// will block, to be called in a new thread
		public native void observe();

		public native void stopObserving();
	}
}
