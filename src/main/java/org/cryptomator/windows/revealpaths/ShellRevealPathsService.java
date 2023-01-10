package org.cryptomator.windows.revealpaths;

import org.cryptomator.integrations.common.OperatingSystem;
import org.cryptomator.integrations.common.Priority;
import org.cryptomator.integrations.revealpaths.RevealFailedException;
import org.cryptomator.integrations.revealpaths.RevealPathsService;
import org.cryptomator.windows.common.NativeLibLoader;
import org.cryptomator.windows.common.WinStrings;

import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;

@Priority(100)
@OperatingSystem(OperatingSystem.Value.WINDOWS)
public class ShellRevealPathsService implements RevealPathsService {

	@Override
	public void reveal(Path p) throws RevealFailedException, NoSuchFileException {
		reveal(p.getParent(), List.of(p.getFileName()));
	}

	//TODO: what if childs is empty?
	@Override
	public void reveal(Path directory, List<Path> childs) throws RevealFailedException, NoSuchFileException {
		var dir = directory.toAbsolutePath();
		if( childs.stream().anyMatch(c -> c.isAbsolute() || c.getNameCount() != 1)) {
			throw new IllegalArgumentException("All child paths must be relative and must have a name count of 1.");
		}
		var winStrings = childs.stream().map(c -> WinStrings.getNullTerminatedUTF16Representation(dir.resolve(c).toString())).toList();
		int max = winStrings.stream().mapToInt(arr -> arr.length).max().orElse(0);
		var nativeWinStrings = new byte [childs.size()][max];
		for(int i=0;i<winStrings.size();i++) {
			var src = winStrings.get(i);
			System.arraycopy(src,0, nativeWinStrings[i] ,0, src.length);
		}
		int result = Native.INSTANCE.reveal( //
				WinStrings.getNullTerminatedUTF16Representation(dir.toString()), //
				nativeWinStrings);
		if(result != 0) {
			throw new RevealFailedException("Return value is "+Integer.toHexString(result));
		}
	}

	@Override
	public String displayName() {
		return "Reveal Paths (Windows Shell API)";
	}

	@Override
	public boolean isSupported() {
		return true;
	}

	private static class Native {
		static final Native INSTANCE = new Native();

		private Native() {
			NativeLibLoader.loadLib();
		}

		public native int reveal(byte[] absoluteDirPath, byte [][] childsToReveal);
	}
}
