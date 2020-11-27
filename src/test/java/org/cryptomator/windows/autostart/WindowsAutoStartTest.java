package org.cryptomator.windows.autostart;

import org.cryptomator.integrations.autostart.ToggleAutoStartFailedException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public class WindowsAutoStartTest {

	private RegistryStrategy registryStrategy;
	private StartupFolderStrategy folderStrategy;

	private WindowsAutoStart autoStart;

	@BeforeEach
	public void init() {
		this.registryStrategy = Mockito.mock(RegistryStrategy.class);
		this.folderStrategy = Mockito.mock(StartupFolderStrategy.class);
		this.autoStart = new WindowsAutoStart(folderStrategy, registryStrategy);
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

			Assertions.assertTimeoutPreemptively(Duration.ofMillis(300), autoStart::enable);

			Mockito.verify(registryStrategy).enable();
			Mockito.verify(folderStrategy).enable();
		}

		@Test
		public void testDoNotUseFolderOnRegistrySuccess() throws ToggleAutoStartFailedException {
			Mockito.when(registryStrategy.enable()).thenReturn(CompletableFuture.completedFuture(null));
			Mockito.when(folderStrategy.enable()).thenReturn(CompletableFuture.completedFuture(null));

			Assertions.assertTimeoutPreemptively(Duration.ofMillis(300), autoStart::enable);

			Mockito.verify(registryStrategy).enable();
			Mockito.verify(folderStrategy, Mockito.never()).enable();
		}

		@Test
		public void testThrowExceptionIfAllStrategiesFail() {
			Mockito.when(registryStrategy.enable()).thenReturn(CompletableFuture.failedFuture(new IOException("test1")));
			Mockito.when(folderStrategy.enable()).thenReturn(CompletableFuture.failedFuture(new IOException("test2")));

			Assertions.assertTimeoutPreemptively(Duration.ofMillis(300), () ->
					Assertions.assertThrows(ToggleAutoStartFailedException.class, autoStart::enable)
			);
			Mockito.verify(folderStrategy).enable();
			Mockito.verify(registryStrategy).enable();
		}
	}

	@Nested
	@TestInstance(TestInstance.Lifecycle.PER_CLASS) //see https://github.com/junit-team/junit5/issues/1229
	public class DisableTests {

		@ParameterizedTest
		@MethodSource("provideAllStrategiesAreAppliedALWAYS")
		public void testAllStrategiesAreAppliedALWAYS(CompletableFuture registryResult, CompletableFuture folderResult) {
			Mockito.when(registryStrategy.disable()).thenReturn(registryResult);
			Mockito.when(folderStrategy.disable()).thenReturn(folderResult);

			Assertions.assertTimeoutPreemptively(Duration.ofMillis(300), () -> {
				try {
					autoStart.disable();
				} catch (ToggleAutoStartFailedException e) {
					// noop
				}
			});

			Mockito.verify(folderStrategy).disable();
			Mockito.verify(registryStrategy).disable();
		}

		@Test
		public void testThrowExceptionIfAnyStrategyFails() {
			Mockito.when(folderStrategy.disable()).thenReturn(CompletableFuture.completedFuture(null));
			Mockito.when(registryStrategy.disable()).thenReturn(CompletableFuture.failedFuture(new IOException("test")));

			Assertions.assertTimeoutPreemptively(Duration.ofMillis(300), () ->
					Assertions.assertThrows(ToggleAutoStartFailedException.class, autoStart::disable)
			);

			Mockito.when(folderStrategy.disable()).thenReturn(CompletableFuture.failedFuture(new IOException("test")));
			Mockito.when(registryStrategy.disable()).thenReturn(CompletableFuture.completedFuture(null));

			Assertions.assertTimeoutPreemptively(Duration.ofMillis(300), () ->
					Assertions.assertThrows(ToggleAutoStartFailedException.class, autoStart::disable)
			);

		}

		private Stream<Arguments> provideAllStrategiesAreAppliedALWAYS() {
			var success = CompletableFuture.completedFuture(null);
			var failure = CompletableFuture.failedFuture(new IOException("fail"));
			return Stream.of(
					Arguments.of(success, success),
					Arguments.of(success, failure),
					Arguments.of(failure, failure),
					Arguments.of(failure, success)
			);
		}
	}

}
