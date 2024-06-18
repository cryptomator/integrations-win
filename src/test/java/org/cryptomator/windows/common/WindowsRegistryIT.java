package org.cryptomator.windows.common;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class WindowsRegistryIT {

	@Test
	@DisplayName("Open not exisitig key fails")
	@Order(1)
	public void testOpenNotExisting() {
		Assertions.assertThrows(RuntimeException.class, () -> {
			try (var t = WindowsRegistry.startTransaction();
				 var k = t.openRegKey(RegistryKey.HKEY_CURRENT_USER, "i\\do\\not\\exist")) {

			}
		});
	}

	@Test
	@DisplayName("Create and no commit leads to rollback")
	@Order(1)
	public void testCreateNotExistingRollback() {
		try (var t = WindowsRegistry.startTransaction();
			 var k = t.createRegKey(RegistryKey.HKEY_CURRENT_USER, "org.cryptomator.integrations-win", true)) {
		}

		Assertions.assertThrows(RuntimeException.class, () -> {
			try (var t = WindowsRegistry.startTransaction();
				 var k = t.openRegKey(RegistryKey.HKEY_CURRENT_USER, "org.cryptomator.integrations-win")) {
			}
		});
	}

	@Test
	@DisplayName("Creating, commit, open succeeds")
	@Order(2)
	public void testCreateNotExistingCommit() {
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
	public void testOpenSetValueRollback() {
		try (var t = WindowsRegistry.startTransaction();
			 var k = t.openRegKey(RegistryKey.HKEY_CURRENT_USER, "org.cryptomator.integrations-win")) {
			k.setStringValue("exampleStringValue", "In Progress", false);
		}

		//TODO: be more specific in the assertion
		Assertions.assertThrows(RuntimeException.class, () -> {
			try (var t = WindowsRegistry.startTransaction();
				 var k = t.openRegKey(RegistryKey.HKEY_CURRENT_USER, "org.cryptomator.integrations-win")) {
				k.getStringValue("exampleStringValue", false);
			}
		});
	}

	@Test
	@DisplayName("Open, setValue, commit")
	@Order(4)
	public void testOpenSetValueCommit() {
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
	public void testOpenDeleteValueRollback() {
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
	public void testOpenDeleteValueCommit() {
		try (var t = WindowsRegistry.startTransaction();
			 var k = t.openRegKey(RegistryKey.HKEY_CURRENT_USER, "org.cryptomator.integrations-win")) {
			k.deleteValue("exampleDwordValue");
			t.commit();
		}

		try (var t = WindowsRegistry.startTransaction();
			 var k = t.openRegKey(RegistryKey.HKEY_CURRENT_USER, "org.cryptomator.integrations-win")) {
			Assertions.assertThrows(RuntimeException.class, () -> {
				k.getDwordValue("exampleDwordValue");
			});
		}
	}

	@Test
	@DisplayName("Open, deleteValuesAndSubtrees, rollback")
	@Order(7)
	public void testOpenDeleteTreeRollback() {
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
	public void testOpenDeleteTreeCommit() {
		try (var t = WindowsRegistry.startTransaction();
			 var k = t.openRegKey(RegistryKey.HKEY_CURRENT_USER, "org.cryptomator.integrations-win");
			 var subk = t.createRegKey(k, "subkey", true)) {
			k.deleteAllValuesAndSubtrees();
			t.commit();
		}

		try (var t = WindowsRegistry.startTransaction();
			 var k = t.openRegKey(RegistryKey.HKEY_CURRENT_USER, "org.cryptomator.integrations-win")) {

			Assertions.assertThrows(RuntimeException.class, () -> t.openRegKey(k, "subkey"));
			Assertions.assertThrows(RuntimeException.class, () -> k.getStringValue("exampleStringValue", false));
		}
	}

	@Test
	@DisplayName("Delete, rollback")
	@Order(9)
	public void testDeleteRollback() {
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
	public void testDeleteCommit() {
		try (var t = WindowsRegistry.startTransaction()) {
			t.deleteRegKey(RegistryKey.HKEY_CURRENT_USER, "org.cryptomator.integrations-win");
			t.commit();
		}

		Assertions.assertThrows(RuntimeException.class, () -> {
			try (var t = WindowsRegistry.startTransaction();
				 var k = t.openRegKey(RegistryKey.HKEY_CURRENT_USER, "org.cryptomator.integrations-win")) {
			}
		});
	}
}
