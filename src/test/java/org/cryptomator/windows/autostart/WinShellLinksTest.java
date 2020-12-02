package org.cryptomator.windows.autostart;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class WinShellLinksTest {

	private Path linkTarget;
	private Path shortcut;

	@BeforeEach
	public void setup(@TempDir Path tempDir) throws IOException {
		this.linkTarget = tempDir.resolve("link.target");
		Files.createFile(linkTarget);
	}

	@Test
	public void testShellLinkCreation(@TempDir Path tempDir) throws IOException {
		WinShellLinks winShellLinks = new WinShellLinks();
		shortcut = linkTarget.getParent().resolve("short.lnk");

		int returnCode = winShellLinks.createShortcut(linkTarget.toString(), shortcut.toString(), "asd");

		Assertions.assertEquals(0, returnCode);
		Assertions.assertTrue(Files.exists(shortcut));
	}

	@AfterEach
	public void cleanup() throws IOException {
		Files.deleteIfExists(linkTarget);
		if (shortcut != null) {
			Files.deleteIfExists(shortcut);
		}
	}
}
