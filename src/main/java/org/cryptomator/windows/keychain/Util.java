package org.cryptomator.windows.keychain;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.cryptomator.integrations.keychain.KeychainAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static java.nio.charset.StandardCharsets.UTF_8;

public class Util {
	private static final Logger LOG = LoggerFactory.getLogger(Util.class);
	private static final Path USER_HOME_REL = Path.of("~");
	private static final Path USER_HOME = Path.of(System.getProperty("user.home"));

	static Path resolveHomeDir(Path path) {
		if (path.startsWith(USER_HOME_REL)) {
			return USER_HOME.resolve(USER_HOME_REL.relativize(path));
		} else {
			return path;
		}
	}

	static byte[] generateSalt() {
		byte[] result = new byte[2 * Long.BYTES];
		UUID uuid = UUID.randomUUID();
		ByteBuffer buf = ByteBuffer.wrap(result);
		buf.putLong(uuid.getMostSignificantBits());
		buf.putLong(uuid.getLeastSignificantBits());
		return result;
	}

}
