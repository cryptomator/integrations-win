package org.cryptomator.windows.common;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class WinStrings {

	private WinStrings() {}

	public static byte[] getNullTerminatedUTF16Representation(String source) {
		byte[] bytes = source.getBytes(StandardCharsets.UTF_16LE);
		return Arrays.copyOf(bytes, bytes.length + 2); // add double-width null terminator 0x00 0x00
	}
}
