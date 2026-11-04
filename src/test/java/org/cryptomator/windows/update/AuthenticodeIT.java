package org.cryptomator.windows.update;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
	private static final String SKYMATIC_DURABLE_IDENTITY = "1.3.6.1.4.1.311.97.986645502.209118791.449271898.355477593";
	private static final String MICROSOFT_ROOT_THUMBPRINT = "F40042E2E5F7E8EF8189FED15519AECE42C3BFA2"; // Microsoft Identity Verification Root Certificate Authority 2020

	// signed by Skymatic GmbH with a timestamped signature, whose certificate is already expired
	private static Path signedDll;

	@BeforeAll
	public static void setup() throws URISyntaxException {
		signedDll = Path.of(AuthenticodeIT.class.getResource("signed-integrations-x64.dll").toURI());
	}

	@Test
	public void testUnknownFileFormat(@TempDir Path tmpDir) throws IOException {
		var file = Files.writeString(tmpDir.resolve("not an installer (x).msi"), "not an installer");

		var e = Assertions.assertThrows(SignatureException.class, () -> Authenticode.getVerifiedCertificateChain(file));
		Assertions.assertTrue(e.getMessage().contains(TRUST_E_SUBJECT_FORM_UNKNOWN), e.getMessage());
	}

	@Test
	public void testCertificateChain() throws SignatureException {
		var chain = Authenticode.getVerifiedCertificateChain(signedDll);

		Assertions.assertEquals(4, chain.size());
		Assertions.assertEquals("CN=Skymatic GmbH,O=Skymatic GmbH,L=Bonn,C=DE", chain.getFirst().getSubjectX500Principal().getName());
		Assertions.assertEquals("CN=Microsoft Identity Verification Root Certificate Authority 2020,O=Microsoft Corporation,C=US", chain.getLast().getSubjectX500Principal().getName());
	}

	@ParameterizedTest
	@ValueSource(strings = {MICROSOFT_ROOT_THUMBPRINT, "f40042e2e5f7e8ef8189fed15519aece42c3bfa2"})
	public void testExpectedSigner(String rootThumbprint) throws SignatureException {
		var signer = Authenticode.verify(signedDll, SKYMATIC_DURABLE_IDENTITY, rootThumbprint);

		Assertions.assertEquals("CN=Skymatic GmbH,O=Skymatic GmbH,L=Bonn,C=DE", signer.getSubjectX500Principal().getName());
	}

	@Test
	public void testUnexpectedSignerEku() {
		var e = Assertions.assertThrows(SignatureException.class, () -> Authenticode.verify(signedDll, "1.3.6.1.4.1.311.97.1.2.3.4", MICROSOFT_ROOT_THUMBPRINT));
		Assertions.assertTrue(e.getMessage().contains("lacks extended key usage"), e.getMessage());
	}

	@Test
	public void testUnexpectedRoot() {
		var e = Assertions.assertThrows(SignatureException.class, () -> Authenticode.verify(signedDll, SKYMATIC_DURABLE_IDENTITY, "0000000000000000000000000000000000000000"));
		Assertions.assertTrue(e.getMessage().contains("unexpected root"), e.getMessage());
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

		var e = Assertions.assertThrows(SignatureException.class, () -> Authenticode.getVerifiedCertificateChain(file));
		Assertions.assertTrue(e.getMessage().contains(TRUST_E_BAD_DIGEST), e.getMessage());
	}

}
