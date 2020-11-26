package org.cryptomator.windows.autostart;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.cryptomator.windows.autostart.AutoStartUtil.waitForProcessOrCancel;

/**
 * A strategy to check, en- and disable autostart on Windows using the registry.
 * <p>
 * The above actions are done by checking/adding/removing at the registry key {@value HKCU_AUTOSTART_KEY} the entry "Cryptomator".
 */
class RegistryStrategy implements WindowsAutoStartStrategy {

	private static final Logger LOG = LoggerFactory.getLogger(RegistryStrategy.class);
	private static final String HKCU_AUTOSTART_KEY = "\"HKCU\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Run\"";
	private static final String AUTOSTART_VALUE = "Cryptomator";

	private final String exePath;

	RegistryStrategy(String exePath) {
		this.exePath = exePath;
	}

	@Override
	public CompletableFuture<Boolean> isEnabled() {
		ProcessBuilder regQuery = new ProcessBuilder("reg", "query", HKCU_AUTOSTART_KEY, //
				"/v", AUTOSTART_VALUE);
		try {
			Process proc = regQuery.start();
			return proc.onExit().thenApply(p -> p.exitValue() == 0);
		} catch (IOException e) {
			LOG.debug("Failed to query {} from registry key {}", AUTOSTART_VALUE, HKCU_AUTOSTART_KEY);
			return CompletableFuture.completedFuture(false);
		}
	}

	@Override
	public CompletableFuture<Void> enable() {
		ProcessBuilder regAdd = new ProcessBuilder("reg", "add", HKCU_AUTOSTART_KEY, //
				"/v", AUTOSTART_VALUE, //
				"/t", "REG_SZ", //
				"/d", "\"" + exePath + "\"", //
				"/f");
		try {
			Process proc = regAdd.start();
			boolean finishedInTime = waitForProcessOrCancel(proc, 5, TimeUnit.SECONDS);
			if (finishedInTime && proc.exitValue() == 0) {
				LOG.debug("Added {} to registry key {}.", AUTOSTART_VALUE, HKCU_AUTOSTART_KEY);
				return CompletableFuture.completedFuture(null);
			} else {
				throw new IOException("Process exited with error code " + proc.exitValue());
			}
		} catch (IOException e) {
			LOG.debug("Registry could not be edited to set auto start.", e);
			return CompletableFuture.failedFuture(new SystemCommandException("Adding registry value failed."));
		}
	}

	@Override
	public CompletableFuture<Void> disable() {
		return isEnabled().thenCompose(result -> {
			if (result) {
				return disableInternal();
			} else {
				return CompletableFuture.completedFuture(null);
			}
		});
	}

	private CompletableFuture<Void> disableInternal() {
		ProcessBuilder regRemove = new ProcessBuilder("reg", "delete", HKCU_AUTOSTART_KEY, //
				"/v", AUTOSTART_VALUE, //
				"/f");
		try {
			Process proc = regRemove.start();
			boolean finishedInTime = waitForProcessOrCancel(proc, 5, TimeUnit.SECONDS);
			if (finishedInTime && proc.exitValue() == 0) {
				LOG.debug("Removed {} from registry key {}.", AUTOSTART_VALUE, HKCU_AUTOSTART_KEY);
				return CompletableFuture.completedFuture(null);
			} else {
				throw new IOException("Process exited with error code " + proc.exitValue());
			}
		} catch (IOException e) {
			LOG.debug("Registry could not be edited to remove auto start.", e);
			return CompletableFuture.failedFuture(new SystemCommandException("Removing registry value failed."));
		}
	}

}
