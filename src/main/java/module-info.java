import org.cryptomator.integrations.autostart.AutoStartProvider;
import org.cryptomator.integrations.keychain.KeychainAccessProvider;
import org.cryptomator.integrations.uiappearance.UiAppearanceProvider;
import org.cryptomator.windows.autostart.WindowsAutoStart;
import org.cryptomator.windows.keychain.WindowsProtectedKeychainAccess;
import org.cryptomator.windows.uiappearance.WinUiAppearanceProvider;

module org.cryptomator.integrations.win {
	requires org.cryptomator.integrations.api;
	requires org.slf4j;
	requires com.google.gson;

	provides AutoStartProvider with WindowsAutoStart;
	provides KeychainAccessProvider with WindowsProtectedKeychainAccess;
	provides UiAppearanceProvider with WinUiAppearanceProvider;
}