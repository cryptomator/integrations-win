package org.cryptomator.windows.revealpaths;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public class ShellRevealPathsServiceTest {

	@Test
	public void testRevealing(@TempDir Path tmpDir) throws IOException {
		Path foo = tmpDir.resolve("foo.txt");
		Path bar = tmpDir.resolve("bar.txt");
		Path baz = tmpDir.resolve("baz.txt");

		Files.createFile(foo);
		Files.createFile(bar);
		Files.createFile(baz);

		ShellRevealPathsService revealService = new ShellRevealPathsService();
		Assertions.assertDoesNotThrow(() -> revealService.reveal(tmpDir, Stream.of(foo,bar).map(Path::getFileName).toList()));
	}

	@Test
	public void testRevealingNoChilds(@TempDir Path tmpDir) throws IOException {
		Path foo = tmpDir.resolve("foo.txt");

		Files.createFile(foo);

		ShellRevealPathsService revealService = new ShellRevealPathsService();
		Assertions.assertDoesNotThrow(() -> revealService.reveal(tmpDir, List.of()));
	}
}
