package org.cryptomator.windows.revealpaths;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ExplorerRevealPathsServiceTest {

	ExplorerRevealPathsService service = new ExplorerRevealPathsService();

	@Test
	public void testReveal(@TempDir Path tmpDir) throws IOException {
		Path foo = tmpDir.resolve("foo.txt");

		Files.createFile(foo);

		Assertions.assertDoesNotThrow(() -> service.reveal(foo));
		Assertions.assertTrue(Files.exists(foo));
	}
}
