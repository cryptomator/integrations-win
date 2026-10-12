package org.cryptomator.windows.update;

import org.cryptomator.integrations.update.DownloadUpdateMechanism.Asset;
import org.cryptomator.integrations.update.DownloadUpdateMechanism.LatestVersion;
import org.cryptomator.integrations.update.DownloadUpdateMechanism.LatestVersionResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

public class InstallerUpdateMechanismTest {

	private static final Asset DMG = new Asset("Cryptomator-1.19.3-x64.dmg", "sha256:00", 1, "https://example.com/Cryptomator-1.19.3-x64.dmg");
	private static final Asset EXE = new Asset("Cryptomator-1.19.3-x64.exe", "sha256:00", 1, "https://example.com/Cryptomator-1.19.3-x64.exe");
	private static final Asset MSI = new Asset("Cryptomator-1.19.3-x64.msi", "sha256:00", 1, "https://example.com/Cryptomator-1.19.3-x64.msi");
	private static final LatestVersionResponse RESPONSE = new LatestVersionResponse(new LatestVersion("1.19.4", "1.19.3", "1.19.2"), List.of(DMG, EXE, MSI));

	@BeforeAll
	public static void setup() {
		Assumptions.assumeTrue(System.getProperty("os.arch").equals("amd64"), "Test assets only exist for x64");
	}

	@ParameterizedTest
	@CsvSource({"EXE, Cryptomator-1.19.3-x64.exe", "MSI, Cryptomator-1.19.3-x64.msi", "msi, Cryptomator-1.19.3-x64.msi"})
	public void testAssetMatchesInstallType(String installType, String expectedAsset) {
		var mechanism = new InstallerUpdateMechanism(() -> installType);

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
		var mechanism = new InstallerUpdateMechanism(() -> installType);

		var updateInfo = mechanism.checkForUpdate("1.19.2", RESPONSE);

		Assertions.assertNull(updateInfo);
	}

	@ParameterizedTest
	@CsvSource({"1.19.3", "1.20.0"})
	public void testNoUpdateIfUpToDate(String currentVersion) {
		var mechanism = new InstallerUpdateMechanism(() -> "MSI");

		var updateInfo = mechanism.checkForUpdate(currentVersion, RESPONSE);

		Assertions.assertNull(updateInfo);
	}

	@Test
	public void testNoUpdateIfAssetMissing() {
		var mechanism = new InstallerUpdateMechanism(() -> "MSI");
		var response = new LatestVersionResponse(RESPONSE.latestVersion(), List.of(DMG, EXE));

		var updateInfo = mechanism.checkForUpdate("1.19.2", response);

		Assertions.assertNull(updateInfo);
	}

}
