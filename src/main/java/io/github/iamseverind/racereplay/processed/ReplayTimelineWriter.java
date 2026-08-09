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
 * Writes a fixed-size replay timeline through random access.
 */
public final class ReplayTimelineWriter
        implements AutoCloseable {

    private final Path target;
    private final Path temporaryFile;
    private final ReplayTimelineHeader header;
    private final RandomAccessFile file;

    private boolean completed;
    private boolean closed;

    /**
     * Creates a new temporary timeline file.
     *
     * @param target final timeline file
     * @param header binary header
     * @throws IOException when the temporary file cannot be created
     */
    public ReplayTimelineWriter(
            final Path target,
            final ReplayTimelineHeader header)
            throws IOException {

        this.target =
                Objects.requireNonNull(
                        target,
                        "target");

        this.header =
                Objects.requireNonNull(
                        header,
                        "header");

        Files.createDirectories(
                target.getParent());

        temporaryFile =
                target.resolveSibling(
                        target.getFileName()
                        + ".part-"
                        + UUID.randomUUID());

        Files.createFile(temporaryFile);

        file =
                new RandomAccessFile(
                        temporaryFile.toFile(),
                        "rw");

        file.setLength(
                header.expectedFileSizeBytes());

        writeHeader();
        initializeFrameTimes();
    }

    /**
     * Writes one state at its deterministic frame and driver offset.
     *
     * @param frameIndex zero-based frame index
     * @param driverIndex zero-based driver index
     * @param state driver state
     * @throws IOException when writing fails
     */
    public void writeState(
            final int frameIndex,
            final int driverIndex,
            final ReplayDriverState state)
            throws IOException {

        ensureOpen();
        validateFrameIndex(frameIndex);
        validateDriverIndex(driverIndex);

        Objects.requireNonNull(
                state,
                "state");

        file.seek(
                stateOffset(
                        frameIndex,
                        driverIndex));

        file.writeInt(state.x());
        file.writeInt(state.y());
        file.writeInt(state.z());

        file.writeShort(state.speed());
        file.writeShort(state.rpm());

        file.writeByte(state.gear());
        file.writeByte(state.throttle());
        file.writeByte(state.brake());
        file.writeByte(state.drs());
        file.writeByte(state.position());
        file.writeByte(state.flags());

        file.writeShort(state.lapNumber());

        file.writeFloat(
                state.gapToLeaderSeconds());

        file.writeFloat(
                state.intervalSeconds());

        file.writeByte(
                state.tyreCompoundCode());

        file.writeByte(0);
        file.writeShort(0);
    }

    /**
     * Synchronizes and atomically publishes the timeline file.
     *
     * @throws IOException when publication fails
     */
    public void complete() throws IOException {
        ensureOpen();

        if (file.length()
                != header.expectedFileSizeBytes()) {

            throw new IOException(
                    "Unexpected timeline file size.");
        }

        file.getFD().sync();
        file.close();
        closed = true;

        moveAtomically(
                temporaryFile,
                target);

        completed = true;
    }

    /**
     * Removes an incomplete temporary timeline.
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

    private void writeHeader() throws IOException {
        file.seek(0);

        file.write(
                ReplayTimelineFormat.magicBytes());

        file.writeInt(
                header.formatVersion());

        file.writeLong(
                header.replayStart()
                        .toEpochMilli());

        file.writeInt(
                header.frameIntervalMillis());

        file.writeInt(
                header.frameCount());

        file.writeInt(
                header.driverCount());

        file.writeInt(
                ReplayTimelineFormat
                        .STATE_SIZE_BYTES);

        file.writeInt(
                header.headerSizeBytes());

        for (final int driverNumber :
                header.driverNumbers()) {

            file.writeInt(driverNumber);
        }
    }

    private void initializeFrameTimes()
            throws IOException {

        for (int frameIndex = 0;
                frameIndex < header.frameCount();
                frameIndex++) {

            final long elapsedMillis =
                    Math.multiplyExact(
                            (long) frameIndex,
                            header.frameIntervalMillis());

            if (elapsedMillis
                    > Integer.MAX_VALUE) {

                throw new IOException(
                        "Timeline duration exceeds binary format limit.");
            }

            file.seek(
                    frameOffset(frameIndex));

            file.writeInt(
                    (int) elapsedMillis);
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
                    "Timeline writer is closed.");
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
