package org.cryptomator.windows.keychain;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;
import java.util.Base64;

class ByteArrayJsonAdapter {
	static class Serializer extends StdSerializer<byte[]> {

		public Serializer() {
			super((Class<byte[]>) null);
		}

		@Override
		public void serialize(byte[] value, JsonGenerator gen, SerializerProvider provider) throws IOException {
			gen.writeString(Base64.getEncoder().encodeToString(value));
		}
	}

	static class Deserializer extends StdDeserializer<byte[]> {

		public Deserializer() {
			super(byte[].class);
		}

		@Override
		public byte[] deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JacksonException {
			String base64 = p.getValueAsString();
			return Base64.getDecoder().decode(base64);
		}
	}

}
