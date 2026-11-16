package org.cryptomator.windows.update;

import org.cryptomator.integrations.update.DownloadUpdateInfo;
import org.cryptomator.integrations.update.DownloadUpdateMechanism.Asset;
import org.cryptomator.integrations.update.DownloadUpdateMechanism.LatestVersion;
import org.cryptomator.integrations.update.DownloadUpdateMechanism.LatestVersionResponse;
import org.cryptomator.integrations.update.UpdateFailedException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

public class InstallerUpdateMechanismTest {

	private static final Asset DMG = new Asset("Cryptomator-1.19.3-x64.dmg", "sha256:00", 1, "https://example.com/Cryptomator-1.19.3-x64.dmg");
	private static final Asset EXE = new Asset("Cryptomator-1.19.3-x64.exe", "sha256:00", 1, "https://example.com/Cryptomator-1.19.3-x64.exe");
	private static final Asset MSI = new Asset("Cryptomator-1.19.3-x64.msi", "sha256:00", 1, "https://example.com/Cryptomator-1.19.3-x64.msi");
	private static final LatestVersionResponse RESPONSE = new LatestVersionResponse(new LatestVersion("1.19.4", "1.19.3", "1.19.2"), List.of(DMG, EXE, MSI));
	private static final String EKU = "1.2.3.4";
	private static final String ROOT_THUMBPRINT = "0000000000000000000000000000000000000000";
	private static final String UPDATER_NAME = "Cryptomator-updater.exe";

	@ParameterizedTest
	@CsvSource({"EXE, Cryptomator-1.19.3-x64.exe", "MSI, Cryptomator-1.19.3-x64.msi", "msi, Cryptomator-1.19.3-x64.msi"})
	public void testAssetMatchesInstallType(String installType, String expectedAsset) {
		var mechanism = new InstallerUpdateMechanism(() -> installType, EKU, ROOT_THUMBPRINT, UPDATER_NAME);

		var updateInfo = mechanism.checkForUpdate("1.19.2", RESPONSE);

		Assertions.assertNotNull(updateInfo);
		Assertions.assertEquals("1.19.3", updateInfo.version());
		Assertions.assertEquals(expectedAsset, updateInfo.asset().name());
		Assertions.assertSame(mechanism, updateInfo.updateMechanism());
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {"winget", "chocolatey", "managed"})
	public void testNoUpdateIfNotSelfManaged(String installType) {
		var mechanism = new InstallerUpdateMechanism(() -> installType, EKU, ROOT_THUMBPRINT, UPDATER_NAME);

		var updateInfo = mechanism.checkForUpdate("1.19.2", RESPONSE);

		Assertions.assertNull(updateInfo);
	}

	@ParameterizedTest
	@CsvSource({"1.19.3", "1.20.0"})
	public void testNoUpdateIfUpToDate(String currentVersion) {
		var mechanism = new InstallerUpdateMechanism(() -> "MSI", EKU, ROOT_THUMBPRINT, UPDATER_NAME);

		var updateInfo = mechanism.checkForUpdate(currentVersion, RESPONSE);

		Assertions.assertNull(updateInfo);
	}

	@Test
	public void testNoUpdateIfAssetMissing() {
		var mechanism = new InstallerUpdateMechanism(() -> "MSI", EKU, ROOT_THUMBPRINT, UPDATER_NAME);
		var response = new LatestVersionResponse(RESPONSE.latestVersion(), List.of(DMG, EXE));

		var updateInfo = mechanism.checkForUpdate("1.19.2", response);

		Assertions.assertNull(updateInfo);
	}

	@ParameterizedTest
	@CsvSource(value = {
			"NULL, " + ROOT_THUMBPRINT + ", " + UPDATER_NAME,
			"1.2.3.4, NULL, " + UPDATER_NAME,
			"'', " + ROOT_THUMBPRINT + ", " + UPDATER_NAME,
			"1.2.3.4, ' ', " + UPDATER_NAME,
			"1.2.3.4, " + ROOT_THUMBPRINT + ", NULL",
			"1.2.3.4, " + ROOT_THUMBPRINT + ", ' '"}, nullValues = "NULL")
	public void testNoUpdateIfNotConfigured(String eku, String rootThumbprint, String updaterName) {
		var mechanism = new InstallerUpdateMechanism(() -> "MSI", eku, rootThumbprint, updaterName);

		var updateInfo = mechanism.checkForUpdate("1.19.2", RESPONSE);

		Assertions.assertNull(updateInfo);
	}

	@Test
	public void testUpdaterCommand(@TempDir Path workDir) throws UpdateFailedException {
		var mechanism = new InstallerUpdateMechanism(() -> "MSI", EKU, ROOT_THUMBPRINT, UPDATER_NAME);
		var cryptomatorExe = Path.of("C:\\Program Files\\Cryptomator\\Cryptomator.exe");
		var assetPath = workDir.resolve(MSI.name());
		var sha256 = "ab".repeat(32);

		var command = mechanism.updaterCommand(cryptomatorExe, 42, workDir, assetPath, "sha256:" + sha256);

		Assertions.assertEquals(List.of("C:\\Program Files\\Cryptomator\\Cryptomator-updater.exe",
				"--wait-pid", "42",
				"--installer", assetPath.toString(),
				"--sha256", sha256,
				"--signer-eku", EKU,
				"--root-thumbprint", ROOT_THUMBPRINT,
				"--relaunch", cryptomatorExe.toString(),
				"--log-dir", workDir.toString()), command);
	}

	@Test
	public void testUpdaterCommandRequiresSha256Digest(@TempDir Path workDir) {
		var mechanism = new InstallerUpdateMechanism(() -> "MSI", EKU, ROOT_THUMBPRINT, UPDATER_NAME);
		var cryptomatorExe = Path.of("C:\\Program Files\\Cryptomator\\Cryptomator.exe");

		Assertions.assertThrows(UpdateFailedException.class, () -> mechanism.updaterCommand(cryptomatorExe, 42, workDir, workDir.resolve(MSI.name()), "sha512:" + "ab".repeat(64)));
	}

	@Test
	public void testSecondStepFailsWithoutUpdater(@TempDir Path workDir) throws IOException, InterruptedException {
		// the tests run in java.exe, next to which there is no updater
		var mechanism = new InstallerUpdateMechanism(() -> "MSI", EKU, ROOT_THUMBPRINT, UPDATER_NAME);
		var assetPath = Files.writeString(workDir.resolve(MSI.name()), "not an installer");

		var step = mechanism.secondStep(workDir, assetPath, new DownloadUpdateInfo(mechanism, "1.19.3", MSI));
		step.start();
		step.await();

		var e = Assertions.assertThrows(UpdateFailedException.class, step::nextStep);
		Assertions.assertTrue(e.getMessage().startsWith("Failed to start"), e.getMessage());
	}

	@Test
	public void testAwaitReady() throws IOException {
		var process = startUpdaterStandIn("echo", "READY");

		Assertions.assertDoesNotThrow(() -> InstallerUpdateMechanism.awaitReady(process, Duration.ofSeconds(10)));
	}

	@Test
	public void testAwaitReadyReportsExitCode() throws IOException {
		var process = startUpdaterStandIn("exit", "6");

		var e = Assertions.assertThrows(UpdateFailedException.class, () -> InstallerUpdateMechanism.awaitReady(process, Duration.ofSeconds(10)));
		Assertions.assertTrue(e.getMessage().contains("exit code 6"), e.getMessage());
	}

	@Test
	public void testAwaitReadyTerminatesUpdaterOnTimeout() throws IOException, InterruptedException {
		var process = startUpdaterStandIn("ping", "-n", "6", "127.0.0.1", ">nul");

		var e = Assertions.assertThrows(UpdateFailedException.class, () -> InstallerUpdateMechanism.awaitReady(process, Duration.ofMillis(200)));
		Assertions.assertInstanceOf(TimeoutException.class, e.getCause());
		Assertions.assertTrue(process.waitFor(5, TimeUnit.SECONDS), "updater still running");
	}

	// runs the given cmd.exe command in place of the updater
	private static Process startUpdaterStandIn(String... cmdCommand) throws IOException {
		var command = Stream.concat(Stream.of("cmd.exe", "/d", "/c"), Stream.of(cmdCommand)).toList();
		return new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
	}

}
