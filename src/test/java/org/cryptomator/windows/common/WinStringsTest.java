package org.cryptomator.windows.common;

import org.cryptomator.windows.autostart.WinShellLinksTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.converter.ConvertWith;
import org.junit.jupiter.params.provider.CsvSource;

public class WinStringsTest {


	@ParameterizedTest
	@CsvSource(value = { // convert utf16-le string to hex with https://dencode.com/en/string/hex, append null terminator
			"foo, '66 00 6f 00 6f 00 00 00'",
			"bar, '62 00 61 00 72 00 00 00'"
	})
	public void testGetNullTerminatedUTF16Representation(String input, @ConvertWith(WinShellLinksTest.ByteArrayConverter.class) byte[] expected) {
		var result = WinStrings.getNullTerminatedUTF16Representation(input);

		Assertions.assertArrayEquals(expected, result);
	}
}
