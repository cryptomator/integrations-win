package org.cryptomator.windows.revealpath;

import org.cryptomator.integrations.revealpath.RevealFailedException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ExplorerRevealPathServiceTest {

	ExplorerRevealPathService service = new ExplorerRevealPathService();

	@Test
	public void testReveal(@TempDir Path tmpDir) throws IOException {
		Path foo = tmpDir.resolve("foo.txt");

		Files.createFile(foo);

		Assertions.assertDoesNotThrow(() -> service.reveal(foo));
		Assertions.assertTrue(Files.exists(foo));
	}

	@Test
	public void testRevealFailed(@TempDir Path tmpDir) {
		Path foo = tmpDir.resolve("bar.txt");

		Assertions.assertThrows(RevealFailedException.class, () -> service.reveal(foo));
	}
}
