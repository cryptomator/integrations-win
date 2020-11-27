package org.cryptomator.windows.autostart;

import org.cryptomator.integrations.autostart.AutoStartProvider;
import org.cryptomator.integrations.autostart.ToggleAutoStartFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.cryptomator.windows.autostart.AutoStartUtil.waitForProcessOrCancel;

/**
 * Checks, en- and disables autostart for Cryptomator on Windows using the startup folder.
 * <p>
 * The above actions are done by checking/adding/removing in the directory {@value WINDOWS_START_MENU_ENTRY} a resource (.lnk file) for Cryptomator.
 */
public class WindowsAutoStart implements AutoStartProvider {

	private static final Logger LOG = LoggerFactory.getLogger(WindowsAutoStart.class);
	private static final String WINDOWS_START_MENU_ENTRY = "\\AppData\\Roaming\\Microsoft\\Windows\\Start Menu\\Programs\\Cryptomator.lnk";

	private final Optional<String> exePath;

	@SuppressWarnings("unused") // default constructor required by ServiceLoader
	public WindowsAutoStart() {
		this.exePath = ProcessHandle.current().info().command();
	}

	@Override
	public boolean isEnabled() {
		Path autoStartEntry = Path.of(System.getProperty("user.home") + WINDOWS_START_MENU_ENTRY);
		return Files.exists(autoStartEntry);
	}

	@Override
	public synchronized void enable() throws ToggleAutoStartFailedException {
		if (exePath.isEmpty()) {
			throw new ToggleAutoStartFailedException("Enabling autostart using the startup folder failed: Path to Cryptomator executable is not set");
		}

		assert exePath.isPresent();
		String autoStartFolderEntry = System.getProperty("user.home") + WINDOWS_START_MENU_ENTRY;
		String createShortcutCommand = "$s=(New-Object -COM WScript.Shell).CreateShortcut('" + autoStartFolderEntry + "');$s.TargetPath='" + exePath.get() + "';$s.Save();";
		ProcessBuilder shortcutAdd = new ProcessBuilder("cmd", "/c", "Start powershell " + createShortcutCommand);
		try {
			Process proc = shortcutAdd.start();
			boolean finishedInTime = waitForProcessOrCancel(proc, 5, TimeUnit.SECONDS);
			if (finishedInTime && proc.exitValue() == 0) {
				LOG.debug("Created file {} for auto start.", autoStartFolderEntry);
				return;
			} else {
				LOG.debug("Adding entry to auto start folder failed.");
				throw new IOException("Process exited with error code " + proc.exitValue());
			}
		} catch (IOException e) {
			throw new ToggleAutoStartFailedException("Enabling autostart using the startup folder failed.", e);
		}
	}

	@Override
	public synchronized void disable() throws ToggleAutoStartFailedException {
		try {
			Files.delete(Path.of(WINDOWS_START_MENU_ENTRY));
			LOG.debug("Successfully deleted {}.", WINDOWS_START_MENU_ENTRY);
			return;
		} catch (NoSuchFileException e) {
			//also okay
			LOG.debug("File {} not present. Nothing to do.", WINDOWS_START_MENU_ENTRY);
			return;
		} catch (IOException e) {
			LOG.debug("Failed to delete entry from auto start folder.", e);
			throw new ToggleAutoStartFailedException("Disabling auto start failed using startup folder.", e);
		}
	}

}
