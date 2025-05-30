package org.cryptomator.windows.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

public class NativeLibLoader {

	private static final Logger LOG = LoggerFactory.getLogger(NativeLibLoader.class);
	private static final String X64_LIB = "/integrations-x64.dll";
	private static final String ARM64_LIB = "/integrations-arm64.dll";
	private static volatile boolean loaded = false;

	/**
	 * Attempts to load the .dll file required for native calls.
	 * @throws UnsatisfiedLinkError If loading the library failed.
	 */
	public static synchronized void loadLib() {
		if (!loaded) {
			var arch = System.getProperty("os.arch");
			String LIBNAME = "";
			if (arch.contains("amd64")) {
				LOG.debug("Loading library for x86_64 architecture");
				LIBNAME = X64_LIB;
			} else if (arch.contains("aarch64")) {
				LOG.debug("Loading library for aarch64 architecture");
				LIBNAME = ARM64_LIB;
			}
			try (var dll = NativeLibLoader.class.getResourceAsStream(LIBNAME)) {
				Objects.requireNonNull(dll);
				Path tmpPath = Files.createTempFile("lib", ".dll");
				Files.copy(dll, tmpPath, StandardCopyOption.REPLACE_EXISTING);
				System.load(tmpPath.toString());
				loaded = true;
			} catch (NullPointerException e) {
				LOG.error("Did not find resource " + LIBNAME, e);
			} catch (IOException e) {
				LOG.error("Failed to copy " + LIBNAME + " to temp dir.", e);
			} catch (UnsatisfiedLinkError e) {
				LOG.error("Failed to load lib from " + LIBNAME, e);
			}
		}
	}

}
