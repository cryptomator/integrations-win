package org.cryptomator.windows.autostart;

import org.cryptomator.integrations.autostart.ToggleAutoStartFailedException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public class WindowsAutoStartTest {

	private RegistryStrategy registryStrategy;
	private StartupFolderStrategy folderStrategy;

	private WindowsAutoStart autoStart;

	@BeforeEach
	public void init() {
		this.registryStrategy = Mockito.mock(RegistryStrategy.class);
		this.folderStrategy = Mockito.mock(StartupFolderStrategy.class);
		this.autoStart = new WindowsAutoStart(folderStrategy, registryStrategy, false, false);
	}

	@Nested
	public class IsEnableTests {

		@Test
		public void testReturnsTrueIfBothAreTrue() {
			Mockito.when(registryStrategy.isEnabled()).thenReturn(CompletableFuture.completedFuture(true));
			Mockito.when(folderStrategy.isEnabled()).thenReturn(CompletableFuture.completedFuture(true));

			boolean result = autoStart.isEnabled();

			Assertions.assertTrue(result);
		}

		@Test
		public void testReturnsTrueIfOneReturnsFalse() {
			Mockito.when(registryStrategy.isEnabled()).thenReturn(CompletableFuture.completedFuture(true));
			Mockito.when(folderStrategy.isEnabled()).thenReturn(CompletableFuture.completedFuture(false));

			boolean result1 = autoStart.isEnabled();

			Mockito.when(registryStrategy.isEnabled()).thenReturn(CompletableFuture.completedFuture(false));
			Mockito.when(folderStrategy.isEnabled()).thenReturn(CompletableFuture.completedFuture(true));

			boolean result2 = autoStart.isEnabled();

			Assertions.assertTrue(result1);
			Assertions.assertTrue(result2);
		}

		@Test
		public void testReturnsFalseIfBothReturnFalse() {
			Mockito.when(registryStrategy.isEnabled()).thenReturn(CompletableFuture.completedFuture(false));
			Mockito.when(folderStrategy.isEnabled()).thenReturn(CompletableFuture.completedFuture(false));

			boolean result = autoStart.isEnabled();
			Assertions.assertFalse(result);
		}

		@Test
		public void testReturnsFalseOnAnyFailedStage() {
			Mockito.when(registryStrategy.isEnabled()).thenReturn(CompletableFuture.failedFuture(new IOException("test")));
			Mockito.when(folderStrategy.isEnabled()).thenReturn(CompletableFuture.completedFuture(true));

			boolean result = autoStart.isEnabled();
			Assertions.assertFalse(result);

		}

	}

	@Nested
	public class EnableTests {

		@Test
		public void testTryFolderOnRegistryFailure() throws ToggleAutoStartFailedException {
			Mockito.when(registryStrategy.enable()).thenReturn(CompletableFuture.failedFuture(new IOException("test")));
			Mockito.when(folderStrategy.enable()).thenReturn(CompletableFuture.completedFuture(null));

			autoStart.enable();

			Mockito.verify(registryStrategy).enable();
			Mockito.verify(folderStrategy).enable();
		}

		@Test
		public void testDoNotUseFolderOnRegistrySuccess() throws ToggleAutoStartFailedException {
			Mockito.when(registryStrategy.enable()).thenReturn(CompletableFuture.completedFuture(null));
			Mockito.when(folderStrategy.enable()).thenReturn(CompletableFuture.completedFuture(null));

			autoStart.enable();

			Mockito.verify(registryStrategy).enable();
			Mockito.verify(folderStrategy, Mockito.never()).enable();
		}

		@Test
		public void testThrowExceptionIfAllStrategiesFail() {
			Mockito.when(registryStrategy.enable()).thenReturn(CompletableFuture.failedFuture(new IOException("test1")));
			Mockito.when(folderStrategy.enable()).thenReturn(CompletableFuture.failedFuture(new IOException("test2")));

			Assertions.assertThrows(ToggleAutoStartFailedException.class, autoStart::enable);
			Mockito.verify(folderStrategy).enable();
			Mockito.verify(registryStrategy).enable();
		}
	}

	@Nested
	public class DisableTests {

		@Test
		public void testDisableIfRegistryActive() throws ToggleAutoStartFailedException {
			var primedAutoStart = new WindowsAutoStart(folderStrategy, registryStrategy, false, true);
			Mockito.when(registryStrategy.disable()).thenReturn(CompletableFuture.completedFuture(null));

			primedAutoStart.disable();

			Mockito.verify(registryStrategy).disable();
		}

		@Test
		public void testDoNothingIfRegistryNotActive() throws ToggleAutoStartFailedException {
			var primedAutoStart = new WindowsAutoStart(folderStrategy, registryStrategy, false, false);
			Mockito.when(registryStrategy.disable()).thenReturn(CompletableFuture.completedFuture(null));

			primedAutoStart.disable();

			Mockito.verify(folderStrategy, Mockito.never()).disable();
		}

		@Test
		public void testDisableIfFolderActive() throws ToggleAutoStartFailedException {
			var primedAutoStart = new WindowsAutoStart(folderStrategy, registryStrategy, true, false);
			Mockito.when(folderStrategy.disable()).thenReturn(CompletableFuture.completedFuture(null));

			primedAutoStart.disable();

			Mockito.verify(folderStrategy).disable();
		}

		@Test
		public void testDoNothingIfFolderNotActive() throws ToggleAutoStartFailedException {
			var primedAutoStart = new WindowsAutoStart(folderStrategy, registryStrategy, false, false);
			Mockito.when(folderStrategy.disable()).thenReturn(CompletableFuture.completedFuture(null));

			primedAutoStart.disable();

			Mockito.verify(folderStrategy, Mockito.never()).disable();
		}

		@Test
		public void testThrowExceptionIfAnyStrategyFails() {
			var primedAutoStart1 = new WindowsAutoStart(folderStrategy, registryStrategy, true, true);
			Mockito.when(folderStrategy.disable()).thenReturn(CompletableFuture.completedFuture(null));
			Mockito.when(registryStrategy.disable()).thenReturn(CompletableFuture.failedFuture(new IOException("test")));

			Assertions.assertThrows(ToggleAutoStartFailedException.class, () -> primedAutoStart1.disable());

			var primedAutoStart2 = new WindowsAutoStart(folderStrategy, registryStrategy, true, true);
			Mockito.when(folderStrategy.disable()).thenReturn(CompletableFuture.failedFuture(new IOException("test")));
			Mockito.when(registryStrategy.disable()).thenReturn(CompletableFuture.completedFuture(null));

			Assertions.assertThrows(ToggleAutoStartFailedException.class, () -> primedAutoStart2.disable());
		}

	}
}
