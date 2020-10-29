package org.cryptomator.windows.keychain;

import org.cryptomator.windows.common.NativeLibLoader;

class WinDataProtection {

	/**
	 * Encrypts the given cleartext using a key provided by Windows for the currently logged-in user.
	 *
	 * @param cleartext The cleartext to encrypt.
	 * @param salt A salt, that needs to be provided during {@link #unprotect(byte[], byte[]) decryption}
	 * @return The ciphertext or <code>null</code> if encryption failed.
	 */
	public byte[] protect(byte[] cleartext, byte[] salt) {
		return Native.INSTANCE.protect(cleartext, salt);
	}

	/**
	 * Decrypts the given ciphertext using a key provided by Windows for the currently logged-in user.
	 *
	 * @param ciphertext Ciphertext as previously encrypted using {@link #protect(byte[], byte[])}
	 * @param salt Same salt as used in {@link #protect(byte[], byte[])}
	 * @return The cleartext or <code>null</code> if decryption failed (wrong salt?).
	 */
	public byte[] unprotect(byte[] ciphertext, byte[] salt) {
		return Native.INSTANCE.unprotect(ciphertext, salt);
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
