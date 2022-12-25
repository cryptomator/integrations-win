package org.cryptomator.windows.autoupdate;

import org.apache.commons.lang3.SystemUtils;
import org.cryptomator.integrations.autoupdate.AutoUpdateProvider;
import org.purejava.windows.MemoryAllocator;
import org.purejava.windows.winsparkle_h;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.MemorySession;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WinsparkleUpdate implements AutoUpdateProvider {

	private static final Logger LOG = LoggerFactory.getLogger(WinsparkleUpdate.class);

	private static final String WINSPARKLE_APPCAST_URL_STR = "https://winsparkle-java.s3.eu-central-1.amazonaws.com/appcast.xml";
	private static MemorySegment WINSPARKLE_APPCAST_URL = null;
	private static MemorySegment WINSPARKLE_DSA_KEYFILE_PUB = null;
	private static final MemorySegment WINSPARKLE_COMPANY_NAME = MemoryAllocator.ALLOCATE_WCHAR_T_FOR("Skymatic GmbH");
	private static final MemorySegment WINSPARKLE_APP_NAME = MemoryAllocator.ALLOCATE_WCHAR_T_FOR("Cryptomator");
	private static final MemorySegment WINSPARKLE_VERSION = MemoryAllocator.ALLOCATE_WCHAR_T_FOR(System.getProperty("cryptomator.appVersion", "SNAPSHOT"));
	private static MemorySession session;

	static {
		if (SystemUtils.IS_OS_WINDOWS) {
			try {
				new URL(WINSPARKLE_APPCAST_URL_STR);
				WINSPARKLE_APPCAST_URL = MemoryAllocator.ALLOCATE_FOR(WINSPARKLE_APPCAST_URL_STR);
			} catch (MalformedURLException e) {
				LOG.error("URL {} is malformed", WINSPARKLE_APPCAST_URL_STR);
			}

			// Check if we run in an IDE and use the dsa_pub.pem from the filesystem
			var fileName = "dist\\win\\contrib\\dsa_pub.pem";
			var file = new File(fileName);
			if (file.isFile()) {
				WINSPARKLE_DSA_KEYFILE_PUB = MemoryAllocator.ALLOCATE_FOR(fileName);
			} else { // otherwise use the dsa_pub.pem from the Cryptomator installation path
				var libraryPath = SystemUtils.JAVA_LIBRARY_PATH;
				var paths = libraryPath.split(";");

				Pattern pattern = Pattern.compile("\\w:\\\\.*?Cryptomator$");

				String path = "";
				for (String s : paths) {
					Matcher m = pattern.matcher(s);
					if (m.find()) {
						path = s;
						break;
					}
				}

				fileName = path + "\\dsa_pub.pem";
				file = new File(fileName);
				if (!file.isFile()) {
					LOG.error("File not found or not readable: " + fileName);
				} else {
					WINSPARKLE_DSA_KEYFILE_PUB = MemoryAllocator.ALLOCATE_FOR(fileName);
				}
			}
		}
	}

	@Override
	public void initAutoUpdate() {
		if (SystemUtils.IS_OS_WINDOWS) {
			if (null != WINSPARKLE_APPCAST_URL && null != WINSPARKLE_DSA_KEYFILE_PUB) {
				session = MemorySession.openShared();
				winsparkle_h.win_sparkle_set_appcast_url(WINSPARKLE_APPCAST_URL);
				winsparkle_h.win_sparkle_set_dsa_pub_pem(WINSPARKLE_DSA_KEYFILE_PUB);
				winsparkle_h.win_sparkle_set_app_details(WINSPARKLE_COMPANY_NAME, WINSPARKLE_APP_NAME, WINSPARKLE_VERSION);
				winsparkle_h.win_sparkle_set_did_find_update_callback(MemoryAllocator.ALLOCATE_CALLBACK_FOR(new DidFindUpdateCallback(), session));
				winsparkle_h.win_sparkle_init();
			} else {
				LOG.error("WinSparkle not initialized properly");
			}
		}
	}

	@Override
	public void cleanUpAutoUpdate() {
		if (SystemUtils.IS_OS_WINDOWS) {
			session.close();
			winsparkle_h.win_sparkle_cleanup();
		}
	}

	@Override
	public boolean enableAutoUpdate() throws AutoUpdateException {
		return false;
	}

	@Override
	public boolean disableAutoUpdate() throws AutoUpdateException {
		return false;
	}
}
