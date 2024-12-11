package org.cryptomator.windows.keychain;

import org.cryptomator.integrations.keychain.KeychainAccessException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.cryptomator.windows.keychain.WindowsKeychainAccessBase.Keychain;
import static org.cryptomator.windows.keychain.WindowsKeychainAccessBase.PassphraseCryptor;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class WindowsKeychainAccessBaseTest {

	PassphraseCryptor passphraseCryptor = mock(PassphraseCryptor.class);
	Keychain keychain = mock(Keychain.class);
	WindowsKeychainAccessBase winKeychainBase = new TestProvider(keychain, passphraseCryptor);


	@Test
	public void storePassphraseFailsIfEncryptionisNull() {
		when(passphraseCryptor.encrypt(any(), any())).thenReturn(null);
		Assertions.assertThrows(KeychainAccessException.class, //
				() -> winKeychainBase.storePassphrase("test3000", "display3000", "abc") //
		);
	}

	@Test
	public void storePassphrasePutsIntoKeychain() throws KeychainAccessException {
		var encrypted = new byte[] {'a','b','x'};
		when(passphraseCryptor.encrypt(any(), any())).thenReturn(encrypted);
		winKeychainBase.storePassphrase("test3000", "display3000", "abc"); //
		verify(keychain).put(eq("test3000"), argThat(entry -> Arrays.equals(entry.ciphertext(),encrypted)));
		verify(passphraseCryptor).encrypt(any(), any());
	}

	@Test
	public void loadPassphraseReturnsNullOnKeychainNull() throws KeychainAccessException {
		when(keychain.get("test3000")).thenReturn(null);

		var result = winKeychainBase.loadPassphrase("test3000");

		verify(keychain).get("test3000");
		verify(passphraseCryptor, never()).decrypt(any(), any());
		Assertions.assertNull(result);
	}

	@Test
	public void loadPassphraseReturnsNullOnDecryptNull() throws KeychainAccessException {
		byte [] ciphertext = {'x','y','z'};
		byte [] salt = {'s'};
		when(keychain.get("test3000")).thenReturn(new KeychainEntry(ciphertext, salt));
		when(passphraseCryptor.decrypt(ciphertext, salt)).thenReturn(null);

		var result = winKeychainBase.loadPassphrase("test3000");

		verify(keychain).get("test3000");
		verify(passphraseCryptor).decrypt(ciphertext, salt);
		Assertions.assertNull(result);
	}

	@Test
	public void loadPassphraseReturnsPassphrase() throws KeychainAccessException {
		byte [] cleartext = {'a','b','c'};
		byte [] ciphertext = {'x','y','z'};
		byte [] salt = {'s'};
		when(keychain.get("test3000")).thenReturn(new KeychainEntry(ciphertext, salt));
		when(passphraseCryptor.decrypt(ciphertext, salt)).thenReturn(cleartext);

		var result = winKeychainBase.loadPassphrase("test3000");

		verify(keychain).get("test3000");
		verify(passphraseCryptor).decrypt(ciphertext, salt);
		Assertions.assertArrayEquals(new char[] {'a', 'b', 'c'}, result);
	}

	static class TestProvider extends WindowsKeychainAccessBase {

		public TestProvider(Keychain keychain, PassphraseCryptor passphraseCryptor) {
			super(keychain, passphraseCryptor);
		}

		@Override
		public String displayName() {
			return "TestProvider";
		}
	}
}
