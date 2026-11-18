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
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
	// passed to the updater, which checks that the installer's signature chains up to this root CA and the signing certificate contains this EKU (with Azure Artifact Signing: the durable identity, which stays the same across certificate renewals)
	private static final String DURABLE_IDENTITY_EKU_PROPERTY = "cryptomator.integrationsWin.updateDurableIdentityEku";
	private static final String ROOT_CA_THUMBPRINT_PROPERTY = "cryptomator.integrationsWin.updateRootCaThumbprint";
	// file name of the updater installed next to Cryptomator.exe, see https://github.com/cryptomator/win-updater
	private static final String UPDATER_NAME_PROPERTY = "cryptomator.integrationsWin.updateHelperName";
	private static final Path SYSTEM32 = Path.of(Optional.ofNullable(System.getenv("SystemRoot")).orElse("C:\\Windows"), "System32");
	private static final String SHA256_DIGEST_PREFIX = "sha256:";
	private static final String READY = "READY";
	// the updater checks the revocation status of the installer's certificates online, which can be slow
	private static final Duration HANDSHAKE_TIMEOUT = Duration.ofSeconds(60);

	private final Supplier<String> installType;
	private final String durableIdentityEku;
	private final String rootCaThumbprint;
	private final String updaterName;

	public InstallerUpdateMechanism() {
		this(InstallerUpdateMechanism::readInstallType, System.getProperty(DURABLE_IDENTITY_EKU_PROPERTY), System.getProperty(ROOT_CA_THUMBPRINT_PROPERTY), System.getProperty(UPDATER_NAME_PROPERTY));
	}

	// visible for testing
	InstallerUpdateMechanism(Supplier<String> installType, String durableIdentityEku, String rootCaThumbprint, String updaterName) {
		this.installType = installType;
		this.durableIdentityEku = durableIdentityEku;
		this.rootCaThumbprint = rootCaThumbprint;
		this.updaterName = updaterName;
	}

	@Override
	protected DownloadUpdateInfo checkForUpdate(String currentVersion, LatestVersionResponse response) {
		if (isNullOrBlank(durableIdentityEku) || isNullOrBlank(rootCaThumbprint)) {
			LOG.warn("Properties {} and {} must be set to verify installers, not updating.", DURABLE_IDENTITY_EKU_PROPERTY, ROOT_CA_THUMBPRINT_PROPERTY);
			return null;
		}
		if (isNullOrBlank(updaterName)) {
			LOG.warn("Property {} must be set to install updates, not updating.", UPDATER_NAME_PROPERTY);
			return null;
		}
		// Currently, only x64 installers are released, which also run on ARM64 systems (via emulation). Re-enable once native ARM64 installers are published.
		//String arch = switch (System.getProperty("os.arch")) {
		//	case "aarch64", "arm64" -> "arm64";
		//	default -> "x64";
		//};
		String arch = "x64";
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
		// the updater verifies the installer before it reports to be ready, so this step covers starting it and the handshake
		return UpdateStep.of(Localization.get().getString("org.cryptomator.windows.update.installer.verifying"), () -> this.startUpdater(workDir, assetPath, updateInfo.asset().digest()));
	}

	private UpdateStep startUpdater(Path workDir, Path assetPath, String digest) throws IOException, InterruptedException {
		String selfPath = ProcessHandle.current().info().command().orElse("");
		if (!selfPath.toLowerCase(Locale.ROOT).endsWith(".exe")) {
			throw new UpdateFailedException("Cannot determine Cryptomator executable, current path: " + selfPath);
		}
		var command = updaterCommand(Path.of(selfPath), ProcessHandle.current().pid(), workDir, assetPath, digest);
		var processBuilder = new ProcessBuilder(command);
		// all paths are passed as absolute paths, so a fixed working directory avoids keeping any other directory in use
		processBuilder.directory(SYSTEM32.toFile());
		processBuilder.redirectError(ProcessBuilder.Redirect.DISCARD);
		LOG.info("Starting {} to apply the update, logging to {}.", command.getFirst(), workDir);
		Process process;
		try {
			process = processBuilder.start();
		} catch (IOException e) {
			throw new UpdateFailedException("Failed to start " + command.getFirst(), e);
		}
		awaitReady(process, HANDSHAKE_TIMEOUT);
		return UpdateStep.EXIT;
	}

	// visible for testing
	List<String> updaterCommand(Path cryptomatorExe, long pid, Path workDir, Path assetPath, String digest) throws UpdateFailedException {
		// DownloadUpdateMechanism only checks the download against SHA-256 digests (and already rejected malformed ones), but the updater requires one
		if (!digest.startsWith(SHA256_DIGEST_PREFIX)) {
			throw new UpdateFailedException("Asset has no SHA-256 digest: " + digest);
		}
		return List.of(cryptomatorExe.resolveSibling(updaterName).toAbsolutePath().toString(),
				"--wait-pid", String.valueOf(pid),
				"--installer", assetPath.toAbsolutePath().toString(),
				"--sha256", digest.substring(SHA256_DIGEST_PREFIX.length()),
				"--signer-eku", durableIdentityEku,
				"--root-thumbprint", rootCaThumbprint,
				"--relaunch", cryptomatorExe.toAbsolutePath().toString(),
				"--log-dir", workDir.toAbsolutePath().toString());
	}

	// Waits for the updater to report that it verified the installer and will install it once Cryptomator exits. Unless it is ready, the updater is terminated,
	// so that it doesn't install the update when Cryptomator exits for other reasons later on. Visible for testing.
	static void awaitReady(Process process, Duration timeout) throws UpdateFailedException, InterruptedException {
		boolean ready = false;
		try {
			// the stream must not be closed before the process is terminated, as closing it blocks while the reader thread waits for a line
			var readLine = new FutureTask<>(process.inputReader(StandardCharsets.US_ASCII)::readLine);
			Thread.ofVirtual().name("updater-handshake").start(readLine);
			String line = readLine.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
			ready = READY.equals(line);
			if (ready) {
				LOG.debug("Updater is ready.");
			} else if (line != null) {
				throw new UpdateFailedException("Unexpected output from updater: " + line);
			} else if (process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
				throw new UpdateFailedException(describeExitCode(process.exitValue()));
			} else {
				throw new UpdateFailedException("Updater closed its output without being ready.");
			}
		} catch (TimeoutException e) {
			throw new UpdateFailedException("Updater wasn't ready within " + timeout.toSeconds() + " s.", e);
		} catch (ExecutionException e) {
			throw new UpdateFailedException("Failed to read output of updater.", e.getCause());
		} finally {
			if (!ready) {
				process.destroyForcibly();
			}
		}
	}

	private static String describeExitCode(int exitCode) {
		String reason = switch (exitCode) {
			case 2 -> "invalid arguments";
			case 3 -> "Cryptomator process not found";
			case 4 -> "installer in use by another process";
			case 5 -> "installer digest mismatch";
			case 6 -> "installer signature invalid";
			case 8 -> "handshake failed";
			default -> "internal error";
		};
		return "Updater failed with exit code " + exitCode + ": " + reason + ".";
	}

	private static boolean isNullOrBlank(String s) {
		return s == null || s.isBlank();
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
