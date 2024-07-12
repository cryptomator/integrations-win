package org.cryptomator.windows.common;

public class RegistryValueException extends RegistryKeyException {

	public RegistryValueException(String method, String keyPath, String valueName, int systemErrorCode) {
		super(method, keyPath + ", value: "+valueName, systemErrorCode);
	}
}
