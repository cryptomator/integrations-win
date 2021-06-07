package org.cryptomator.windows.common;

import java.util.ResourceBundle;

public enum Localization {
	INSTANCE;

	private final ResourceBundle resourceBundle = ResourceBundle.getBundle("WinIntegrationsBundle");

	public static ResourceBundle get() {
		return INSTANCE.resourceBundle;
	}

}
