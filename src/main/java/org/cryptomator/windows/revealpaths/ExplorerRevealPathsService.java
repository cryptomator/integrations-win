package org.cryptomator.windows.revealpaths;

import org.cryptomator.integrations.common.OperatingSystem;
import org.cryptomator.integrations.common.Priority;
import org.cryptomator.integrations.revealpaths.RevealFailedException;
import org.cryptomator.integrations.revealpaths.RevealPathsService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@Priority(100)
@OperatingSystem(OperatingSystem.Value.WINDOWS)
public class ExplorerRevealPathsService implements RevealPathsService {

	@Override
	public void reveal(Path p) throws RevealFailedException, NoSuchFileException {
		if(!Files.exists(p)) {
			throw new NoSuchFileException("File cannot be found: "+p.toString());
		}
		ProcessBuilder pb = new ProcessBuilder()
				.command("explorer", "/select,\"" + p.toString() + "\"");
		try {
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
	public String displayName() {
		return "Windows Explorer";
	}

	@Override
	public boolean isSupported() {
		return true;
	}
}
