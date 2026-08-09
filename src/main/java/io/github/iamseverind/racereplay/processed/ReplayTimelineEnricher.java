package io.github.iamseverind.racereplay.processed;

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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Enriches a binary replay timeline with race metadata.
 */
public final class ReplayTimelineEnricher {

    private static final Pattern LAPPED_GAP_PATTERN =
            Pattern.compile(
                    "\\+\\d+ LAPS?");

    /**
     * Version of the race-metadata enrichment policy.
     */
    public static final int ENRICHMENT_VERSION = 1;

    /**
     * Maximum age of held interval measurements.
     */
    public static final long INTERVAL_HOLD_MILLIS =
            10_000;

    /**
     * Binary tyre code for soft tyres.
     */
    public static final int TYRE_SOFT = 1;

    /**
     * Binary tyre code for medium tyres.
     */
    public static final int TYRE_MEDIUM = 2;

    /**
     * Binary tyre code for hard tyres.
     */
    public static final int TYRE_HARD = 3;

    /**
     * Policy for laps without a uniquely matching stint.
     */
    private static final String TYRE_POLICY =
            "apply_unique_stint_coverage_only";

    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * Creates a race-metadata enricher.
     *
     * @param objectMapper JSON mapper
     * @param clock manifest timestamp source
     */
    public ReplayTimelineEnricher(
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
     * Enriches or reuses an enriched timeline.
     *
     * @param sessionCacheDirectory session cache directory
     * @param progressListener progress receiver
     * @return enrichment result
     * @throws IOException when source data is missing or invalid
     */
    public ReplayTimelineEnrichmentResult enrich(
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

        final Path timelineFile =
                processedDirectory.resolve(
                        "timeline.bin");

        final Path manifestFile =
                processedDirectory.resolve(
                        "replay-manifest.json");

        final Path positionFile =
                rawDirectory.resolve(
                        "position.json");

        final Path intervalsFile =
                rawDirectory.resolve(
                        "intervals.json");

        final Path lapsFile =
                rawDirectory.resolve(
                        "laps.json");

        final Path stintsFile =
                rawDirectory.resolve(
                        "stints.json");

        requireRegularFile(timelineFile);
        requireRegularFile(manifestFile);
        requireRegularFile(positionFile);
        requireRegularFile(intervalsFile);
        requireRegularFile(lapsFile);
        requireRegularFile(stintsFile);

        final ObjectNode manifest =
                readObject(manifestFile);

        validateManifest(
                manifest,
                timelineFile);

        final ReplayTimelineHeader header;

        try (ReplayTimelineReader reader =
                new ReplayTimelineReader(
                        timelineFile)) {

            header = reader.header();
        }

        final List<Integer> driverNumbers =
                header.driverNumbers();

        final Set<Integer> expectedDrivers =
                Set.copyOf(driverNumbers);

        final PositionData positionData =
                readPositions(
                        positionFile,
                        expectedDrivers,
                        header.driverCount());

        final IntervalData intervalData =
                readIntervals(
                        intervalsFile,
                        expectedDrivers);

        final LapData lapData =
                readLaps(
                        lapsFile,
                        expectedDrivers);

        final StintData stintData =
                readStints(
                        stintsFile,
                        expectedDrivers,
                        lapData);

        final Map<String, SourceFingerprint>
                sourceFingerprints =
                Map.of(
                        "position",
                        new SourceFingerprint(
                                positionFile.getFileName()
                                        .toString(),
                                positionData.records(),
                                sha256(positionFile)),

                        "intervals",
                        new SourceFingerprint(
                                intervalsFile.getFileName()
                                        .toString(),
                                intervalData.records(),
                                sha256(intervalsFile)),

                        "laps",
                        new SourceFingerprint(
                                lapsFile.getFileName()
                                        .toString(),
                                lapData.records(),
                                sha256(lapsFile)),

                        "stints",
                        new SourceFingerprint(
                                stintsFile.getFileName()
                                        .toString(),
                                stintData.records(),
                                sha256(stintsFile)));

        final ReplayTimelineEnrichmentResult reusable =
                inspectReusableEnrichment(
                        timelineFile,
                        manifest,
                        header,
                        sourceFingerprints);

        if (reusable != null) {
            progressListener.onProgress(
                    "Bestehende Rennmetadaten wurden validiert "
                    + "und wiederverwendet.");

            return reusable;
        }

        final Map<Integer, Integer> gridPositions =
                determineGridPositions(
                        positionData.byDriver(),
                        driverNumbers,
                        lapData.raceStart());

        progressListener.onProgress(
                "Rennmetadaten werden atomar ergänzt.");

        final long totalStates =
                Math.multiplyExact(
                        (long) header.frameCount(),
                        header.driverCount());

        long positionValidStates = 0;
        long lapValidStates = 0;
        long gapValidStates = 0;
        long intervalValidStates = 0;
        long tyreValidStates = 0;

        final long replayStartMillis =
                header.replayStart()
                        .toEpochMilli();

        try (ReplayTimelinePatcher patcher =
                new ReplayTimelinePatcher(
                        timelineFile)) {

            if (!patcher.header().equals(header)) {
                throw new IOException(
                        "Timeline header changed before enrichment.");
            }

            for (int driverIndex = 0;
                    driverIndex < driverNumbers.size();
                    driverIndex++) {

                CancellationSupport.checkpoint();

                final int driverNumber =
                        driverNumbers.get(driverIndex);

                final PositionCursor positionCursor =
                        new PositionCursor(
                                positionData.byDriver()
                                        .get(driverNumber),
                                lapData.raceStart()
                                        .toEpochMilli(),
                                gridPositions.get(
                                        driverNumber));

                final LapCursor lapCursor =
                        new LapCursor(
                                lapData.byDriver()
                                        .get(driverNumber));

                final IntervalCursor intervalCursor =
                        new IntervalCursor(
                                intervalData.byDriver()
                                        .get(driverNumber),
                                lapData.raceStart()
                                        .toEpochMilli());

                final Map<Integer, Integer> tyreByLap =
                        stintData.tyreByDriverAndLap()
                                .get(driverNumber);

                long driverGapValid = 0;
                long driverIntervalValid = 0;

                for (int frameIndex = 0;
                        frameIndex < header.frameCount();
                        frameIndex++) {

                    if ((frameIndex & 0xFF) == 0) {
                        CancellationSupport.checkpoint();
                    }

                    final long elapsedMillis =
                            Math.multiplyExact(
                                    (long) frameIndex,
                                    header.frameIntervalMillis());

                    final long targetEpochMillis =
                            Math.addExact(
                                    replayStartMillis,
                                    elapsedMillis);

                    final int position =
                            positionCursor.valueAt(
                                    targetEpochMillis);

                    final int lap =
                            lapCursor.valueAt(
                                    targetEpochMillis);

                    final boolean lapValid =
                            lap > 0;

                    final int tyreCode =
                            lapValid
                                    ? tyreByLap.getOrDefault(
                                            lap,
                                            0)
                                    : 0;

                    final boolean tyreValid =
                            tyreCode > 0;

                    final IntervalValue intervalValue =
                            intervalCursor.valueAt(
                                    targetEpochMillis);

                    final ReplayTimelineMetadata metadata =
                            new ReplayTimelineMetadata(
                                    position,
                                    true,
                                    lap,
                                    lapValid,
                                    intervalValue.gap(),
                                    intervalValue.gapValid(),
                                    intervalValue.interval(),
                                    intervalValue.intervalValid(),
                                    tyreCode,
                                    tyreValid);

                    patcher.patchMetadata(
                            frameIndex,
                            driverIndex,
                            metadata);

                    positionValidStates++;

                    if (lapValid) {
                        lapValidStates++;
                    }

                    if (intervalValue.gapValid()) {
                        gapValidStates++;
                        driverGapValid++;
                    }

                    if (intervalValue.intervalValid()) {
                        intervalValidStates++;
                        driverIntervalValid++;
                    }

                    if (tyreValid) {
                        tyreValidStates++;
                    }
                }

                progressListener.onProgress(
                        "["
                        + (driverIndex + 1)
                        + "/"
                        + driverNumbers.size()
                        + "] Fahrer #"
                        + driverNumber
                        + " | position="
                        + header.frameCount()
                        + "/"
                        + header.frameCount()
                        + " | gap="
                        + driverGapValid
                        + " | interval="
                        + driverIntervalValid);
            }

            patcher.complete();
        }

        final long bytes =
                Files.size(timelineFile);

        if (bytes != header.expectedFileSizeBytes()) {
            throw new IOException(
                    "Enriched timeline size changed.");
        }

        final String checksum =
                sha256(timelineFile);

        validateCompletedTimeline(
                timelineFile,
                header);

        updateManifest(
                manifestFile,
                manifest,
                bytes,
                checksum,
                lapData.raceStart(),
                totalStates,
                positionValidStates,
                lapValidStates,
                gapValidStates,
                intervalValidStates,
                tyreValidStates,
                sourceFingerprints);

        return new ReplayTimelineEnrichmentResult(
                timelineFile,
                false,
                bytes,
                checksum,
                totalStates,
                positionValidStates,
                lapValidStates,
                gapValidStates,
                intervalValidStates,
                tyreValidStates);
    }

    private ReplayTimelineEnrichmentResult
            inspectReusableEnrichment(
                    final Path timelineFile,
                    final ObjectNode manifest,
                    final ReplayTimelineHeader header,
                    final Map<String, SourceFingerprint>
                            fingerprints)
                    throws IOException {

        final JsonNode timeline =
                manifest.path("timeline");

        if (!"complete".equals(
                timeline.path("enrichment_state")
                        .asText())) {

            return null;
        }

        if (timeline.path("enrichment_version")
                .asInt(-1)
                != ENRICHMENT_VERSION) {

            return null;
        }

        if (timeline.path("interval_hold_millis")
                .asLong(-1)
                != INTERVAL_HOLD_MILLIS) {

            return null;
        }

        if (!TYRE_POLICY.equals(
                timeline.path("tyre_policy")
                        .asText())) {

            return null;
        }

        final JsonNode sources =
                timeline.path("enrichment_sources");

        for (final Map.Entry<String, SourceFingerprint>
                entry : fingerprints.entrySet()) {

            final JsonNode source =
                    sources.path(
                            entry.getKey());

            final SourceFingerprint expected =
                    entry.getValue();

            if (!expected.path().equals(
                    source.path("path").asText())
                    || expected.records()
                    != source.path("records")
                            .asInt(-1)
                    || !expected.sha256().equals(
                            source.path("sha256")
                                    .asText())) {

                return null;
            }
        }

        if (!Files.isRegularFile(timelineFile)
                || Files.size(timelineFile)
                != header.expectedFileSizeBytes()) {

            return null;
        }

        final String manifestChecksum =
                timeline.path("sha256")
                        .asText("");

        if (manifestChecksum.length() != 64) {
            return null;
        }

        final String actualChecksum =
                sha256(timelineFile);

        if (!manifestChecksum.equals(
                actualChecksum)) {

            return null;
        }

        try {
            validateCompletedTimeline(
                    timelineFile,
                    header);
        } catch (final IOException exception) {
            return null;
        }

        final long totalStates =
                timeline.path("total_states")
                        .asLong(-1);

        final long positionValidStates =
                timeline.path(
                        "position_valid_states")
                        .asLong(-1);

        final long lapValidStates =
                timeline.path(
                        "lap_valid_states")
                        .asLong(-1);

        final long gapValidStates =
                timeline.path(
                        "gap_valid_states")
                        .asLong(-1);

        final long intervalValidStates =
                timeline.path(
                        "interval_valid_states")
                        .asLong(-1);

        final long tyreValidStates =
                timeline.path(
                        "tyre_valid_states")
                        .asLong(-1);

        if (!validCount(
                positionValidStates,
                totalStates)
                || !validCount(
                        lapValidStates,
                        totalStates)
                || !validCount(
                        gapValidStates,
                        totalStates)
                || !validCount(
                        intervalValidStates,
                        totalStates)
                || !validCount(
                        tyreValidStates,
                        totalStates)) {

            return null;
        }

        return new ReplayTimelineEnrichmentResult(
                timelineFile,
                true,
                header.expectedFileSizeBytes(),
                actualChecksum,
                totalStates,
                positionValidStates,
                lapValidStates,
                gapValidStates,
                intervalValidStates,
                tyreValidStates);
    }

    private void updateManifest(
            final Path manifestFile,
            final ObjectNode manifest,
            final long bytes,
            final String checksum,
            final Instant raceStart,
            final long totalStates,
            final long positionValidStates,
            final long lapValidStates,
            final long gapValidStates,
            final long intervalValidStates,
            final long tyreValidStates,
            final Map<String, SourceFingerprint>
                    fingerprints)
            throws IOException {

        final JsonNode timelineNode =
                manifest.get("timeline");

        if (!(timelineNode
                instanceof ObjectNode timeline)) {

            throw new IOException(
                    "Processed manifest has no timeline object.");
        }

        timeline.put(
                "enrichment_state",
                "complete");

        timeline.put(
                "enrichment_version",
                ENRICHMENT_VERSION);

        timeline.put(
                "enriched_at",
                Instant.now(clock).toString());

        timeline.put(
                "race_start",
                raceStart.toString());

        timeline.put(
                "interval_policy",
                "hold_last_with_explicit_null_invalidation");

        timeline.put(
                "interval_hold_millis",
                INTERVAL_HOLD_MILLIS);

        timeline.put(
                "tyre_policy",
                TYRE_POLICY);

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
                "position_valid_states",
                positionValidStates);

        timeline.put(
                "lap_valid_states",
                lapValidStates);

        timeline.put(
                "gap_valid_states",
                gapValidStates);

        timeline.put(
                "interval_valid_states",
                intervalValidStates);

        timeline.put(
                "tyre_valid_states",
                tyreValidStates);

        final ObjectNode sourceNode =
                timeline.putObject(
                        "enrichment_sources");

        for (final Map.Entry<String, SourceFingerprint>
                entry : fingerprints.entrySet()) {

            final ObjectNode source =
                    sourceNode.putObject(
                            entry.getKey());

            source.put(
                    "path",
                    entry.getValue().path());

            source.put(
                    "records",
                    entry.getValue().records());

            source.put(
                    "sha256",
                    entry.getValue().sha256());
        }

        final ObjectNode tyreCodes =
                timeline.putObject(
                        "tyre_compound_codes");

        tyreCodes.put(
                "SOFT",
                TYRE_SOFT);

        tyreCodes.put(
                "MEDIUM",
                TYRE_MEDIUM);

        tyreCodes.put(
                "HARD",
                TYRE_HARD);

        atomicWriteJson(
                manifestFile,
                manifest);
    }

    private PositionData readPositions(
            final Path file,
            final Set<Integer> expectedDrivers,
            final int driverCount)
            throws IOException {

        final JsonNode root =
                readArray(file);

        final Map<Integer, List<PositionSample>>
                byDriver =
                initializedDriverMap(
                        expectedDrivers);

        long previousGlobalTime =
                Long.MIN_VALUE;

        for (final JsonNode row : root) {
            requireObject(
                    row,
                    file);

            final int driverNumber =
                    requiredPositiveInt(
                            row,
                            "driver_number",
                            file);

            requireExpectedDriver(
                    driverNumber,
                    expectedDrivers,
                    file);

            final long epochMillis =
                    requiredInstant(
                            row,
                            "date",
                            file)
                            .toEpochMilli();

            if (epochMillis < previousGlobalTime) {
                throw new IOException(
                        "Position rows are not globally time-sorted.");
            }

            previousGlobalTime =
                    epochMillis;

            final int position =
                    requiredPositiveInt(
                            row,
                            "position",
                            file);

            if (position > driverCount) {
                throw new IOException(
                        "Invalid race position: "
                        + position);
            }

            byDriver.get(driverNumber)
                    .add(
                            new PositionSample(
                                    epochMillis,
                                    position));
        }

        requireAllDrivers(
                byDriver,
                file);

        return new PositionData(
                immutableLists(byDriver),
                root.size());
    }

    private IntervalData readIntervals(
            final Path file,
            final Set<Integer> expectedDrivers)
            throws IOException {

        final JsonNode root =
                readArray(file);

        final Map<Integer, List<IntervalSample>>
                byDriver =
                initializedDriverMap(
                        expectedDrivers);

        long previousGlobalTime =
                Long.MIN_VALUE;

        for (final JsonNode row : root) {
            requireObject(
                    row,
                    file);

            final int driverNumber =
                    requiredPositiveInt(
                            row,
                            "driver_number",
                            file);

            requireExpectedDriver(
                    driverNumber,
                    expectedDrivers,
                    file);

            final long epochMillis =
                    requiredInstant(
                            row,
                            "date",
                            file)
                            .toEpochMilli();

            if (epochMillis < previousGlobalTime) {
                throw new IOException(
                        "Interval rows are not globally time-sorted.");
            }

            previousGlobalTime =
                    epochMillis;

            final Float gap =
                    nullableGapSeconds(
                            row,
                            "gap_to_leader",
                            file);

            final Float interval =
                    nullableNonNegativeFloat(
                            row,
                            "interval",
                            file);

            byDriver.get(driverNumber)
                    .add(
                            new IntervalSample(
                                    epochMillis,
                                    gap,
                                    interval));
        }

        return new IntervalData(
                immutableLists(byDriver),
                root.size());
    }

    private LapData readLaps(
            final Path file,
            final Set<Integer> expectedDrivers)
            throws IOException {

        final JsonNode root =
                readArray(file);

        final Map<Integer, List<LapSample>>
                mutable =
                initializedDriverMap(
                        expectedDrivers);

        final Set<Instant> raceStarts =
                new HashSet<>();

        for (final JsonNode row : root) {
            requireObject(
                    row,
                    file);

            final int driverNumber =
                    requiredPositiveInt(
                            row,
                            "driver_number",
                            file);

            requireExpectedDriver(
                    driverNumber,
                    expectedDrivers,
                    file);

            final Instant dateStart =
                    requiredInstant(
                            row,
                            "date_start",
                            file);

            final int lapNumber =
                    requiredPositiveInt(
                            row,
                            "lap_number",
                            file);

            if (lapNumber > 65_535) {
                throw new IOException(
                        "Lap number exceeds binary range.");
            }

            mutable.get(driverNumber)
                    .add(
                            new LapSample(
                                    dateStart.toEpochMilli(),
                                    lapNumber));

            if (lapNumber == 1) {
                raceStarts.add(dateStart);
            }
        }

        requireAllDrivers(
                mutable,
                file);

        if (raceStarts.size() != 1) {
            throw new IOException(
                    "Expected one common lap-one start, found "
                    + raceStarts);
        }

        for (final Map.Entry<Integer, List<LapSample>>
                entry : mutable.entrySet()) {

            entry.getValue()
                    .sort(
                            Comparator.comparingLong(
                                    LapSample::epochMillis));

            int expectedLap = 1;
            long previousTime =
                    Long.MIN_VALUE;

            for (final LapSample lap :
                    entry.getValue()) {

                if (lap.lapNumber()
                        != expectedLap) {

                    throw new IOException(
                            "Non-contiguous laps for driver #"
                            + entry.getKey());
                }

                if (lap.epochMillis()
                        < previousTime) {

                    throw new IOException(
                            "Laps are not time-sorted for driver #"
                            + entry.getKey());
                }

                expectedLap++;
                previousTime =
                        lap.epochMillis();
            }
        }

        return new LapData(
                immutableLists(mutable),
                raceStarts.iterator()
                        .next(),
                root.size());
    }

    private StintData readStints(
            final Path file,
            final Set<Integer> expectedDrivers,
            final LapData lapData)
            throws IOException {

        final JsonNode root =
                readArray(file);

        final Map<Integer, List<StintSample>>
                stintsByDriver =
                initializedDriverMap(
                        expectedDrivers);

        for (final JsonNode row : root) {
            requireObject(
                    row,
                    file);

            final int driverNumber =
                    requiredPositiveInt(
                            row,
                            "driver_number",
                            file);

            requireExpectedDriver(
                    driverNumber,
                    expectedDrivers,
                    file);

            final int stintNumber =
                    requiredPositiveInt(
                            row,
                            "stint_number",
                            file);

            final int lapStart =
                    requiredPositiveInt(
                            row,
                            "lap_start",
                            file);

            final int lapEnd =
                    requiredPositiveInt(
                            row,
                            "lap_end",
                            file);

            if (lapEnd < lapStart) {
                throw new IOException(
                        "Stint lap_end precedes lap_start.");
            }

            final String compound =
                    requiredText(
                            row,
                            "compound",
                            file);

            final int tyreCode =
                    tyreCode(compound);

            stintsByDriver.get(driverNumber)
                    .add(
                            new StintSample(
                                    stintNumber,
                                    lapStart,
                                    lapEnd,
                                    tyreCode));
        }

        requireAllDrivers(
                stintsByDriver,
                file);

        final Map<Integer, Map<Integer, Integer>>
                tyreByDriverAndLap =
                new LinkedHashMap<>();

        for (final int driver :
                expectedDrivers.stream()
                        .sorted()
                        .toList()) {

            final List<StintSample> stints =
                    stintsByDriver.get(driver);

            stints.sort(
                    Comparator.comparingInt(
                            StintSample::stintNumber));

            int previousStint = 0;

            for (final StintSample stint : stints) {
                if (stint.stintNumber()
                        <= previousStint) {

                    throw new IOException(
                            "Duplicate or unsorted stints for driver #"
                            + driver);
                }

                previousStint =
                        stint.stintNumber();
            }

            final Map<Integer, Integer> tyreByLap =
                    new HashMap<>();

            for (final LapSample lap :
                    lapData.byDriver()
                            .get(driver)) {

                int matches = 0;
                int tyreCode = 0;

                for (final StintSample stint : stints) {
                    if (lap.lapNumber()
                            >= stint.lapStart()
                            && lap.lapNumber()
                            <= stint.lapEnd()) {

                        matches++;
                        tyreCode =
                                stint.tyreCode();
                    }
                }

                if (matches > 1) {
                    throw new IOException(
                            "Lap "
                            + lap.lapNumber()
                            + " for driver #"
                            + driver
                            + " is covered by "
                            + matches
                            + " stints.");
                }

                if (matches == 1) {
                    tyreByLap.put(
                            lap.lapNumber(),
                            tyreCode);
                }
            }

            tyreByDriverAndLap.put(
                    driver,
                    Map.copyOf(tyreByLap));
        }

        return new StintData(
                Map.copyOf(
                        tyreByDriverAndLap),
                root.size());
    }

    private static Map<Integer, Integer>
            determineGridPositions(
                    final Map<Integer, List<PositionSample>>
                            positionsByDriver,
                    final List<Integer> driverNumbers,
                    final Instant raceStart)
                    throws IOException {

        final Map<Integer, Integer> grid =
                new LinkedHashMap<>();

        final Set<Integer> uniquePositions =
                new HashSet<>();

        final long raceStartMillis =
                raceStart.toEpochMilli();

        for (final int driver :
                driverNumbers) {

            int gridPosition = 0;

            for (final PositionSample sample :
                    positionsByDriver.get(driver)) {

                if (sample.epochMillis()
                        <= raceStartMillis) {

                    gridPosition =
                            sample.position();
                } else {
                    break;
                }
            }

            if (gridPosition == 0) {
                throw new IOException(
                        "No grid position for driver #"
                        + driver);
            }

            if (!uniquePositions.add(
                    gridPosition)) {

                throw new IOException(
                        "Duplicate grid position: "
                        + gridPosition);
            }

            grid.put(
                    driver,
                    gridPosition);
        }

        if (uniquePositions.size()
                != driverNumbers.size()) {

            throw new IOException(
                    "Grid positions are incomplete.");
        }

        for (int position = 1;
                position <= driverNumbers.size();
                position++) {

            if (!uniquePositions.contains(
                    position)) {

                throw new IOException(
                        "Grid position "
                        + position
                        + " is missing.");
            }
        }

        return Map.copyOf(grid);
    }

    private static void validateManifest(
            final ObjectNode manifest,
            final Path timelineFile)
            throws IOException {

        if (!"timeline_complete".equals(
                manifest.path("cache_state")
                        .asText())) {

            throw new IOException(
                    "Processed cache is not timeline_complete.");
        }

        final JsonNode timeline =
                manifest.path("timeline");

        if (!"complete".equals(
                timeline.path("state")
                        .asText())) {

            throw new IOException(
                    "Timeline manifest is incomplete.");
        }

        final String checksum =
                timeline.path("sha256")
                        .asText("");

        if (checksum.length() != 64) {
            throw new IOException(
                    "Timeline manifest has no valid checksum.");
        }

        if (!checksum.equals(
                sha256(timelineFile))) {

            throw new IOException(
                    "Timeline checksum differs from manifest.");
        }
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
                        "Enriched timeline header changed.");
            }

            if (Files.size(timelineFile)
                    != expectedHeader
                            .expectedFileSizeBytes()) {

                throw new IOException(
                        "Enriched timeline size changed.");
            }
        }
    }

    private ObjectNode readObject(
            final Path file)
            throws IOException {

        final JsonNode root =
                objectMapper.readTree(
                        file.toFile());

        if (!(root instanceof ObjectNode object)) {
            throw new IOException(
                    "Expected JSON object: "
                    + file);
        }

        return object;
    }

    private JsonNode readArray(
            final Path file)
            throws IOException {

        final JsonNode root =
                objectMapper.readTree(
                        file.toFile());

        if (!root.isArray()) {
            throw new IOException(
                    "Expected JSON array: "
                    + file);
        }

        return root;
    }

    private static void requireObject(
            final JsonNode node,
            final Path file)
            throws IOException {

        if (!node.isObject()) {
            throw new IOException(
                    "Expected object row in "
                    + file);
        }
    }

    private static int requiredPositiveInt(
            final JsonNode row,
            final String field,
            final Path file)
            throws IOException {

        final JsonNode value =
                row.get(field);

        if (value == null
                || !value.canConvertToInt()
                || value.intValue() <= 0) {

            throw new IOException(
                    "Invalid positive integer "
                    + field
                    + " in "
                    + file);
        }

        return value.intValue();
    }

    private static String requiredText(
            final JsonNode row,
            final String field,
            final Path file)
            throws IOException {

        final JsonNode value =
                row.get(field);

        if (value == null
                || !value.isTextual()
                || value.textValue().isBlank()) {

            throw new IOException(
                    "Invalid text field "
                    + field
                    + " in "
                    + file);
        }

        return value.textValue();
    }

    private static Instant requiredInstant(
            final JsonNode row,
            final String field,
            final Path file)
            throws IOException {

        final String value =
                requiredText(
                        row,
                        field,
                        file);

        try {
            return Instant.parse(value);
        } catch (final RuntimeException exception) {
            throw new IOException(
                    "Invalid timestamp "
                    + field
                    + " in "
                    + file
                    + ": "
                    + value,
                    exception);
        }
    }

    private static Float nullableNonNegativeFloat(
            final JsonNode row,
            final String field,
            final Path file)
            throws IOException {

        final JsonNode value =
                row.get(field);

        if (value == null || value.isNull()) {
            return null;
        }

        if (!value.isNumber()) {
            throw new IOException(
                    "Expected numeric or null field "
                    + field
                    + " in "
                    + file);
        }

        final double number =
                value.doubleValue();

        if (!Double.isFinite(number)
                || number < 0.0
                || number > Float.MAX_VALUE) {

            throw new IOException(
                    "Invalid non-negative number "
                    + field
                    + " in "
                    + file);
        }

        return (float) number;
    }

    private static Float nullableGapSeconds(
            final JsonNode row,
            final String field,
            final Path file)
            throws IOException {

        final JsonNode value =
                row.get(field);

        if (value != null
                && value.isTextual()
                && LAPPED_GAP_PATTERN
                        .matcher(
                                value.textValue())
                        .matches()) {

            return null;
        }

        return nullableNonNegativeFloat(
                row,
                field,
                file);
    }

    private static void requireExpectedDriver(
            final int driver,
            final Set<Integer> expectedDrivers,
            final Path file)
            throws IOException {

        if (!expectedDrivers.contains(driver)) {
            throw new IOException(
                    "Unexpected driver #"
                    + driver
                    + " in "
                    + file);
        }
    }

    private static <T> Map<Integer, List<T>>
            initializedDriverMap(
                    final Set<Integer> drivers) {

        final Map<Integer, List<T>> result =
                new LinkedHashMap<>();

        for (final int driver :
                drivers.stream()
                        .sorted()
                        .toList()) {

            result.put(
                    driver,
                    new ArrayList<>());
        }

        return result;
    }

    private static <T> void requireAllDrivers(
            final Map<Integer, List<T>> byDriver,
            final Path file)
            throws IOException {

        for (final Map.Entry<Integer, List<T>>
                entry : byDriver.entrySet()) {

            if (entry.getValue().isEmpty()) {
                throw new IOException(
                        "Missing driver #"
                        + entry.getKey()
                        + " in "
                        + file);
            }
        }
    }

    private static <T> Map<Integer, List<T>>
            immutableLists(
                    final Map<Integer, List<T>> source) {

        final Map<Integer, List<T>> result =
                new LinkedHashMap<>();

        for (final Map.Entry<Integer, List<T>>
                entry : source.entrySet()) {

            result.put(
                    entry.getKey(),
                    List.copyOf(
                            entry.getValue()));
        }

        return Map.copyOf(result);
    }

    private static int tyreCode(
            final String compound)
            throws IOException {

        return switch (compound) {
            case "SOFT" -> TYRE_SOFT;
            case "MEDIUM" -> TYRE_MEDIUM;
            case "HARD" -> TYRE_HARD;

            default ->
                throw new IOException(
                        "Unsupported tyre compound: "
                        + compound);
        };
    }

    private static boolean validCount(
            final long value,
            final long total) {

        return total > 0
                && value >= 0
                && value <= total;
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

    private static String sha256(
            final Path file)
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

    private record PositionData(
            Map<Integer, List<PositionSample>> byDriver,
            int records) {
    }

    private record IntervalData(
            Map<Integer, List<IntervalSample>> byDriver,
            int records) {
    }

    private record LapData(
            Map<Integer, List<LapSample>> byDriver,
            Instant raceStart,
            int records) {
    }

    private record StintData(
            Map<Integer, Map<Integer, Integer>>
                    tyreByDriverAndLap,
            int records) {
    }

    private record SourceFingerprint(
            String path,
            int records,
            String sha256) {
    }

    private record PositionSample(
            long epochMillis,
            int position) {
    }

    private record IntervalSample(
            long epochMillis,
            Float gap,
            Float interval) {
    }

    private record LapSample(
            long epochMillis,
            int lapNumber) {
    }

    private record StintSample(
            int stintNumber,
            int lapStart,
            int lapEnd,
            int tyreCode) {
    }

    private record IntervalValue(
            float gap,
            boolean gapValid,
            float interval,
            boolean intervalValid) {

        private static IntervalValue invalid() {
            return new IntervalValue(
                    Float.NaN,
                    false,
                    Float.NaN,
                    false);
        }
    }

    private static final class PositionCursor {

        private final List<PositionSample> samples;

        private int index;
        private int currentPosition;

        private PositionCursor(
                final List<PositionSample> samples,
                final long raceStartMillis,
                final int gridPosition) {

            this.samples =
                    Objects.requireNonNull(
                            samples,
                            "samples");

            currentPosition =
                    gridPosition;

            while (index < samples.size()
                    && samples.get(index)
                            .epochMillis()
                            <= raceStartMillis) {

                index++;
            }
        }

        private int valueAt(
                final long targetEpochMillis) {

            while (index < samples.size()
                    && samples.get(index)
                            .epochMillis()
                            <= targetEpochMillis) {

                currentPosition =
                        samples.get(index)
                                .position();

                index++;
            }

            return currentPosition;
        }
    }

    private static final class LapCursor {

        private final List<LapSample> samples;

        private int index;
        private int currentLap;

        private LapCursor(
                final List<LapSample> samples) {

            this.samples =
                    Objects.requireNonNull(
                            samples,
                            "samples");
        }

        private int valueAt(
                final long targetEpochMillis) {

            while (index < samples.size()
                    && samples.get(index)
                            .epochMillis()
                            <= targetEpochMillis) {

                currentLap =
                        samples.get(index)
                                .lapNumber();

                index++;
            }

            return currentLap;
        }
    }

    private static final class IntervalCursor {

        private final List<IntervalSample> samples;

        private int index;
        private IntervalSample current;

        private IntervalCursor(
                final List<IntervalSample> samples,
                final long raceStartMillis) {

            this.samples =
                    Objects.requireNonNull(
                            samples,
                            "samples");

            while (index < samples.size()
                    && samples.get(index)
                            .epochMillis()
                            < raceStartMillis) {

                index++;
            }
        }

        private IntervalValue valueAt(
                final long targetEpochMillis) {

            while (index < samples.size()
                    && samples.get(index)
                            .epochMillis()
                            <= targetEpochMillis) {

                current =
                        samples.get(index);

                index++;
            }

            if (current == null) {
                return IntervalValue.invalid();
            }

            final long age =
                    targetEpochMillis
                    - current.epochMillis();

            if (age < 0
                    || age
                    > INTERVAL_HOLD_MILLIS) {

                return IntervalValue.invalid();
            }

            final boolean gapValid =
                    current.gap() != null;

            final boolean intervalValid =
                    current.interval() != null;

            return new IntervalValue(
                    gapValid
                            ? current.gap()
                            : Float.NaN,
                    gapValid,
                    intervalValid
                            ? current.interval()
                            : Float.NaN,
                    intervalValid);
        }
    }
}
