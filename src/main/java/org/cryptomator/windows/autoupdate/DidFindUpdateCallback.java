package org.cryptomator.windows.autoupdate;

import org.purejava.windows.win_sparkle_did_find_update_callback_t;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DidFindUpdateCallback implements win_sparkle_did_find_update_callback_t {

	private static final Logger LOG = LoggerFactory.getLogger(DidFindUpdateCallback.class);

	@Override
	public void apply() {
		LOG.info("Update found to newer version of Cryptomator");
	}
}
