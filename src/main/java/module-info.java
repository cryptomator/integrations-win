import org.cryptomator.integrations.autostart.AutoStartProvider;
import org.cryptomator.integrations.autoupdate.AutoUpdateProvider;
import org.cryptomator.integrations.keychain.KeychainAccessProvider;
import org.cryptomator.integrations.uiappearance.UiAppearanceProvider;
import org.cryptomator.windows.autostart.WindowsAutoStart;
import org.cryptomator.windows.autoupdate.WinsparkleUpdate;
import org.cryptomator.windows.keychain.WindowsProtectedKeychainAccess;
import org.cryptomator.windows.uiappearance.WinUiAppearanceProvider;

module org.cryptomator.integrations.win {
	requires org.cryptomator.integrations.api;
	requires org.slf4j;
	requires com.google.gson;
	requires org.apache.commons.lang3;
	requires winsparkle.java;

	opens org.cryptomator.windows.keychain to com.google.gson;

	provides AutoStartProvider with WindowsAutoStart;
	provides AutoUpdateProvider with WinsparkleUpdate;
	provides KeychainAccessProvider with WindowsProtectedKeychainAccess;
	provides UiAppearanceProvider with WinUiAppearanceProvider;
}