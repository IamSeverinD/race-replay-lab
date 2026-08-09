package io.github.iamseverind.racereplay.processed;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.iamseverind.racereplay.core.CancellationSupport;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Builds the fixed-rate binary replay timeline.
 *
 * <p>The large OpenF1 files are processed one driver at a time.
 * Only one driver's location and telemetry samples are retained in
 * memory during interpolation.</p>
 */
public final class ReplayTimelineBuilder {

    /**
     * Version of telemetry domain normalization.
     */
    public static final int TELEMETRY_NORMALIZATION_VERSION = 1;

    private static final long MAX_INTERPOLATION_GAP_MILLIS =
            2_000;

    private static final long MAX_EDGE_HOLD_MILLIS =
            1_000;

    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * Creates a timeline builder.
     *
     * @param objectMapper JSON mapper
     * @param clock manifest timestamp source
     */
    public ReplayTimelineBuilder(
            final ObjectMapper objectMapper,
            final Clock clock) {

        this.objectMapper =
                Objects.requireNonNull(
                        objectMapper,
                        "objectMapper");

        this.clock =
                Objects.requireNonNull(
                        clock,
                        "clock");
    }

    /**
     * Builds or reuses the binary timeline.
     *
     * @param sessionCacheDirectory complete session cache
     * @param progressListener progress receiver
     * @return timeline build result
     * @throws IOException when cache data is missing or invalid
     */
    public ReplayTimelineBuildResult build(
            final Path sessionCacheDirectory,
            final ReplayTimelineProgressListener progressListener)
            throws IOException {

        final Path cacheDirectory =
                Objects.requireNonNull(
                        sessionCacheDirectory,
                        "sessionCacheDirectory");

        Objects.requireNonNull(
                progressListener,
                "progressListener");

        final Path rawDirectory =
                cacheDirectory.resolve("raw");

        final Path processedDirectory =
                cacheDirectory.resolve("processed");

        final Path manifestFile =
                processedDirectory.resolve(
                        "replay-manifest.json");

        final Path driversFile =
                processedDirectory.resolve(
                        "drivers.json");

        final Path locationFile =
                rawDirectory.resolve(
                        "location.json");

        final Path carDataFile =
                rawDirectory.resolve(
                        "car-data.json");

        requireRegularFile(manifestFile);
        requireRegularFile(driversFile);
        requireRegularFile(locationFile);
        requireRegularFile(carDataFile);

        final ObjectNode manifest =
                readObject(manifestFile);

        validateManifest(manifest);

        final List<Integer> driverNumbers =
                readDriverNumbers(driversFile);

        final JsonNode replayNode =
                manifest.path("replay");

        final Instant replayStart =
                parseInstant(
                        requiredText(
                                replayNode,
                                "start",
                                manifestFile),
                        manifestFile);

        final int frameIntervalMillis =
                requiredPositiveInt(
                        replayNode,
                        "frame_interval_millis",
                        manifestFile);

        final int frameCount =
                requiredPositiveInt(
                        replayNode,
                        "frame_count",
                        manifestFile);

        final int manifestDriverCount =
                requiredPositiveInt(
                        replayNode,
                        "driver_count",
                        manifestFile);

        if (manifestDriverCount
                != driverNumbers.size()) {

            throw new IOException(
                    "Manifest driver count does not match drivers.json.");
        }

        final ReplayTimelineHeader header =
                new ReplayTimelineHeader(
                        ReplayTimelineFormat.VERSION,
                        replayStart,
                        frameIntervalMillis,
                        frameCount,
                        driverNumbers);

        final Path timelineFile =
                processedDirectory.resolve(
                        "timeline.bin");

        final ReplayTimelineBuildResult reusable =
                inspectReusableTimeline(
                        timelineFile,
                        manifest,
                        header);

        if (reusable != null) {
            progressListener.onProgress(
                    "Bestehende timeline.bin wurde validiert "
                    + "und wiederverwendet.");

            return reusable;
        }

        progressListener.onProgress(
                "Neue timeline.bin wird erstellt.");

        final int expectedLocationRecords =
                requiredPositiveInt(
                        manifest.path("source_ranges")
                                .path("location"),
                        "records",
                        manifestFile);

        final int expectedTelemetryRecords =
                requiredPositiveInt(
                        manifest.path("source_ranges")
                                .path("car_data"),
                        "records",
                        manifestFile);

        long locationValidStates = 0;
        long telemetryValidStates = 0;
        long fullyValidStates = 0;

        long actualLocationRecords = 0;
        long actualTelemetryRecords = 0;
        long invalidTelemetryRecords = 0;

        try (
                GroupedSampleReader<LocationSample>
                        locationReader =
                        new GroupedSampleReader<>(
                                locationFile,
                                objectMapper,
                                ReplayTimelineBuilder
                                        ::decodeLocationSample);

                GroupedSampleReader<TelemetrySample>
                        telemetryReader =
                        new GroupedSampleReader<>(
                                carDataFile,
                                objectMapper,
                                ReplayTimelineBuilder
                                        ::decodeTelemetrySample);

                ReplayTimelineWriter writer =
                        new ReplayTimelineWriter(
                                timelineFile,
                                header)) {

            for (int driverIndex = 0;
                    driverIndex < driverNumbers.size();
                    driverIndex++) {

                CancellationSupport.checkpoint();

                final int expectedDriverNumber =
                        driverNumbers.get(driverIndex);

                final SampleGroup<LocationSample>
                        locationGroup =
                        requireExpectedGroup(
                                locationReader.nextGroup(),
                                expectedDriverNumber,
                                locationFile);

                final SampleGroup<TelemetrySample>
                        telemetryGroup =
                        requireExpectedGroup(
                                telemetryReader.nextGroup(),
                                expectedDriverNumber,
                                carDataFile);

                actualLocationRecords +=
                        locationGroup.samples().size();

                actualTelemetryRecords +=
                        telemetryGroup.samples().size();

                final List<TelemetrySample>
                        validTelemetrySamples =
                        telemetryGroup.samples()
                                .stream()
                                .filter(
                                        TelemetrySample
                                                ::domainValid)
                                .toList();

                invalidTelemetryRecords +=
                        telemetryGroup.samples().size()
                        - validTelemetrySamples.size();

                if (validTelemetrySamples.isEmpty()) {
                    throw new IOException(
                            "Driver #"
                            + expectedDriverNumber
                            + " has no domain-valid telemetry.");
                }

                final SampleCursor<LocationSample>
                        locationCursor =
                        new SampleCursor<>(
                                locationGroup.samples());

                final SampleCursor<TelemetrySample>
                        telemetryCursor =
                        new SampleCursor<>(
                                validTelemetrySamples);

                long driverLocationValid = 0;
                long driverTelemetryValid = 0;

                for (int frameIndex = 0;
                        frameIndex < frameCount;
                        frameIndex++) {

                    if ((frameIndex & 0xFF) == 0) {
                        CancellationSupport.checkpoint();
                    }

                    final long elapsedMillis =
                            Math.multiplyExact(
                                    (long) frameIndex,
                                    frameIntervalMillis);

                    final long targetEpochMillis =
                            Math.addExact(
                                    replayStart.toEpochMilli(),
                                    elapsedMillis);

                    final LocationValue location =
                            interpolateLocation(
                                    locationCursor.bracket(
                                            targetEpochMillis),
                                    targetEpochMillis);

                    final TelemetryValue telemetry =
                            interpolateTelemetry(
                                    telemetryCursor.bracket(
                                            targetEpochMillis),
                                    targetEpochMillis);

                    int flags = 0;

                    if (location.valid()) {
                        flags |=
                                ReplayTimelineFormat
                                        .FLAG_LOCATION_VALID;

                        flags |=
                                ReplayTimelineFormat
                                        .FLAG_ACTIVE;

                        locationValidStates++;
                        driverLocationValid++;
                    }

                    if (telemetry.valid()) {
                        flags |=
                                ReplayTimelineFormat
                                        .FLAG_TELEMETRY_VALID;

                        telemetryValidStates++;
                        driverTelemetryValid++;
                    }

                    if (location.valid()
                            && telemetry.valid()) {

                        fullyValidStates++;
                    }

                    final ReplayDriverState state =
                            new ReplayDriverState(
                                    location.x(),
                                    location.y(),
                                    location.z(),
                                    telemetry.speed(),
                                    telemetry.rpm(),
                                    telemetry.gear(),
                                    telemetry.throttle(),
                                    telemetry.brake(),
                                    telemetry.drs(),
                                    0,
                                    flags,
                                    0,
                                    Float.NaN,
                                    Float.NaN,
                                    0);

                    writer.writeState(
                            frameIndex,
                            driverIndex,
                            state);
                }

                progressListener.onProgress(
                        "["
                        + (driverIndex + 1)
                        + "/"
                        + driverNumbers.size()
                        + "] Fahrer #"
                        + expectedDriverNumber
                        + " | location="
                        + driverLocationValid
                        + "/"
                        + frameCount
                        + " | telemetry="
                        + driverTelemetryValid
                        + "/"
                        + frameCount);
            }

            if (locationReader.nextGroup() != null) {
                throw new IOException(
                        "Location cache contains unexpected "
                        + "additional driver groups.");
            }

            if (telemetryReader.nextGroup() != null) {
                throw new IOException(
                        "Car-data cache contains unexpected "
                        + "additional driver groups.");
            }

            if (actualLocationRecords
                    != expectedLocationRecords) {

                throw new IOException(
                        "Location record count differs from manifest: "
                        + actualLocationRecords
                        + " instead of "
                        + expectedLocationRecords);
            }

            if (actualTelemetryRecords
                    != expectedTelemetryRecords) {

                throw new IOException(
                        "Telemetry record count differs from manifest: "
                        + actualTelemetryRecords
                        + " instead of "
                        + expectedTelemetryRecords);
            }

            writer.complete();
        }

        final long bytes =
                Files.size(timelineFile);

        if (bytes != header.expectedFileSizeBytes()) {
            throw new IOException(
                    "Completed timeline size is "
                    + bytes
                    + " instead of "
                    + header.expectedFileSizeBytes());
        }

        final String checksum =
                sha256(timelineFile);

        validateCompletedTimeline(
                timelineFile,
                header);

        final long totalStates =
                Math.multiplyExact(
                        (long) frameCount,
                        driverNumbers.size());

        updateManifest(
                manifestFile,
                manifest,
                bytes,
                checksum,
                totalStates,
                locationValidStates,
                telemetryValidStates,
                fullyValidStates,
                actualLocationRecords,
                actualTelemetryRecords,
                invalidTelemetryRecords);

        return new ReplayTimelineBuildResult(
                timelineFile,
                false,
                bytes,
                checksum,
                frameCount,
                driverNumbers.size(),
                totalStates,
                locationValidStates,
                telemetryValidStates,
                fullyValidStates,
                invalidTelemetryRecords);
    }

    private ReplayTimelineBuildResult inspectReusableTimeline(
            final Path timelineFile,
            final ObjectNode manifest,
            final ReplayTimelineHeader expectedHeader)
            throws IOException {

        final JsonNode timeline =
                manifest.path("timeline");

        if (!"complete".equals(
                timeline.path("state").asText())) {

            return null;
        }

        if (timeline.path(
                        "telemetry_normalization_version")
                .asInt(-1)
                != TELEMETRY_NORMALIZATION_VERSION) {

            return null;
        }

        if (!Files.isRegularFile(timelineFile)) {
            return null;
        }

        final long expectedBytes =
                expectedHeader.expectedFileSizeBytes();

        if (Files.size(timelineFile)
                != expectedBytes) {

            return null;
        }

        final String expectedChecksum =
                timeline.path("sha256")
                        .asText("");

        if (expectedChecksum.length() != 64) {
            return null;
        }

        final String actualChecksum =
                sha256(timelineFile);

        if (!expectedChecksum.equals(
                actualChecksum)) {

            return null;
        }

        try {
            validateCompletedTimeline(
                    timelineFile,
                    expectedHeader);
        } catch (final IOException exception) {
            return null;
        }

        final long totalStates =
                timeline.path("total_states")
                        .asLong(-1);

        final long locationValidStates =
                timeline.path("location_valid_states")
                        .asLong(-1);

        final long telemetryValidStates =
                timeline.path("telemetry_valid_states")
                        .asLong(-1);

        final long fullyValidStates =
                timeline.path("fully_valid_states")
                        .asLong(-1);

        final long invalidTelemetryRecords =
                timeline.path(
                        "source_invalid_car_data_records")
                        .asLong(-1);

        if (totalStates <= 0
                || locationValidStates < 0
                || telemetryValidStates < 0
                || fullyValidStates < 0
                || invalidTelemetryRecords < 0) {

            return null;
        }

        return new ReplayTimelineBuildResult(
                timelineFile,
                true,
                expectedBytes,
                actualChecksum,
                expectedHeader.frameCount(),
                expectedHeader.driverCount(),
                totalStates,
                locationValidStates,
                telemetryValidStates,
                fullyValidStates,
                invalidTelemetryRecords);
    }

    private static void validateCompletedTimeline(
            final Path timelineFile,
            final ReplayTimelineHeader expectedHeader)
            throws IOException {

        try (ReplayTimelineReader reader =
                new ReplayTimelineReader(
                        timelineFile)) {

            if (!reader.header().equals(
                    expectedHeader)) {

                throw new IOException(
                        "Timeline header differs from manifest.");
            }

            if (reader.readElapsedMillis(0) != 0) {
                throw new IOException(
                        "First frame has an invalid timestamp.");
            }

            final int lastFrameIndex =
                    expectedHeader.frameCount() - 1;

            final int expectedLastElapsed =
                    Math.multiplyExact(
                            lastFrameIndex,
                            expectedHeader
                                    .frameIntervalMillis());

            if (reader.readElapsedMillis(
                    lastFrameIndex)
                    != expectedLastElapsed) {

                throw new IOException(
                        "Last frame has an invalid timestamp.");
            }
        }
    }

    private void updateManifest(
            final Path manifestFile,
            final ObjectNode manifest,
            final long bytes,
            final String checksum,
            final long totalStates,
            final long locationValidStates,
            final long telemetryValidStates,
            final long fullyValidStates,
            final long locationRecords,
            final long telemetryRecords,
            final long invalidTelemetryRecords)
            throws IOException {

        manifest.put(
                "cache_state",
                "timeline_complete");

        final JsonNode timelineNode =
                manifest.get("timeline");

        final ObjectNode timeline;

        if (timelineNode
                instanceof ObjectNode objectNode) {

            timeline = objectNode;
        } else {
            timeline =
                    manifest.putObject("timeline");
        }

        timeline.put(
                "state",
                "complete");

        timeline.put(
                "built_at",
                Instant.now(clock).toString());

        timeline.put(
                "bytes",
                bytes);

        timeline.put(
                "sha256",
                checksum);

        timeline.put(
                "total_states",
                totalStates);

        timeline.put(
                "location_valid_states",
                locationValidStates);

        timeline.put(
                "telemetry_valid_states",
                telemetryValidStates);

        timeline.put(
                "fully_valid_states",
                fullyValidStates);

        timeline.put(
                "source_location_records",
                locationRecords);

        timeline.put(
                "source_car_data_records",
                telemetryRecords);

        timeline.put(
                "source_invalid_car_data_records",
                invalidTelemetryRecords);

        timeline.put(
                "telemetry_normalization_version",
                TELEMETRY_NORMALIZATION_VERSION);

        timeline.put(
                "invalid_telemetry_policy",
                "exclude_out_of_domain_samples");

        timeline.put(
                "interpolation_max_gap_millis",
                MAX_INTERPOLATION_GAP_MILLIS);

        timeline.put(
                "edge_hold_millis",
                MAX_EDGE_HOLD_MILLIS);

        atomicWriteJson(
                manifestFile,
                manifest);
    }

    private static LocationValue interpolateLocation(
            final SampleBracket<LocationSample> bracket,
            final long targetEpochMillis) {

        final LocationSample previous =
                bracket.previous();

        final LocationSample next =
                bracket.next();

        if (previous != null
                && previous.epochMillis()
                        == targetEpochMillis) {

            return LocationValue.from(previous);
        }

        if (previous != null && next != null) {
            final long sampleGap =
                    next.epochMillis()
                    - previous.epochMillis();

            if (sampleGap > 0
                    && sampleGap
                            <= MAX_INTERPOLATION_GAP_MILLIS) {

                final double ratio =
                        (double) (
                                targetEpochMillis
                                - previous.epochMillis())
                        / sampleGap;

                return new LocationValue(
                        true,
                        interpolateInt(
                                previous.x(),
                                next.x(),
                                ratio),
                        interpolateInt(
                                previous.y(),
                                next.y(),
                                ratio),
                        interpolateInt(
                                previous.z(),
                                next.z(),
                                ratio));
            }
        }

        if (previous != null
                && targetEpochMillis
                        - previous.epochMillis()
                        <= MAX_EDGE_HOLD_MILLIS) {

            return LocationValue.from(previous);
        }

        if (next != null
                && next.epochMillis()
                        - targetEpochMillis
                        <= MAX_EDGE_HOLD_MILLIS) {

            return LocationValue.from(next);
        }

        return LocationValue.invalid();
    }

    private static TelemetryValue interpolateTelemetry(
            final SampleBracket<TelemetrySample> bracket,
            final long targetEpochMillis) {

        final TelemetrySample previous =
                bracket.previous();

        final TelemetrySample next =
                bracket.next();

        if (previous != null
                && previous.epochMillis()
                        == targetEpochMillis) {

            return TelemetryValue.from(previous);
        }

        if (previous != null && next != null) {
            final long sampleGap =
                    next.epochMillis()
                    - previous.epochMillis();

            if (sampleGap > 0
                    && sampleGap
                            <= MAX_INTERPOLATION_GAP_MILLIS) {

                final double ratio =
                        (double) (
                                targetEpochMillis
                                - previous.epochMillis())
                        / sampleGap;

                final TelemetrySample discrete =
                        nearestSample(
                                previous,
                                next,
                                targetEpochMillis);

                return new TelemetryValue(
                        true,
                        clampUnsignedShort(
                                interpolateInt(
                                        previous.speed(),
                                        next.speed(),
                                        ratio)),
                        clampUnsignedShort(
                                interpolateInt(
                                        previous.rpm(),
                                        next.rpm(),
                                        ratio)),
                        clampUnsignedByte(
                                discrete.gear()),
                        clampUnsignedByte(
                                interpolateInt(
                                        previous.throttle(),
                                        next.throttle(),
                                        ratio)),
                        clampUnsignedByte(
                                discrete.brake()),
                        clampUnsignedByte(
                                discrete.drs()));
            }
        }

        if (previous != null
                && targetEpochMillis
                        - previous.epochMillis()
                        <= MAX_EDGE_HOLD_MILLIS) {

            return TelemetryValue.from(previous);
        }

        if (next != null
                && next.epochMillis()
                        - targetEpochMillis
                        <= MAX_EDGE_HOLD_MILLIS) {

            return TelemetryValue.from(next);
        }

        return TelemetryValue.invalid();
    }

    private static TelemetrySample nearestSample(
            final TelemetrySample previous,
            final TelemetrySample next,
            final long targetEpochMillis) {

        final long distanceToPrevious =
                targetEpochMillis
                - previous.epochMillis();

        final long distanceToNext =
                next.epochMillis()
                - targetEpochMillis;

        return distanceToPrevious <= distanceToNext
                ? previous
                : next;
    }

    private static int interpolateInt(
            final int first,
            final int second,
            final double ratio) {

        return (int) Math.round(
                first
                + (second - first) * ratio);
    }

    private static int clampUnsignedByte(
            final int value) {

        return Math.max(
                0,
                Math.min(255, value));
    }

    private static int clampUnsignedShort(
            final int value) {

        return Math.max(
                0,
                Math.min(65_535, value));
    }

    private static boolean isKnownDrsValue(
            final int value) {

        return switch (value) {
            case 0, 1, 2, 3, 8, 9, 10, 12, 14 -> true;
            default -> false;
        };
    }

    private static LocationSample decodeLocationSample(
            final JsonParser parser,
            final Path source)
            throws IOException {

        int driverNumber = -1;
        long epochMillis = Long.MIN_VALUE;
        Integer x = null;
        Integer y = null;
        Integer z = null;

        while (parser.nextToken()
                != JsonToken.END_OBJECT) {

            final String fieldName =
                    parser.currentName();

            final JsonToken valueToken =
                    parser.nextToken();

            switch (fieldName) {
                case "driver_number" ->
                    driverNumber =
                            parser.getIntValue();

                case "date" ->
                    epochMillis =
                            parseEpochMillis(
                                    parser,
                                    valueToken,
                                    source);

                case "x" ->
                    x = parser.getIntValue();

                case "y" ->
                    y = parser.getIntValue();

                case "z" ->
                    z = parser.getIntValue();

                default ->
                    parser.skipChildren();
            }
        }

        if (driverNumber <= 0
                || epochMillis == Long.MIN_VALUE
                || x == null
                || y == null
                || z == null) {

            throw new IOException(
                    "Invalid location row in "
                    + source);
        }

        return new LocationSample(
                driverNumber,
                epochMillis,
                x,
                y,
                z);
    }

    private static TelemetrySample decodeTelemetrySample(
            final JsonParser parser,
            final Path source)
            throws IOException {

        int driverNumber = -1;
        long epochMillis = Long.MIN_VALUE;
        Integer speed = null;
        Integer rpm = null;
        Integer gear = null;
        Integer throttle = null;
        Integer brake = null;
        Integer drs = null;

        while (parser.nextToken()
                != JsonToken.END_OBJECT) {

            final String fieldName =
                    parser.currentName();

            final JsonToken valueToken =
                    parser.nextToken();

            switch (fieldName) {
                case "driver_number" ->
                    driverNumber =
                            parser.getIntValue();

                case "date" ->
                    epochMillis =
                            parseEpochMillis(
                                    parser,
                                    valueToken,
                                    source);

                case "speed" ->
                    speed = parser.getIntValue();

                case "rpm" ->
                    rpm = parser.getIntValue();

                case "n_gear" ->
                    gear = parser.getIntValue();

                case "throttle" ->
                    throttle = parser.getIntValue();

                case "brake" ->
                    brake = parser.getIntValue();

                case "drs" ->
                    drs = valueToken == JsonToken.VALUE_NULL
                            ? 0
                            : parser.getIntValue();

                default ->
                    parser.skipChildren();
            }
        }

        if (driverNumber <= 0
                || epochMillis == Long.MIN_VALUE
                || speed == null
                || rpm == null
                || gear == null
                || throttle == null
                || brake == null
                || drs == null) {

            throw new IOException(
                    "Invalid car-data row in "
                    + source);
        }

        return new TelemetrySample(
                driverNumber,
                epochMillis,
                speed,
                rpm,
                gear,
                throttle,
                brake,
                drs);
    }

    private static long parseEpochMillis(
            final JsonParser parser,
            final JsonToken valueToken,
            final Path source)
            throws IOException {

        if (valueToken
                != JsonToken.VALUE_STRING) {

            throw new IOException(
                    "Invalid timestamp in "
                    + source);
        }

        try {
            return Instant.parse(
                    parser.getText())
                    .toEpochMilli();
        } catch (final RuntimeException exception) {
            throw new IOException(
                    "Invalid timestamp in "
                    + source
                    + ": "
                    + parser.getText(),
                    exception);
        }
    }

    private ObjectNode readObject(final Path file)
            throws IOException {

        final JsonNode root =
                objectMapper.readTree(
                        file.toFile());

        if (!(root instanceof ObjectNode objectNode)) {
            throw new IOException(
                    "Expected JSON object: "
                    + file);
        }

        return objectNode;
    }

    private static void validateManifest(
            final ObjectNode manifest)
            throws IOException {

        final String cacheState =
                manifest.path("cache_state")
                        .asText();

        if (!"metadata_complete".equals(cacheState)
                && !"timeline_complete".equals(cacheState)) {

            throw new IOException(
                    "Processed cache metadata is incomplete: "
                    + cacheState);
        }

        if (!manifest.path("replay").isObject()) {
            throw new IOException(
                    "Processed manifest has no replay object.");
        }
    }

    private List<Integer> readDriverNumbers(
            final Path driversFile)
            throws IOException {

        final JsonNode root =
                objectMapper.readTree(
                        driversFile.toFile());

        if (!root.isArray() || root.isEmpty()) {
            throw new IOException(
                    "Processed drivers file is invalid.");
        }

        final List<Integer> driverNumbers =
                new ArrayList<>();

        int previous = 0;

        for (final JsonNode driver : root) {
            final int driverNumber =
                    driver.path("driver_number")
                            .asInt(-1);

            if (driverNumber <= previous) {
                throw new IOException(
                        "Driver numbers must be positive, "
                        + "unique and sorted.");
            }

            driverNumbers.add(driverNumber);
            previous = driverNumber;
        }

        return List.copyOf(driverNumbers);
    }

    private static String requiredText(
            final JsonNode parent,
            final String field,
            final Path source)
            throws IOException {

        final JsonNode value =
                parent.get(field);

        if (value == null
                || !value.isTextual()
                || value.textValue().isBlank()) {

            throw new IOException(
                    "Missing field "
                    + field
                    + " in "
                    + source);
        }

        return value.textValue();
    }

    private static int requiredPositiveInt(
            final JsonNode parent,
            final String field,
            final Path source)
            throws IOException {

        final JsonNode value =
                parent.get(field);

        if (value == null
                || !value.canConvertToInt()
                || value.intValue() <= 0) {

            throw new IOException(
                    "Missing positive integer "
                    + field
                    + " in "
                    + source);
        }

        return value.intValue();
    }

    private static Instant parseInstant(
            final String value,
            final Path source)
            throws IOException {

        try {
            return Instant.parse(value);
        } catch (final RuntimeException exception) {
            throw new IOException(
                    "Invalid timestamp in "
                    + source
                    + ": "
                    + value,
                    exception);
        }
    }

    private void atomicWriteJson(
            final Path target,
            final ObjectNode content)
            throws IOException {

        final String json =
                objectMapper
                        .writerWithDefaultPrettyPrinter()
                        .writeValueAsString(content)
                + System.lineSeparator();

        final Path temporaryFile =
                target.resolveSibling(
                        target.getFileName()
                        + ".part-"
                        + UUID.randomUUID());

        try {
            Files.writeString(
                    temporaryFile,
                    json,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);

            try {
                Files.move(
                        temporaryFile,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (
                    final AtomicMoveNotSupportedException exception) {

                Files.move(
                        temporaryFile,
                        target,
                        StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(
                    temporaryFile);
        }
    }

    private static String sha256(final Path file)
            throws IOException {

        final MessageDigest digest;

        try {
            digest =
                    MessageDigest.getInstance(
                            "SHA-256");
        } catch (final NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable.",
                    exception);
        }

        try (var input =
                Files.newInputStream(file)) {

            final byte[] buffer =
                    new byte[64 * 1024];

            int bytesRead;

            while ((bytesRead = input.read(buffer)) != -1) {
                digest.update(
                        buffer,
                        0,
                        bytesRead);
            }
        }

        return HexFormat.of()
                .formatHex(
                        digest.digest());
    }

    private static void requireRegularFile(
            final Path file)
            throws IOException {

        if (!Files.isRegularFile(file)) {
            throw new IOException(
                    "Required file is missing: "
                    + file);
        }
    }

    private static <T extends TimedSample>
            SampleGroup<T> requireExpectedGroup(
                    final SampleGroup<T> group,
                    final int expectedDriverNumber,
                    final Path source)
                    throws IOException {

        if (group == null) {
            throw new IOException(
                    "Missing driver #"
                    + expectedDriverNumber
                    + " in "
                    + source);
        }

        if (group.driverNumber()
                != expectedDriverNumber) {

            throw new IOException(
                    "Expected driver #"
                    + expectedDriverNumber
                    + " but found #"
                    + group.driverNumber()
                    + " in "
                    + source);
        }

        return group;
    }

    private interface TimedSample {

        /**
         * Returns the provider-specific driver number.
         *
         * @return driver number
         */
        int driverNumber();

        /**
         * Returns the sample time.
         *
         * @return UTC epoch milliseconds
         */
        long epochMillis();
    }

    @FunctionalInterface
    private interface SampleDecoder<T extends TimedSample> {

        /**
         * Decodes one object while the parser is at START_OBJECT.
         *
         * @param parser JSON parser
         * @param source source file
         * @return decoded sample
         * @throws IOException when the object is invalid
         */
        T decode(
                JsonParser parser,
                Path source)
                throws IOException;
    }

    private static final class GroupedSampleReader<
            T extends TimedSample>
            implements AutoCloseable {

        private final Path source;
        private final JsonParser parser;
        private final SampleDecoder<T> decoder;

        private T buffered;
        private boolean finished;
        private int previousGroupDriver;

        private GroupedSampleReader(
                final Path source,
                final ObjectMapper objectMapper,
                final SampleDecoder<T> decoder)
                throws IOException {

            this.source =
                    Objects.requireNonNull(
                            source,
                            "source");

            this.decoder =
                    Objects.requireNonNull(
                            decoder,
                            "decoder");

            parser =
                    Objects.requireNonNull(
                            objectMapper,
                            "objectMapper")
                            .getFactory()
                            .createParser(
                                    source.toFile());

            if (parser.nextToken()
                    != JsonToken.START_ARRAY) {

                parser.close();

                throw new IOException(
                        "Expected JSON array: "
                        + source);
            }
        }

        private SampleGroup<T> nextGroup()
                throws IOException {

            final T first =
                    takeNextSample();

            if (first == null) {
                return null;
            }

            if (first.driverNumber()
                    <= previousGroupDriver) {

                throw new IOException(
                        "Driver groups are not sorted in "
                        + source);
            }

            final int driverNumber =
                    first.driverNumber();

            final List<T> samples =
                    new ArrayList<>();

            samples.add(first);

            long previousTimestamp =
                    first.epochMillis();

            while (true) {
                final T sample =
                        readNextSample();

                if (sample == null) {
                    break;
                }

                if (sample.driverNumber()
                        != driverNumber) {

                    buffered = sample;
                    break;
                }

                if (sample.epochMillis()
                        < previousTimestamp) {

                    throw new IOException(
                            "Samples for driver #"
                            + driverNumber
                            + " are not time-sorted in "
                            + source);
                }

                samples.add(sample);

                previousTimestamp =
                        sample.epochMillis();
            }

            previousGroupDriver =
                    driverNumber;

            return new SampleGroup<>(
                    driverNumber,
                    List.copyOf(samples));
        }

        private T takeNextSample()
                throws IOException {

            if (buffered != null) {
                final T result = buffered;
                buffered = null;
                return result;
            }

            return readNextSample();
        }

        private T readNextSample()
                throws IOException {

            if (finished) {
                return null;
            }

            final JsonToken token =
                    parser.nextToken();

            if (token == JsonToken.END_ARRAY) {
                if (parser.nextToken() != null) {
                    throw new IOException(
                            "Unexpected data after array in "
                            + source);
                }

                finished = true;
                return null;
            }

            if (token == null) {
                throw new IOException(
                        "Unexpected end of JSON in "
                        + source);
            }

            if (token != JsonToken.START_OBJECT) {
                throw new IOException(
                        "Expected object row in "
                        + source);
            }

            return decoder.decode(
                    parser,
                    source);
        }

        @Override
        public void close() throws IOException {
            parser.close();
        }
    }

    private static final class SampleCursor<
            T extends TimedSample> {

        private final List<T> samples;

        private int nextIndex;
        private T previous;

        private SampleCursor(
                final List<T> samples) {

            this.samples =
                    List.copyOf(
                            Objects.requireNonNull(
                                    samples,
                                    "samples"));

            if (this.samples.isEmpty()) {
                throw new IllegalArgumentException(
                        "Sample list must not be empty.");
            }
        }

        private SampleBracket<T> bracket(
                final long targetEpochMillis) {

            while (nextIndex < samples.size()
                    && samples.get(nextIndex)
                            .epochMillis()
                            <= targetEpochMillis) {

                previous =
                        samples.get(nextIndex);

                nextIndex++;
            }

            final T next =
                    nextIndex < samples.size()
                            ? samples.get(nextIndex)
                            : null;

            return new SampleBracket<>(
                    previous,
                    next);
        }
    }

    private record SampleGroup<T extends TimedSample>(
            int driverNumber,
            List<T> samples) {

        private SampleGroup {
            samples =
                    List.copyOf(
                            Objects.requireNonNull(
                                    samples,
                                    "samples"));

            if (driverNumber <= 0
                    || samples.isEmpty()) {

                throw new IllegalArgumentException(
                        "Invalid sample group.");
            }
        }
    }

    private record SampleBracket<T extends TimedSample>(
            T previous,
            T next) {
    }

    private record LocationSample(
            int driverNumber,
            long epochMillis,
            int x,
            int y,
            int z)
            implements TimedSample {
    }

    private record TelemetrySample(
            int driverNumber,
            long epochMillis,
            int speed,
            int rpm,
            int gear,
            int throttle,
            int brake,
            int drs)
            implements TimedSample {

        private boolean domainValid() {
            return speed >= 0
                    && speed <= 65_535
                    && rpm >= 0
                    && rpm <= 65_535
                    && gear >= 0
                    && gear <= 8
                    && throttle >= 0
                    && throttle <= 100
                    && (brake == 0 || brake == 100)
                    && isKnownDrsValue(drs);
        }
    }

    private record LocationValue(
            boolean valid,
            int x,
            int y,
            int z) {

        private static LocationValue from(
                final LocationSample sample) {

            return new LocationValue(
                    true,
                    sample.x(),
                    sample.y(),
                    sample.z());
        }

        private static LocationValue invalid() {
            return new LocationValue(
                    false,
                    0,
                    0,
                    0);
        }
    }

    private record TelemetryValue(
            boolean valid,
            int speed,
            int rpm,
            int gear,
            int throttle,
            int brake,
            int drs) {

        private static TelemetryValue from(
                final TelemetrySample sample) {

            return new TelemetryValue(
                    true,
                    clampUnsignedShort(
                            sample.speed()),
                    clampUnsignedShort(
                            sample.rpm()),
                    clampUnsignedByte(
                            sample.gear()),
                    clampUnsignedByte(
                            sample.throttle()),
                    clampUnsignedByte(
                            sample.brake()),
                    clampUnsignedByte(
                            sample.drs()));
        }

        private static TelemetryValue invalid() {
            return new TelemetryValue(
                    false,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0);
        }
    }
}
