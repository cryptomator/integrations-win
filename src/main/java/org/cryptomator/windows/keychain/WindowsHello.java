package org.cryptomator.windows.keychain;

import org.cryptomator.windows.common.NativeLibLoader;

class WindowsHello {

	/**
	 * Encrypts the given cleartext using a Windows Hello key.
	 * Note: Caller is responsible for zeroing the cleartext array after use.
	 *
	 * @param cleartext The cleartext to encrypt.
	 * @param challenge A challenge, that needs to be provided during {@link #getEncryptionKey(byte[], byte[]) decryption}
	 * @return The ciphertext or <code>null</code> if encryption failed.
	 */
	public byte[] setEncryptionKey(byte[] cleartext, byte[] challenge) {
		return Native.INSTANCE.setEncryptionKey(cleartext, challenge);
	}

	/**
	 * Decrypts the given ciphertext using a Windows Hello key.
	 * Note: Caller is responsible for zeroing the ciphertext array after use.
	 *
	 * @param ciphertext Ciphertext as previously encrypted using {@link #setEncryptionKey(byte[], byte[])}
	 * @param challenge Same challenge as used in {@link #setEncryptionKey(byte[], byte[])}
	 * @return The cleartext or <code>null</code> if decryption failed.
	 */
	public byte[] getEncryptionKey(byte[] ciphertext, byte[] challenge) {
		return Native.INSTANCE.getEncryptionKey(ciphertext, challenge);
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
