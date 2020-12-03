package org.cryptomator.windows.autostart;

import org.cryptomator.windows.common.NativeLibLoader;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Interface to the native Windows shell link interface.
 * <p>
 * For more info, see https://docs.microsoft.com/de-de/windows/win32/shell/links.
 */
public class WinShellLinks {

	/**
	 * Create a Windows shortcut file to the given target, at the specified location and with a description.
	 *
	 * @param target path of the target the shortcut points to
	 * @param storagePath path where the shortcut is created
	 * @param description string inserted in the description field of the shortcut.
	 * @return {@code 0} if everything worked, otherwise an HRESULT error code
	 */
	public int createShortcut(String target, String storagePath, String description) {
		return Native.INSTANCE.createShortcut(
				nullterminateAndConvert(target),
				nullterminateAndConvert(storagePath),
				nullterminateAndConvert(description)
		);
	}

	private byte[] nullterminateAndConvert(String source) {
		byte[] bytes = source.getBytes(StandardCharsets.UTF_16LE);
		return Arrays.copyOf(bytes, bytes.length + 1); // add single byte with "null" padding
	}

	private static class Native {
		static final Native INSTANCE = new Native();

		private Native() {
			NativeLibLoader.loadLib();
		}

		public synchronized native int createShortcut(byte[] target, byte[] storagePath, byte[] description);
	}
}
