package org.cryptomator.windows.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class NativeLibLoader {

	private static final Logger LOG = LoggerFactory.getLogger(NativeLibLoader.class);
	private static final String LIB = "/integrations.dll";
	private static volatile boolean loaded = false;

	/**
	 * Attempts to load the .dll file required for native calls.
	 * @throws UnsatisfiedLinkError If loading the library failed.
	 */
	public static synchronized void loadLib() {
		if (!loaded) {
			try (var dll = NativeLibLoader.class.getResourceAsStream(LIB)) {
				Path tmpPath = Files.createTempFile("lib", ".dll");
				Files.copy(dll, tmpPath, StandardCopyOption.REPLACE_EXISTING);
				System.load(tmpPath.toString());
				loaded = true;
			} catch (IOException e) {
				LOG.error("Failed to copy " + LIB + " to temp dir.", e);
			} catch (UnsatisfiedLinkError | RuntimeException e) {
				LOG.error("Failed to load lib from " + LIB, e);
			}
		}
	}

}
