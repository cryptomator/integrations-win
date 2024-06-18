package org.cryptomator.windows.common;

public class WindowsException extends Exception {

	private final int systemErrorCode;

	public WindowsException(String method, int systemErrorCode) {
		super("Method %s returned system error code %d".formatted(method, systemErrorCode));
		this.systemErrorCode = systemErrorCode;
	}

	public int getSystemErrorCode() {
		return systemErrorCode;
	}
}
