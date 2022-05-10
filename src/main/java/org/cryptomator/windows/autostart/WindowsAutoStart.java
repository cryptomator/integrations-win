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
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Checks, en- and disables autostart for an application on Windows using the startup folder.
 * <p>
 * The above actions are done by checking/adding/removing in the directory {@value RELATIVE_STARTUP_FOLDER} a shell link (.lnk).
 * The filename of the shell link is given by the JVM property {@value LNK_NAME_PROPERTY}. If the property is not set, the start command of the calling process is used.
 */
@Priority(1000)
@OperatingSystem(OperatingSystem.Value.WINDOWS)
public class WindowsAutoStart implements AutoStartProvider {

	private static final Logger LOG = LoggerFactory.getLogger(WindowsAutoStart.class);
	private static final String RELATIVE_STARTUP_FOLDER = "AppData\\Roaming\\Microsoft\\Windows\\Start Menu\\Programs\\Startup\\";
	private static final String LNK_FILE_EXTENSION = ".lnk";
	private static final String LNK_NAME_PROPERTY = "org.cryptomator.integrations_win.autostart.shell_link_name";

	private final WinShellLinks winShellLinks;
	private final Supplier<Path> absStartupEntryPathSupplier;
	private final Optional<String> exePath;

	@SuppressWarnings("unused") // default constructor required by ServiceLoader
	public WindowsAutoStart() {
		this.winShellLinks = new WinShellLinks();
		this.exePath = ProcessHandle.current().info().command();
		this.absStartupEntryPathSupplier = () -> Path.of(System.getProperty("user.home"), RELATIVE_STARTUP_FOLDER, this.getShellLinkName() + LNK_FILE_EXTENSION).toAbsolutePath();
	}

	@Override
	public boolean isEnabled() {
		try {
			return Files.exists(absStartupEntryPathSupplier.get());
		} catch (NoSuchElementException e) {
			return false;
		}
	}

	@Override
	public synchronized void enable() throws ToggleAutoStartFailedException {
		if (exePath.isEmpty()) {
			throw new ToggleAutoStartFailedException("Enabling autostart using the startup folder failed: Path to executable is not set");
		}

		assert exePath.isPresent();
		int returnCode = winShellLinks.createShortcut(exePath.get(), absStartupEntryPathSupplier.get().toString(), getShellLinkName());
		if (returnCode == 0) {
			LOG.debug("Successfully created {}.", absStartupEntryPathSupplier.get());
		} else {
			throw new ToggleAutoStartFailedException("Enabling autostart using the startup folder failed. Windows error code: " + Integer.toHexString(returnCode));
		}
	}

	@Override
	public synchronized void disable() throws ToggleAutoStartFailedException {
		try {
			Files.delete(absStartupEntryPathSupplier.get());
			LOG.debug("Successfully deleted {}.", absStartupEntryPathSupplier.get());
		} catch (NoSuchElementException e) {
			throw new ToggleAutoStartFailedException("Disabling auto start failed using startup folder: Name of shell link is not defined.");
		} catch (NoSuchFileException e) {
			//also okay
			LOG.debug("File {} not present. Nothing to do.", absStartupEntryPathSupplier.get());
		} catch (IOException e) {
			LOG.debug("Failed to delete entry from auto start folder.", e);
			throw new ToggleAutoStartFailedException("Disabling auto start failed using startup folder.", e);
		}
	}

	private String getShellLinkName() throws NoSuchElementException {
		var name = System.getProperty(LNK_NAME_PROPERTY);
		return Objects.requireNonNullElseGet(name, //
				() -> exePath.map(s -> s.substring(s.lastIndexOf('\\') + 1, s.lastIndexOf('.'))).get() //
		);
	}

}
