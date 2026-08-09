package io.github.iamseverind.racereplay.processed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.iamseverind.racereplay.core.DriverSnapshot;
import io.github.iamseverind.racereplay.core.ReplaySimulation;
import io.github.iamseverind.racereplay.core.ReplaySnapshot;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Provides replay snapshots from a processed binary timeline.
 *
 * <p>The adapter keeps the existing user-interface model intact. Lap progress
 * is derived from real lap transitions, while driver visibility requires
 * recent location and telemetry data.</p>
 */
public final class ProcessedReplaySimulation
        implements ReplaySimulation, AutoCloseable {

    private static final double MAX_LAP_PROGRESS =
            Math.nextDown(1.0);

    private static final int VISIBILITY_GRACE_MILLIS =
            5_000;

    private final ReplayTimelineReader reader;
    private final ReplayTimelineHeader header;
    private final List<DriverMetadata> driverMetadata;
    private final List<LapIndex> lapIndexes;
    private final List<BitSet> visibleFrames;

    private boolean closed;

    /**
     * Opens a processed replay cache.
     *
     * @param cacheDirectory session cache directory
     * @throws IOException when processed data is missing or invalid
     */
    public ProcessedReplaySimulation(
            final Path cacheDirectory)
            throws IOException {

        Objects.requireNonNull(
                cacheDirectory,
                "cacheDirectory");

        final Path processedDirectory =
                cacheDirectory.resolve("processed");

        final ReplayTimelineReader openedReader =
                new ReplayTimelineReader(
                        processedDirectory.resolve(
                                "timeline.bin"));

        try {
            reader = openedReader;
            header = reader.header();

            driverMetadata =
                    readDriverMetadata(
                            processedDirectory.resolve(
                                    "drivers.json"),
                            header.driverNumbers());

            lapIndexes =
                    buildLapIndexes();

            visibleFrames =
                    buildDriverVisibility();
        } catch (final IOException
                | RuntimeException exception) {

            openedReader.close();
            throw exception;
        }
    }

    /**
     * Returns the duration represented by the final frame.
     *
     * @return timeline duration in seconds
     */
    public double durationSeconds() {
        return durationMillis() / 1000.0;
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
     * Reads and interpolates the replay state at the requested time.
     *
     * <p>Continuous values are interpolated between adjacent timeline
     * frames. Times after the replay end are clamped to the final frame.</p>
     *
     * @param replaySeconds non-negative logical replay time
     * @return processed replay snapshot
     */
    @Override
    public synchronized ReplaySnapshot snapshotAt(
            final double replaySeconds) {

        validateReplaySeconds(
                replaySeconds);

        ensureOpen();

        final double clampedReplaySeconds =
                Math.min(
                        replaySeconds,
                        durationSeconds());

        final int currentFrameIndex =
                frameIndexAt(
                        clampedReplaySeconds);

        final int nextFrameIndex =
                Math.min(
                        currentFrameIndex + 1,
                        header.frameCount() - 1);

        final double interpolationFraction =
                interpolationFraction(
                        clampedReplaySeconds,
                        currentFrameIndex,
                        nextFrameIndex);

        final double framePosition =
                currentFrameIndex
                + interpolationFraction;

        final ReplayTimelineFrame currentFrame;
        final ReplayTimelineFrame nextFrame;

        try {
            currentFrame =
                    reader.readFrame(
                            currentFrameIndex);

            nextFrame =
                    nextFrameIndex == currentFrameIndex
                            ? currentFrame
                            : reader.readFrame(
                                    nextFrameIndex);
        } catch (final IOException exception) {
            throw new UncheckedIOException(
                    "Unable to read replay frames.",
                    exception);
        }

        final List<DriverSnapshot> drivers =
                new ArrayList<>(
                        header.driverCount());

        for (int driverIndex = 0;
                driverIndex < header.driverCount();
                driverIndex++) {

            final ReplayDriverState state =
                    ReplayStateInterpolator.interpolate(
                            currentFrame.drivers()
                                    .get(driverIndex),
                            nextFrame.drivers()
                                    .get(driverIndex),
                            interpolationFraction);

            if (!isDriverVisible(
                    driverIndex,
                    currentFrameIndex,
                    nextFrameIndex,
                    state)) {

                continue;
            }

            drivers.add(
                    createDriverSnapshot(
                            driverIndex,
                            framePosition,
                            state));
        }

        drivers.sort(
                Comparator
                        .comparingInt(
                                DriverSnapshot::position)
                        .thenComparing(
                                DriverSnapshot::code));

        return new ReplaySnapshot(
                clampedReplaySeconds,
                drivers);
    }

    private boolean isDriverVisible(
            final int driverIndex,
            final int currentFrameIndex,
            final int nextFrameIndex,
            final ReplayDriverState state) {

        if (!hasFlag(
                state,
                ReplayTimelineFormat
                        .FLAG_LOCATION_VALID)) {

            return false;
        }

        final BitSet visibility =
                visibleFrames.get(driverIndex);

        return visibility.get(currentFrameIndex)
                || visibility.get(nextFrameIndex);
    }

    /**
     * Closes the underlying timeline reader.
     *
     * @throws IOException when closing the binary file fails
     */
    @Override
    public synchronized void close()
            throws IOException {

        if (!closed) {
            reader.close();
            closed = true;
        }
    }

    private DriverSnapshot createDriverSnapshot(
            final int driverIndex,
            final double framePosition,
            final ReplayDriverState state) {

        final DriverMetadata metadata =
                driverMetadata.get(driverIndex);

        final boolean positionValid =
                hasFlag(
                        state,
                        ReplayTimelineFormat
                                .FLAG_POSITION_VALID);

        final boolean lapValid =
                hasFlag(
                        state,
                        ReplayTimelineFormat
                                .FLAG_LAP_VALID);

        final boolean tyreValid =
                hasFlag(
                        state,
                        ReplayTimelineFormat
                                .FLAG_TYRE_VALID);

        final boolean telemetryValid =
                hasFlag(
                        state,
                        ReplayTimelineFormat
                                .FLAG_TELEMETRY_VALID);

        final boolean gapValid =
                hasFlag(
                        state,
                        ReplayTimelineFormat
                                .FLAG_GAP_VALID)
                && Float.isFinite(
                        state.gapToLeaderSeconds());

        final boolean intervalValid =
                hasFlag(
                        state,
                        ReplayTimelineFormat
                                .FLAG_INTERVAL_VALID)
                && Float.isFinite(
                        state.intervalSeconds());

        final boolean locationValid =
                hasFlag(
                        state,
                        ReplayTimelineFormat
                                .FLAG_LOCATION_VALID);

        final int position =
                positionValid
                        && state.position() > 0
                        ? state.position()
                        : driverIndex + 1;

        final double lapProgress =
                lapValid
                        ? lapIndexes
                                .get(driverIndex)
                                .progress(
                                        state.lapNumber(),
                                        framePosition)
                        : 0.0;

        final double totalDistanceLaps =
                lapValid
                        ? Math.max(
                                0,
                                state.lapNumber() - 1)
                                + lapProgress
                        : 0.0;

        final String tyre =
                tyreValid
                        ? tyreName(
                                state.tyreCompoundCode())
                        : "UNKNOWN";

        final boolean drsActive =
                telemetryValid
                        && isDrsActive(
                                state.drs());

        return new DriverSnapshot(
                position,
                metadata.code(),
                metadata.team(),
                tyre,
                tyreValid,
                lapProgress,
                totalDistanceLaps,
                telemetryValid
                        ? state.speed()
                        : 0.0,
                telemetryValid
                        ? state.rpm()
                        : 0,
                telemetryValid
                        ? state.gear()
                        : 0,
                telemetryValid
                        ? state.throttle()
                        : 0,
                telemetryValid
                        ? state.brake()
                        : 0,
                drsActive,
                telemetryValid,
                lapValid
                        ? state.lapNumber()
                        : 0,
                lapValid,
                gapValid
                        ? state.gapToLeaderSeconds()
                        : Double.NaN,
                gapValid,
                intervalValid
                        ? state.intervalSeconds()
                        : Double.NaN,
                intervalValid,
                state.x(),
                state.y(),
                state.z(),
                locationValid);
    }

    private List<LapIndex> buildLapIndexes()
            throws IOException {

        final List<Map<Integer, Integer>> startFrames =
                new ArrayList<>(
                        header.driverCount());

        for (int driverIndex = 0;
                driverIndex < header.driverCount();
                driverIndex++) {

            startFrames.add(
                    new HashMap<>());
        }

        final int[] previousLaps =
                new int[
                        header.driverCount()];

        for (int frameIndex = 0;
                frameIndex < header.frameCount();
                frameIndex++) {

            final ReplayTimelineFrame frame =
                    reader.readFrame(
                            frameIndex);

            for (int driverIndex = 0;
                    driverIndex < header.driverCount();
                    driverIndex++) {

                final ReplayDriverState state =
                        frame.drivers()
                                .get(driverIndex);

                if (!hasFlag(
                        state,
                        ReplayTimelineFormat
                                .FLAG_LAP_VALID)) {

                    continue;
                }

                final int lapNumber =
                        state.lapNumber();

                if (lapNumber
                        < previousLaps[driverIndex]) {

                    throw new IOException(
                            "Lap number moves backwards for driver #"
                            + header.driverNumbers()
                                    .get(driverIndex)
                            + ".");
                }

                if (lapNumber
                        != previousLaps[driverIndex]) {

                    final Integer existing =
                            startFrames
                                    .get(driverIndex)
                                    .putIfAbsent(
                                            lapNumber,
                                            frameIndex);

                    if (existing != null) {
                        throw new IOException(
                                "Lap appears more than once for driver #"
                                + header.driverNumbers()
                                        .get(driverIndex)
                                + ": "
                                + lapNumber);
                    }

                    previousLaps[driverIndex] =
                            lapNumber;
                }
            }
        }

        final List<LapIndex> indexes =
                new ArrayList<>(
                        header.driverCount());

        for (final Map<Integer, Integer> frames
                : startFrames) {

            indexes.add(
                    new LapIndex(frames));
        }

        return List.copyOf(indexes);
    }

    private List<BitSet> buildDriverVisibility()
            throws IOException {

        final List<BitSet> telemetryFrames =
                new ArrayList<>(
                        header.driverCount());

        for (int driverIndex = 0;
                driverIndex < header.driverCount();
                driverIndex++) {

            telemetryFrames.add(
                    new BitSet(
                            header.frameCount()));
        }

        for (int frameIndex = 0;
                frameIndex < header.frameCount();
                frameIndex++) {

            final ReplayTimelineFrame frame =
                    reader.readFrame(
                            frameIndex);

            for (int driverIndex = 0;
                    driverIndex < header.driverCount();
                    driverIndex++) {

                final ReplayDriverState state =
                        frame.drivers()
                                .get(driverIndex);

                if (hasFlag(
                        state,
                        ReplayTimelineFormat
                                .FLAG_TELEMETRY_VALID)) {

                    telemetryFrames.get(driverIndex)
                            .set(frameIndex);
                }
            }
        }

        final int graceFrames =
                (int) Math.ceil(
                        (double) VISIBILITY_GRACE_MILLIS
                        / header.frameIntervalMillis());

        final List<BitSet> visibility =
                new ArrayList<>(
                        header.driverCount());

        for (final BitSet telemetry : telemetryFrames) {
            final BitSet visible =
                    new BitSet(
                            header.frameCount());

            int runStart =
                    telemetry.nextSetBit(0);

            while (runStart >= 0) {
                final int runEnd =
                        telemetry.nextClearBit(
                                runStart);

                visible.set(
                        Math.max(
                                0,
                                runStart - graceFrames),
                        Math.min(
                                header.frameCount(),
                                runEnd + graceFrames));

                runStart =
                        telemetry.nextSetBit(
                                runEnd);
            }

            visibility.add(visible);
        }

        return List.copyOf(visibility);
    }

    private static List<DriverMetadata> readDriverMetadata(
            final Path driversFile,
            final List<Integer> driverNumbers)
            throws IOException {

        final JsonNode root =
                new ObjectMapper()
                        .readTree(
                                driversFile.toFile());

        if (!root.isArray()) {
            throw new IOException(
                    "Processed drivers must be a JSON array.");
        }

        final Map<Integer, DriverMetadata> byNumber =
                new HashMap<>();

        for (final JsonNode row : root) {
            final int driverNumber =
                    requiredPositiveInt(
                            row,
                            "driver_number");

            final DriverMetadata previous =
                    byNumber.put(
                            driverNumber,
                            new DriverMetadata(
                                    requiredText(
                                            row,
                                            "name_acronym"),
                                    requiredText(
                                            row,
                                            "team_name")));

            if (previous != null) {
                throw new IOException(
                        "Duplicate processed driver number: "
                        + driverNumber);
            }
        }

        final List<DriverMetadata> ordered =
                new ArrayList<>(
                        driverNumbers.size());

        for (final int driverNumber : driverNumbers) {
            final DriverMetadata metadata =
                    byNumber.remove(
                            driverNumber);

            if (metadata == null) {
                throw new IOException(
                        "Missing processed driver metadata for #"
                        + driverNumber);
            }

            ordered.add(metadata);
        }

        if (!byNumber.isEmpty()) {
            throw new IOException(
                    "Processed drivers contain entries "
                    + "not present in the timeline header.");
        }

        return List.copyOf(ordered);
    }

    private static int requiredPositiveInt(
            final JsonNode row,
            final String field)
            throws IOException {

        final JsonNode value =
                row.get(field);

        if (value == null
                || !value.canConvertToInt()
                || value.asInt() <= 0) {

            throw new IOException(
                    "Invalid processed driver field: "
                    + field);
        }

        return value.asInt();
    }

    private static String requiredText(
            final JsonNode row,
            final String field)
            throws IOException {

        final JsonNode value =
                row.get(field);

        if (value == null
                || !value.isTextual()
                || value.asText().isBlank()) {

            throw new IOException(
                    "Invalid processed driver field: "
                    + field);
        }

        return value.asText();
    }

    private double interpolationFraction(
            final double replaySeconds,
            final int currentFrameIndex,
            final int nextFrameIndex) {

        if (currentFrameIndex == nextFrameIndex) {
            return 0.0;
        }

        final double frameIntervalSeconds =
                header.frameIntervalMillis()
                / 1_000.0;

        final double currentFrameSeconds =
                currentFrameIndex
                * frameIntervalSeconds;

        final double fraction =
                (replaySeconds
                - currentFrameSeconds)
                / frameIntervalSeconds;

        return Math.max(
                0.0,
                Math.min(
                        1.0,
                        fraction));
    }

    private int frameIndexAt(
            final double replaySeconds) {

        if (replaySeconds
                >= durationSeconds()) {

            return header.frameCount() - 1;
        }

        final double replayMillis =
                replaySeconds * 1000.0;

        return (int) Math.floor(
                replayMillis
                / header.frameIntervalMillis());
    }

    private long durationMillis() {
        return Math.multiplyExact(
                (long) header.frameCount() - 1L,
                header.frameIntervalMillis());
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException(
                    "Processed replay simulation is closed.");
        }
    }

    private static void validateReplaySeconds(
            final double replaySeconds) {

        if (!Double.isFinite(replaySeconds)
                || replaySeconds < 0.0) {

            throw new IllegalArgumentException(
                    "Replay time must be non-negative and finite.");
        }
    }

    private static boolean hasFlag(
            final ReplayDriverState state,
            final int flag) {

        return (state.flags() & flag) != 0;
    }

    private static boolean isDrsActive(
            final int drs) {

        return drs == 10
                || drs == 12
                || drs == 14;
    }

    private static String tyreName(
            final int tyreCode) {

        return switch (tyreCode) {
            case ReplayTimelineEnricher.TYRE_SOFT ->
                    "SOFT";

            case ReplayTimelineEnricher.TYRE_MEDIUM ->
                    "MEDIUM";

            case ReplayTimelineEnricher.TYRE_HARD ->
                    "HARD";

            default ->
                    "UNKNOWN";
        };
    }

    private record DriverMetadata(
            String code,
            String team) {
    }

    private record LapIndex(
            Map<Integer, Integer> startFrames) {

        private LapIndex {
            startFrames =
                    Map.copyOf(
                            startFrames);
        }

        private double progress(
                final int lapNumber,
                final double framePosition) {

            final Integer lapStart =
                    startFrames.get(
                            lapNumber);

            if (lapStart == null) {
                return 0.0;
            }

            final Integer nextLapStart =
                    startFrames.get(
                            lapNumber + 1);

            final int durationFrames;

            if (nextLapStart != null) {
                durationFrames =
                        nextLapStart - lapStart;
            } else {
                final Integer previousLapStart =
                        startFrames.get(
                                lapNumber - 1);

                durationFrames =
                        previousLapStart == null
                                ? 1
                                : lapStart
                                - previousLapStart;
            }

            if (durationFrames <= 0) {
                return 0.0;
            }

            final double progress =
                    (framePosition - lapStart)
                    / durationFrames;

            return Math.max(
                    0.0,
                    Math.min(
                            MAX_LAP_PROGRESS,
                            progress));
        }
    }
}
