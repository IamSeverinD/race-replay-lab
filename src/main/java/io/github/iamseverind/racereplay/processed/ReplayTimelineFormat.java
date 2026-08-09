package io.github.iamseverind.racereplay.processed;

import java.nio.charset.StandardCharsets;

/**
 * Binary layout constants for replay timeline files.
 *
 * <p>All multi-byte values use Java's default big-endian order.</p>
 */
public final class ReplayTimelineFormat {

    /**
     * Current binary format version.
     */
    public static final int VERSION = 1;

    /**
     * Number of bytes in one driver state.
     */
    public static final int STATE_SIZE_BYTES = 36;

    /**
     * Location coordinates are valid.
     */
    public static final int FLAG_LOCATION_VALID = 1;

    /**
     * Car telemetry is valid.
     */
    public static final int FLAG_TELEMETRY_VALID = 1 << 1;

    /**
     * Gap to leader is valid.
     */
    public static final int FLAG_GAP_VALID = 1 << 2;

    /**
     * Interval to the preceding driver is valid.
     */
    public static final int FLAG_INTERVAL_VALID = 1 << 3;

    /**
     * Race position is valid.
     */
    public static final int FLAG_POSITION_VALID = 1 << 4;

    /**
     * Lap number is valid.
     */
    public static final int FLAG_LAP_VALID = 1 << 5;

    /**
     * Tyre compound is valid.
     */
    public static final int FLAG_TYRE_VALID = 1 << 6;

    /**
     * Driver is considered active.
     */
    public static final int FLAG_ACTIVE = 1 << 7;

    /**
     * Mask containing all metadata validity flags.
     */
    public static final int FLAG_METADATA_MASK =
            FLAG_GAP_VALID
            | FLAG_INTERVAL_VALID
            | FLAG_POSITION_VALID
            | FLAG_LAP_VALID
            | FLAG_TYRE_VALID;

    /**
     * Position byte offset inside one driver state.
     */
    public static final int STATE_POSITION_OFFSET_BYTES = 20;

    /**
     * Flags byte offset inside one driver state.
     */
    public static final int STATE_FLAGS_OFFSET_BYTES = 21;

    /**
     * Lap-number offset inside one driver state.
     */
    public static final int STATE_LAP_OFFSET_BYTES = 22;

    /**
     * Gap-to-leader offset inside one driver state.
     */
    public static final int STATE_GAP_OFFSET_BYTES = 24;

    /**
     * Interval offset inside one driver state.
     */
    public static final int STATE_INTERVAL_OFFSET_BYTES = 28;

    /**
     * Tyre-compound offset inside one driver state.
     */
    public static final int STATE_TYRE_OFFSET_BYTES = 32;

    private static final String MAGIC_TEXT = "F1RPLYV1";

    private static final byte[] MAGIC_BYTES =
            MAGIC_TEXT.getBytes(
                    StandardCharsets.US_ASCII);

    private static final int FIXED_HEADER_SIZE_BYTES = 40;

    private static final int FRAME_TIME_SIZE_BYTES =
            Integer.BYTES;

    private ReplayTimelineFormat() {
    }

    /**
     * Returns a copy of the eight-byte magic sequence.
     *
     * @return timeline magic bytes
     */
    public static byte[] magicBytes() {
        return MAGIC_BYTES.clone();
    }

    /**
     * Returns the human-readable magic sequence.
     *
     * @return timeline magic text
     */
    public static String magicText() {
        return MAGIC_TEXT;
    }

    /**
     * Calculates the header size.
     *
     * @param driverCount driver count
     * @return header size in bytes
     */
    public static int headerSizeBytes(
            final int driverCount) {

        requirePositive(
                driverCount,
                "driverCount");

        return Math.addExact(
                FIXED_HEADER_SIZE_BYTES,
                Math.multiplyExact(
                        driverCount,
                        Integer.BYTES));
    }

    /**
     * Calculates one frame's fixed size.
     *
     * @param driverCount driver count
     * @return frame size in bytes
     */
    public static int frameSizeBytes(
            final int driverCount) {

        requirePositive(
                driverCount,
                "driverCount");

        return Math.addExact(
                FRAME_TIME_SIZE_BYTES,
                Math.multiplyExact(
                        driverCount,
                        STATE_SIZE_BYTES));
    }

    /**
     * Calculates the complete expected file size.
     *
     * @param frameCount frame count
     * @param driverCount driver count
     * @return expected file size
     */
    public static long expectedFileSizeBytes(
            final int frameCount,
            final int driverCount) {

        requirePositive(
                frameCount,
                "frameCount");

        return Math.addExact(
                headerSizeBytes(driverCount),
                Math.multiplyExact(
                        (long) frameCount,
                        frameSizeBytes(driverCount)));
    }

    private static void requirePositive(
            final int value,
            final String name) {

        if (value <= 0) {
            throw new IllegalArgumentException(
                    name + " must be positive.");
        }
    }
}
