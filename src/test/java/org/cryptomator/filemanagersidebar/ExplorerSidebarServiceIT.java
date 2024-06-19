package org.cryptomator.filemanagersidebar;

import org.cryptomator.integrations.sidebar.SidebarServiceException;
import org.cryptomator.windows.sidebar.ExplorerSidebarService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

public class ExplorerSidebarServiceIT {

	@TempDir
	Path base;

	@Test
	@DisplayName("Integrates a temp dir for 20s into the file explorer sidebar")
	@Disabled
	public void testExplorerSidebarIntegration() throws IOException, InterruptedException, SidebarServiceException {
		var p = base.resolve("integration-win-testVault");
		Files.createDirectory(p);
		Files.createFile(p.resolve("firstLevel.file"));
		var sub = Files.createDirectory(p.resolve("subdir"));
		Files.createFile(sub.resolve("secondLevel.file"));
		var entry = new ExplorerSidebarService().add(p, "integration-win-tempDir");

		Thread.sleep(Duration.ofSeconds(20));

		entry.remove();
	}
}
