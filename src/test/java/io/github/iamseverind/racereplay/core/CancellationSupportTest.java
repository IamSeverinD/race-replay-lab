package io.github.iamseverind.racereplay.core;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;

/**
 * Tests cooperative cancellation checkpoints.
 */
final class CancellationSupportTest {

    /**
     * Preserves the interrupt flag while reporting cancellation.
     */
    @Test
    void reportsInterruptedThread() {
        Thread.currentThread().interrupt();

        try {
            assertThrows(
                    CancellationException.class,
                    CancellationSupport::checkpoint);

            assertTrue(
                    Thread.currentThread()
                            .isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }
}
