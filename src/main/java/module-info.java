import org.cryptomator.integrations.autostart.AutoStartProvider;
import org.cryptomator.integrations.keychain.KeychainAccessProvider;
import org.cryptomator.integrations.quickaccess.QuickAccessService;
import org.cryptomator.integrations.revealpath.RevealPathService;
import org.cryptomator.integrations.uiappearance.UiAppearanceProvider;
import org.cryptomator.windows.autostart.WindowsAutoStart;
import org.cryptomator.windows.keychain.WindowsProtectedKeychainAccess;
import org.cryptomator.windows.quickaccess.ExplorerQuickAccessService;
import org.cryptomator.windows.revealpath.ExplorerRevealPathService;
import org.cryptomator.windows.uiappearance.WinUiAppearanceProvider;

module org.cryptomator.integrations.win {
	requires org.cryptomator.integrations.api;
	requires org.slf4j;
	requires com.fasterxml.jackson.annotation;
	requires com.fasterxml.jackson.databind;

	opens org.cryptomator.windows.keychain to com.fasterxml.jackson.databind;

	provides AutoStartProvider with WindowsAutoStart;
	provides KeychainAccessProvider with WindowsProtectedKeychainAccess;
	provides UiAppearanceProvider with WinUiAppearanceProvider;
	provides RevealPathService with ExplorerRevealPathService;
	provides QuickAccessService with ExplorerQuickAccessService;
}