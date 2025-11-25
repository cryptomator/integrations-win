package org.cryptomator.windows.notify;

import org.cryptomator.windows.common.NativeLibLoader;
import org.cryptomator.windows.common.WinStrings;

class WinToasts {


	/**
	 * Sends an app notification to Windows.
	 *
	 * @param appUserModelId Application User Model ID associated with the notification
	 * @param toastXml       xml string describing the toast message
	 * @return {@code 0} if everything worked, otherwise an HRESULT error code
	 */
	public int sendToastNotification(String appUserModelId, String toastXml) {
		return Native.INSTANCE.sendToastNotification(
				WinStrings.getNullTerminatedUTF16Representation(appUserModelId),
				WinStrings.getNullTerminatedUTF16Representation(toastXml));
	}

	private static class Native {
		static final Native INSTANCE = new Native();

		private Native() {
			NativeLibLoader.loadLib();
		}

		native int sendToastNotification(byte[] AppUserModelId, byte[] storagePath);

	}
}
