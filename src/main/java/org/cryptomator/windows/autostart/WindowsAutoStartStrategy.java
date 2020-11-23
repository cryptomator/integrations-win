package org.cryptomator.windows.autostart;

import java.util.concurrent.CompletableFuture;

interface WindowsAutoStartStrategy {

	CompletableFuture<Boolean> isEnabled();

	CompletableFuture<Void> enable();

	CompletableFuture<Void> disable();
}
