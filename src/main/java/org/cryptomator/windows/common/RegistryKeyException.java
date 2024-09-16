package org.cryptomator.windows.common;

public class RegistryKeyException extends WindowsException {

	private final String keyPath;

	public RegistryKeyException(String method, String keyPath, int systemErrorCode) {
		super(method + "(regKey: "+keyPath+")", systemErrorCode);
		this.keyPath = keyPath;
	}
}
