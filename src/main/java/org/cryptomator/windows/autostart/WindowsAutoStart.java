package org.cryptomator.windows.autostart;

import org.cryptomator.integrations.autostart.AutoStartProvider;
import org.cryptomator.integrations.autostart.ToggleAutoStartFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Checks, en- and disables the auto start on Windows.
 * <p>
 * Two strategies are implemented for this feature, the first uses the registry and the second one the autostart folder.
 * <p>
 * To check and disable this feature, both strategies are applied.
 * To enable the feature, first the registry is tried and only on failure the autostart folder is used.
 *
 * @see RegistryStrategy
 * @see StartupFolderStrategy
 */
public class WindowsAutoStart implements AutoStartProvider {

	private static final Logger LOG = LoggerFactory.getLogger(WindowsAutoStart.class);

	private final WindowsAutoStartStrategy startupFolderStrategy;
	private final WindowsAutoStartStrategy registryStrategy;

	public WindowsAutoStart(String exePath) {
		this(new StartupFolderStrategy(exePath), new RegistryStrategy(exePath));
	}

	//Visisble for testing
	WindowsAutoStart(StartupFolderStrategy folderStrategy, RegistryStrategy registryStrategy) {
		this.startupFolderStrategy = folderStrategy;
		this.registryStrategy = registryStrategy;
	}

	@Override
	public synchronized boolean isEnabled() {
		try {
			return registryStrategy.isEnabled()
					.thenCombine(startupFolderStrategy.isEnabled(), (result1, result2) -> result1 || result2)
					.get();
		} catch (InterruptedException | ExecutionException e) {
			return false;
		}
	}

	@Override
	public synchronized void enable() throws ToggleAutoStartFailedException {
		try {
			registryStrategy.enable()
					.handle((result, exception) -> {
						if (exception != null) {
							LOG.debug("Falling back to autostart folder.");
							return startupFolderStrategy.enable();
						} else {
							return CompletableFuture.completedFuture(result);
						}
					})
					.thenCompose(future -> future) // for unwrapping
					.get();
		} catch (InterruptedException | ExecutionException e) {
			throw new ToggleAutoStartFailedException("Enabling auto start failed both using registry and auto start folder.", e);
		}
	}

	@Override
	public synchronized void disable() throws ToggleAutoStartFailedException {
		try {
			registryStrategy.disable()
					.runAfterBoth(startupFolderStrategy.disable(), () -> {
						return;
					})
					.get();
		} catch (InterruptedException | ExecutionException e) {
			throw new ToggleAutoStartFailedException("Disabling auto start failed using registry and/or auto start folder.", e);
		}
	}

}
