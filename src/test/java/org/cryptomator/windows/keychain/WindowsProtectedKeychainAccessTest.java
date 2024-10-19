/*******************************************************************************
 * Copyright (c) 2017 Skymatic UG (haftungsbeschränkt).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the accompanying LICENSE file.
 *******************************************************************************/
package org.cryptomator.windows.keychain;

import org.cryptomator.integrations.keychain.KeychainAccessException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import java.nio.file.Path;
import java.util.List;

public class WindowsProtectedKeychainAccessTest {

	private WindowsProtectedKeychainAccess keychain;

	@BeforeEach
	public void setup(@TempDir Path tempDir) {
		Path keychainPath = tempDir.resolve("keychainfile.tmp");
		WinDataProtection winDataProtection = Mockito.mock(WinDataProtection.class);
		WinHello winHello = Mockito.mock(WinHello.class);
		Answer<byte[]> answerReturningFirstArg = invocation -> ((byte[]) invocation.getArgument(0)).clone();
		Mockito.when(winDataProtection.protect(Mockito.any(), Mockito.any())).thenAnswer(answerReturningFirstArg);
		Mockito.when(winDataProtection.unprotect(Mockito.any(), Mockito.any())).thenAnswer(answerReturningFirstArg);
		keychain = new WindowsProtectedKeychainAccess(List.of(keychainPath), winDataProtection, winHello);
	}

	@Test
	public void testStoreAndLoad() throws KeychainAccessException {
		String storedPw1 = "topSecret";
		String storedPw2 = "bottomSecret";
		keychain.storePassphrase("myPassword", null, storedPw1, false);
		keychain.storePassphrase("myOtherPassword", null, storedPw2, false);
		String loadedPw1 = new String(keychain.loadPassphrase("myPassword"));
		String loadedPw2 = new String(keychain.loadPassphrase("myOtherPassword"));
		Assertions.assertEquals(storedPw1, loadedPw1);
		Assertions.assertEquals(storedPw2, loadedPw2);
		keychain.deletePassphrase("myPassword");
		Assertions.assertNull(keychain.loadPassphrase("myPassword"));
		Assertions.assertNotNull(keychain.loadPassphrase("myOtherPassword"));
		Assertions.assertNull(keychain.loadPassphrase("nonExistingPassword"));
	}

	@Nested
	public class ParsePaths {
		@Test
		@DisplayName("String is split with path separator")
		public void testParsePaths() {
			String paths = "C:\\foo\\bar;bar\\kuz";
			var result = WindowsProtectedKeychainAccess.parsePaths(paths, ";");
			Assertions.assertEquals(2, result.size());
			Assertions.assertTrue(result.contains(Path.of("C:\\foo\\bar")));
			Assertions.assertTrue(result.contains(Path.of("bar\\kuz")));
		}

		@Test
		@DisplayName("Empty string returns empty list")
		public void testParsePathsEmpty() {
			var result = WindowsProtectedKeychainAccess.parsePaths("", ";");
			Assertions.assertEquals(0, result.size());
		}

		@Test
		@DisplayName("Strings starting with ~ are resolved to user home")
		public void testParsePathsUserHome() {
			var userHome = Path.of(System.getProperty("user.home"));
			var result = WindowsProtectedKeychainAccess.parsePaths("this\\~\\not;~\\foo\\bar", ";");
			Assertions.assertEquals(2, result.size());
			Assertions.assertTrue(result.contains(Path.of("this\\~\\not")));
			Assertions.assertTrue(result.contains(userHome.resolve("foo\\bar")));
		}

	}

}
