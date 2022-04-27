package org.cryptomator.windows.autostart;

import org.cryptomator.integrations.autostart.AutoStartProvider;
import org.cryptomator.integrations.autostart.ToggleAutoStartFailedException;
import org.cryptomator.integrations.common.OperatingSystem;
import org.cryptomator.integrations.common.Priority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Checks, en- and disables autostart for Cryptomator on Windows using the startup folder.
 * <p>
 * The above actions are done by checking/adding/removing in the directory {@value RELATIVE_STARTUP_FOLDER_ENTRY} a resource (.lnk file) for Cryptomator.
 */
@Priority(1000)
@OperatingSystem(OperatingSystem.Value.WINDOWS)
public class WindowsAutoStart implements AutoStartProvider {

	private static final Logger LOG = LoggerFactory.getLogger(WindowsAutoStart.class);
	private static final String RELATIVE_STARTUP_FOLDER_ENTRY = "\\AppData\\Roaming\\Microsoft\\Windows\\Start Menu\\Programs\\Startup\\Cryptomator.lnk";

	private final WinShellLinks winShellLinks;
	private final Path absoluteStartupEntryPath;
	private final Optional<String> exePath;

	@SuppressWarnings("unused") // default constructor required by ServiceLoader
	public WindowsAutoStart() {
		this.winShellLinks = new WinShellLinks();
		this.exePath = ProcessHandle.current().info().command();
		this.absoluteStartupEntryPath = Path.of(System.getProperty("user.home"), RELATIVE_STARTUP_FOLDER_ENTRY).toAbsolutePath();
	}

	@Override
	public boolean isEnabled() {
		return Files.exists(absoluteStartupEntryPath);
	}

	@Override
	public synchronized void enable() throws ToggleAutoStartFailedException {
		if (exePath.isEmpty()) {
			throw new ToggleAutoStartFailedException("Enabling autostart using the startup folder failed: Path to Cryptomator executable is not set");
		}

		assert exePath.isPresent();
		int returnCode = winShellLinks.createShortcut(exePath.get(), absoluteStartupEntryPath.toString(), "Cryptomator");
		if (returnCode == 0) {
			LOG.debug("Successfully created {}.", absoluteStartupEntryPath);
		} else {
			throw new ToggleAutoStartFailedException("Enabling autostart using the startup folder failed. Windows error code: " + Integer.toHexString(returnCode));
		}
	}

	@Override
	public synchronized void disable() throws ToggleAutoStartFailedException {
		try {
			Files.delete(absoluteStartupEntryPath);
			LOG.debug("Successfully deleted {}.", absoluteStartupEntryPath);
		} catch (NoSuchFileException e) {
			//also okay
			LOG.debug("File {} not present. Nothing to do.", absoluteStartupEntryPath);
		} catch (IOException e) {
			LOG.debug("Failed to delete entry from auto start folder.", e);
			throw new ToggleAutoStartFailedException("Disabling auto start failed using startup folder.", e);
		}
	}

}
