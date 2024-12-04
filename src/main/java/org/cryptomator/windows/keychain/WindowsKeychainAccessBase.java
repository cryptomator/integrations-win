package org.cryptomator.windows.keychain;

import org.cryptomator.integrations.keychain.KeychainAccessException;
import org.cryptomator.integrations.keychain.KeychainAccessProvider;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.util.Arrays;

import static java.nio.charset.StandardCharsets.UTF_8;

abstract class WindowsKeychainAccessBase implements KeychainAccessProvider {

	private final Keychain keychain;
	private final PassphraseCryptor passphraseCryptor;

	protected WindowsKeychainAccessBase(Keychain keychain, PassphraseCryptor passphraseCryptor) {
		this.keychain = keychain;
		this.passphraseCryptor = passphraseCryptor;
	}

	@Override
	public void storePassphrase(String id, String displayName, CharSequence passphrase, boolean ignored) throws KeychainAccessException {
		var keychainEntry = encryptPassphrase(passphrase);
		keychain.put(id, keychainEntry);
	}

	private KeychainEntry encryptPassphrase(CharSequence passphrase) throws KeychainAccessException {
		ByteBuffer buf = UTF_8.encode(CharBuffer.wrap(passphrase));
		byte[] cleartext = new byte[buf.remaining()];
		try {
			buf.get(cleartext);
			var salt = Util.generateSalt();
			var ciphertext = passphraseCryptor.encrypt(cleartext, salt);
			if (ciphertext == null) {
				throw new KeychainAccessException("Encrypting the passphrase failed.");
			}
			return new KeychainEntry(ciphertext, salt);
		} finally {
			Arrays.fill(buf.array(), (byte) 0x00);
			Arrays.fill(cleartext, (byte) 0x00);
		}
	}

	@Override
	public char[] loadPassphrase(String id) throws KeychainAccessException {
		KeychainEntry entry = keychain.get(id);
		if (entry == null) {
			return null;
		}
		byte[] cleartext = null;
		CharBuffer intermediate = null;
		try {
			cleartext = passphraseCryptor.decrypt(entry.ciphertext(), entry.salt());
			if (cleartext == null) {
				return null;
			}
			intermediate = UTF_8.decode(ByteBuffer.wrap(cleartext));
			char[] passphrase = new char[intermediate.remaining()];
			intermediate.get(passphrase);
			return passphrase;
		} finally {
			if (cleartext != null) {
				Arrays.fill(cleartext, (byte) 0x00);
			}
			if (intermediate != null) {
				Arrays.fill(intermediate.array(), (char) 0x00);
			}
		}
	}

	@Override
	public void deletePassphrase(String id) throws KeychainAccessException {
		keychain.remove(id);
	}

	@Override
	public void changePassphrase(String id, String displayName, CharSequence passphrase) throws KeychainAccessException {
		keychain.change(id, encryptPassphrase(passphrase));
	}

	@Override
	public boolean isSupported() {
		return keychain.isSupported() && passphraseCryptor.isSupported();
	}

	@Override
	public boolean isLocked() {
		return false;
	}

	interface Keychain {

		/**
		 * Puts a new entry in the keychain
		 *
		 * @param id    Identifier of the keychain entry
		 * @param value {@link KeychainEntry} to be stored in the keychain
		 * @return the former entry or null, if none was present
		 * @throws KeychainAccessException if the keychain cannot be accessed or persisted
		 */
		KeychainEntry put(String id, KeychainEntry value) throws KeychainAccessException;

		/**
		 * Looks up and retrieves a keychain entry.
		 *
		 * @param id Identifier of the keychain entry
		 * @return the {@link KeychainEntry} associated with the given id or null, if no entry is associated for the id
		 * @throws KeychainAccessException if the keychain cannot be accessed
		 */
		KeychainEntry get(String id) throws KeychainAccessException;

		/**
		 * Removes a keychain entry.
		 *
		 * @param id Identifier of the keychain entry
		 * @return the {@link KeychainEntry} formerly associated with the given id or null, if no entry was associated for the id
		 * @throws KeychainAccessException if the keychain cannot be accessed or persisted
		 */
		KeychainEntry remove(String id) throws KeychainAccessException;

		/**
		 * Replaces an existing keychain entry. Returns null, if the id is not mapped.
		 *
		 * @param id       Identifier of the keychain entry
		 * @param newValue the new {@link KeychainEntry} to be associated for {@code id}
		 * @return the former keychain entry or null, if there was no mapping for the given id
		 * @throws KeychainAccessException if the keychain cannot be accessed or persisted
		 */
		KeychainEntry change(String id, KeychainEntry newValue) throws KeychainAccessException;

		boolean isSupported();
	}

	interface PassphraseCryptor {

		/**
		 * Encrypts the given cleartext using a key provided by Windows.
		 * The caller is responsible for zeroing the cleartext array after use.
		 *
		 * @param cleartext      The cleartext to encrypt.
		 * @param additionalData Additional data fed into the encryption. Needs to be provided during {@link #decrypt(byte[], byte[])} decryption}
		 * @return The ciphertext or {@code null} if encryption failed.
		 */
		byte[] encrypt(byte[] cleartext, byte[] additionalData);

		/**
		 * Decrypts the given ciphertext using a key provided by Windows.
		 *
		 * @param ciphertext     The cleartext to encrypt.
		 * @param additionalData Additional data fed into decryption. Must be the same as used in {@link #encrypt(byte[], byte[])} encryption}
		 * @return The cleartext or {@code null} if decryption failed.
		 */
		byte[] decrypt(byte[] ciphertext, byte[] additionalData);

		boolean isSupported();
	}
}
