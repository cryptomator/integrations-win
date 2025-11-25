package org.cryptomator.windows.notify;

public class WindowsNotifications {

	private static final String WINDOWS_NOTIFIATION_AUMID_PROPERTY = "cryptomator.integrationsWin.aumid";
	private static final String XML_TEMPLATE = """
			<toast>
			  <visual>
			    <binding template='ToastGeneric'>
			      <text>Cryptomator says Hello</text>
			    </binding>
			  </visual>
			  <actions>
			    <action content='Call back Cryptomator' arguments='the_args' activationKind='Foreground' />
			  </actions>
			</toast>""";

	private final WinToasts proxy;

	public WindowsNotifications() {
		proxy = new WinToasts();
	}

	public void sendNotification() {
		var aumid = System.getProperty(WINDOWS_NOTIFIATION_AUMID_PROPERTY, "Cryptomator.Cryptomator");
		proxy.sendToastNotification(aumid,XML_TEMPLATE);
	}
}
