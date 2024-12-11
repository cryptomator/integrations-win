package org.cryptomator.windows.keychain;

import org.cryptomator.windows.common.NativeLibLoader;

class WindowsHello implements WindowsKeychainAccessBase.PassphraseCryptor {

	@Override
	public byte[] encrypt(byte[] cleartext, byte[] challenge) {
		return Native.INSTANCE.setEncryptionKey(cleartext, challenge);
	}

	@Override
	public byte[] decrypt(byte[] ciphertext, byte[] challenge) {
		return Native.INSTANCE.getEncryptionKey(ciphertext, challenge);
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

		public native byte[] setEncryptionKey(byte[] cleartext, byte[] challenge);

		public native byte[] getEncryptionKey(byte[] ciphertext, byte[] challenge);
	}

}
