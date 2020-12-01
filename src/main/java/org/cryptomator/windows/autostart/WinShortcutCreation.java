package org.cryptomator.windows.autostart;

import org.cryptomator.windows.common.NativeLibLoader;

/**
 * Creates Windows shortcuts (a special type of Windows shell links).
 * <p>
 * For more info, see https://docs.microsoft.com/de-de/windows/win32/shell/links.
 */
public class WinShortcutCreation {

	public int createShortcut(String target, String storagePath, String description) {
		return Native.INSTANCE.createShortcut(target, storagePath, description);
	}

	private static class Native {
		static final Native INSTANCE = new Native();

		private Native() {
			NativeLibLoader.loadLib();
		}

		public native int createShortcut(String target, String storagePath, String description);
	}
}
