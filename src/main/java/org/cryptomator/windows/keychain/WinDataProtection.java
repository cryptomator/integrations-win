package org.cryptomator.windows.keychain;

import org.cryptomator.windows.common.NativeLibLoader;

class WinDataProtection implements WindowsKeychainAccessBase.PassphraseCryptor {

	@Override
	public byte[] encrypt(byte[] cleartext, byte[] salt) {
		return Native.INSTANCE.protect(cleartext, salt);
	}

	@Override
	public byte[] decrypt(byte[] ciphertext, byte[] salt) {
		return Native.INSTANCE.unprotect(ciphertext, salt);
	}

	@Override
	public boolean isSupported() {
		return true;
	}

	// initialization-on-demand pattern, as loading the .dll is an expensive operation
	private static class Native {
		static final Native INSTANCE = new Native();

		private Native() {
			NativeLibLoader.loadLib();
		}

		public native byte[] protect(byte[] cleartext, byte[] salt);

		public native byte[] unprotect(byte[] ciphertext, byte[] salt);
	}

}
