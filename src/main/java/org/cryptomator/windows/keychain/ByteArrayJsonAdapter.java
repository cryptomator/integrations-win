package org.cryptomator.windows.keychain;

import com.google.common.io.BaseEncoding;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;

class ByteArrayJsonAdapter implements JsonSerializer<byte[]>, JsonDeserializer<byte[]> {

	private static final BaseEncoding BASE64 = BaseEncoding.base64();

	@Override
	public byte[] deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
		return BASE64.decode(json.getAsString());
	}

	@Override
	public JsonElement serialize(byte[] src, Type typeOfSrc, JsonSerializationContext context) {
		return new JsonPrimitive(BASE64.encode(src));
	}

}
