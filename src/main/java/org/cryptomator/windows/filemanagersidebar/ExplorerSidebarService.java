package org.cryptomator.windows.filemanagersidebar;

import org.cryptomator.integrations.filemanagersidebar.SidebarService;
import org.cryptomator.windows.common.WindowsRegistry;

import java.nio.file.Path;
import java.util.UUID;

/**
 * Implementation of the FileManagerSidebarService for Windows Explorer
 * <p>
 * Based on a <a href="https://learn.microsoft.com/en-us/windows/win32/shell/integrate-cloud-storage">Microsoft docs example</a>.
 */
public class ExplorerSidebarService implements SidebarService {

	@Override
	public SidebarEntry add(Path mountpoint) {
		var entryName = "Vault " + mountpoint.getFileName().toString();
		var clsid = UUID.randomUUID().toString();
		System.out.println(clsid);
		//1. reg add HKCU\Software\Classes\CLSID\{0672A6D1-A6E0-40FE-AB16-F25BADC6D9E3} /ve /t REG_SZ /d "MyCloudStorageApp" /f
		try (var t = WindowsRegistry.startTransaction()) {
			try (var baseKey = t.createRegKey(WindowsRegistry.RegistryKey.HKEY_CURRENT_USER, "Software\\Classes\\CLSID\\{%s}".formatted(clsid), true)) {

				baseKey.setStringValue("", entryName, false);

				//2. reg add HKCU\Software\Classes\CLSID\{0672A6D1-A6E0-40FE-AB16-F25BADC6D9E3}\DefaultIcon /ve /t REG_EXPAND_SZ /d %%SystemRoot%%\system32\imageres.dll,-1043 /f
				try (var iconKey = t.createRegKey(baseKey, "DefaultIcon", true)) {
					iconKey.setStringValue("", "C:\\Program Files\\Cryptomator\\Cryptomator.exe", false);
				}

				//3. reg add HKCU\Software\Classes\CLSID\{0672A6D1-A6E0-40FE-AB16-F25BADC6D9E3} /v System.IsPinnedToNameSpaceTree /t REG_DWORD /d 0x1 /f
				baseKey.setDwordValue("System.IsPinnedToNameSpaceTree", 0x1);

				//4. reg add HKCU\Software\Classes\CLSID\{0672A6D1-A6E0-40FE-AB16-F25BADC6D9E3} /v SortOrderIndex /t REG_DWORD /d 0x42 /f
				baseKey.setDwordValue("SortOrderIndex", 0x41);

				//5. reg add HKCU\Software\Classes\CLSID\{0672A6D1-A6E0-40FE-AB16-F25BADC6D9E3}\InProcServer32 /ve /t REG_EXPAND_SZ /d  /f
				try (var inProcServer32Key = t.createRegKey(baseKey, "InProcServer32", true)) {
					inProcServer32Key.setStringValue("", "%systemroot%\\system32\\shell32.dll", true);
				}

				//6. reg add HKCU\Software\Classes\CLSID\{0672A6D1-A6E0-40FE-AB16-F25BADC6D9E3}\Instance /v CLSID /t REG_SZ /d {0E5AAE11-A475-4c5b-AB00-C66DE400274E} /f
				try (var instanceKey = t.createRegKey(baseKey, "Instance", true)) {
					instanceKey.setStringValue("CLSID", "{0E5AAE11-A475-4c5b-AB00-C66DE400274E}", false);

					//7. reg add HKCU\Software\Classes\CLSID\{0672A6D1-A6E0-40FE-AB16-F25BADC6D9E3}\Instance\InitPropertyBag /v Attributes /t REG_DWORD /d 0x411 /f
					// Attributes are READ_ONLY, DIRECTORY, REPARSE_POINT
					try (var initPropertyBagKey = t.createRegKey(instanceKey, "InitPropertyBag", true)) {
						initPropertyBagKey.setDwordValue("Attributes", 0x411);

						//8. reg add HKCU\Software\Classes\CLSID\{0672A6D1-A6E0-40FE-AB16-F25BADC6D9E3}\Instance\InitPropertyBag /v TargetFolderPath /t REG_EXPAND_SZ /d %%USERPROFILE%%\MyCloudStorageApp /f
						initPropertyBagKey.setStringValue("TargetFolderPath", mountpoint.toString(), false);
					}
				}

				//9. reg add HKCU\Software\Classes\CLSID\{0672A6D1-A6E0-40FE-AB16-F25BADC6D9E3}\ShellFolder /v FolderValueFlags /t REG_DWORD /d 0x28 /f
				try (var shellFolderKey = t.createRegKey(baseKey, "ShellFolder", true)) {
					shellFolderKey.setDwordValue("FolderValueFlags", 0x28);

					//10. reg add HKCU\Software\Classes\CLSID\{0672A6D1-A6E0-40FE-AB16-F25BADC6D9E3}\ShellFolder /v Attributes /t REG_DWORD /d 0xF080004D /f
					shellFolderKey.setDwordValue("Attributes", 0xF080004D);
				}
			}

			//11. reg add HKCU\Software\Microsoft\Windows\CurrentVersion\Explorer\Desktop\NameSpace\{0672A6D1-A6E0-40FE-AB16-F25BADC6D9E3} /ve /t REG_SZ /d MyCloudStorageApp /f
			var nameSpaceSubKey = "Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Desktop\\NameSpace\\{%s}".formatted(clsid);
			try (var nameSpaceKey = t.createRegKey(WindowsRegistry.RegistryKey.HKEY_CURRENT_USER, nameSpaceSubKey, true)) {
				nameSpaceKey.setStringValue("", entryName, false);
			}

			//12. reg add HKCU\Software\Microsoft\Windows\CurrentVersion\Explorer\HideDesktopIcons\NewStartPanel /v {0672A6D1-A6E0-40FE-AB16-F25BADC6D9E3} /t REG_DWORD /d 0x1 /f
			try (var newStartPanelKey = t.createRegKey(WindowsRegistry.RegistryKey.HKEY_CURRENT_USER, "Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\HideDesktopIcons\\NewStartPanel", true)) {
				newStartPanelKey.setDwordValue("{%s}".formatted(clsid), 0x1);
			}
			t.commit();
		}
		return null;
	}

}
