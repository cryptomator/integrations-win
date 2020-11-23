package org.cryptomator.windows.autostart;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.cryptomator.windows.autostart.AutoStartUtil.waitForProcessOrCancel;

class StartupFolderStrategy implements WindowsAutoStartStrategy {

	private static final Logger LOG = LoggerFactory.getLogger(StartupFolderStrategy.class);
	private static final String WINDOWS_START_MENU_ENTRY = "\\AppData\\Roaming\\Microsoft\\Windows\\Start Menu\\Programs\\Cryptomator.lnk";

	private final String exePath;

	StartupFolderStrategy(String exePath) {
		this.exePath = exePath;
	}

	@Override
	public CompletableFuture<Boolean> isEnabled() {
		Path autoStartEntry = Path.of(System.getProperty("user.home") + WINDOWS_START_MENU_ENTRY);
		return CompletableFuture.completedFuture(Files.exists(autoStartEntry));
	}

	@Override
	public CompletableFuture<Void> enable() {
		String autoStartFolderEntry = System.getProperty("user.home") + WINDOWS_START_MENU_ENTRY;
		String createShortcutCommand = "$s=(New-Object -COM WScript.Shell).CreateShortcut('" + autoStartFolderEntry + "');$s.TargetPath='" + exePath + "';$s.Save();";
		ProcessBuilder shortcutAdd = new ProcessBuilder("cmd", "/c", "Start powershell " + createShortcutCommand);
		try {
			Process proc = shortcutAdd.start();
			boolean finishedInTime = waitForProcessOrCancel(proc, 5, TimeUnit.SECONDS);
			if (finishedInTime && proc.exitValue() == 0) {
				LOG.debug("Created file {} for auto start.", autoStartFolderEntry);
				return CompletableFuture.completedFuture(null);
			} else {
				throw new IOException("Process exited with error code " + proc.exitValue());
			}
		} catch (IOException e) {
			LOG.debug("Adding entry to auto start folder failed.", e);
			return CompletableFuture.failedFuture(new SystemCommandException("Adding entry to auto start folder failed."));
		}
	}

	@Override
	public CompletableFuture<Void> disable() {
		try {
			Files.delete(Path.of(WINDOWS_START_MENU_ENTRY));
			LOG.debug("Successfully deleted {}.", WINDOWS_START_MENU_ENTRY);
			return CompletableFuture.completedFuture(null);
		} catch (NoSuchFileException e) {
			//that is also okay
			return CompletableFuture.completedFuture(null);
		} catch (IOException e) {
			LOG.debug("Failed to delete entry from auto start folder.", e);
			return CompletableFuture.failedFuture(e);
		}
	}
}
