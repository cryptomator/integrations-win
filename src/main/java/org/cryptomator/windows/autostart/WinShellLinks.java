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
	 * @param target      path of the shortcut target
	 * @param storagePath full path where the shortcut will be created (including filename & extension)
	 * @param description string inserted in the description field of the shortcut.
	 * @return {@code 0} if everything worked, otherwise an HRESULT error code
	 */
	public int createShortcut(String target, String storagePath, String description) {
		return Native.INSTANCE.createShortcut(
				getNullTerminatedUTF16Representation(target),
				getNullTerminatedUTF16Representation(storagePath),
				getNullTerminatedUTF16Representation(description)
		);
	}

	/**
	 * Gets the file system path of the startup folder. Creates all directories in the path, if necessary.
	 *
	 * @return path to the startup folder with no trailing \ or {@code null}, if the folder is not defined and cannot be created
	 */
	public String getPathToStartupFolder() {
		return Native.INSTANCE.createAndGetStartupFolderPath();
	}

	// visible for testing
	byte[] getNullTerminatedUTF16Representation(String source) {
		byte[] bytes = source.getBytes(StandardCharsets.UTF_16LE);
		return Arrays.copyOf(bytes, bytes.length + 2); // add double-width null terminator 0x00 0x00
	}

	private static class Native {
		static final Native INSTANCE = new Native();

		private Native() {
			NativeLibLoader.loadLib();
		}

		synchronized native int createShortcut(byte[] target, byte[] storagePath, byte[] description);

		native String createAndGetStartupFolderPath();
	}
}
