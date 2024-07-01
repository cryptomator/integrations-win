package org.cryptomator.windows.common;

import org.cryptomator.windows.capi.common.Windows_h;
import org.cryptomator.windows.capi.ktmw32.Ktmw32_h;
import org.cryptomator.windows.capi.winreg.Winreg_h;

import java.lang.foreign.AddressLayout;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;

import static java.lang.foreign.MemorySegment.NULL;
import static org.cryptomator.windows.capi.common.Windows_h.ERROR_FILE_NOT_FOUND;
import static org.cryptomator.windows.capi.common.Windows_h.ERROR_SUCCESS;
import static org.cryptomator.windows.capi.common.Windows_h.INVALID_HANDLE_VALUE;
import static org.cryptomator.windows.capi.winreg.Winreg_h.*;

public class WindowsRegistry {

	public static RegistryTransaction startTransaction() throws WindowsException {
		var transactionHandle = Ktmw32_h.CreateTransaction(NULL, NULL, 0, 0, 0, 0, NULL);
		if (transactionHandle.address() == INVALID_HANDLE_VALUE().address()) {
			//GetLastError()
			int error = Windows_h.GetLastError();
			throw new WindowsException("ktmw32.h:CreateTransaction", error);
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

		public RegistryKey createRegKey(RegistryKey key, String subkey, boolean isVolatile) throws RegistryKeyException {
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
					throw new RegistryKeyException("winreg.h:RegCreateKeyTransactedW", key.getPath() + "\\" + subkey, result);
				}
				return new RegistryKey(pointerToResultKey.get(C_POINTER, 0), key.getPath() + "\\" + subkey);
			}
		}

		public RegistryKey openRegKey(RegistryKey key, String subkey) throws RegistryKeyException {
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
					throw new RegistryKeyException("winreg.h:RegOpenKeyTransactedW", key.getPath() + "\\" + subkey, result);
				}
				return new RegistryKey(pointerToResultKey.get(C_POINTER, 0), key.getPath() + "\\" + subkey);
			}
		}

		public void deleteRegKey(RegistryKey key, String subkey) throws WindowsException {
			deleteRegKey(key, subkey, false);
		}

		public void deleteRegKey(RegistryKey key, String subkey, boolean ignoreNotExisting) throws RegistryKeyException {
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
				if (result != ERROR_SUCCESS() //
						&& !(result == ERROR_FILE_NOT_FOUND() && ignoreNotExisting)) {
					throw new RegistryKeyException("winreg.h:RegDeleteKeyTransactedW", key.getPath() + "\\" + subkey, result);
				}
			}
		}


		public synchronized void commit() throws WindowsException {
			if (isClosed) {
				throw new IllegalStateException("Transaction already closed");
			}
			int result = Ktmw32_h.CommitTransaction(transactionHandle);
			if (result == 0) {
				int error = Windows_h.GetLastError();
				throw new WindowsException("ktmw32.h:CommitTransaction", error);
			}
			isCommited = true;
			closeInternal();
		}

		public synchronized void rollback() throws WindowsException {
			if (isClosed) {
				throw new IllegalStateException("Transaction already closed");
			}
			int result = Ktmw32_h.RollbackTransaction(transactionHandle);
			if (result == 0) {
				int error = Windows_h.GetLastError();
				throw new WindowsException("ktmw32.h:CommitTransaction", error);
			}
			closeInternal();
		}

		;

		@Override
		public synchronized void close() throws WindowsException {
			if (!isCommited) {
				try {
					rollback();
				} catch (WindowsException e) {
					System.err.printf("Failed to rollback uncommited transaction on close: %s%n", e.getMessage());
				}
			}
			closeInternal();
		}

		private synchronized void closeInternal() throws WindowsException {
			if (!isClosed) {
				int result = Windows_h.CloseHandle(transactionHandle);
				if (result == 0) {
					int error = Windows_h.GetLastError();
					throw new WindowsException("Windows.h:CloseHandle", error);
				}
				transactionHandle = null;
				isClosed = true;
			}
		}
	}
}