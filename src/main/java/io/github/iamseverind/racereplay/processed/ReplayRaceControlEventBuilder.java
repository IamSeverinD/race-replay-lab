package io.github.iamseverind.racereplay.processed;

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
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Normalizes OpenF1 race-control messages for offline replay display.
 */
public final class ReplayRaceControlEventBuilder {

    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * Creates a race-control event builder.
     *
     * @param objectMapper JSON mapper
     * @param clock manifest timestamp source
     */
    public ReplayRaceControlEventBuilder(
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
     * Builds normalized events and updates the processed manifest.
     *
     * @param sessionCacheDirectory complete session cache
     * @return normalized event count
     * @throws IOException when source or processed metadata is invalid
     */
    public int build(
            final Path sessionCacheDirectory)
            throws IOException {

        final Path cacheDirectory =
                Objects.requireNonNull(
                        sessionCacheDirectory,
                        "sessionCacheDirectory");

        final Path rawEventsFile =
                cacheDirectory.resolve("raw")
                        .resolve("race-control.json");

        final Path processedDirectory =
                cacheDirectory.resolve("processed");

        final Path manifestFile =
                processedDirectory.resolve(
                        "replay-manifest.json");

        requireRegularFile(rawEventsFile);
        requireRegularFile(manifestFile);

        final ObjectNode manifest =
                readObject(manifestFile);

        final Instant replayStart =
                parseInstant(
                        manifest.path("replay")
                                .path("start")
                                .asText(),
                        "replay.start");

        final JsonNode rawEvents =
                objectMapper.readTree(
                        rawEventsFile.toFile());

        if (!rawEvents.isArray()) {
            throw new IOException(
                    "Race-control source is not an array: "
                    + rawEventsFile);
        }

        final List<RawEvent> events =
                readEvents(
                        rawEvents,
                        rawEventsFile);

        final ObjectNode normalized =
                objectMapper.createObjectNode();

        normalized.put(
                "schema_version",
                1);

        normalized.put(
                "source",
                "OpenF1");

        final ArrayNode outputEvents =
                normalized.putArray(
                        "events");

        int scheduledLaps = 0;

        for (int sequence = 0;
                sequence < events.size();
                sequence++) {

            CancellationSupport.checkpoint();

            final RawEvent event =
                    events.get(sequence);

            final ObjectNode output =
                    outputEvents.addObject();

            output.put(
                    "sequence",
                    sequence);

            output.put(
                    "replay_seconds",
                    Duration.between(
                            replayStart,
                            event.date())
                            .toMillis()
                            / 1_000.0);

            output.put(
                    "category",
                    event.category());

            output.put(
                    "flag",
                    event.flag());

            output.put(
                    "scope",
                    event.scope());

            putOptionalInteger(
                    output,
                    "sector",
                    event.sector());

            putOptionalInteger(
                    output,
                    "lap_number",
                    event.lapNumber());

            output.put(
                    "message",
                    event.message());

            if (isCheckered(event)
                    && event.lapNumber() != null) {

                scheduledLaps =
                        Math.max(
                                scheduledLaps,
                                event.lapNumber());
            }
        }

        final Path eventsFile =
                processedDirectory.resolve(
                        "events.json");

        atomicWriteJson(
                eventsFile,
                normalized);

        final ObjectNode eventsManifest =
                manifest.withObject(
                        "/events");

        eventsManifest.put(
                "state",
                "complete");

        eventsManifest.put(
                "path",
                "events.json");

        eventsManifest.put(
                "records",
                events.size());

        eventsManifest.put(
                "bytes",
                Files.size(eventsFile));

        eventsManifest.put(
                "sha256",
                sha256(eventsFile));

        eventsManifest.put(
                "built_at",
                Instant.now(clock)
                        .toString());

        if (scheduledLaps > 0) {
            manifest.withObject("/replay")
                    .put(
                            "scheduled_laps",
                            scheduledLaps);
        }

        atomicWriteJson(
                manifestFile,
                manifest);

        return events.size();
    }

    private List<RawEvent> readEvents(
            final JsonNode rows,
            final Path source)
            throws IOException {

        final List<RawEvent> events =
                new ArrayList<>(
                        rows.size());

        int sourceSequence = 0;

        for (final JsonNode row : rows) {
            CancellationSupport.checkpoint();

            if (!row.isObject()) {
                throw new IOException(
                        "Race-control row is not an object: "
                        + source);
            }

            events.add(
                    new RawEvent(
                            parseInstant(
                                    requiredText(
                                            row,
                                            "date",
                                            source),
                                    "date"),
                            sourceSequence,
                            optionalText(
                                    row,
                                    "category"),
                            optionalText(
                                    row,
                                    "flag"),
                            optionalText(
                                    row,
                                    "scope"),
                            optionalInteger(
                                    row,
                                    "sector",
                                    source),
                            optionalInteger(
                                    row,
                                    "lap_number",
                                    source),
                            requiredText(
                                    row,
                                    "message",
                                    source)));

            sourceSequence++;
        }

        events.sort(
                Comparator.comparing(
                        RawEvent::date)
                        .thenComparingInt(
                                RawEvent::sourceSequence));

        return List.copyOf(events);
    }

    private ObjectNode readObject(
            final Path file)
            throws IOException {

        final JsonNode root =
                objectMapper.readTree(
                        file.toFile());

        if (root instanceof ObjectNode objectNode) {
            return objectNode;
        }

        throw new IOException(
                "Expected JSON object: "
                + file);
    }

    private static boolean isCheckered(
            final RawEvent event) {

        final String flag =
                event.flag()
                        .toUpperCase(
                                Locale.ROOT);

        final String message =
                event.message()
                        .toUpperCase(
                                Locale.ROOT);

        return "CHEQUERED".equals(flag)
                || "CHECKERED".equals(flag)
                || message.contains(
                        "CHEQUERED FLAG")
                || message.contains(
                        "CHECKERED FLAG");
    }

    private static String requiredText(
            final JsonNode row,
            final String field,
            final Path source)
            throws IOException {

        final JsonNode value =
                row.get(field);

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
            final JsonNode row,
            final String field) {

        final JsonNode value =
                row.get(field);

        if (value == null
                || !value.isTextual()) {

            return "";
        }

        return value.textValue().strip();
    }

    private static Integer optionalInteger(
            final JsonNode row,
            final String field,
            final Path source)
            throws IOException {

        final JsonNode value =
                row.get(field);

        if (value == null || value.isNull()) {
            return null;
        }

        if (!value.canConvertToInt()) {
            throw new IOException(
                    "Invalid integer field "
                    + field
                    + " in "
                    + source);
        }

        return value.intValue();
    }

    private static void putOptionalInteger(
            final ObjectNode node,
            final String field,
            final Integer value) {

        if (value == null) {
            node.putNull(field);
        } else {
            node.put(field, value);
        }
    }

    private static Instant parseInstant(
            final String value,
            final String field)
            throws IOException {

        try {
            return Instant.parse(value);
        } catch (final RuntimeException exception) {
            throw new IOException(
                    "Invalid timestamp "
                    + field
                    + ": "
                    + value,
                    exception);
        }
    }

    private void atomicWriteJson(
            final Path target,
            final JsonNode value)
            throws IOException {

        Files.createDirectories(
                target.getParent());

        final Path temporary =
                target.resolveSibling(
                        target.getFileName()
                        + "."
                        + UUID.randomUUID()
                        + ".tmp");

        try {
            final String json =
                    objectMapper
                            .writerWithDefaultPrettyPrinter()
                            .writeValueAsString(value)
                    + System.lineSeparator();

            Files.writeString(
                    temporary,
                    json,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);

            atomicReplace(
                    temporary,
                    target);
        } finally {
            Files.deleteIfExists(
                    temporary);
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
                    new byte[16_384];

            int count;

            while ((count = input.read(buffer)) >= 0) {
                digest.update(
                        buffer,
                        0,
                        count);
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

    private record RawEvent(
            Instant date,
            int sourceSequence,
            String category,
            String flag,
            String scope,
            Integer sector,
            Integer lapNumber,
            String message) {
    }
}
