package org.cryptomator.windows.common;

import org.cryptomator.windows.capi.winreg.Winreg_h;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;

import static java.lang.foreign.MemorySegment.NULL;
import static org.cryptomator.windows.capi.common.Windows_h.ERROR_FILE_NOT_FOUND;
import static org.cryptomator.windows.capi.common.Windows_h.ERROR_MORE_DATA;
import static org.cryptomator.windows.capi.common.Windows_h.ERROR_SUCCESS;
import static org.cryptomator.windows.capi.winreg.Winreg_h.REG_EXPAND_SZ;
import static org.cryptomator.windows.capi.winreg.Winreg_h.RRF_RT_REG_DWORD;
import static org.cryptomator.windows.capi.winreg.Winreg_h.RRF_RT_REG_EXPAND_SZ;
import static org.cryptomator.windows.capi.winreg.Winreg_h.RRF_RT_REG_SZ;

public class RegistryKey implements AutoCloseable {

	public static final RegistryKey HKEY_CURRENT_USER = new RegistryRoot(Winreg_h.HKEY_CURRENT_USER(), "HKEY_CURRENT_USER");
	public static final RegistryKey HKEY_LOCAL_MACHINE = new RegistryRoot(Winreg_h.HKEY_LOCAL_MACHINE(), "HKEY_LOCAL_MACHINE");
	public static final RegistryKey HKEY_CLASSES_ROOT = new RegistryRoot(Winreg_h.HKEY_CLASSES_ROOT(), "HKEY_CLASSES_ROOT");
	public static final RegistryKey HKEY_USERS = new RegistryRoot(Winreg_h.HKEY_USERS(), "HKEY_USERS");

	private final String path;
	private MemorySegment handle;
	private volatile boolean isClosed = false;

	RegistryKey(MemorySegment handle, String path) {
		this.handle = handle;
		this.path = path;
	}

	//-- GetValue functions --

	public String getStringValue(String name, boolean isExpandable) throws RegistryValueException {
		try (var arena = Arena.ofConfined()) {
			var lpValueName = arena.allocateFrom(name, StandardCharsets.UTF_16LE);
			var data = getValue(lpValueName, isExpandable ? RRF_RT_REG_EXPAND_SZ() : RRF_RT_REG_SZ(), 256L);
			return data.getString(0, StandardCharsets.UTF_16LE);
		}
	}

	public int getDwordValue(String name) throws RegistryValueException {
		try (var arena = Arena.ofConfined()) {
			var lpValueName = arena.allocateFrom(name, StandardCharsets.UTF_16LE);
			var data = getValue(lpValueName, RRF_RT_REG_DWORD(), 5L);
			return data.get(ValueLayout.JAVA_INT, 0);
		}
	}

	private MemorySegment getValue(MemorySegment lpValueName, int dwFlags, long seed) throws RegistryValueException {
		long bufferSize = seed - 1;
		try (var arena = Arena.ofConfined()) {
			var lpDataSize = arena.allocateFrom(ValueLayout.JAVA_INT, (int) bufferSize);

			try (var dataArena = Arena.ofConfined()) {
				var lpData = dataArena.allocate(bufferSize);

				int result = Winreg_h.RegGetValueW(handle, NULL, lpValueName, dwFlags, NULL, lpData, lpDataSize);
				if (result == ERROR_MORE_DATA()) {
					throw new BufferTooSmallException();
				} else if (result != ERROR_SUCCESS()) {
					throw new RegistryValueException("winreg_h:RegGetValue", path, lpValueName.getString(0, StandardCharsets.UTF_16LE), result);
				}

				var returnBuffer = Arena.ofAuto().allocate(Integer.toUnsignedLong(lpDataSize.get(ValueLayout.JAVA_INT, 0)));
				MemorySegment.copy(lpData, 0L, returnBuffer, 0L, returnBuffer.byteSize());
				return returnBuffer;
			} catch (BufferTooSmallException _) {
				if (bufferSize <= WindowsRegistry.MAX_DATA_SIZE) {
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

	public void setStringValue(String name, String data, boolean isExpandable) throws RegistryValueException {
		try (var arena = Arena.ofConfined()) {
			var lpValueName = arena.allocateFrom(name, StandardCharsets.UTF_16LE);
			var lpValueData = arena.allocateFrom(data, StandardCharsets.UTF_16LE);
			setValue(lpValueName, lpValueData, isExpandable ? REG_EXPAND_SZ() : Winreg_h.REG_SZ());
		}
	}

	public void setDwordValue(String name, int data) throws RegistryValueException {
		try (var arena = Arena.ofConfined()) {
			var lpValueName = arena.allocateFrom(name, StandardCharsets.UTF_16LE);
			var lpValueData = arena.allocateFrom(ValueLayout.JAVA_INT, data);
			setValue(lpValueName, lpValueData, Winreg_h.REG_DWORD());
		}
	}

	private void setValue(MemorySegment lpValueName, MemorySegment data, int dwFlags) throws RegistryValueException {
		if (data.byteSize() > WindowsRegistry.MAX_DATA_SIZE) {
			throw new IllegalArgumentException("Data must be smaller than " + WindowsRegistry.MAX_DATA_SIZE + "bytes.");
		}

		int result = Winreg_h.RegSetKeyValueW(handle, NULL, lpValueName, dwFlags, data, (int) data.byteSize());
		if (result != ERROR_SUCCESS()) {
			throw new RegistryValueException("winreg_h:RegSetKeyValueW", path, lpValueName.getString(0, StandardCharsets.UTF_16LE), result);
		}
	}

	//-- delete operations

	public void deleteValue(String valueName) throws RegistryValueException {
		deleteValue(valueName, false);
	}

	public void deleteValue(String valueName, boolean ignoreNotExisting) throws RegistryValueException {
		try (var arena = Arena.ofConfined()) {
			var lpValueName = arena.allocateFrom(valueName, StandardCharsets.UTF_16LE);
			int result = Winreg_h.RegDeleteKeyValueW(handle, NULL, lpValueName);
			if (result != ERROR_SUCCESS() //
					&& !(result == ERROR_FILE_NOT_FOUND() && ignoreNotExisting)) {
				throw new RegistryValueException("winreg_h:RegSetKeyValueW", path, lpValueName.getString(0, StandardCharsets.UTF_16LE), result);
			}
		}
	}

	public void deleteSubtree(String subkey) throws RegistryKeyException {
		if (subkey == null || subkey.isBlank()) {
			throw new IllegalArgumentException("Subkey must not be empty");
		}
		deleteValuesAndSubtrees(subkey);
	}

	public void deleteAllValuesAndSubtrees() throws RegistryKeyException {
		deleteValuesAndSubtrees("");
	}

	private void deleteValuesAndSubtrees(String subkey) throws RegistryKeyException {
		try (var arena = Arena.ofConfined()) {
			var lpSubkey = arena.allocateFrom(subkey, StandardCharsets.UTF_16LE);
			int result = Winreg_h.RegDeleteTreeW(handle, lpSubkey);
			if (result != ERROR_SUCCESS()) {
				throw new RegistryKeyException("winreg.h:RegDeleteTreeW", path + "\\" + lpSubkey, result);
			}
		}
	}

	@Override
	public synchronized void close() {
		if (!isClosed) {
			int result = Winreg_h.RegCloseKey(handle);
			if (result != ERROR_SUCCESS()) {
				throw new RuntimeException(new RegistryKeyException("winreg.h:RegCloseKey", path, result));
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


	/**
	 * Closing predefined registry keys has undefined behaviour. Hence, we shade the super method and do nothing on close.
	 */
	private static class RegistryRoot extends RegistryKey {

		RegistryRoot(MemorySegment handle, String path) {
			super(handle, path);
		}

		@Override
		public void close() {
			//no-op
		}
	}
}
