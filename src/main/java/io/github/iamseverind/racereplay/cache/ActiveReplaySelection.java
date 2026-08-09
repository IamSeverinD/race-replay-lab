package io.github.iamseverind.racereplay.cache;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.UUID;

/**
 * Stores the locally selected replay without copying its processed data.
 */
public final class ActiveReplaySelection {

    private static final String DEFAULT_REPLAY_DIRECTORY =
            "default-replay";

    private static final String SELECTION_FILE =
            "active-replay.txt";

    private ActiveReplaySelection() {
    }

    /**
     * Resolves the selected replay or the empty default location.
     *
     * @param cacheRoot OpenF1 application cache root
     * @return selected session cache directory
     * @throws IOException when the selection is malformed or incomplete
     */
    public static Path resolve(
            final Path cacheRoot)
            throws IOException {

        final Path normalizedRoot =
                normalizeRoot(cacheRoot);

        final Path selectionFile =
                normalizedRoot.resolve(
                        SELECTION_FILE);

        if (!Files.exists(selectionFile)) {
            return normalizedRoot.resolve(
                    DEFAULT_REPLAY_DIRECTORY);
        }

        if (!Files.isRegularFile(selectionFile)) {
            throw new IOException(
                    "Active replay selection is not a regular file: "
                    + selectionFile);
        }

        final String directoryName =
                Files.readString(
                        selectionFile,
                        StandardCharsets.UTF_8)
                        .strip();

        final Path selected =
                resolveDirectChild(
                        normalizedRoot,
                        directoryName);

        requireCompleteReplay(selected);
        return selected;
    }

    /**
     * Atomically selects one complete session cache.
     *
     * @param cacheRoot OpenF1 application cache root
     * @param sessionCacheDirectory completed session cache
     * @throws IOException when validation or activation fails
     */
    public static void activate(
            final Path cacheRoot,
            final Path sessionCacheDirectory)
            throws IOException {

        final Path normalizedRoot =
                normalizeRoot(cacheRoot);

        final Path selected =
                Objects.requireNonNull(
                        sessionCacheDirectory,
                        "sessionCacheDirectory")
                        .toAbsolutePath()
                        .normalize();

        if (!normalizedRoot.equals(
                selected.getParent())) {

            throw new IOException(
                    "Replay cache must be a direct child of: "
                    + normalizedRoot);
        }

        requireCompleteReplay(selected);
        Files.createDirectories(normalizedRoot);

        final Path selectionFile =
                normalizedRoot.resolve(
                        SELECTION_FILE);

        final Path temporaryFile =
                normalizedRoot.resolve(
                        SELECTION_FILE
                        + "."
                        + UUID.randomUUID()
                        + ".tmp");

        try {
            Files.writeString(
                    temporaryFile,
                    selected.getFileName().toString()
                    + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);

            atomicReplace(
                    temporaryFile,
                    selectionFile);
        } finally {
            Files.deleteIfExists(
                    temporaryFile);
        }
    }

    /**
     * Clears the local selection so the synthetic fallback is used.
     *
     * @param cacheRoot OpenF1 application cache root
     * @return whether an existing selection was removed
     * @throws IOException when the selection cannot be removed
     */
    public static boolean clear(
            final Path cacheRoot)
            throws IOException {

        return Files.deleteIfExists(
                normalizeRoot(cacheRoot)
                        .resolve(SELECTION_FILE));
    }

    private static Path normalizeRoot(
            final Path cacheRoot) {

        return Objects.requireNonNull(
                cacheRoot,
                "cacheRoot")
                .toAbsolutePath()
                .normalize();
    }

    private static Path resolveDirectChild(
            final Path cacheRoot,
            final String directoryName)
            throws IOException {

        if (directoryName.isBlank()) {
            throw new IOException(
                    "Active replay selection is empty.");
        }

        final Path relative;

        try {
            relative = Path.of(directoryName);
        } catch (final RuntimeException exception) {
            throw new IOException(
                    "Active replay selection is invalid.",
                    exception);
        }

        if (relative.isAbsolute()
                || relative.getNameCount() != 1
                || ".".equals(directoryName)
                || "..".equals(directoryName)) {

            throw new IOException(
                    "Active replay must name one cache directory.");
        }

        final Path selected =
                cacheRoot.resolve(relative)
                        .normalize();

        if (!cacheRoot.equals(selected.getParent())) {
            throw new IOException(
                    "Active replay escapes the cache root.");
        }

        return selected;
    }

    private static void requireCompleteReplay(
            final Path cacheDirectory)
            throws IOException {

        final Path processedDirectory =
                cacheDirectory.resolve(
                        "processed");

        final Path timelineFile =
                processedDirectory.resolve(
                        "timeline.bin");

        final Path driversFile =
                processedDirectory.resolve(
                        "drivers.json");

        if (!Files.isRegularFile(timelineFile)
                || !Files.isRegularFile(driversFile)) {

            throw new IOException(
                    "Selected replay cache is incomplete: "
                    + cacheDirectory);
        }
    }

    private static void atomicReplace(
            final Path source,
            final Path target)
            throws IOException {

        try {
            Files.move(
                    source,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (final AtomicMoveNotSupportedException exception) {
            Files.move(
                    source,
                    target,
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
