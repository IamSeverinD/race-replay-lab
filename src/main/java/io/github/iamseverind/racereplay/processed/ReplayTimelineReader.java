package io.github.iamseverind.racereplay.processed;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Reads and validates a binary replay timeline.
 */
public final class ReplayTimelineReader
        implements AutoCloseable {

    private final RandomAccessFile file;
    private final ReplayTimelineHeader header;

    private boolean closed;

    /**
     * Opens and validates a timeline file.
     *
     * @param timelineFile timeline path
     * @throws IOException when the file is invalid
     */
    public ReplayTimelineReader(
            final Path timelineFile)
            throws IOException {

        Objects.requireNonNull(
                timelineFile,
                "timelineFile");

        file =
                new RandomAccessFile(
                        timelineFile.toFile(),
                        "r");

        try {
            header =
                    readHeader(file);

            if (file.length()
                    != header.expectedFileSizeBytes()) {

                throw new IOException(
                        "Timeline size does not match its header.");
            }
        } catch (final IOException exception) {
            file.close();
            throw exception;
        }
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
     * Reads a frame's elapsed milliseconds.
     *
     * @param frameIndex frame index
     * @return elapsed replay milliseconds
     * @throws IOException when reading fails
     */
    public int readElapsedMillis(
            final int frameIndex)
            throws IOException {

        ensureOpen();
        validateFrameIndex(frameIndex);

        file.seek(
                frameOffset(frameIndex));

        return file.readInt();
    }

    /**
     * Reads one complete frame with all drivers.
     *
     * <p>The frame is decoded using one seek followed by sequential reads.
     * Driver states use the fixed order stored in the timeline header.</p>
     *
     * @param frameIndex frame index
     * @return complete decoded frame
     * @throws IOException when reading fails
     */
    public ReplayTimelineFrame readFrame(
            final int frameIndex)
            throws IOException {

        ensureOpen();
        validateFrameIndex(frameIndex);

        file.seek(
                frameOffset(frameIndex));

        final int elapsedMillis =
                file.readInt();

        final List<ReplayDriverState> drivers =
                new ArrayList<>(
                        header.driverCount());

        for (int driverIndex = 0;
                driverIndex < header.driverCount();
                driverIndex++) {

            drivers.add(
                    readDriverState(file));
        }

        return new ReplayTimelineFrame(
                elapsedMillis,
                drivers);
    }

    /**
     * Reads one fixed-size driver state.
     *
     * @param frameIndex frame index
     * @param driverIndex driver index
     * @return decoded driver state
     * @throws IOException when reading fails
     */
    public ReplayDriverState readState(
            final int frameIndex,
            final int driverIndex)
            throws IOException {

        ensureOpen();
        validateFrameIndex(frameIndex);
        validateDriverIndex(driverIndex);

        file.seek(
                stateOffset(
                        frameIndex,
                        driverIndex));

        return readDriverState(file);
    }

    /**
     * Closes the timeline file.
     *
     * @throws IOException when closing fails
     */
    @Override
    public void close() throws IOException {
        if (!closed) {
            file.close();
            closed = true;
        }
    }

    private static ReplayDriverState readDriverState(
            final RandomAccessFile source)
            throws IOException {

        final int x = source.readInt();
        final int y = source.readInt();
        final int z = source.readInt();

        final int speed =
                source.readUnsignedShort();

        final int rpm =
                source.readUnsignedShort();

        final int gear =
                source.readUnsignedByte();

        final int throttle =
                source.readUnsignedByte();

        final int brake =
                source.readUnsignedByte();

        final int drs =
                source.readUnsignedByte();

        final int position =
                source.readUnsignedByte();

        final int flags =
                source.readUnsignedByte();

        final int lapNumber =
                source.readUnsignedShort();

        final float gapToLeaderSeconds =
                source.readFloat();

        final float intervalSeconds =
                source.readFloat();

        final int tyreCompoundCode =
                source.readUnsignedByte();

        source.readUnsignedByte();
        source.readUnsignedShort();

        return new ReplayDriverState(
                x,
                y,
                z,
                speed,
                rpm,
                gear,
                throttle,
                brake,
                drs,
                position,
                flags,
                lapNumber,
                gapToLeaderSeconds,
                intervalSeconds,
                tyreCompoundCode);
    }

    private static ReplayTimelineHeader readHeader(
            final RandomAccessFile source)
            throws IOException {

        source.seek(0);

        final byte[] actualMagic =
                new byte[
                        ReplayTimelineFormat
                                .magicBytes()
                                .length];

        source.readFully(actualMagic);

        if (!Arrays.equals(
                actualMagic,
                ReplayTimelineFormat.magicBytes())) {

            throw new IOException(
                    "Invalid timeline magic.");
        }

        final int version =
                source.readInt();

        final long replayStartEpochMillis =
                source.readLong();

        final int frameIntervalMillis =
                source.readInt();

        final int frameCount =
                source.readInt();

        final int driverCount =
                source.readInt();

        final int stateSizeBytes =
                source.readInt();

        final int headerSizeBytes =
                source.readInt();

        if (stateSizeBytes
                != ReplayTimelineFormat
                        .STATE_SIZE_BYTES) {

            throw new IOException(
                    "Unsupported timeline state size: "
                    + stateSizeBytes);
        }

        if (headerSizeBytes
                != ReplayTimelineFormat
                        .headerSizeBytes(driverCount)) {

            throw new IOException(
                    "Invalid timeline header size.");
        }

        final List<Integer> driverNumbers =
                new ArrayList<>(driverCount);

        for (int index = 0;
                index < driverCount;
                index++) {

            driverNumbers.add(
                    source.readInt());
        }

        try {
            return new ReplayTimelineHeader(
                    version,
                    Instant.ofEpochMilli(
                            replayStartEpochMillis),
                    frameIntervalMillis,
                    frameCount,
                    driverNumbers);
        } catch (final IllegalArgumentException exception) {
            throw new IOException(
                    "Invalid timeline header.",
                    exception);
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
                    "Timeline reader is closed.");
        }
    }
}
