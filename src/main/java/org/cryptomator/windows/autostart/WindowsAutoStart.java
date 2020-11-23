package org.cryptomator.windows.autostart;

import org.cryptomator.integrations.autostart.AutoStartProvider;
import org.cryptomator.integrations.autostart.ToggleAutoStartFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * OS specific class to check, en- and disable the auto start on Windows.
 * <p>
 * Two strategies are implemented for this feature, the first uses the registry and the second one the autostart folder.
 * <p>
 * The registry strategy checks/add/removes at the registry key {@code HKCU_AUTOSTART_KEY} an entry for Cryptomator. //TODO
 * The folder strategy checks/add/removes at the location {@code WINDOWS_START_MENU_ENTRY}. //TODO
 * <p>
 * To check if the feature is active, both strategies are applied.
 * To enable the feature, first the registry is tried and only on failure the autostart folder is used.
 * To disable it, first it is determined by an internal state, which strategies must be used and in the second step those are executed.
 *
 */
public class WindowsAutoStart implements AutoStartProvider {

	private static final Logger LOG = LoggerFactory.getLogger(WindowsAutoStart.class);

	private final WindowsAutoStartStrategy startupFolderStrategy;
	private final WindowsAutoStartStrategy registryStrategy;

	private boolean activatedUsingFolder;
	private boolean activatedUsingRegistry;

	public WindowsAutoStart(String exePath) {
		this.activatedUsingFolder = false;
		this.activatedUsingRegistry = false;
		this.startupFolderStrategy = new StartupFolderStrategy(exePath);
		this.registryStrategy = new RegistryStrategy(exePath);
	}

	@Override
	public synchronized boolean isEnabled() {
		try {
			return registryStrategy.isEnabled().
					thenCombine(startupFolderStrategy.isEnabled(), (bReg, bFolder) -> bReg || bFolder)
					.get(1000, TimeUnit.MILLISECONDS);
		} catch (InterruptedException | ExecutionException | TimeoutException e) {
			return false;
		}
	}

	@Override
	public synchronized void enable() throws ToggleAutoStartFailedException {
		try {
			registryStrategy.enable()
					.thenAccept((Void v) -> this.activatedUsingRegistry = true)
					.handle((result, exception) -> {
						if(exception != null){
							LOG.debug("Falling back to autostart folder.");
							return startupFolderStrategy.enable();
						}
						else {
							return CompletableFuture.completedFuture(result);
						}
					})
					.thenCompose(future -> future)
					.thenAccept((Void v) -> {
						this.activatedUsingFolder = true;
					})
					.get();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new ToggleAutoStartFailedException("Execution of enabling auto start setting was interrupted.");
		} catch (ExecutionException e) {
			throw new ToggleAutoStartFailedException("Enabling auto start failed both using registry and auto start folder.");
		}
	}

	@Override
	public synchronized void disable() throws ToggleAutoStartFailedException {
		if (activatedUsingRegistry) {
			registryStrategy.disable().whenComplete((voit, ex) -> {
				if (ex == null) {
					this.activatedUsingRegistry = false;
				}
			});
		}

		if (activatedUsingFolder) {
			startupFolderStrategy.disable().whenComplete((voit, ex) -> {
				if (ex == null) {
					this.activatedUsingFolder = false;
				}
			});
		}

		if (activatedUsingRegistry || activatedUsingFolder) {
			throw new ToggleAutoStartFailedException("Disabling auto start failed using registry and/or auto start folder.");
		}
	}

}
