package org.cryptomator.windows.update;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SignatureException;

public class AuthenticodeIT {

	private static final String TRUST_E_SUBJECT_FORM_UNKNOWN = "0x800B0003";
	private static final String TRUST_E_BAD_DIGEST = "0x80096010";

	// signed by Skymatic GmbH with a timestamped signature, whose certificate is already expired
	private static Path signedDll;

	@BeforeAll
	public static void setup() throws URISyntaxException {
		signedDll = Path.of(AuthenticodeIT.class.getResource("signed-integrations-x64.dll").toURI());
	}

	@Test
	public void testUnknownFileFormat(@TempDir Path tmpDir) throws IOException {
		var file = Files.writeString(tmpDir.resolve("not an installer (x).msi"), "not an installer");

		var e = Assertions.assertThrows(SignatureException.class, () -> Authenticode.getVerifiedSignerName(file));
		Assertions.assertTrue(e.getMessage().contains(TRUST_E_SUBJECT_FORM_UNKNOWN), e.getMessage());
	}

	@Test
	public void testSignedFile() throws SignatureException {
		var signer = Authenticode.getVerifiedSignerName(signedDll);

		Assertions.assertEquals("Skymatic GmbH", signer);
	}

	@Test
	public void testTamperedFile(@TempDir Path tmpDir) throws IOException {
		var file = Files.copy(signedDll, tmpDir.resolve("tampered-integrations-x64.dll"));
		try (var ch = FileChannel.open(file, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
			long pos = ch.size() / 2; // within the signed content, the signature is located at the end
			var buf = ByteBuffer.allocate(1);
			ch.read(buf, pos);
			buf.put(0, (byte) ~buf.get(0));
			ch.write(buf.rewind(), pos);
		}

		var e = Assertions.assertThrows(SignatureException.class, () -> Authenticode.getVerifiedSignerName(file));
		Assertions.assertTrue(e.getMessage().contains(TRUST_E_BAD_DIGEST), e.getMessage());
	}

}
