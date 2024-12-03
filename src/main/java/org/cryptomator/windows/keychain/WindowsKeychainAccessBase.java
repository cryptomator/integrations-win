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
		return false;
	}

	@Override
	public boolean isLocked() {
		return false;
	}

	interface Keychain {
		KeychainEntry put(String id, KeychainEntry value) throws KeychainAccessException;

		KeychainEntry get(String key) throws KeychainAccessException;

		KeychainEntry remove(String key) throws KeychainAccessException;

		KeychainEntry change(String key, KeychainEntry newValue) throws KeychainAccessException;
	}

	interface PassphraseCryptor {
		byte[] encrypt(byte[] cleartext, byte[] salt);

		byte[] decrypt(byte[] ciphertext, byte[] salt);
	}
}
