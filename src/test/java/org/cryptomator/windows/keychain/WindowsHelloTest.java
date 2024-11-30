package org.cryptomator.windows.keychain;

import org.cryptomator.integrations.keychain.KeychainAccessException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

@EnabledOnOs(OS.WINDOWS)
@DisabledIfEnvironmentVariable(named = "CI", matches = ".*")
class WindowsHelloTest {

	@Test
	@DisplayName("Test Windows Hello Authentication")
	@Disabled
	public void testStoreAndLoadWithAuth() throws KeychainAccessException {
		var winhello = new WindowsHello();
		String storedPw = "h€llo wørld123";
		byte[] ciphertext = winhello.setEncryptionKey(storedPw.getBytes(), "salt".getBytes());
		Assertions.assertNotNull(ciphertext);

		byte[] shouldBeNull = winhello.getEncryptionKey(ciphertext, "pepper".getBytes());
		Assertions.assertNull(shouldBeNull);

		byte[] cleartext = winhello.getEncryptionKey(ciphertext, "salt".getBytes());
		Assertions.assertArrayEquals(storedPw.getBytes(), cleartext);
	}

}