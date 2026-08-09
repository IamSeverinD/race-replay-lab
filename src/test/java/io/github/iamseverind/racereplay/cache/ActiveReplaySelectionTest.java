package io.github.iamseverind.racereplay.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests persistent local replay selection.
 */
final class ActiveReplaySelectionTest {

    @TempDir
    private Path temporaryDirectory;

    /**
     * Uses the non-existing default location without a selection.
     *
     * @throws Exception when resolution fails
     */
    @Test
    void resolvesDefaultWithoutSelection()
            throws Exception {

        assertEquals(
                temporaryDirectory
                        .toAbsolutePath()
                        .resolve("default-replay"),
                ActiveReplaySelection.resolve(
                        temporaryDirectory));
    }

    /**
     * Activates and clears a complete direct child cache.
     *
     * @throws Exception when fixture creation fails
     */
    @Test
    void activatesAndClearsCompleteReplay()
            throws Exception {

        final Path replay =
                createCompleteReplay(
                        "2026-belgium-race-1234");

        ActiveReplaySelection.activate(
                temporaryDirectory,
                replay);

        assertEquals(
                replay.toAbsolutePath(),
                ActiveReplaySelection.resolve(
                        temporaryDirectory));

        assertTrue(
                ActiveReplaySelection.clear(
                        temporaryDirectory));

        assertFalse(
                ActiveReplaySelection.clear(
                        temporaryDirectory));
    }

    /**
     * Rejects incomplete and out-of-root cache directories.
     *
     * @throws Exception when fixture creation fails
     */
    @Test
    void rejectsUnsafeActivationTargets()
            throws Exception {

        final Path incomplete =
                temporaryDirectory.resolve(
                        "incomplete");

        Files.createDirectories(incomplete);

        assertThrows(
                IOException.class,
                () -> ActiveReplaySelection.activate(
                        temporaryDirectory,
                        incomplete));

        final Path outside =
                Files.createTempDirectory(
                        "race-replay-selection-test-");

        assertThrows(
                IOException.class,
                () -> ActiveReplaySelection.activate(
                        temporaryDirectory,
                        outside));
    }

    /**
     * Rejects a selection containing path traversal.
     *
     * @throws Exception when fixture creation fails
     */
    @Test
    void rejectsPathTraversalSelection()
            throws Exception {

        Files.writeString(
                temporaryDirectory.resolve(
                        "active-replay.txt"),
                "../outside",
                StandardCharsets.UTF_8);

        assertThrows(
                IOException.class,
                () -> ActiveReplaySelection.resolve(
                        temporaryDirectory));
    }

    private Path createCompleteReplay(
            final String directoryName)
            throws IOException {

        final Path replay =
                temporaryDirectory.resolve(
                        directoryName);

        final Path processed =
                replay.resolve(
                        "processed");

        Files.createDirectories(processed);

        Files.write(
                processed.resolve(
                        "timeline.bin"),
                new byte[] {1});

        Files.writeString(
                processed.resolve(
                        "drivers.json"),
                "[]",
                StandardCharsets.UTF_8);

        return replay;
    }
}
