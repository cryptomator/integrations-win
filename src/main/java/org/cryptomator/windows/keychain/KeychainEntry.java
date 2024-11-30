package org.cryptomator.windows.keychain;


import com.fasterxml.jackson.annotation.JsonProperty;

record KeychainEntry(@JsonProperty("ciphertext") byte[] ciphertext, @JsonProperty("salt") byte[] salt) {
}
