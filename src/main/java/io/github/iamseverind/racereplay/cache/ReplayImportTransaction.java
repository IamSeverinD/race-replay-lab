package io.github.iamseverind.racereplay.cache;

import io.github.iamseverind.racereplay.core.CancellationSupport;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;

/**
 * Isolates one replay import until it is ready for atomic activation.
 */
public final class ReplayImportTransaction
        implements AutoCloseable {

    private static final String LOCK_FILE = ".import.lock";
    private static final String STAGING_PREFIX = ".import-staging-";
    private static final String REPLAY_VERSION_MARKER = "-replay-";

    private final Path cacheRoot;
    private final Path stagingRoot;
    private final FileChannel lockChannel;
    private final FileLock lock;

    private Path publishedDirectory;
    private boolean activated;
    private boolean closed;

    private ReplayImportTransaction(
            final Path cacheRoot,
            final Path stagingRoot,
            final FileChannel lockChannel,
            final FileLock lock) {

        this.cacheRoot = cacheRoot;
        this.stagingRoot = stagingRoot;
        this.lockChannel = lockChannel;
        this.lock = lock;
    }

    /**
     * Acquires the cache-wide import lock and creates isolated staging.
     *
     * @param cacheRoot application OpenF1 cache root
     * @return active import transaction
     * @throws IOException when another import is active or setup fails
     */
    public static ReplayImportTransaction begin(
            final Path cacheRoot)
            throws IOException {

        final Path normalizedRoot =
                Objects.requireNonNull(
                        cacheRoot,
                        "cacheRoot")
                        .toAbsolutePath()
                        .normalize();

        Files.createDirectories(normalizedRoot);

        final FileChannel lockChannel =
                FileChannel.open(
                        normalizedRoot.resolve(LOCK_FILE),
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE);

        FileLock lock = null;

        try {
            lock = tryAcquire(lockChannel);

            if (lock == null) {
                throw new IOException(
                        "Another replay import is already running.");
            }

            removeStaleStagingDirectories(normalizedRoot);

            final Path stagingRoot =
                    Files.createDirectory(
                            normalizedRoot.resolve(
                                    STAGING_PREFIX
                                    + UUID.randomUUID()));

            return new ReplayImportTransaction(
                    normalizedRoot,
                    stagingRoot,
                    lockChannel,
                    lock);
        } catch (final IOException | RuntimeException exception) {
            closeSetupResources(
                    lock,
                    lockChannel,
                    exception);

            throw exception;
        }
    }

    /**
     * Returns the temporary cache root used by cache-building services.
     *
     * @return isolated staging cache root
     */
    public Path stagingCacheRoot() {
        ensureOpen();
        return stagingRoot;
    }

    /**
     * Atomically publishes one completed direct child of the staging root.
     *
     * @param completedCache completed session cache directory
     * @return immutable cache directory ready for activation
     * @throws IOException when publication fails
     */
    public Path publish(
            final Path completedCache)
            throws IOException {

        ensureOpen();
        CancellationSupport.checkpoint();

        if (publishedDirectory != null) {
            throw new IllegalStateException(
                    "The import has already been published.");
        }

        final Path source =
                Objects.requireNonNull(
                        completedCache,
                        "completedCache")
                        .toAbsolutePath()
                        .normalize();

        if (!stagingRoot.equals(source.getParent())
                || !Files.isDirectory(source)) {

            throw new IOException(
                    "Published replay must be a staging child: "
                    + source);
        }

        final Path target =
                cacheRoot.resolve(
                        source.getFileName()
                        + REPLAY_VERSION_MARKER
                        + UUID.randomUUID());

        moveAtomically(source, target);
        publishedDirectory = target;
        return target;
    }

    /**
     * Atomically makes the published directory the active replay selection.
     *
     * @throws IOException when activation fails
     */
    public void activatePublished() throws IOException {
        ensureOpen();

        if (publishedDirectory == null) {
            throw new IllegalStateException(
                    "No published replay is available.");
        }

        CancellationSupport.checkpoint();

        ActiveReplaySelection.activate(
                cacheRoot,
                publishedDirectory);

        activated = true;
    }

    /**
     * Removes staging and any published replay that was not activated.
     *
     * @throws IOException when cleanup or lock release fails
     */
    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }

        closed = true;
        IOException failure = null;

        failure = deleteWithSuppression(
                stagingRoot,
                failure);

        if (!activated && publishedDirectory != null) {
            failure = deleteWithSuppression(
                    publishedDirectory,
                    failure);
        }

        try {
            lock.release();
        } catch (final IOException exception) {
            failure = appendFailure(failure, exception);
        }

        try {
            lockChannel.close();
        } catch (final IOException exception) {
            failure = appendFailure(failure, exception);
        }

        if (failure != null) {
            throw failure;
        }
    }

    private static FileLock tryAcquire(
            final FileChannel channel)
            throws IOException {

        try {
            return channel.tryLock();
        } catch (final OverlappingFileLockException exception) {
            return null;
        }
    }

    private static void removeStaleStagingDirectories(
            final Path cacheRoot)
            throws IOException {

        try (var children = Files.list(cacheRoot)) {
            for (final Path child : children.toList()) {
                if (Files.isDirectory(child)
                        && child.getFileName()
                                .toString()
                                .startsWith(STAGING_PREFIX)) {

                    deleteRecursively(child);
                }
            }
        }
    }

    private static void moveAtomically(
            final Path source,
            final Path target)
            throws IOException {

        try {
            Files.move(
                    source,
                    target,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (
                final AtomicMoveNotSupportedException exception) {

            Files.move(source, target);
        }
    }

    private static IOException deleteWithSuppression(
            final Path path,
            final IOException previous) {

        try {
            deleteRecursively(path);
            return previous;
        } catch (final IOException exception) {
            return appendFailure(previous, exception);
        }
    }

    private static void deleteRecursively(
            final Path directory)
            throws IOException {

        if (!Files.exists(directory)) {
            return;
        }

        try (var paths = Files.walk(directory)) {
            for (final Path path :
                    paths.sorted(
                            Comparator.reverseOrder())
                            .toList()) {

                Files.deleteIfExists(path);
            }
        }
    }

    private static IOException appendFailure(
            final IOException previous,
            final IOException next) {

        if (previous == null) {
            return next;
        }

        previous.addSuppressed(next);
        return previous;
    }

    private static void closeSetupResources(
            final FileLock lock,
            final FileChannel channel,
            final Throwable failure) {

        if (lock != null) {
            try {
                lock.release();
            } catch (final IOException exception) {
                failure.addSuppressed(exception);
            }
        }

        try {
            channel.close();
        } catch (final IOException exception) {
            failure.addSuppressed(exception);
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException(
                    "Import transaction is closed.");
        }
    }
}
