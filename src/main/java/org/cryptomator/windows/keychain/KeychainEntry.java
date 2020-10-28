package org.cryptomator.windows.keychain;

import com.google.gson.annotations.SerializedName;

class KeychainEntry {

	@SerializedName("ciphertext")
	byte[] ciphertext;
	
	@SerializedName("salt")
	byte[] salt;
}
