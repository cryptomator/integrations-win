package org.cryptomator.windows.common;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.cryptomator.windows.capi.common.Windows_h.ERROR_FILE_NOT_FOUND;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class WindowsRegistryIT {

	@Test
	@DisplayName("Open not exisitig key fails")
	@Order(1)
	public void testOpenNotExisting() {
		var winException = Assertions.assertThrows(RegistryKeyException.class, () -> {
			try (var t = WindowsRegistry.startTransaction()) {
				var k = t.openRegKey(RegistryKey.HKEY_CURRENT_USER, "i\\do\\not\\exist");
			}
		});
		Assertions.assertEquals(ERROR_FILE_NOT_FOUND(), winException.getSystemErrorCode());
	}

	@Test
	@DisplayName("Deleting not exisitig key fails")
	@Order(1)
	public void testDeleteNotExisting() {
		var winException = Assertions.assertThrows(RegistryKeyException.class, () -> {
			try (var t = WindowsRegistry.startTransaction()) {
				t.deleteRegKey(RegistryKey.HKEY_CURRENT_USER, "i\\do\\not\\exist");
			}
		});
		Assertions.assertEquals(ERROR_FILE_NOT_FOUND(), winException.getSystemErrorCode());
	}

	@Test
	@DisplayName("Deleting key, ingoring not-existence succeeds")
	@Order(1)
	public void testDeleteIgnoreNotExisting() throws WindowsException {
		try (var t = WindowsRegistry.startTransaction()) {
			Assertions.assertDoesNotThrow(() -> t.deleteRegKey(RegistryKey.HKEY_CURRENT_USER, "i\\do\\not\\exist", true));
		}
	}

	@Test
	@DisplayName("Create and no commit leads to rollback")
	@Order(1)
	public void testCreateNotExistingRollback() throws WindowsException {
		try (var t = WindowsRegistry.startTransaction();
			 var k = t.createRegKey(RegistryKey.HKEY_CURRENT_USER, "org.cryptomator.integrations-win", true)) {
		}

		var winException = Assertions.assertThrows(RegistryKeyException.class, () -> {
			try (var t = WindowsRegistry.startTransaction();
				 var k = t.openRegKey(RegistryKey.HKEY_CURRENT_USER, "org.cryptomator.integrations-win")) {
			}
		});
		Assertions.assertEquals(ERROR_FILE_NOT_FOUND(), winException.getSystemErrorCode());
	}

	@Test
	@DisplayName("Creating, commit, open succeeds")
	@Order(2)
	public void testCreateNotExistingCommit() throws WindowsException {
		try (var t = WindowsRegistry.startTransaction();
			 var k = t.createRegKey(RegistryKey.HKEY_CURRENT_USER, "org.cryptomator.integrations-win", true)) {
			t.commit();
		}

		try (var t = WindowsRegistry.startTransaction();
			 var k = t.openRegKey(RegistryKey.HKEY_CURRENT_USER, "org.cryptomator.integrations-win")) {
		}
	}

	@Test
	@DisplayName("Open, setValue, rollback")
	@Order(3)
	public void testOpenSetValueRollback() throws WindowsException {
		try (var t = WindowsRegistry.startTransaction();
			 var k = t.openRegKey(RegistryKey.HKEY_CURRENT_USER, "org.cryptomator.integrations-win")) {
			k.setStringValue("exampleStringValue", "In Progress", false);
		}

		try (var t = WindowsRegistry.startTransaction();
			 var k = t.openRegKey(RegistryKey.HKEY_CURRENT_USER, "org.cryptomator.integrations-win")) {
			var regException = Assertions.assertThrows(RegistryValueException.class, () -> {
				k.getStringValue("exampleStringValue", false);
			});
			Assertions.assertEquals(ERROR_FILE_NOT_FOUND(), regException.getSystemErrorCode());
		}
	}

	@Test
	@DisplayName("Open, setValue, commit")
	@Order(4)
	public void testOpenSetValueCommit() throws WindowsException {
		try (var t = WindowsRegistry.startTransaction();
			 var k = t.openRegKey(RegistryKey.HKEY_CURRENT_USER, "org.cryptomator.integrations-win")) {
			k.setStringValue("exampleStringValue", "In Progress", false);
			k.setDwordValue("exampleDwordValue", 0x42);
			t.commit();
		}

		try (var t = WindowsRegistry.startTransaction();
			 var k = t.openRegKey(RegistryKey.HKEY_CURRENT_USER, "org.cryptomator.integrations-win")) {
			var stringData = k.getStringValue("exampleStringValue", false);
			var binaryData = k.getDwordValue("exampleDwordValue");
			Assertions.assertEquals("In Progress", stringData);
			Assertions.assertEquals(0x42, binaryData);
		}
	}

	@Test
	@DisplayName("Open, deleteValue, rollback")
	@Order(5)
	public void testOpenDeleteValueRollback() throws WindowsException {
		try (var t = WindowsRegistry.startTransaction();
			 var k = t.openRegKey(RegistryKey.HKEY_CURRENT_USER, "org.cryptomator.integrations-win")) {
			k.deleteValue("exampleDwordValue");
		}

		try (var t = WindowsRegistry.startTransaction();
			 var k = t.openRegKey(RegistryKey.HKEY_CURRENT_USER, "org.cryptomator.integrations-win")) {
			var data = k.getDwordValue("exampleDwordValue");
			Assertions.assertEquals(0x42, data);
		}
	}

	@Test
	@DisplayName("Open, deleteValue, commit")
	@Order(6)
	public void testOpenDeleteValueCommit() throws WindowsException {
		try (var t = WindowsRegistry.startTransaction();
			 var k = t.openRegKey(RegistryKey.HKEY_CURRENT_USER, "org.cryptomator.integrations-win")) {
			k.deleteValue("exampleDwordValue");
			t.commit();
		}

		try (var t = WindowsRegistry.startTransaction();
			 var k = t.openRegKey(RegistryKey.HKEY_CURRENT_USER, "org.cryptomator.integrations-win")) {
			//TODO: check for system error code
			var regException = Assertions.assertThrows(RegistryValueException.class, () -> {
				k.getDwordValue("exampleDwordValue");
			});
			Assertions.assertEquals(ERROR_FILE_NOT_FOUND(), regException.getSystemErrorCode());
		}
	}

	@Test
	@DisplayName("Open, deleteValuesAndSubtrees, rollback")
	@Order(7)
	public void testOpenDeleteTreeRollback() throws WindowsException {
		try (var t = WindowsRegistry.startTransaction();
			 var k = t.openRegKey(RegistryKey.HKEY_CURRENT_USER, "org.cryptomator.integrations-win")) {
			k.deleteAllValuesAndSubtrees();
		}

		Assertions.assertDoesNotThrow(() -> {
			try (var t = WindowsRegistry.startTransaction();
				 var k = t.openRegKey(RegistryKey.HKEY_CURRENT_USER, "org.cryptomator.integrations-win")) {
				k.getStringValue("exampleStringValue", false);
			}
		});
	}

	@Test
	@DisplayName("Open, deleteValuesAndSubtrees, commit")
	@Order(8)
	public void testOpenDeleteTreeCommit() throws WindowsException {
		try (var t = WindowsRegistry.startTransaction();
			 var k = t.openRegKey(RegistryKey.HKEY_CURRENT_USER, "org.cryptomator.integrations-win");
			 var subk = t.createRegKey(k, "subkey", true)) {
			k.deleteAllValuesAndSubtrees();
			t.commit();
		}

		try (var t = WindowsRegistry.startTransaction();
			 var k = t.openRegKey(RegistryKey.HKEY_CURRENT_USER, "org.cryptomator.integrations-win")) {

			var regKeyException = Assertions.assertThrows(RegistryKeyException.class, () -> t.openRegKey(k, "subkey"));
			var regValueException = Assertions.assertThrows(RegistryValueException.class, () -> k.getStringValue("exampleStringValue", false));
			Assertions.assertEquals(ERROR_FILE_NOT_FOUND(), regKeyException.getSystemErrorCode());
			Assertions.assertEquals(ERROR_FILE_NOT_FOUND(), regValueException.getSystemErrorCode());
		}
	}

	@Test
	@DisplayName("Delete, rollback")
	@Order(9)
	public void testDeleteRollback() throws WindowsException {
		try (var t = WindowsRegistry.startTransaction()) {
			t.deleteRegKey(RegistryKey.HKEY_CURRENT_USER, "org.cryptomator.integrations-win");
		}

		try (var t = WindowsRegistry.startTransaction();
			 var k = t.openRegKey(RegistryKey.HKEY_CURRENT_USER, "org.cryptomator.integrations-win")) {
		}
	}

	@Test
	@DisplayName("Delete, commit")
	@Order(10)
	public void testDeleteCommit() throws WindowsException {
		try (var t = WindowsRegistry.startTransaction()) {
			t.deleteRegKey(RegistryKey.HKEY_CURRENT_USER, "org.cryptomator.integrations-win");
			t.commit();
		}

		var regException = Assertions.assertThrows(WindowsException.class, () -> {
			try (var t = WindowsRegistry.startTransaction();
				 var k = t.openRegKey(RegistryKey.HKEY_CURRENT_USER, "org.cryptomator.integrations-win")) {
			}
		});
		Assertions.assertEquals(ERROR_FILE_NOT_FOUND(), regException.getSystemErrorCode());
	}
}
