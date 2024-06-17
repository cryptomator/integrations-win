package org.cryptomator.windows.common;

import org.cryptomator.windows.capi.common.Windows_h;
import org.cryptomator.windows.capi.ktmw32.Ktmw32_h;
import org.cryptomator.windows.capi.winreg.Winreg_h;

import java.lang.foreign.AddressLayout;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;

import static java.lang.foreign.MemorySegment.NULL;
import static org.cryptomator.windows.capi.common.Windows_h.ERROR_MORE_DATA;
import static org.cryptomator.windows.capi.common.Windows_h.ERROR_SUCCESS;
import static org.cryptomator.windows.capi.common.Windows_h.INVALID_HANDLE_VALUE;
import static org.cryptomator.windows.capi.winreg.Winreg_h.*;

public class WindowsRegistry {

	public static final long MAX_DATA_SIZE = (1L << 32) - 1L; //unsinged integer

	public static RegistryTransaction startTransaction() {
		var transactionHandle = Ktmw32_h.CreateTransaction(NULL, NULL, 0, 0, 0, 0, NULL);
		if (transactionHandle.address() == INVALID_HANDLE_VALUE().address()) {
			//GetLastError()
			int error = Windows_h.GetLastError();
			//TODO: get error message directly? https://learn.microsoft.com/en-us/windows/win32/Debug/retrieving-the-last-error-code
			throw new RuntimeException("Native code returned win32 error code " + error);
		}
		return new RegistryTransaction(transactionHandle);
	}

	public static class RegistryTransaction implements AutoCloseable {

		private MemorySegment transactionHandle;
		private volatile boolean isCommited = false;
		private volatile boolean isClosed = false;

		RegistryTransaction(MemorySegment handle) {
			this.transactionHandle = handle;
		}

		public RegistryKey createRegKey(RegistryKey key, String subkey, boolean isVolatile) {
			var pointerToResultKey = Arena.ofAuto().allocate(AddressLayout.ADDRESS);
			try (var arena = Arena.ofConfined()) {
				var lpSubkey = arena.allocateFrom(subkey, StandardCharsets.UTF_16LE);
				int result = Winreg_h.RegCreateKeyTransactedW(
						key.getHandle(),
						lpSubkey,
						0,
						NULL,
						isVolatile ? REG_OPTION_VOLATILE() : REG_OPTION_NON_VOLATILE(),
						KEY_READ() | KEY_WRITE(),
						NULL,
						pointerToResultKey,
						NULL,
						transactionHandle,
						NULL
				);
				if (result != ERROR_SUCCESS()) {
					throw new RuntimeException("Creating Key failed with error code " + result);
				}
				return new RegistryKey(pointerToResultKey.get(C_POINTER, 0), key.getPath() + "\\" + subkey);
			}
		}

		public RegistryKey openRegKey(RegistryKey key, String subkey) {
			var pointerToResultKey = Arena.ofAuto().allocate(AddressLayout.ADDRESS);
			try (var arena = Arena.ofConfined()) {
				var lpSubkey = arena.allocateFrom(subkey, StandardCharsets.UTF_16LE);
				int result = Winreg_h.RegOpenKeyTransactedW(
						key.getHandle(),
						lpSubkey,
						0,
						KEY_READ() | KEY_WRITE(),
						pointerToResultKey,
						transactionHandle,
						NULL
				);
				if (result != ERROR_SUCCESS()) {
					throw new RuntimeException("Opening key failed with error code " + result);
				}
				return new RegistryKey(pointerToResultKey.get(C_POINTER, 0), key.getPath() + "\\" + subkey);
			}
		}

		public void deleteRegKey(RegistryKey key, String subkey) {
			try (var arena = Arena.ofConfined()) {
				var lpSubkey = arena.allocateFrom(subkey, StandardCharsets.UTF_16LE);
				int result = Winreg_h.RegDeleteKeyTransactedW(
						key.getHandle(),
						lpSubkey,
						0x0100, //KEY_WOW64_64KEY
						0,
						transactionHandle,
						NULL
				);
				if (result != ERROR_SUCCESS()) {
					throw new RuntimeException("Opening key failed with error code " + result);
				}
			}
		}


		public synchronized void commit() {
			if (isClosed) {
				throw new IllegalStateException("Transaction already closed");
			}
			int result = Ktmw32_h.CommitTransaction(transactionHandle);
			if (result == 0) {
				int error = Windows_h.GetLastError();
				throw new RuntimeException("Native code returned win32 error code " + error);
			}
			isCommited = true;
			closeInternal();
		}

		public synchronized void rollback() {
			if (isClosed) {
				throw new IllegalStateException("Transaction already closed");
			}
			int result = Ktmw32_h.RollbackTransaction(transactionHandle);
			if (result == 0) {
				int error = Windows_h.GetLastError();
				throw new RuntimeException("Native code returned win32 error code " + error);
			}
			closeInternal();
		}

		;

		@Override
		public synchronized void close() throws RuntimeException {
			if (!isCommited) {
				try {
					rollback();
				} catch (RuntimeException e) {
					System.err.printf("Failed to rollback uncommited transaction on close. Exception message: %s%n", e.getMessage());
				}
			}
			closeInternal();
		}

		private synchronized void closeInternal() {
			if (!isClosed) {
				int result = Windows_h.CloseHandle(transactionHandle);
				if (result == 0) {
					int error = Windows_h.GetLastError();
					throw new RuntimeException("Native code returned win32 error code " + error);
				}
				transactionHandle = null;
				isClosed = true;
			}
		}
	}

	public static class RegistryKey implements AutoCloseable {

		public static final RegistryKey HKEY_CURRENT_USER = new RegistryKey(Winreg_h.HKEY_CURRENT_USER(), "HKEY_CURRENT_USER");
		public static final RegistryKey HKEY_LOCAL_MACHINE = new RegistryKey(Winreg_h.HKEY_LOCAL_MACHINE(), "HKEY_LOCAL_MACHINE");
		public static final RegistryKey HKEY_CLASSES_ROOT = new RegistryKey(Winreg_h.HKEY_CLASSES_ROOT(), "HKEY_CLASSES_ROOT");
		public static final RegistryKey HKEY_USERS = new RegistryKey(Winreg_h.HKEY_USERS(), "HKEY_USERS");

		protected final String path;
		private MemorySegment handle;
		private volatile boolean isClosed = false;

		RegistryKey(MemorySegment handle, String path) {
			this.handle = handle;
			this.path = path;
		}

		//-- GetValue functions --

		public String getStringValue(String name, boolean isExpandable) throws RuntimeException {
			try (var arena = Arena.ofConfined()) {
				var lpValueName = arena.allocateFrom(name, StandardCharsets.UTF_16LE);
				var data = getValue(lpValueName, isExpandable? RRF_RT_REG_EXPAND_SZ() : RRF_RT_REG_SZ(), 256L);
				return data.getString(0, StandardCharsets.UTF_16LE);
			}
		}

		public int getDwordValue(String name) {
			try (var arena = Arena.ofConfined()) {
				var lpValueName = arena.allocateFrom(name, StandardCharsets.UTF_16LE);
				var data = getValue(lpValueName, RRF_RT_REG_DWORD(), 5L);
				return data.get(ValueLayout.JAVA_INT,0);
			}
		}

		private MemorySegment getValue(MemorySegment lpValueName, int dwFlags, long seed) throws RuntimeException {
			long bufferSize = seed - 1;
			try (var arena = Arena.ofConfined()) {
				var lpDataSize = arena.allocateFrom(ValueLayout.JAVA_INT, (int) bufferSize);

				try (var dataArena = Arena.ofConfined()) {
					var lpData = dataArena.allocate(bufferSize);

					int result = Winreg_h.RegGetValueW(handle, NULL, lpValueName, dwFlags, NULL, lpData, lpDataSize);
					if (result == ERROR_MORE_DATA()) {
						throw new BufferTooSmallException();
					} else if (result != ERROR_SUCCESS()) {
						throw new RuntimeException("Getting value %s for key %s failed with error code %d".formatted(lpValueName.getString(0, StandardCharsets.UTF_16LE), path, result));
					}

					var returnBuffer = Arena.ofAuto().allocate(Integer.toUnsignedLong(lpDataSize.get(ValueLayout.JAVA_INT, 0)));
					MemorySegment.copy(lpData, 0L, returnBuffer, 0L, returnBuffer.byteSize());
					return returnBuffer;

				} catch (BufferTooSmallException _) {
					if (bufferSize <= MAX_DATA_SIZE) {
						return getValue(lpValueName, dwFlags, seed << 1);
					} else {
						throw new RuntimeException("Getting value %s for key %s failed. Maximum buffer size of %d reached.".formatted(lpValueName.getString(0, StandardCharsets.UTF_16LE), path, bufferSize));
					}
				}
			}
		}

		private static class BufferTooSmallException extends RuntimeException {

		}

		//-- SetValue functions --

		public void setStringValue(String name, String data, boolean isExpandable) throws RuntimeException {
			try (var arena = Arena.ofConfined()) {
				var lpValueName = arena.allocateFrom(name, StandardCharsets.UTF_16LE);
				var lpValueData = arena.allocateFrom(data, StandardCharsets.UTF_16LE);
				setValue(lpValueName, lpValueData, isExpandable? REG_EXPAND_SZ() : Winreg_h.REG_SZ());
			}
		}

		public void setDwordValue(String name, int data) {
			try (var arena = Arena.ofConfined()) {
				var lpValueName = arena.allocateFrom(name, StandardCharsets.UTF_16LE);
				var lpValueData = arena.allocateFrom(ValueLayout.JAVA_INT, data);
				setValue(lpValueName, lpValueData, Winreg_h.REG_DWORD());
			}
		}

		private void setValue(MemorySegment lpValueName, MemorySegment data, int dwFlags) {
			if (data.byteSize() > MAX_DATA_SIZE) {
				throw new IllegalArgumentException("Data must be smaller than " + MAX_DATA_SIZE + "bytes.");
			}

			int result = Winreg_h.RegSetKeyValueW(handle, NULL, lpValueName, dwFlags, data , (int) data.byteSize());
			if (result != ERROR_SUCCESS()) {
				throw new RuntimeException("Setting value %s for key %s failed with error code %d".formatted(lpValueName.getString(0, StandardCharsets.UTF_16LE), path, result));
			}
		}

		//-- delete operations

		public void deleteValue(String valueName) {
			try (var arena = Arena.ofConfined()) {
				var lpValueName = arena.allocateFrom(valueName, StandardCharsets.UTF_16LE);
				int result = Winreg_h.RegDeleteKeyValueW(handle, NULL, lpValueName);
				if (result != ERROR_SUCCESS()) {
					throw new RuntimeException("Deleting Key failed with error code " + result);
				}
			}
		}

		public void deleteSubtree(String subkey) {
			if (subkey == null || subkey.isBlank()) {
				throw new IllegalArgumentException("Subkey must not be empty");
			}
			deleteValuesAndSubtrees(subkey);
		}

		public void deleteAllValuesAndSubtrees() {
			deleteValuesAndSubtrees("");
		}

		private void deleteValuesAndSubtrees(String subkey) {
			try (var arena = Arena.ofConfined()) {
				var lpSubkey = arena.allocateFrom(subkey, StandardCharsets.UTF_16LE);
				int result = Winreg_h.RegDeleteTreeW(handle, lpSubkey);
				if (result != ERROR_SUCCESS()) {
					throw new RuntimeException("Deleting Key failed with error code " + result);
				}
			}
		}

		@Override
		public synchronized void close() {
			if (!isClosed) {
				int result = Winreg_h.RegCloseKey(handle);
				if (result != ERROR_SUCCESS()) {
					throw new RuntimeException("Closing key %s failed with error code %d.".formatted(path, result));
				}
				handle = NULL;
				isClosed = true;
			}
		}

		MemorySegment getHandle() {
			return handle;
		}

		public String getPath() {
			return path;
		}
	}


}