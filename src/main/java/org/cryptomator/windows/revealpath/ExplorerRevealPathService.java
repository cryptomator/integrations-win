package org.cryptomator.windows.revealpath;

import org.cryptomator.integrations.common.OperatingSystem;
import org.cryptomator.integrations.common.Priority;
import org.cryptomator.integrations.revealpath.RevealFailedException;
import org.cryptomator.integrations.revealpath.RevealPathService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.TimeUnit;

@Priority(100)
@OperatingSystem(OperatingSystem.Value.WINDOWS)
public class ExplorerRevealPathService implements RevealPathService {

	@Override
	public void reveal(Path p) throws RevealFailedException {
		try {
			var attrs = Files.readAttributes(p, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
			ProcessBuilder pb = new ProcessBuilder();
			if(attrs.isDirectory()) {
				pb.command("explorer.exe",p.toString());
			} else {
				pb.command("explorer.exe ","/select,",p.toString());
			}

			var process = pb.start();
			if (process.waitFor(5000, TimeUnit.MILLISECONDS)) {
				int exitValue = process.exitValue();
				if (process.exitValue() != 1) { //explorer.exe seems to return always 1
					throw new RevealFailedException("Explorer.exe exited with value " + exitValue);
				}
			}
		} catch (IOException e) {
			throw new RevealFailedException(e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RevealFailedException(e);
		}
	}

	@Override
	public boolean isSupported() {
		return true;
	}
}
