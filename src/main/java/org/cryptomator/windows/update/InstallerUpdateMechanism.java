package org.cryptomator.windows.update;

import org.cryptomator.integrations.common.LocalizedDisplayName;
import org.cryptomator.integrations.common.OperatingSystem;
import org.cryptomator.integrations.update.DownloadUpdateInfo;
import org.cryptomator.integrations.update.DownloadUpdateMechanism;
import org.cryptomator.integrations.update.UpdateFailedException;
import org.cryptomator.integrations.update.UpdateMechanism;
import org.cryptomator.integrations.update.UpdateStep;
import org.cryptomator.windows.common.Localization;
import org.cryptomator.windows.common.RegistryKey;
import org.cryptomator.windows.common.RegistryValueException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SignatureException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

import static org.cryptomator.windows.capi.common.Windows_h.ERROR_FILE_NOT_FOUND;

/**
 * Updates Cryptomator by downloading and running the same kind of installer (EXE bundle or MSI) that was used to install it, unless it is managed by a package manager.
 */
@OperatingSystem(OperatingSystem.Value.WINDOWS)
@LocalizedDisplayName(bundle = "WinIntegrationsBundle", key = "org.cryptomator.windows.update.installer.displayName")
public class InstallerUpdateMechanism extends DownloadUpdateMechanism {

	private static final Logger LOG = LoggerFactory.getLogger(InstallerUpdateMechanism.class);
	// written by the Cryptomator installer. Package managers (winget, Chocolatey, ...) set the INSTALLTYPE installer property to a different value, opting out of self-updates.
	private static final String INSTALL_TYPE_REG_KEY = "SOFTWARE\\Skymatic GmbH\\Cryptomator";
	private static final String INSTALL_TYPE_REG_VALUE = "InstallType";
	private static final String EXPECTED_SIGNER = "Skymatic GmbH"; // common name of the code signing certificate
	private static final Path SYSTEM32 = Path.of(Optional.ofNullable(System.getenv("SystemRoot")).orElse("C:\\Windows"), "System32");

	private final Supplier<String> installType;

	public InstallerUpdateMechanism() {
		this(InstallerUpdateMechanism::readInstallType);
	}

	// visible for testing
	InstallerUpdateMechanism(Supplier<String> installType) {
		this.installType = installType;
	}

	@Override
	protected DownloadUpdateInfo checkForUpdate(String currentVersion, LatestVersionResponse response) {
		String arch = switch (System.getProperty("os.arch")) {
			case "aarch64", "arm64" -> "arm64";
			default -> "x64";
		};
		// stick to the installer type used for the current installation, so that the entry in "Apps & Features" stays accurate
		String installType = this.installType.get();
		String extension = switch (installType == null ? "" : installType.toUpperCase(Locale.ROOT)) {
			case "EXE" -> ".exe";
			case "MSI" -> ".msi";
			default -> null;
		};
		if (extension == null) {
			LOG.info("Install type is {}, not updating.", installType);
			return null;
		}
		String suffix = "-" + arch + extension;
		var updateVersion = response.latestVersion().winVersion();
		var asset = response.assets().stream().filter(a -> a.name().endsWith(suffix)).findAny().orElse(null);

		if (updateVersion != null && asset != null && UpdateMechanism.isUpdateAvailable(updateVersion, currentVersion)) {
			return new DownloadUpdateInfo(this, updateVersion, asset);
		} else {
			return null;
		}
	}

	@Override
	public UpdateStep secondStep(Path workDir, Path assetPath, DownloadUpdateInfo updateInfo) {
		return UpdateStep.of(Localization.get().getString("org.cryptomator.windows.update.installer.verifying"), () -> this.verify(workDir, assetPath));
	}

	private UpdateStep verify(Path workDir, Path assetPath) throws IOException {
		String signer;
		try {
			signer = Authenticode.getVerifiedSignerName(assetPath);
		} catch (SignatureException e) {
			LOG.error("Checking signature of {} failed.", assetPath, e);
			throw new UpdateFailedException("Invalid Signature.", e);
		}
		if (!EXPECTED_SIGNER.equals(signer)) {
			LOG.error("Installer {} is signed by unexpected signer {}.", assetPath, signer);
			throw new UpdateFailedException("Invalid Signature.");
		}
		LOG.debug("Verified installer {}, signed by {}.", assetPath, signer);
		return UpdateStep.of(Localization.get().getString("org.cryptomator.windows.update.installer.restarting"), () -> this.restart(workDir, assetPath));
	}

	private UpdateStep restart(Path workDir, Path assetPath) throws IOException {
		String selfPath = ProcessHandle.current().info().command().orElse("");
		if (!selfPath.toLowerCase(Locale.ROOT).endsWith(".exe")) {
			throw new UpdateFailedException("Cannot determine Cryptomator executable, current path: " + selfPath);
		}
		Path executable = Path.of(selfPath);
		String installerType = assetPath.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".msi") ? "msi" : "exe";
		LOG.info("Restarting to apply update in {} now...", workDir);
		// System tools are called by full path, as PATH may contain look-alikes (e.g. GNU find). `timeout` fails if stdin is redirected, hence `ping` is used to sleep.
		// INSTALLDIR is only supported by the MSI, the bundle always installs to its default location.
		String script = """
				@echo off
				set "SYS32=%SystemRoot%\\System32"
				:waitForExit
				"%SYS32%\\tasklist.exe" /NH /FI "PID eq %CRYPTOMATOR_PID%" 2>nul | "%SYS32%\\find.exe" " %CRYPTOMATOR_PID% " >nul
				if not errorlevel 1 (
				  "%SYS32%\\PING.EXE" -n 2 127.0.0.1 >nul
				  goto waitForExit
				)
				echo Running %INSTALLER_TYPE% installer %INSTALLER_PATH%
				if /i "%INSTALLER_TYPE%"=="msi" (
				  start "" /wait "%SYS32%\\msiexec.exe" /i "%INSTALLER_PATH%" /passive /norestart INSTALLDIR="%CRYPTOMATOR_INSTALL_DIR%" /l*v "%~dp0installer.log"
				) else (
				  start "" /wait "%INSTALLER_PATH%" /passive /norestart /log "%~dp0installer.log"
				)
				echo Installer exited with code %ERRORLEVEL%
				start "" "%CRYPTOMATOR_EXE%"
				""".replace("\n", "\r\n");
		Path scriptPath = workDir.resolve("install.cmd");
		Files.writeString(scriptPath, script, StandardCharsets.US_ASCII, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW);
		var processBuilder = new ProcessBuilder(List.of(SYSTEM32.resolve("cmd.exe").toString(), "/c", scriptPath.toString()));
		processBuilder.directory(workDir.toFile());
		processBuilder.redirectErrorStream(true);
		processBuilder.redirectOutput(workDir.resolve("install.log").toFile());
		processBuilder.environment().put("CRYPTOMATOR_PID", String.valueOf(ProcessHandle.current().pid()));
		processBuilder.environment().put("CRYPTOMATOR_EXE", executable.toString());
		processBuilder.environment().put("CRYPTOMATOR_INSTALL_DIR", executable.getParent().toString());
		processBuilder.environment().put("INSTALLER_TYPE", installerType);
		processBuilder.environment().put("INSTALLER_PATH", assetPath.toString());
		processBuilder.start();

		return UpdateStep.EXIT;
	}

	private static String readInstallType() {
		try {
			return RegistryKey.HKEY_LOCAL_MACHINE.getStringValue(INSTALL_TYPE_REG_KEY, INSTALL_TYPE_REG_VALUE, false);
		} catch (RegistryValueException e) {
			if (e.getSystemErrorCode() == ERROR_FILE_NOT_FOUND()) {
				LOG.debug("Install type not found in registry.");
			} else {
				LOG.warn("Failed to read install type from registry.", e);
			}
			return null;
		}
	}

}
