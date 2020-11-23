package org.cryptomator.windows.autostart;

import java.util.concurrent.TimeUnit;

class AutoStartUtil {

	static boolean waitForProcessOrCancel(Process proc, int timeout, TimeUnit timeUnit) {
		boolean finishedInTime = false;
		try {
			finishedInTime = proc.waitFor(timeout, timeUnit);
		} catch (InterruptedException e) {
			//LOG.error("Timeout while reading registry", e);
			Thread.currentThread().interrupt();
		} finally {
			if (!finishedInTime) {
				proc.destroyForcibly();
			}
		}
		return finishedInTime;
	}

}
