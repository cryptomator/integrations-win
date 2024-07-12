package org.cryptomator.windows.common;

import org.cryptomator.windows.capi.common.Windows_h;
import org.cryptomator.windows.capi.ktmw32.Ktmw32_h;
import org.cryptomator.windows.capi.winreg.Winreg_h;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

	private static final Logger LOG = LoggerFactory.getLogger(WindowsRegistry.class);

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

		/**
		 * Creates and opens a registry key.
		 * <p>
		 * If the registry key already exists, it is just opened. The key is opened with the access rights KEY_READ and KEY_WRITE.
		 *
		 * @param key        handle to an already opened registry key
		 * @param subkey     name/path of a subkey that this function opens or creates
		 * @param isVolatile flag indicating if this key is volatile. A volatile key is not preserved over a system restart.
		 * @return the opened {@link RegistryKey}
		 * @throws RegistryKeyException if Winreg_h.RegCreateKeyTransactedW returns with a result != ERROR_SUCCESS
		 */
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
				//TODO: we can check if a registry root is opened (key is any regRoot && subkey == "")
				//	if so, we should wrap it in the corresponding class
				return new RegistryKey(pointerToResultKey.get(C_POINTER, 0), key.getPath() + "\\" + subkey);
			}
		}

		/**
		 * Opens a registry key.
		 * <p>
		 * The key is opened with the access rights KEY_READ and KEY_WRITE.
		 *
		 * @param key    handle to an already opened registry key
		 * @param subkey name/path of a subkey that this function opens
		 * @return the opened {@link RegistryKey}
		 * @throws RegistryKeyException if Winreg_h.RegOpenKeyTransactedW returns with a result != ERROR_SUCCESS
		 */
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

		/**
		 * Deletes a registry key.
		 *
		 * @param key    handle to an already opened registry key
		 * @param subkey name/path of a subkey that this function opens or creates
		 * @throws RegistryKeyException if Winreg_h.RegDeleteKeyTransactedW returns with a result != ERROR_SUCCESS
		 */
		public void deleteRegKey(RegistryKey key, String subkey) throws RegistryKeyException {
			deleteRegKey(key, subkey, false);
		}

		/**
		 * Deletes a registry key.
		 * <p>
		 * If the key does not exists and {@code ignoreNotExisting == true}, no exceptions is thrown.
		 *
		 * @param key    handle to an already opened registry key
		 * @param subkey name/path of a subkey that this function opens or creates
		 * @throws RegistryKeyException if Winreg_h.RegDeleteKeyTransactedW returns with a result != ERROR_SUCCESS, <em>except</em> the result is ERROR_FILE_NOT_FOUND and {@code ignoreNotExisting == true}
		 */
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

		/**
		 * Commits and closes this transaction.
		 *
		 * @throws WindowsException if Ktmw32_h:CommitTransaction returned 0. The exception contains the system error code.
		 */
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

		/**
		 * Rolls this transaction back and closes it.
		 *
		 * @throws WindowsException if Ktmw32_h:RollbackTransaction returned 0. The exception contains the system error code.
		 */
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

		/**
		 * Closes this transaction.
		 * <p>
		 * If this transaction is not commited, it is rolled back.
		 * The close method always discards the transaction handle. If an error occurs in either rolling back or closing the handle, the error is logged, but no exception thrown.
		 */
		@Override
		public synchronized void close() {
			try {
				if (!isCommited) {
					rollback();
				}
			} catch (WindowsException e) {
				LOG.error("Failed to rollback uncommited transaction on close: {}", e.getMessage());
			} finally {
				closeInternal();
			}
		}

		private synchronized void closeInternal() {
			if (!isClosed) {
				int result = Windows_h.CloseHandle(transactionHandle);
				if (result == 0) {
					int error = Windows_h.GetLastError();
					LOG.error("Closing transaction handle failed. Function Windows.h:CloseHandle set system error code to {}", error);
				}
				transactionHandle = null;
				isClosed = true;
			}
		}
	}
}