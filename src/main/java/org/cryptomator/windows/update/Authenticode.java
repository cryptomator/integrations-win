package org.cryptomator.windows.update;

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
import java.security.SignatureException;
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

	// Hand-written bindings, see WinTrust.h, SoftPub.h and wincrypt.h. DLLs are loaded by absolute path to prevent DLL search order hijacking.
	private static final Path SYSTEM32 = Path.of(Optional.ofNullable(System.getenv("SystemRoot")).orElse("C:\\Windows"), "System32");
	private static final Linker LINKER = Linker.nativeLinker();
	private static final SymbolLookup WINTRUST = SymbolLookup.libraryLookup(SYSTEM32.resolve("wintrust.dll"), Arena.global());
	private static final SymbolLookup CRYPT32 = SymbolLookup.libraryLookup(SYSTEM32.resolve("crypt32.dll"), Arena.global());

	private static final MethodHandle WIN_VERIFY_TRUST = downcall(WINTRUST, "WinVerifyTrust", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
	private static final MethodHandle WT_HELPER_PROV_DATA_FROM_STATE_DATA = downcall(WINTRUST, "WTHelperProvDataFromStateData", FunctionDescriptor.of(ADDRESS, ADDRESS));
	private static final MethodHandle WT_HELPER_GET_PROV_SIGNER_FROM_CHAIN = downcall(WINTRUST, "WTHelperGetProvSignerFromChain", FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT));
	private static final MethodHandle WT_HELPER_GET_PROV_CERT_FROM_CHAIN = downcall(WINTRUST, "WTHelperGetProvCertFromChain", FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT));
	private static final MethodHandle CERT_GET_NAME_STRING_W = downcall(CRYPT32, "CertGetNameStringW", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS, ADDRESS, JAVA_INT));

	private static final int WTD_UI_NONE = 2;
	private static final int WTD_REVOKE_WHOLECHAIN = 0x1;
	private static final int WTD_CHOICE_FILE = 1;
	private static final int WTD_STATEACTION_VERIFY = 0x1;
	private static final int WTD_STATEACTION_CLOSE = 0x2;
	private static final int WTD_REVOCATION_CHECK_CHAIN_EXCLUDE_ROOT = 0x80;
	private static final int CERT_NAME_ATTR_TYPE = 3;
	private static final String SZ_OID_COMMON_NAME = "2.5.4.3";
	private static final long CRYPT_PROVIDER_CERT_PCERT_OFFSET = 8; // DWORD cbStruct, padding, PCCERT_CONTEXT pCert

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

	private Authenticode() {
	}

	/**
	 * Verifies the embedded Authenticode signature of a file, including the certificate chain and its revocation status.
	 *
	 * @param file the signed file, e.g. an .exe or .msi
	 * @return the common name (CN) of the certificate, which created the primary signature
	 * @throws SignatureException if the file is not signed or the signature is not trusted
	 */
	static String getVerifiedSignerName(Path file) throws SignatureException {
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
				var cert = signer.equals(NULL) ? NULL : call(WT_HELPER_GET_PROV_CERT_FROM_CHAIN, signer, 0);
				if (cert.equals(NULL)) {
					throw new SignatureException("No signer certificate found for " + file);
				}
				var certContext = cert.reinterpret(CRYPT_PROVIDER_CERT_PCERT_OFFSET + ADDRESS.byteSize()).get(ADDRESS, CRYPT_PROVIDER_CERT_PCERT_OFFSET);
				return getCommonName(arena, certContext);
			} finally {
				// release state data allocated by WTD_STATEACTION_VERIFY
				setInt(data, WINTRUST_DATA, "dwStateAction", WTD_STATEACTION_CLOSE);
				winVerifyTrust(actionId, data);
			}
		}
	}

	private static String getCommonName(Arena arena, MemorySegment certContext) {
		var oid = arena.allocateFrom(SZ_OID_COMMON_NAME, StandardCharsets.US_ASCII);
		int length = certGetNameString(certContext, oid, NULL, 0); // number of chars, including the null terminator
		var name = arena.allocate((long) length * Character.BYTES);
		certGetNameString(certContext, oid, name, length);
		return name.getString(0, StandardCharsets.UTF_16LE);
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

	private static int certGetNameString(MemorySegment certContext, MemorySegment oid, MemorySegment buffer, int bufferLength) {
		try {
			return (int) CERT_GET_NAME_STRING_W.invokeExact(certContext, CERT_NAME_ATTR_TYPE, 0, oid, buffer, bufferLength);
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
