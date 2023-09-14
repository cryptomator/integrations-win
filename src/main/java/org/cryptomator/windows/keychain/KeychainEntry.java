package org.cryptomator.windows.keychain;


import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

record KeychainEntry(@JsonProperty("ciphertext") //
					 @JsonSerialize(using = ByteArrayJsonAdapter.Serializer.class) //
					 @JsonDeserialize(using = ByteArrayJsonAdapter.Deserializer.class) //
					 byte[] ciphertext,
					 @JsonProperty("salt") //
					 @JsonSerialize(using = ByteArrayJsonAdapter.Serializer.class) //
					 @JsonDeserialize(using = ByteArrayJsonAdapter.Deserializer.class) //
					 byte[] salt) {
}
