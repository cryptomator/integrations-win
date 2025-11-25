package org.cryptomator.windows.notify;

import org.junit.jupiter.api.Test;

public class WinToastsTest {

	@Test
	public void sendToast() {
		var toaster = new WindowsNotifications();

		toaster.sendNotification();
	}
}
