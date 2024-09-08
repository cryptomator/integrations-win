package org.cryptomator.windows.keychain;

import org.cryptomator.windows.common.NativeLibLoader;

class WinHello {

	/**
	 * Encrypts the given cleartext using a Windows Hello key.
	 *
	 * @param cleartext The cleartext to encrypt.
	 * @param salt A salt, that needs to be provided during {@link #getEncryptionKey(byte[], byte[]) decryption}
	 * @return The ciphertext or <code>null</code> if encryption failed.
	 */
	public byte[] setEncryptionKey(byte[] cleartext, byte[] salt) {
		return Native.INSTANCE.setEncryptionKey(cleartext, salt);
	}

	/**
	 * Decrypts the given ciphertext using a Windows Hello key.
	 *
	 * @param ciphertext Ciphertext as previously encrypted using {@link #setEncryptionKey(byte[], byte[])}
	 * @param salt Same salt as used in {@link #setEncryptionKey(byte[], byte[])}
	 * @return The cleartext or <code>null</code> if decryption failed.
	 */
	public byte[] getEncryptionKey(byte[] ciphertext, byte[] salt) {
		return Native.INSTANCE.getEncryptionKey(ciphertext, salt);
	}

	// initialization-on-demand pattern, as loading the .dll is an expensive operation
	private static class Native {
		static final Native INSTANCE = new Native();

		private Native() {
			NativeLibLoader.loadLib();
		}

		public native byte[] setEncryptionKey(byte[] cleartext, byte[] salt);

		public native byte[] getEncryptionKey(byte[] ciphertext, byte[] salt);
	}

}
