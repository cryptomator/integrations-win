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
				 var k = t.openRegKey(WindowsRegistry.RegistryKey.HKEY_CURRENT_USER, "i\\do\\not\\exist")) {

			}
		});
	}

	@Test
	@DisplayName("Create and no commit leads to rollback")
	@Order(1)
	public void testCreateNotExistingRollback() {
		try (var t = WindowsRegistry.startTransaction();
			 var k = t.createRegKey(WindowsRegistry.RegistryKey.HKEY_CURRENT_USER, "org.cryptomator.integrations-win", true)) {
		}

		Assertions.assertThrows(RuntimeException.class, () -> {
			try (var t = WindowsRegistry.startTransaction();
				 var k = t.openRegKey(WindowsRegistry.RegistryKey.HKEY_CURRENT_USER, "org.cryptomator.integrations-win")) {
			}
		});
	}

	@Test
	@DisplayName("Creating, commit, open succeeds")
	@Order(2)
	public void testCreateNotExistingCommit() {
		try (var t = WindowsRegistry.startTransaction();
			 var k = t.createRegKey(WindowsRegistry.RegistryKey.HKEY_CURRENT_USER, "org.cryptomator.integrations-win", true)) {
			t.commit();
		}

		try (var t = WindowsRegistry.startTransaction();
			 var k = t.openRegKey(WindowsRegistry.RegistryKey.HKEY_CURRENT_USER, "org.cryptomator.integrations-win")) {
		}
	}

	@Test
	@DisplayName("Open, setValue, rollback")
	@Order(3)
	public void testOpenSetValueRollback() {
		try (var t = WindowsRegistry.startTransaction();
			 var k = t.openRegKey(WindowsRegistry.RegistryKey.HKEY_CURRENT_USER, "org.cryptomator.integrations-win")) {
			k.setStringValue("itTest", "In Progress");
		}

		Assertions.assertThrows(RuntimeException.class, () -> {
			try (var t = WindowsRegistry.startTransaction();
				 var k = t.openRegKey(WindowsRegistry.RegistryKey.HKEY_CURRENT_USER, "org.cryptomator.integrations-win")) {
				k.getStringValue("itTest");
			}
		});
	}

	@Test
	@DisplayName("Open, setValue, commit")
	@Order(4)
	public void testOpenSetValueCommit() {
		try (var t = WindowsRegistry.startTransaction();
			 var k = t.openRegKey(WindowsRegistry.RegistryKey.HKEY_CURRENT_USER, "org.cryptomator.integrations-win")) {
			k.setStringValue("itTest", "In Progress");
			t.commit();
		}

		try (var t = WindowsRegistry.startTransaction();
			 var k = t.openRegKey(WindowsRegistry.RegistryKey.HKEY_CURRENT_USER, "org.cryptomator.integrations-win")) {
			k.getStringValue("itTest");
		}
	}

	@Test
	@DisplayName("Open, deleteTree, rollback")
	@Order(5)
	public void testOpenDeleteTreeRollback() {
		try (var t = WindowsRegistry.startTransaction();
			 var k = t.openRegKey(WindowsRegistry.RegistryKey.HKEY_CURRENT_USER, "org.cryptomator.integrations-win")) {
			k.deleteValuesAndSubtree("");
			t.rollback();
		}

		Assertions.assertDoesNotThrow(() -> {
			try (var t = WindowsRegistry.startTransaction();
				 var k = t.openRegKey(WindowsRegistry.RegistryKey.HKEY_CURRENT_USER, "org.cryptomator.integrations-win")) {
				k.getStringValue("itTest");
			}
		});
	}

	@Test
	@DisplayName("Open, deleteValuesAndSubtrees, commit")
	@Order(6)
	public void testOpenDeleteTreeCommit() {
		try (var t = WindowsRegistry.startTransaction();
			 var k = t.openRegKey(WindowsRegistry.RegistryKey.HKEY_CURRENT_USER, "org.cryptomator.integrations-win");
			 var subk = t.createRegKey(k, "subkey", true)) {
			k.deleteValuesAndSubtree("");
			t.commit();
		}

		try (var t = WindowsRegistry.startTransaction();
			 var k = t.openRegKey(WindowsRegistry.RegistryKey.HKEY_CURRENT_USER, "org.cryptomator.integrations-win")) {

			Assertions.assertThrows(RuntimeException.class, () -> t.openRegKey(k, "subkey"));
			Assertions.assertThrows(RuntimeException.class, () -> k.getStringValue("itTest"));
		}
	}

	@Test
	@DisplayName("Delete, rollback")
	@Order(7)
	public void testDeleteRollback() {
		try (var t = WindowsRegistry.startTransaction()) {
			t.deleteRegKey(WindowsRegistry.RegistryKey.HKEY_CURRENT_USER, "org.cryptomator.integrations-win");
			t.rollback();
		}

		try (var t = WindowsRegistry.startTransaction();
			 var k = t.openRegKey(WindowsRegistry.RegistryKey.HKEY_CURRENT_USER, "org.cryptomator.integrations-win")) {
		}
	}

	@Test
	@DisplayName("Delete, commit")
	@Order(8)
	public void testDeleteCommit() {
		try (var t = WindowsRegistry.startTransaction()) {
			t.deleteRegKey(WindowsRegistry.RegistryKey.HKEY_CURRENT_USER, "org.cryptomator.integrations-win");
			t.commit();
		}

		Assertions.assertThrows(RuntimeException.class, () -> {
			try (var t = WindowsRegistry.startTransaction();
				 var k = t.openRegKey(WindowsRegistry.RegistryKey.HKEY_CURRENT_USER, "org.cryptomator.integrations-win")) {
			}
		});
	}
}
