package org.cryptomator.windows.autostart;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.converter.ArgumentConversionException;
import org.junit.jupiter.params.converter.ConvertWith;
import org.junit.jupiter.params.converter.SimpleArgumentConverter;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.regex.Pattern;

public class WinShellLinksTest {

	private Path linkTarget;
	private Path shortcut;

	@BeforeEach
	public void setup(@TempDir Path tempDir) throws IOException {
		this.linkTarget = tempDir.resolve("link.target");
		Files.createFile(linkTarget);
	}

	@Test
	public void testGetStartupFolderPath() {
		WinShellLinks winShellLinks = new WinShellLinks();
		Assertions.assertDoesNotThrow(() -> winShellLinks.getPathToStartupFolder());
	}

	@Test
	public void testShellLinkCreation() {
		WinShellLinks winShellLinks = new WinShellLinks();
		shortcut = linkTarget.getParent().resolve("short.lnk");

		int returnCode = winShellLinks.createShortcut(linkTarget.toString(), shortcut.toString(), "asd");

		Assertions.assertEquals(0, returnCode);
		Assertions.assertTrue(Files.exists(shortcut));
	}

	@ParameterizedTest
	@CsvSource(value = { // convert utf16-le string to hex with https://dencode.com/en/string/hex, append null terminator
			"foo, '66 00 6f 00 6f 00 00 00'",
			"bar, '62 00 61 00 72 00 00 00'"
	})
	public void testGetNullTerminatedUTF16Representation(String input, @ConvertWith(ByteArrayConverter.class) byte[] expected) {
		WinShellLinks winShellLinks = new WinShellLinks();

		var result = winShellLinks.getNullTerminatedUTF16Representation(input);

		Assertions.assertArrayEquals(expected, result);
	}

	@ParameterizedTest
	@CsvSource(value = {
			"'00', 0",
			"'01', 1",
			"'02', 2",
			"'0A', 10",
			"'7F', 127",
			"'80', 128",
			"'FF', 255"
	})
	public void testByteArrayConverter(@ConvertWith(ByteArrayConverter.class) byte[] input, int firstByte) {
		Assertions.assertEquals((byte) firstByte, input[0]);
	}

	@AfterEach
	public void cleanup() throws IOException {
		Files.deleteIfExists(linkTarget);
		if (shortcut != null) {
			Files.deleteIfExists(shortcut);
		}
	}

	public static class ByteArrayConverter extends SimpleArgumentConverter {

		@Override
		protected byte[] convert(Object source, Class<?> targetType) throws ArgumentConversionException {
			assert source instanceof String;
			assert byte[].class.isAssignableFrom(targetType);
			return convertString((String) source);
		}

		private byte[] convertString(String source) {
			var intStream = Arrays.stream(source.split(Pattern.quote(" "))).mapToInt(s -> Integer.valueOf(s, 16));
			ByteArrayOutputStream result = new ByteArrayOutputStream();
			intStream.forEachOrdered(result::write);
			return result.toByteArray();
		}
	}
}
