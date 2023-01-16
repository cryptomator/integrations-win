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
import java.util.Optional;

/**
 * Checks, en- and disables autostart for an application on Windows using the startup folder.
 * <p>
 * The above actions are done by checking/adding/removing a shell link (.lnk) in the Windows defined Startup directory (see also https://learn.microsoft.com/en-us/windows/win32/shell/knownfolderid#constants).
 * The filename of the shell link is given by the JVM property {@value LNK_NAME_PROPERTY}. If the property is not set before object creation, the start command of the calling process is used.
 */
@Priority(1000)
@OperatingSystem(OperatingSystem.Value.WINDOWS)
public class WindowsAutoStart implements AutoStartProvider {

	private static final Logger LOG = LoggerFactory.getLogger(WindowsAutoStart.class);
	private static final String LNK_FILE_EXTENSION = ".lnk";
	private static final String LNK_NAME_PROPERTY = "cryptomator.integrationsWin.autoStartShellLinkName";

	private final WinShellLinks winShellLinks;
	private final Optional<String> shellLinkName;
	private final Optional<Path> absShellLinkPath;
	private final Optional<Path> exePath;

	@SuppressWarnings("unused") // default constructor required by ServiceLoader
	public WindowsAutoStart() {
		this.winShellLinks = new WinShellLinks();
		this.exePath = ProcessHandle.current().info().command().map(Path::of);
		this.shellLinkName = Optional.ofNullable(System.getProperty(LNK_NAME_PROPERTY)).or(() -> exePath.map(this::getExeBaseName));
		this.absShellLinkPath = Optional.ofNullable(winShellLinks.getPathToStartupFolder()).flatMap(p -> shellLinkName.map(name -> p + "\\" + name + LNK_FILE_EXTENSION)).map(Path::of);
	}

	@Override
	public boolean isEnabled() {
		return absShellLinkPath.map(Files::exists).orElse(false);
	}

	@Override
	public synchronized void enable() throws ToggleAutoStartFailedException {
		if (exePath.isEmpty()) {
			throw new ToggleAutoStartFailedException("Enabling autostart using the startup folder failed: Path to executable is not set");
		}

		assert exePath.isPresent() && absShellLinkPath.isPresent() && shellLinkName.isPresent();
		int returnCode = winShellLinks.createShortcut(exePath.get().toString(), absShellLinkPath.get().toString(), shellLinkName.get());
		if (returnCode == 0) {
			LOG.debug("Successfully created {}.", absShellLinkPath.get());
		} else {
			throw new ToggleAutoStartFailedException("Enabling autostart using the startup folder failed. Windows error code: " + Integer.toHexString(returnCode));
		}
	}

	@Override
	public synchronized void disable() throws ToggleAutoStartFailedException {
		try {
			Files.delete(absShellLinkPath.get());
			LOG.debug("Successfully deleted {}.", absShellLinkPath.get());
		} catch (NoSuchElementException e) { //thrown by Optional::get
			throw new ToggleAutoStartFailedException("Disabling auto start failed using startup folder: Name of shell link is not defined.");
		} catch (NoSuchFileException e) {
			//also okay
			LOG.debug("File {} not present. Nothing to do.", absShellLinkPath.get());
		} catch (IOException e) {
			LOG.debug("Failed to delete entry from auto start folder.", e);
			throw new ToggleAutoStartFailedException("Disabling auto start failed using startup folder.", e);
		}
	}

	private String getExeBaseName(Path exePath) {
		var name = exePath.getFileName().toString();
		if (name.lastIndexOf('.') != -1) {
			return name.substring(0, name.lastIndexOf('.'));
		} else {
			return name;
		}
	}

}
