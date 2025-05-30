package org.cryptomator.windows.keychain;

import org.cryptomator.windows.common.NativeLibLoader;
import org.cryptomator.windows.common.WinStrings;

class WindowsHello implements WindowsKeychainAccessBase.PassphraseCryptor {

	private final byte[] keyId;

	public WindowsHello(String keyId) {
		this.keyId = WinStrings.getNullTerminatedUTF16Representation(keyId);
	}

	@Override
	public byte[] encrypt(byte[] cleartext, byte[] salt) {
		return Native.INSTANCE.encrypt(keyId, cleartext, salt);
	}

	@Override
	public byte[] decrypt(byte[] ciphertext, byte[] salt) {
		return Native.INSTANCE.decrypt(keyId, ciphertext, salt);
	}

	public boolean isSupported() {
		return Native.INSTANCE.isSupported();
	}

	// initialization-on-demand pattern, as loading the .dll is an expensive operation
	private static class Native {
		static final Native INSTANCE = new Native();

		private Native() {
			NativeLibLoader.loadLib();
		}

		public native boolean isSupported();

		public native byte[] encrypt(byte[] keyId, byte[] cleartext, byte[] salt);

		public native byte[] decrypt(byte[] keyId, byte[] ciphertext, byte[] salt);
	}

}
