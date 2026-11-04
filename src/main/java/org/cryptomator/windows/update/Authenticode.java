package org.cryptomator.windows.update;

import java.io.ByteArrayInputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import static java.lang.foreign.MemorySegment.NULL;
import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;

/**
 * Verifies Authenticode signatures of files using <a href="https://learn.microsoft.com/en-us/windows/win32/api/wintrust/nf-wintrust-winverifytrust">WinVerifyTrust</a>.
 */
final class Authenticode {

	// Hand-written bindings, see WinTrust.h, SoftPub.h and wincrypt.h. The DLL is loaded by absolute path to prevent DLL search order hijacking.
	private static final Path SYSTEM32 = Path.of(Optional.ofNullable(System.getenv("SystemRoot")).orElse("C:\\Windows"), "System32");
	private static final Linker LINKER = Linker.nativeLinker();
	private static final SymbolLookup WINTRUST = SymbolLookup.libraryLookup(SYSTEM32.resolve("wintrust.dll"), Arena.global());

	private static final MethodHandle WIN_VERIFY_TRUST = downcall(WINTRUST, "WinVerifyTrust", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
	private static final MethodHandle WT_HELPER_PROV_DATA_FROM_STATE_DATA = downcall(WINTRUST, "WTHelperProvDataFromStateData", FunctionDescriptor.of(ADDRESS, ADDRESS));
	private static final MethodHandle WT_HELPER_GET_PROV_SIGNER_FROM_CHAIN = downcall(WINTRUST, "WTHelperGetProvSignerFromChain", FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT));
	private static final MethodHandle WT_HELPER_GET_PROV_CERT_FROM_CHAIN = downcall(WINTRUST, "WTHelperGetProvCertFromChain", FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT));

	private static final int WTD_UI_NONE = 2;
	private static final int WTD_REVOKE_WHOLECHAIN = 0x1;
	private static final int WTD_CHOICE_FILE = 1;
	private static final int WTD_STATEACTION_VERIFY = 0x1;
	private static final int WTD_STATEACTION_CLOSE = 0x2;
	private static final int WTD_REVOCATION_CHECK_CHAIN_EXCLUDE_ROOT = 0x80;

	private static final StructLayout GUID = MemoryLayout.structLayout(
			JAVA_INT.withName("Data1"),
			JAVA_SHORT.withName("Data2"),
			JAVA_SHORT.withName("Data3"),
			MemoryLayout.sequenceLayout(8, JAVA_BYTE).withName("Data4"));

	private static final StructLayout WINTRUST_FILE_INFO = MemoryLayout.structLayout(
			JAVA_INT.withName("cbStruct"),
			MemoryLayout.paddingLayout(4),
			ADDRESS.withName("pcwszFilePath"),
			ADDRESS.withName("hFile"),
			ADDRESS.withName("pgKnownSubject"));

	private static final StructLayout WINTRUST_DATA = MemoryLayout.structLayout(
			JAVA_INT.withName("cbStruct"),
			MemoryLayout.paddingLayout(4),
			ADDRESS.withName("pPolicyCallbackData"),
			ADDRESS.withName("pSIPClientData"),
			JAVA_INT.withName("dwUIChoice"),
			JAVA_INT.withName("fdwRevocationChecks"),
			JAVA_INT.withName("dwUnionChoice"),
			MemoryLayout.paddingLayout(4),
			ADDRESS.withName("pFile"),
			JAVA_INT.withName("dwStateAction"),
			MemoryLayout.paddingLayout(4),
			ADDRESS.withName("hWVTStateData"),
			ADDRESS.withName("pwszURLReference"),
			JAVA_INT.withName("dwProvFlags"),
			JAVA_INT.withName("dwUIContext"),
			ADDRESS.withName("pSignatureSettings"));

	// only the leading fields are declared
	private static final StructLayout CRYPT_PROVIDER_SGNR = MemoryLayout.structLayout(
			JAVA_INT.withName("cbStruct"),
			JAVA_INT.withName("sftVerifyAsOfLow"), // FILETIME, 4-byte aligned
			JAVA_INT.withName("sftVerifyAsOfHigh"),
			JAVA_INT.withName("csCertChain"),
			ADDRESS.withName("pasCertChain"));

	// only the leading fields are declared
	private static final StructLayout CRYPT_PROVIDER_CERT = MemoryLayout.structLayout(
			JAVA_INT.withName("cbStruct"),
			MemoryLayout.paddingLayout(4),
			ADDRESS.withName("pCert"));

	private static final StructLayout CERT_CONTEXT = MemoryLayout.structLayout(
			JAVA_INT.withName("dwCertEncodingType"),
			MemoryLayout.paddingLayout(4),
			ADDRESS.withName("pbCertEncoded"),
			JAVA_INT.withName("cbCertEncoded"),
			MemoryLayout.paddingLayout(4),
			ADDRESS.withName("pCertInfo"),
			ADDRESS.withName("hCertStore"));

	private Authenticode() {
	}

	/**
	 * Verifies the embedded Authenticode signature of a file and checks that it was created by the expected signer.
	 *
	 * @param file the signed file, e.g. an .exe or .msi
	 * @param signerEku OID of an extended key usage, which the signing certificate must contain, e.g. the durable identity of an Azure Artifact Signing account
	 * @param rootThumbprint SHA-1 thumbprint (hex) of the root certificate, at which the certificate chain must end
	 * @return the signing certificate
	 * @throws SignatureException if the file is not signed, the signature is not trusted or it was created by a different signer
	 */
	static X509Certificate verify(Path file, String signerEku, String rootThumbprint) throws SignatureException {
		var chain = getVerifiedCertificateChain(file);
		var signerCert = chain.getFirst();
		var rootCert = chain.getLast();
		try {
			var ekus = Objects.requireNonNullElse(signerCert.getExtendedKeyUsage(), List.<String>of());
			if (!ekus.contains(signerEku)) {
				throw new SignatureException("Signing certificate %s of %s lacks extended key usage %s".formatted(signerCert.getSubjectX500Principal(), file, signerEku));
			}
			var actualRootThumbprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(rootCert.getEncoded()));
			if (!actualRootThumbprint.equalsIgnoreCase(rootThumbprint)) {
				throw new SignatureException("Certificate chain of %s ends at unexpected root %s (thumbprint %s)".formatted(file, rootCert.getSubjectX500Principal(), actualRootThumbprint));
			}
		} catch (CertificateException e) {
			throw new SignatureException("Failed to read certificate of " + file, e);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("Every Java platform is required to support SHA-1", e);
		}
		return signerCert;
	}

	/**
	 * Verifies the embedded Authenticode signature of a file, including the certificate chain and its revocation status.
	 *
	 * @param file the signed file, e.g. an .exe or .msi
	 * @return the certificate chain of the primary signature, starting with the signing certificate and ending with the trusted root certificate
	 * @throws SignatureException if the file is not signed or the signature is not trusted
	 */
	static List<X509Certificate> getVerifiedCertificateChain(Path file) throws SignatureException {
		try (var arena = Arena.ofConfined()) {
			var fileInfo = arena.allocate(WINTRUST_FILE_INFO);
			setInt(fileInfo, WINTRUST_FILE_INFO, "cbStruct", (int) WINTRUST_FILE_INFO.byteSize());
			setAddress(fileInfo, WINTRUST_FILE_INFO, "pcwszFilePath", arena.allocateFrom(file.toString(), StandardCharsets.UTF_16LE));

			var data = arena.allocate(WINTRUST_DATA);
			setInt(data, WINTRUST_DATA, "cbStruct", (int) WINTRUST_DATA.byteSize());
			setInt(data, WINTRUST_DATA, "dwUIChoice", WTD_UI_NONE);
			setInt(data, WINTRUST_DATA, "fdwRevocationChecks", WTD_REVOKE_WHOLECHAIN);
			setInt(data, WINTRUST_DATA, "dwUnionChoice", WTD_CHOICE_FILE);
			setAddress(data, WINTRUST_DATA, "pFile", fileInfo);
			setInt(data, WINTRUST_DATA, "dwStateAction", WTD_STATEACTION_VERIFY);
			setInt(data, WINTRUST_DATA, "dwProvFlags", WTD_REVOCATION_CHECK_CHAIN_EXCLUDE_ROOT);

			var actionId = allocateGenericVerifyV2(arena);
			int result = winVerifyTrust(actionId, data);
			try {
				if (result != 0) {
					throw new SignatureException("WinVerifyTrust failed with error 0x%08X for %s".formatted(result, file));
				}
				var provData = call(WT_HELPER_PROV_DATA_FROM_STATE_DATA, data.get(ADDRESS, offset(WINTRUST_DATA, "hWVTStateData")));
				var signer = provData.equals(NULL) ? NULL : call(WT_HELPER_GET_PROV_SIGNER_FROM_CHAIN, provData, 0, 0, 0);
				int chainLength = signer.equals(NULL) ? 0 : signer.reinterpret(CRYPT_PROVIDER_SGNR.byteSize()).get(JAVA_INT, offset(CRYPT_PROVIDER_SGNR, "csCertChain"));
				if (chainLength == 0) {
					throw new SignatureException("No signer certificate found for " + file);
				}
				var certFactory = CertificateFactory.getInstance("X.509");
				var chain = new ArrayList<X509Certificate>(chainLength);
				for (int i = 0; i < chainLength; i++) {
					var cert = call(WT_HELPER_GET_PROV_CERT_FROM_CHAIN, signer, i).reinterpret(CRYPT_PROVIDER_CERT.byteSize());
					var certContext = cert.get(ADDRESS, offset(CRYPT_PROVIDER_CERT, "pCert")).reinterpret(CERT_CONTEXT.byteSize());
					var encoded = certContext.get(ADDRESS, offset(CERT_CONTEXT, "pbCertEncoded")).reinterpret(certContext.get(JAVA_INT, offset(CERT_CONTEXT, "cbCertEncoded")));
					chain.add((X509Certificate) certFactory.generateCertificate(new ByteArrayInputStream(encoded.toArray(JAVA_BYTE))));
				}
				return chain;
			} catch (CertificateException e) {
				throw new SignatureException("Failed to read certificate chain of " + file, e);
			} finally {
				// release state data allocated by WTD_STATEACTION_VERIFY
				setInt(data, WINTRUST_DATA, "dwStateAction", WTD_STATEACTION_CLOSE);
				winVerifyTrust(actionId, data);
			}
		}
	}

	// {00AAC56B-CD44-11d0-8CC2-00C04FC295EE}
	private static MemorySegment allocateGenericVerifyV2(Arena arena) {
		var guid = arena.allocate(GUID);
		guid.set(JAVA_INT, offset(GUID, "Data1"), 0x00AAC56B);
		guid.set(JAVA_SHORT, offset(GUID, "Data2"), (short) 0xCD44);
		guid.set(JAVA_SHORT, offset(GUID, "Data3"), (short) 0x11D0);
		guid.asSlice(offset(GUID, "Data4")).copyFrom(MemorySegment.ofArray(new byte[]{(byte) 0x8C, (byte) 0xC2, 0x00, (byte) 0xC0, 0x4F, (byte) 0xC2, (byte) 0x95, (byte) 0xEE}));
		return guid;
	}

	private static int winVerifyTrust(MemorySegment actionId, MemorySegment data) {
		try {
			return (int) WIN_VERIFY_TRUST.invokeExact(NULL, actionId, data);
		} catch (Throwable e) {
			throw new AssertionError("should not reach here", e);
		}
	}

	private static MemorySegment call(MethodHandle handle, Object... args) {
		try {
			return (MemorySegment) handle.invokeWithArguments(args);
		} catch (Throwable e) {
			throw new AssertionError("should not reach here", e);
		}
	}

	private static MethodHandle downcall(SymbolLookup lookup, String name, FunctionDescriptor descriptor) {
		var symbol = lookup.find(name).orElseThrow(() -> new UnsatisfiedLinkError("unresolved symbol: " + name));
		return LINKER.downcallHandle(symbol, descriptor);
	}

	private static long offset(StructLayout layout, String field) {
		return layout.byteOffset(groupElement(field));
	}

	private static void setInt(MemorySegment segment, StructLayout layout, String field, int value) {
		segment.set(JAVA_INT, offset(layout, field), value);
	}

	private static void setAddress(MemorySegment segment, StructLayout layout, String field, MemorySegment value) {
		segment.set(ADDRESS, offset(layout, field), value);
	}

}
