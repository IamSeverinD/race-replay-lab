package io.github.iamseverind.racereplay.processed;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.UUID;

/**
 * Atomically patches metadata fields in an existing timeline.
 *
 * <p>The original file remains unchanged until {@link #complete()}
 * successfully validates and publishes the temporary copy.</p>
 */
public final class ReplayTimelinePatcher
        implements AutoCloseable {

    private final Path timelineFile;
    private final Path temporaryFile;
    private final ReplayTimelineHeader header;
    private final RandomAccessFile file;

    private boolean completed;
    private boolean closed;

    /**
     * Creates a patchable temporary copy of a timeline.
     *
     * @param timelineFile completed timeline file
     * @throws IOException when the source cannot be validated or copied
     */
    public ReplayTimelinePatcher(
            final Path timelineFile)
            throws IOException {

        this.timelineFile =
                Objects.requireNonNull(
                        timelineFile,
                        "timelineFile");

        if (!Files.isRegularFile(timelineFile)) {
            throw new IOException(
                    "Timeline file does not exist: "
                    + timelineFile);
        }

        try (ReplayTimelineReader reader =
                new ReplayTimelineReader(
                        timelineFile)) {

            header = reader.header();
        }

        temporaryFile =
                timelineFile.resolveSibling(
                        timelineFile.getFileName()
                        + ".enrich-part-"
                        + UUID.randomUUID());

        RandomAccessFile openedFile = null;

        try {
            Files.copy(
                    timelineFile,
                    temporaryFile,
                    StandardCopyOption.COPY_ATTRIBUTES);

            openedFile =
                    new RandomAccessFile(
                            temporaryFile.toFile(),
                            "rw");
        } catch (final IOException exception) {
            if (openedFile != null) {
                openedFile.close();
            }

            Files.deleteIfExists(
                    temporaryFile);

            throw exception;
        }

        file = openedFile;
    }

    /**
     * Returns the validated timeline header.
     *
     * @return timeline header
     */
    public ReplayTimelineHeader header() {
        return header;
    }

    /**
     * Patches metadata for one frame and driver.
     *
     * @param frameIndex zero-based frame index
     * @param driverIndex zero-based driver index
     * @param metadata metadata values
     * @throws IOException when the patch cannot be written
     */
    public void patchMetadata(
            final int frameIndex,
            final int driverIndex,
            final ReplayTimelineMetadata metadata)
            throws IOException {

        ensureOpen();
        validateFrameIndex(frameIndex);
        validateDriverIndex(driverIndex);

        Objects.requireNonNull(
                metadata,
                "metadata");

        final long stateOffset =
                stateOffset(
                        frameIndex,
                        driverIndex);

        file.seek(
                stateOffset
                + ReplayTimelineFormat
                        .STATE_FLAGS_OFFSET_BYTES);

        final int existingFlags =
                file.readUnsignedByte();

        final int patchedFlags =
                (existingFlags
                        & ~ReplayTimelineFormat
                                .FLAG_METADATA_MASK)
                | metadata.validityFlags();

        file.seek(
                stateOffset
                + ReplayTimelineFormat
                        .STATE_POSITION_OFFSET_BYTES);

        file.writeByte(
                metadata.position());

        file.writeByte(
                patchedFlags);

        file.writeShort(
                metadata.lapNumber());

        file.writeFloat(
                metadata.gapToLeaderSeconds());

        file.writeFloat(
                metadata.intervalSeconds());

        file.writeByte(
                metadata.tyreCompoundCode());
    }

    /**
     * Validates and atomically publishes the patched file.
     *
     * @throws IOException when validation or publication fails
     */
    public void complete() throws IOException {
        ensureOpen();

        if (file.length()
                != header.expectedFileSizeBytes()) {

            throw new IOException(
                    "Patched timeline has an unexpected size.");
        }

        file.getFD().sync();
        file.close();
        closed = true;

        validateTemporaryFile();

        moveAtomically(
                temporaryFile,
                timelineFile);

        completed = true;
    }

    /**
     * Closes and removes an unpublished temporary file.
     *
     * @throws IOException when cleanup fails
     */
    @Override
    public void close() throws IOException {
        if (!closed) {
            file.close();
            closed = true;
        }

        if (!completed) {
            Files.deleteIfExists(
                    temporaryFile);
        }
    }

    private void validateTemporaryFile()
            throws IOException {

        try (ReplayTimelineReader reader =
                new ReplayTimelineReader(
                        temporaryFile)) {

            if (!reader.header().equals(header)) {
                throw new IOException(
                        "Patched timeline header changed.");
            }
        }
    }

    private long frameOffset(
            final int frameIndex) {

        return Math.addExact(
                header.headerSizeBytes(),
                Math.multiplyExact(
                        (long) frameIndex,
                        header.frameSizeBytes()));
    }

    private long stateOffset(
            final int frameIndex,
            final int driverIndex) {

        return Math.addExact(
                frameOffset(frameIndex),
                Math.addExact(
                        Integer.BYTES,
                        Math.multiplyExact(
                                (long) driverIndex,
                                ReplayTimelineFormat
                                        .STATE_SIZE_BYTES)));
    }

    private void validateFrameIndex(
            final int frameIndex) {

        if (frameIndex < 0
                || frameIndex >= header.frameCount()) {

            throw new IndexOutOfBoundsException(
                    "Invalid frame index: "
                    + frameIndex);
        }
    }

    private void validateDriverIndex(
            final int driverIndex) {

        if (driverIndex < 0
                || driverIndex >= header.driverCount()) {

            throw new IndexOutOfBoundsException(
                    "Invalid driver index: "
                    + driverIndex);
        }
    }

    private void ensureOpen() throws IOException {
        if (closed) {
            throw new IOException(
                    "Timeline patcher is closed.");
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
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (
                final AtomicMoveNotSupportedException exception) {

            Files.move(
                    source,
                    target,
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
