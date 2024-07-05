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
	//allocate at most 128MiB when reading from the registry to keep the library responsive
	static final int MAX_DATA_SIZE = (1 << 27);

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

	/**
	 * Gets a REG_SZ or REG_EXPAND_SZ value.
	 * <p>
	 * The size of the data is restricted to at most  {@value MAX_DATA_SIZE}. If the the value exceeds the size, a runtime exception is thrown.
	 *
	 * @param name         name of the value
	 * @param isExpandable flag indicating if the value is of type REG_EXPAND_SZ
	 * @return the data of the value
	 * @throws RegistryValueException if winreg.h:RegGetValueW returns a result != ERROR_SUCCESS
	 */
	public String getStringValue(String name, boolean isExpandable) throws RegistryValueException {
		try (var arena = Arena.ofConfined()) {
			var data = getValue(arena, name, isExpandable ? RRF_RT_REG_EXPAND_SZ() : RRF_RT_REG_SZ());
			return data.getString(0, StandardCharsets.UTF_16LE);
		}
	}

	/**
	 * Gets a DWORD value.
	 *
	 * @param name name of the value
	 * @return the data of the value
	 * @throws RegistryValueException if winreg.h:RegGetValueW returns a result != ERROR_SUCCESS
	 */
	public int getDwordValue(String name) throws RegistryValueException {
		try (var arena = Arena.ofConfined()) {
			var data = getValue(arena, name, RRF_RT_REG_DWORD());
			return data.get(ValueLayout.JAVA_INT, 0);
		}
	}

	private MemorySegment getValue(Arena arena, String name, int dwFlags) throws RegistryValueException {
		var lpValueName = arena.allocateFrom(name, StandardCharsets.UTF_16LE);
		var lpDataSize = arena.allocateFrom(ValueLayout.JAVA_INT, 0);

		int result;
		int bufferSize = 128; //will be doubled in first iteration
		MemorySegment lpData;
		do {
			bufferSize = bufferSize << 1;
			if (bufferSize >= MAX_DATA_SIZE) {
				throw new RuntimeException("Getting value %s for key %s failed. Maximum buffer size of %d reached.".formatted(name, path, bufferSize));
			}
			lpData = arena.allocate(bufferSize);
			lpDataSize.set(ValueLayout.JAVA_INT, 0, bufferSize);

			result = Winreg_h.RegGetValueW(handle, NULL, lpValueName, dwFlags, NULL, lpData, lpDataSize);

		} while (result == ERROR_MORE_DATA());

		if (result == ERROR_SUCCESS()) {
			return lpData;
		} else {
			throw new RegistryValueException("winreg_h:RegGetValue", path, name, result);
		}
	}

	//-- SetValue functions --

	/**
	 * Sets a REG_SZ or REG_EXPAND_SZ value for this registry key.
	 *
	 * @param name         name of the value
	 * @param data         Data to be set
	 * @param isExpandable flag marking if the value is of type REG_EXPAND_SZ
	 * @throws RegistryValueException if winreg.h:RegSetKeyValueW returns a result != ERROR_SUCCESS
	 */
	public void setStringValue(String name, String data, boolean isExpandable) throws RegistryValueException {
		try (var arena = Arena.ofConfined()) {
			var lpValueName = arena.allocateFrom(name, StandardCharsets.UTF_16LE);
			var lpValueData = arena.allocateFrom(data, StandardCharsets.UTF_16LE);
			setValue(lpValueName, lpValueData, isExpandable ? REG_EXPAND_SZ() : Winreg_h.REG_SZ());
		}
	}

	/**
	 * Sets a DWORD value for this registry key.
	 *
	 * @param name name of the value
	 * @param data Data to be set
	 * @throws RegistryValueException if winreg.h:RegSetKeyValueW returns a result != ERROR_SUCCESS
	 */
	public void setDwordValue(String name, int data) throws RegistryValueException {
		try (var arena = Arena.ofConfined()) {
			var lpValueName = arena.allocateFrom(name, StandardCharsets.UTF_16LE);
			var lpValueData = arena.allocateFrom(ValueLayout.JAVA_INT, data);
			setValue(lpValueName, lpValueData, Winreg_h.REG_DWORD());
		}
	}

	private void setValue(MemorySegment lpValueName, MemorySegment data, int dwFlags) throws RegistryValueException {
		if (data.byteSize() > MAX_DATA_SIZE) {
			throw new IllegalArgumentException("Data must be smaller than " + MAX_DATA_SIZE + "bytes.");
		}

		int result = Winreg_h.RegSetKeyValueW(handle, NULL, lpValueName, dwFlags, data, (int) data.byteSize());
		if (result != ERROR_SUCCESS()) {
			throw new RegistryValueException("winreg_h:RegSetKeyValueW", path, lpValueName.getString(0, StandardCharsets.UTF_16LE), result);
		}
	}

	//-- delete operations

	/**
	 * Deletes a value of this registry key.
	 *
	 * @param valueName name of the value
	 * @throws RegistryValueException if winreg.h:RegDeleteKeyValueW returns a result != ERROR_SUCCESS
	 * @see RegistryKey#deleteValue(String, boolean)
	 */
	public void deleteValue(String valueName) throws RegistryValueException {
		deleteValue(valueName, false);
	}

	/**
	 * Deletes a value of this registry key.
	 *
	 * @param valueName         name of the value
	 * @param ignoreNotExisting flag indicating wether a not existing value should be ignored
	 * @throws RegistryValueException if winreg.h:RegDeleteKeyValueW returns a result != ERROR_SUCCESS, <em>except</em> the result is ERROR_FILE_NOT_FOUND and {@code ignoreNotExisting == true}
	 */
	public void deleteValue(String valueName, boolean ignoreNotExisting) throws RegistryValueException {
		try (var arena = Arena.ofConfined()) {
			var lpValueName = arena.allocateFrom(valueName, StandardCharsets.UTF_16LE);
			int result = Winreg_h.RegDeleteKeyValueW(handle, NULL, lpValueName);
			if (result != ERROR_SUCCESS() //
					&& !(result == ERROR_FILE_NOT_FOUND() && ignoreNotExisting)) {
				throw new RegistryValueException("winreg_h:RegSetKeyValueW", path, valueName, result);
			}
		}
	}

	/**
	 * Deletes recursively content of this registry key.
	 * <p>
	 * If a non-empty subkey name is specified, then the corresponding subkey and its descendants are deleted.
	 * If an empty string or {@code null} is specified as the subkey, this method deletes <em>all subtrees and values</em> of this registry key.
	 *
	 * @param subkey Name of the subkey, being the root of the subtree to be deleted. Can be {@code null} or empty.
	 * @throws RegistryKeyException if winreg.h:RegDeleteTreeW returns a result != ERROR_SUCCESS
	 */
	public void deleteTree(String subkey) throws RegistryKeyException {
		try (var arena = Arena.ofConfined()) {
			var lpSubkey = arena.allocateFrom(subkey, StandardCharsets.UTF_16LE);
			int result = Winreg_h.RegDeleteTreeW(handle, lpSubkey);
			if (result != ERROR_SUCCESS()) {
				throw new RegistryKeyException("winreg.h:RegDeleteTreeW", path + "\\" + lpSubkey, result);
			}
		}
	}

	/**
	 * Closes this registry key.
	 *
	 * @throws RuntimeException wrapping a {@link RegistryKeyException}, if winreg.h:RegCloseKey returns a result != ERROR_SUCCESS
	 */
	@Override
	public synchronized void close() throws RuntimeException {
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
