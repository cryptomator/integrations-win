package org.cryptomator.windows.keychain;

import org.cryptomator.windows.common.NativeLibLoader;
import org.cryptomator.windows.common.WinStrings;

class WindowsHello implements WindowsKeychainAccessBase.PassphraseCryptor {

	private final byte[] keyId;
	private final byte[] fixedChallenge = new byte[] {
		'1', '2', '3', '4',
		'5', '6', '7', '8',
		'9', '0', 'A', 'B',
		'C', 'D', 'E', 'F'
	};

	public WindowsHello(String keyId) {
		this.keyId = WinStrings.getNullTerminatedUTF16Representation(keyId);
	}

	@Override
	public byte[] encrypt(byte[] cleartext, byte[] challenge) {
		return Native.INSTANCE.setEncryptionKey(keyId, cleartext, fixedChallenge);
	}

	@Override
	public byte[] decrypt(byte[] ciphertext, byte[] challenge) {
		return Native.INSTANCE.getEncryptionKey(keyId, ciphertext, fixedChallenge);
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

		public native byte[] setEncryptionKey(byte[] keyId, byte[] cleartext, byte[] challenge);

		public native byte[] getEncryptionKey(byte[] keyId, byte[] ciphertext, byte[] challenge);
	}

}
