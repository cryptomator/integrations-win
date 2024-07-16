package org.cryptomator.windows.quickaccess;

import org.cryptomator.integrations.common.DisplayName;
import org.cryptomator.integrations.common.OperatingSystem;
import org.cryptomator.integrations.common.Priority;
import org.cryptomator.integrations.quickaccess.QuickAccessService;
import org.cryptomator.integrations.quickaccess.QuickAccessServiceException;
import org.cryptomator.windows.common.RegistryKey;
import org.cryptomator.windows.common.WindowsException;
import org.cryptomator.windows.common.WindowsRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.UUID;

/**
 * Implementation of the {@link QuickAccessService} for Windows Explorer
 * <p>
 * Uses shell namespace extensions and based on a <a href="https://learn.microsoft.com/en-us/windows/win32/shell/integrate-cloud-storage">Microsoft docs example</a>.
 */
@Priority(100)
@OperatingSystem(OperatingSystem.Value.WINDOWS)
@DisplayName("Explorer Navigation Pane")
public class ExplorerQuickAccessService implements QuickAccessService {

	private static final Logger LOG = LoggerFactory.getLogger(ExplorerQuickAccessService.class);

	@Override
	public QuickAccessEntry add(Path target, String displayName) throws QuickAccessServiceException {
		if (displayName == null) {
			throw new IllegalArgumentException("Parameter 'displayname' must not be null.");
		}
		if (target == null) {
			throw new IllegalArgumentException("Parameter 'target' must not be null.");
		}
		var entryName = "Vault - " + displayName;
		var clsid = "{" + UUID.randomUUID() + "}";
		LOG.debug("Creating navigation pane entry with CLSID {}", clsid);
		//1. Creates the shell extension and names it
		try (var t = WindowsRegistry.startTransaction()) {
			try (var baseKey = t.createRegKey(RegistryKey.HKEY_CURRENT_USER, "Software\\Classes\\CLSID\\" + clsid, true)) {
				baseKey.setStringValue("", entryName, false);

				//2. Set icon
				//TODO: should this be customizable?
				try (var iconKey = t.createRegKey(baseKey, "DefaultIcon", true)) {
					var exePath = ProcessHandle.current().info().command();
					if (exePath.isPresent()) {
						iconKey.setStringValue("", exePath.get(), false);
					} else {
						iconKey.setStringValue("", "%SystemRoot%\\system32\\shell32.dll,4", true); //the regular folder icon
					}
				}

				//3. Pin the entry to navigation pane
				baseKey.setDwordValue("System.IsPinnedToNameSpaceTree", 0x1);

				//4. Place it in the top section of the navigation pane
				baseKey.setDwordValue("SortOrderIndex", 0x41);

				//5. Regsiter as a namespace extension
				try (var inProcServer32Key = t.createRegKey(baseKey, "InProcServer32", true)) {
					inProcServer32Key.setStringValue("", "%systemroot%\\system32\\shell32.dll", true);
				}

				//6. This extenstion works like a folder
				try (var instanceKey = t.createRegKey(baseKey, "Instance", true)) {
					instanceKey.setStringValue("CLSID", "{0E5AAE11-A475-4c5b-AB00-C66DE400274E}", false);

					//7. Set directory attributes for this "folder"
					// Attributes are READ_ONLY, DIRECTORY, REPARSE_POINT
					try (var initPropertyBagKey = t.createRegKey(instanceKey, "InitPropertyBag", true)) {
						initPropertyBagKey.setDwordValue("Attributes", 0x411);

						//8. Set the target folder
						initPropertyBagKey.setStringValue("TargetFolderPath", target.toString(), false);
					}
				}

				//9. Pin extenstion to the File Explorer tree
				try (var shellFolderKey = t.createRegKey(baseKey, "ShellFolder", true)) {
					shellFolderKey.setDwordValue("FolderValueFlags", 0x28);

					//10. Set SFGAO attributes for the shell folder
					shellFolderKey.setDwordValue("Attributes", 0xF080004D);
				}
				LOG.trace("Created RegKey {} and subkeys, including Values", baseKey.getPath());
			}

			//11. register extenstion in name space root
			var nameSpaceSubKey = "Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Desktop\\NameSpace\\" + clsid;
			try (var nameSpaceKey = t.createRegKey(RegistryKey.HKEY_CURRENT_USER, nameSpaceSubKey, true)) {
				nameSpaceKey.setStringValue("", entryName, false);
				LOG.trace("Created RegKey {} and setting default value", nameSpaceKey.getPath());
			}

			//12. Hide extension from Desktop
			try (var newStartPanelKey = t.createRegKey(RegistryKey.HKEY_CURRENT_USER, "Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\HideDesktopIcons\\NewStartPanel", true)) {
				newStartPanelKey.setDwordValue(clsid, 0x1);
				LOG.trace("Set value {} for RegKey {}", clsid, newStartPanelKey.getPath());
			}
			t.commit();
		} catch (WindowsException e) {
			throw new QuickAccessServiceException("Adding entry to Explorer navigation pane via Windows registry failed.", e);
		}
		return new ExplorerQuickAccessEntry(clsid);
	}

	static class ExplorerQuickAccessEntry implements QuickAccessService.QuickAccessEntry {

		private final String clsid;
		private volatile boolean isClosed = false;

		private ExplorerQuickAccessEntry(String clsid) {
			this.clsid = clsid;
		}

		@Override
		public synchronized void remove() throws QuickAccessServiceException {
			if (isClosed) {
				return;
			}

			LOG.debug("Removing navigation pane entry with CLSID {}", clsid);
			try (var t = WindowsRegistry.startTransaction()) {
				//undo step 11.
				var nameSpaceSubkey = "Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Desktop\\NameSpace\\" + clsid;
				LOG.trace("Removing RegKey {}", nameSpaceSubkey);
				t.deleteRegKey(RegistryKey.HKEY_CURRENT_USER, nameSpaceSubkey, true);

				//undo step 12.
				try (var nameSpaceKey = t.openRegKey(RegistryKey.HKEY_CURRENT_USER, "Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\HideDesktopIcons\\NewStartPanel")) {
					LOG.trace("Removing Value {} of RegKey {}", clsid, nameSpaceKey.getPath());
					nameSpaceKey.deleteValue(clsid, true);
				}

				//undo everything else
				try (var baseKey = t.openRegKey(RegistryKey.HKEY_CURRENT_USER, "Software\\Classes\\CLSID\\" + clsid)) {
					LOG.trace("Wiping everything under RegKey {} and key itself.", baseKey.getPath());
					baseKey.deleteTree("");
				}
				t.deleteRegKey(RegistryKey.HKEY_CURRENT_USER, "Software\\Classes\\CLSID\\" + clsid, true);
				t.commit();
				isClosed = true;
			} catch (WindowsException e) {
				throw new QuickAccessServiceException("Removing entry from Explorer navigation pane via Windows registry failed.", e);
			}
		}
	}

}
