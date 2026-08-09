package io.github.iamseverind.racereplay.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests isolated and atomic replay import publication.
 */
final class ReplayImportTransactionTest {

    @TempDir
    private Path temporaryDirectory;

    /**
     * Removes incomplete staging when a transaction closes.
     *
     * @throws Exception when fixture setup fails
     */
    @Test
    void removesIncompleteStagingOnClose()
            throws Exception {

        final Path stagingRoot;

        try (ReplayImportTransaction transaction =
                ReplayImportTransaction.begin(
                        temporaryDirectory)) {

            stagingRoot = transaction.stagingCacheRoot();

            Files.writeString(
                    stagingRoot.resolve("partial.json"),
                    "{}",
                    StandardCharsets.UTF_8);
        }

        assertFalse(Files.exists(stagingRoot));
    }

    /**
     * Keeps only a published replay whose activation was confirmed.
     *
     * @throws Exception when fixture setup fails
     */
    @Test
    void publishesAndKeepsActivatedReplay()
            throws Exception {

        final Path published;
        final Path stagingRoot;

        try (ReplayImportTransaction transaction =
                ReplayImportTransaction.begin(
                        temporaryDirectory)) {

            stagingRoot = transaction.stagingCacheRoot();

            final Path candidate =
                    createCompleteReplay(
                            stagingRoot,
                            "2026-belgium-race-1234");

            published = transaction.publish(candidate);

            transaction.activatePublished();
        }

        assertFalse(Files.exists(stagingRoot));
        assertTrue(Files.isDirectory(published));
        assertEquals(
                published,
                ActiveReplaySelection.resolve(
                        temporaryDirectory));
    }

    /**
     * Deletes unactivated publication and preserves the old selection.
     *
     * @throws Exception when fixture setup fails
     */
    @Test
    void failedActivationPreservesPreviousReplay()
            throws Exception {

        final Path previous =
                createCompleteReplay(
                        temporaryDirectory,
                        "previous-replay");

        ActiveReplaySelection.activate(
                temporaryDirectory,
                previous);

        final Path published;

        try (ReplayImportTransaction transaction =
                ReplayImportTransaction.begin(
                        temporaryDirectory)) {

            final Path candidate =
                    Files.createDirectory(
                            transaction.stagingCacheRoot()
                                    .resolve("replacement-replay"));

            published = transaction.publish(candidate);

            assertThrows(
                    IOException.class,
                    transaction::activatePublished);
        }

        assertFalse(Files.exists(published));
        assertEquals(
                previous,
                ActiveReplaySelection.resolve(
                        temporaryDirectory));
    }

    /**
     * Cancels before publication without changing the active replay.
     *
     * @throws Exception when fixture setup fails
     */
    @Test
    void cancelledPublicationPreservesPreviousReplay()
            throws Exception {

        final Path previous =
                createCompleteReplay(
                        temporaryDirectory,
                        "previous-replay");

        ActiveReplaySelection.activate(
                temporaryDirectory,
                previous);

        try (ReplayImportTransaction transaction =
                ReplayImportTransaction.begin(
                        temporaryDirectory)) {

            final Path candidate =
                    createCompleteReplay(
                            transaction.stagingCacheRoot(),
                            "replacement-replay");

            Thread.currentThread().interrupt();

            try {
                assertThrows(
                        CancellationException.class,
                        () -> transaction.publish(candidate));
            } finally {
                Thread.interrupted();
            }
        }

        assertEquals(
                previous,
                ActiveReplaySelection.resolve(
                        temporaryDirectory));
    }

    /**
     * Prevents two imports from modifying the cache concurrently.
     *
     * @throws Exception when the first transaction cannot start
     */
    @Test
    void rejectsConcurrentImport()
            throws Exception {

        try (ReplayImportTransaction transaction =
                ReplayImportTransaction.begin(
                        temporaryDirectory)) {

            assertTrue(
                    Files.isDirectory(
                            transaction.stagingCacheRoot()));

            assertThrows(
                    IOException.class,
                    () -> ReplayImportTransaction.begin(
                            temporaryDirectory));
        }
    }

    /**
     * Removes staging left by a terminated previous process.
     *
     * @throws Exception when fixture setup fails
     */
    @Test
    void removesStaleStagingBeforeImport()
            throws Exception {

        final Path stale =
                temporaryDirectory.resolve(
                        ".import-staging-stale");

        Files.createDirectories(stale);
        Files.writeString(
                stale.resolve("partial.json"),
                "{}",
                StandardCharsets.UTF_8);

        try (ReplayImportTransaction transaction =
                ReplayImportTransaction.begin(
                        temporaryDirectory)) {

            assertTrue(
                    Files.isDirectory(
                            transaction.stagingCacheRoot()));

            assertFalse(Files.exists(stale));
        }
    }

    private static Path createCompleteReplay(
            final Path root,
            final String directoryName)
            throws IOException {

        final Path replay = root.resolve(directoryName);
        final Path processed = replay.resolve("processed");

        Files.createDirectories(processed);
        Files.write(
                processed.resolve("timeline.bin"),
                new byte[] {1});

        Files.writeString(
                processed.resolve("drivers.json"),
                "[]",
                StandardCharsets.UTF_8);

        return replay.toAbsolutePath();
    }
}
