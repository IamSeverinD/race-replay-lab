package io.github.iamseverind.racereplay.processed;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Creates normalized replay metadata from a complete OpenF1 raw cache.
 */
public final class ProcessedReplayCacheBuilder {

    private static final int SCHEMA_VERSION = 1;

    private static final long FRAME_INTERVAL_MILLIS = 250;

    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * Creates a processed replay metadata builder.
     *
     * @param objectMapper JSON mapper
     * @param clock build timestamp source
     */
    public ProcessedReplayCacheBuilder(
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
     * Builds processed driver metadata and the replay manifest.
     *
     * @param sessionCacheDirectory complete OpenF1 session cache
     * @return generated processed cache metadata
     * @throws IOException when the raw cache is incomplete or invalid
     */
    public ProcessedReplayBuildResult build(
            final Path sessionCacheDirectory)
            throws IOException {

        final Path cacheDirectory =
                Objects.requireNonNull(
                        sessionCacheDirectory,
                        "sessionCacheDirectory");

        final Path rawManifestFile =
                cacheDirectory.resolve("manifest.json");

        final Path rawDirectory =
                cacheDirectory.resolve("raw");

        final Path rawDriversFile =
                rawDirectory.resolve("drivers.json");

        final Path rawLocationFile =
                rawDirectory.resolve("location.json");

        final Path rawCarDataFile =
                rawDirectory.resolve("car-data.json");

        requireRegularFile(rawManifestFile);
        requireRegularFile(rawDriversFile);
        requireRegularFile(rawLocationFile);
        requireRegularFile(rawCarDataFile);

        final ObjectNode rawManifest =
                readObject(rawManifestFile);

        validateRawManifest(rawManifest);

        final ArrayNode normalizedDrivers =
                readNormalizedDrivers(
                        rawDriversFile);

        final TimeRange locationRange =
                scanDateRange(
                        rawLocationFile);

        final TimeRange carDataRange =
                scanDateRange(
                        rawCarDataFile);

        final Instant replayStart =
                laterOf(
                        locationRange.start(),
                        carDataRange.start());

        final Instant replayEnd =
                earlierOf(
                        locationRange.end(),
                        carDataRange.end());

        if (!replayStart.isBefore(replayEnd)) {
            throw new IOException(
                    "Location and car-data ranges do not overlap.");
        }

        final long durationMillis =
                Duration.between(
                        replayStart,
                        replayEnd)
                        .toMillis();

        final long frameCount =
                Math.floorDiv(
                        durationMillis,
                        FRAME_INTERVAL_MILLIS)
                + 1;

        final Path processedDirectory =
                cacheDirectory.resolve("processed");

        Files.createDirectories(
                processedDirectory);

        final Path driversFile =
                processedDirectory.resolve(
                        "drivers.json");

        final String driversJson =
                objectMapper
                        .writerWithDefaultPrettyPrinter()
                        .writeValueAsString(
                                normalizedDrivers)
                + System.lineSeparator();

        atomicWrite(
                driversFile,
                driversJson);

        final ProcessedFileInfo driversInfo =
                inspectFile(
                        driversFile,
                        normalizedDrivers.size());

        final Path processedManifestFile =
                processedDirectory.resolve(
                        "replay-manifest.json");

        final ObjectNode processedManifest =
                createProcessedManifest(
                        rawManifest,
                        rawManifestFile,
                        normalizedDrivers.size(),
                        locationRange,
                        carDataRange,
                        replayStart,
                        replayEnd,
                        durationMillis,
                        frameCount,
                        driversInfo);

        final String processedManifestJson =
                objectMapper
                        .writerWithDefaultPrettyPrinter()
                        .writeValueAsString(
                                processedManifest)
                + System.lineSeparator();

        atomicWrite(
                processedManifestFile,
                processedManifestJson);

        return new ProcessedReplayBuildResult(
                processedDirectory,
                processedManifestFile,
                driversFile,
                replayStart,
                replayEnd,
                FRAME_INTERVAL_MILLIS,
                frameCount,
                normalizedDrivers.size());
    }

    private ObjectNode createProcessedManifest(
            final ObjectNode rawManifest,
            final Path rawManifestFile,
            final int driverCount,
            final TimeRange locationRange,
            final TimeRange carDataRange,
            final Instant replayStart,
            final Instant replayEnd,
            final long durationMillis,
            final long frameCount,
            final ProcessedFileInfo driversInfo)
            throws IOException {

        final ObjectNode manifest =
                objectMapper.createObjectNode();

        manifest.put(
                "schema_version",
                SCHEMA_VERSION);

        manifest.put(
                "cache_state",
                "metadata_complete");

        manifest.put(
                "built_at",
                Instant.now(clock).toString());

        manifest.set(
                "query",
                rawManifest.path("query").deepCopy());

        manifest.set(
                "session",
                rawManifest.path("session").deepCopy());

        final ObjectNode source =
                manifest.putObject("source");

        source.put(
                "provider",
                rawManifest.path("source")
                        .asText("OpenF1"));

        source.put(
                "manifest_path",
                "../manifest.json");

        source.put(
                "manifest_sha256",
                sha256(rawManifestFile));

        source.put(
                "raw_cache_state",
                rawManifest.path("cache_state")
                        .asText());

        source.put(
                "raw_dataset_count",
                rawManifest.path("raw_dataset_count")
                        .asInt());

        final ObjectNode replay =
                manifest.putObject("replay");

        replay.put(
                "start",
                replayStart.toString());

        replay.put(
                "end",
                replayEnd.toString());

        replay.put(
                "duration_millis",
                durationMillis);

        replay.put(
                "frame_interval_millis",
                FRAME_INTERVAL_MILLIS);

        replay.put(
                "frame_count",
                frameCount);

        replay.put(
                "driver_count",
                driverCount);

        final ObjectNode ranges =
                manifest.putObject("source_ranges");

        addTimeRange(
                ranges,
                "location",
                locationRange);

        addTimeRange(
                ranges,
                "car_data",
                carDataRange);

        final ObjectNode files =
                manifest.putObject("files");

        final ObjectNode drivers =
                files.putObject("drivers");

        drivers.put(
                "path",
                "drivers.json");

        drivers.put(
                "bytes",
                driversInfo.bytes());

        drivers.put(
                "records",
                driversInfo.records());

        drivers.put(
                "sha256",
                driversInfo.sha256());

        final ObjectNode timeline =
                manifest.putObject("timeline");

        timeline.put(
                "state",
                "pending");

        timeline.put(
                "path",
                "timeline.bin");

        timeline.put(
                "format_version",
                ReplayTimelineFormat.VERSION);

        timeline.put(
                "magic",
                ReplayTimelineFormat.magicText());

        timeline.put(
                "endianness",
                "big_endian");

        timeline.put(
                "header_size_bytes",
                ReplayTimelineFormat.headerSizeBytes(
                        driverCount));

        timeline.put(
                "state_size_bytes",
                ReplayTimelineFormat.STATE_SIZE_BYTES);

        timeline.put(
                "frame_size_bytes",
                ReplayTimelineFormat.frameSizeBytes(
                        driverCount));

        timeline.put(
                "expected_bytes",
                ReplayTimelineFormat.expectedFileSizeBytes(
                        Math.toIntExact(frameCount),
                        driverCount));

        timeline.put(
                "telemetry_normalization_version",
                ReplayTimelineBuilder
                        .TELEMETRY_NORMALIZATION_VERSION);

        timeline.put(
                "invalid_telemetry_policy",
                "exclude_out_of_domain_samples");

        final ObjectNode events =
                manifest.putObject("events");

        events.put(
                "state",
                "pending");

        events.put(
                "path",
                "events.json");

        return manifest;
    }

    private static void addTimeRange(
            final ObjectNode parent,
            final String name,
            final TimeRange range) {

        final ObjectNode node =
                parent.putObject(name);

        node.put(
                "start",
                range.start().toString());

        node.put(
                "end",
                range.end().toString());

        node.put(
                "records",
                range.records());
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

    private static void validateRawManifest(
            final ObjectNode rawManifest)
            throws IOException {

        final String cacheState =
                rawManifest.path("cache_state")
                        .asText();

        if (!"raw_complete".equals(cacheState)) {
            throw new IOException(
                    "Raw cache is not complete: "
                    + cacheState);
        }

        if (rawManifest.path("raw_dataset_count")
                .asInt(-1) != 10) {

            throw new IOException(
                    "Raw cache does not contain ten datasets.");
        }

        if (!rawManifest.path("session")
                .isObject()) {

            throw new IOException(
                    "Raw manifest has no session object.");
        }
    }

    private ArrayNode readNormalizedDrivers(
            final Path driversFile)
            throws IOException {

        final JsonNode root =
                objectMapper.readTree(
                        driversFile.toFile());

        if (!root.isArray()) {
            throw new IOException(
                    "Drivers file is not a JSON array: "
                    + driversFile);
        }

        final List<ObjectNode> drivers =
                new ArrayList<>();

        final Set<Integer> driverNumbers =
                new HashSet<>();

        for (final JsonNode rawDriver : root) {
            final int driverNumber =
                    requiredInt(
                            rawDriver,
                            "driver_number",
                            driversFile);

            if (!driverNumbers.add(driverNumber)) {
                throw new IOException(
                        "Duplicate driver number: "
                        + driverNumber);
            }

            final String teamColour =
                    normalizeTeamColour(
                            requiredText(
                                    rawDriver,
                                    "team_colour",
                                    driversFile));

            final ObjectNode driver =
                    objectMapper.createObjectNode();

            driver.put(
                    "driver_number",
                    driverNumber);

            driver.put(
                    "name_acronym",
                    requiredText(
                            rawDriver,
                            "name_acronym",
                            driversFile));

            driver.put(
                    "first_name",
                    requiredText(
                            rawDriver,
                            "first_name",
                            driversFile));

            driver.put(
                    "last_name",
                    requiredText(
                            rawDriver,
                            "last_name",
                            driversFile));

            driver.put(
                    "full_name",
                    requiredText(
                            rawDriver,
                            "full_name",
                            driversFile));

            driver.put(
                    "broadcast_name",
                    requiredText(
                            rawDriver,
                            "broadcast_name",
                            driversFile));

            final String countryCode =
                    optionalText(
                            rawDriver,
                            "country_code");

            if (countryCode != null) {
                driver.put(
                        "country_code",
                        countryCode);
            }

            driver.put(
                    "team_name",
                    requiredText(
                            rawDriver,
                            "team_name",
                            driversFile));

            driver.put(
                    "team_colour",
                    teamColour);

            final JsonNode headshotUrl =
                    rawDriver.get("headshot_url");

            if (headshotUrl != null
                    && headshotUrl.isTextual()
                    && !headshotUrl.textValue().isBlank()) {

                driver.put(
                        "headshot_url",
                        headshotUrl.textValue());
            }

            drivers.add(driver);
        }

        if (drivers.isEmpty()) {
            throw new IOException(
                    "Drivers file contains no drivers.");
        }

        drivers.sort(
                Comparator.comparingInt(
                        driver -> driver.path(
                                "driver_number")
                                .asInt()));

        final ArrayNode result =
                objectMapper.createArrayNode();

        drivers.forEach(result::add);

        return result;
    }

    private TimeRange scanDateRange(final Path file)
            throws IOException {

        Instant minimum = null;
        Instant maximum = null;
        long records = 0;

        try (JsonParser parser =
                objectMapper
                        .getFactory()
                        .createParser(file.toFile())) {

            if (parser.nextToken()
                    != JsonToken.START_ARRAY) {

                throw new IOException(
                        "Expected JSON array: "
                        + file);
            }

            while (true) {
                CancellationSupport.checkpoint();

                final JsonToken rowToken =
                        parser.nextToken();

                if (rowToken == JsonToken.END_ARRAY) {
                    break;
                }

                if (rowToken == null) {
                    throw new IOException(
                            "Unexpected end of JSON array: "
                            + file);
                }

                if (rowToken != JsonToken.START_OBJECT) {
                    throw new IOException(
                            "Expected JSON object row: "
                            + file);
                }

                Instant rowDate = null;

                while (parser.nextToken()
                        != JsonToken.END_OBJECT) {

                    final String fieldName =
                            parser.currentName();

                    final JsonToken valueToken =
                            parser.nextToken();

                    if ("date".equals(fieldName)) {
                        if (valueToken
                                != JsonToken.VALUE_STRING) {

                            throw new IOException(
                                    "Invalid date field in "
                                    + file);
                        }

                        rowDate =
                                parseInstant(
                                        parser.getText(),
                                        file);
                    } else {
                        parser.skipChildren();
                    }
                }

                if (rowDate == null) {
                    throw new IOException(
                            "Row has no date field in "
                            + file);
                }

                if (minimum == null
                        || rowDate.isBefore(minimum)) {

                    minimum = rowDate;
                }

                if (maximum == null
                        || rowDate.isAfter(maximum)) {

                    maximum = rowDate;
                }

                records++;
            }

            if (parser.nextToken() != null) {
                throw new IOException(
                        "Unexpected data after JSON array: "
                        + file);
            }
        }

        if (records == 0
                || minimum == null
                || maximum == null) {

            throw new IOException(
                    "No dated records found in "
                    + file);
        }

        return new TimeRange(
                minimum,
                maximum,
                records);
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

    private static int requiredInt(
            final JsonNode node,
            final String field,
            final Path source)
            throws IOException {

        final JsonNode value =
                node.get(field);

        if (value == null
                || !value.canConvertToInt()) {

            throw new IOException(
                    "Missing integer field "
                    + field
                    + " in "
                    + source);
        }

        return value.intValue();
    }

    private static String requiredText(
            final JsonNode node,
            final String field,
            final Path source)
            throws IOException {

        final JsonNode value =
                node.get(field);

        if (value == null
                || !value.isTextual()
                || value.textValue().isBlank()) {

            throw new IOException(
                    "Missing text field "
                    + field
                    + " in "
                    + source);
        }

        return value.textValue().strip();
    }

    private static String optionalText(
            final JsonNode node,
            final String field) {

        final JsonNode value =
                node.get(field);

        if (value == null
                || !value.isTextual()
                || value.textValue().isBlank()) {

            return null;
        }

        return value.textValue().strip();
    }

    private static String normalizeTeamColour(
            final String value)
            throws IOException {

        final String normalized =
                value.strip()
                        .toUpperCase(Locale.ROOT);

        if (!normalized.matches("[0-9A-F]{6}")) {
            throw new IOException(
                    "Invalid team colour: "
                    + value);
        }

        return normalized;
    }

    private static ProcessedFileInfo inspectFile(
            final Path file,
            final long records)
            throws IOException {

        return new ProcessedFileInfo(
                Files.size(file),
                records,
                sha256(file));
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

    private static void atomicWrite(
            final Path target,
            final String content)
            throws IOException {

        Files.createDirectories(
                target.getParent());

        final Path temporaryFile =
                target.resolveSibling(
                        target.getFileName()
                        + ".part-"
                        + UUID.randomUUID());

        try {
            Files.writeString(
                    temporaryFile,
                    content,
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

    private static void requireRegularFile(
            final Path file)
            throws IOException {

        if (!Files.isRegularFile(file)) {
            throw new IOException(
                    "Required cache file is missing: "
                    + file);
        }
    }

    private static Instant laterOf(
            final Instant first,
            final Instant second) {

        return first.isAfter(second)
                ? first
                : second;
    }

    private static Instant earlierOf(
            final Instant first,
            final Instant second) {

        return first.isBefore(second)
                ? first
                : second;
    }

    private record TimeRange(
            Instant start,
            Instant end,
            long records) {

        private TimeRange {
            Objects.requireNonNull(
                    start,
                    "start");

            Objects.requireNonNull(
                    end,
                    "end");

            if (end.isBefore(start)) {
                throw new IllegalArgumentException(
                        "Time range end precedes start.");
            }

            if (records <= 0) {
                throw new IllegalArgumentException(
                        "Time range must contain records.");
            }
        }
    }

    private record ProcessedFileInfo(
            long bytes,
            long records,
            String sha256) {

        private ProcessedFileInfo {
            if (bytes < 0 || records < 0) {
                throw new IllegalArgumentException(
                        "File values must not be negative.");
            }

            sha256 =
                    Objects.requireNonNull(
                            sha256,
                            "sha256");
        }
    }
}
