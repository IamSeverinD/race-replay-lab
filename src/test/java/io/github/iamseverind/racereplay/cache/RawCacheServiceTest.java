package io.github.iamseverind.racereplay.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.iamseverind.racereplay.openf1.OpenF1Dataset;
import io.github.iamseverind.racereplay.openf1.OpenF1HttpException;
import io.github.iamseverind.racereplay.openf1.OpenF1Session;
import io.github.iamseverind.racereplay.openf1.RawDatasetFileInfo;
import io.github.iamseverind.racereplay.openf1.RawDownloadClient;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests resumable full raw-cache creation.
 */
final class RawCacheServiceTest {

    @TempDir
    private Path temporaryDirectory;

    /**
     * Downloads every endpoint once and reuses all files later.
     *
     * @throws Exception when the test cache cannot be created
     */
    @Test
    void downloadsAndReusesAllRawDatasets()
            throws Exception {

        final ObjectMapper objectMapper =
                new ObjectMapper();

        final SessionCacheResult sessionCache =
                createSessionCache(objectMapper);

        final AtomicInteger downloadCalls =
                new AtomicInteger();

        final RawDownloadClient downloadClient =
                (uri, target) -> {
                    downloadCalls.incrementAndGet();

                    final boolean driversRequest =
                            uri.getPath()
                                    .endsWith("/drivers");

                    final String json;

                    final long records;

                    if (driversRequest) {
                        json =
                                """
                                [
                                  {"driver_number":1},
                                  {"driver_number":44}
                                ]
                                """;

                        records = 2;
                    } else {
                        json =
                                "[{\"uri\":\""
                                + uri
                                + "\"}]";

                        records = 1;
                    }

                    Files.createDirectories(
                            target.getParent());

                    Files.writeString(
                            target,
                            json,
                            StandardCharsets.UTF_8);

                    return new RawDatasetFileInfo(
                            Files.size(target),
                            records,
                            sha256(target));
                };

        final RawCacheService service =
                new RawCacheService(
                        downloadClient,
                        objectMapper,
                        Clock.fixed(
                                Instant.parse(
                                        "2026-07-19T05:00:00Z"),
                                ZoneOffset.UTC));

        final RawCacheDownloadResult firstResult =
                service.downloadAll(
                        sessionCache,
                        message -> { });

        assertEquals(
                OpenF1Dataset.values().length,
                firstResult.downloadedDatasets());

        assertEquals(
                0,
                firstResult.reusedDatasets());

        /*
         * Eight normal endpoint calls plus:
         * two drivers times four 30-minute windows
         * for each of the two high-frequency endpoints.
         */
        assertEquals(
                24,
                downloadCalls.get());

        final JsonNode firstManifest =
                objectMapper.readTree(
                        sessionCache
                                .manifestFile()
                                .toFile());

        assertEquals(
                "raw_complete",
                firstManifest
                        .path("cache_state")
                        .asText());

        assertEquals(
                OpenF1Dataset.values().length,
                firstManifest
                        .path("raw_dataset_count")
                        .asInt());

        assertTrue(
                firstManifest
                        .path("files")
                        .has("car_data"));

        assertEquals(
                "driver_and_time_partitions",
                firstManifest
                        .path("files")
                        .path("location")
                        .path("download_strategy")
                        .asText());

        final Path locationFile =
                sessionCache.cacheDirectory()
                        .resolve("raw")
                        .resolve("location.json");

        final JsonNode locationRows =
                objectMapper.readTree(
                        locationFile.toFile());

        assertEquals(
                8,
                locationRows.size());

        assertFalse(
                Files.exists(
                        sessionCache
                                .cacheDirectory()
                                .resolve("raw")
                                .resolve(".chunks")));

        final RawCacheDownloadResult secondResult =
                service.downloadAll(
                        sessionCache,
                        message -> { });

        assertEquals(
                0,
                secondResult.downloadedDatasets());

        assertEquals(
                OpenF1Dataset.values().length,
                secondResult.reusedDatasets());

        assertEquals(
                24,
                downloadCalls.get());
    }

    /**
     * A 404 in a high-frequency partition represents no rows.
     *
     * @throws Exception when the test cache cannot be created
     */
    @Test
    void treatsMissingPartitionAsEmptyJsonArray()
            throws Exception {

        final ObjectMapper objectMapper =
                new ObjectMapper();

        final SessionCacheResult sessionCache =
                createSessionCache(objectMapper);

        final RawDownloadClient downloadClient =
                (uri, target) -> {
                    final boolean driversRequest =
                            uri.getPath()
                                    .endsWith("/drivers");

                    final boolean partitionRequest =
                            uri.getPath()
                                    .endsWith("/location")
                            || uri.getPath()
                                    .endsWith("/car_data");

                    if (partitionRequest) {
                        throw new OpenF1HttpException(
                                404,
                                "No results found.");
                    }

                    final String json;

                    final long records;

                    if (driversRequest) {
                        json =
                                """
                                [
                                  {"driver_number":1}
                                ]
                                """;

                        records = 1;
                    } else {
                        json = "[{}]";
                        records = 1;
                    }

                    Files.createDirectories(
                            target.getParent());

                    Files.writeString(
                            target,
                            json,
                            StandardCharsets.UTF_8);

                    return new RawDatasetFileInfo(
                            Files.size(target),
                            records,
                            sha256(target));
                };

        final RawCacheService service =
                new RawCacheService(
                        downloadClient,
                        objectMapper,
                        Clock.fixed(
                                Instant.parse(
                                        "2026-07-19T05:30:00Z"),
                                ZoneOffset.UTC));

        final RawCacheDownloadResult result =
                service.downloadAll(
                        sessionCache,
                        message -> { });

        assertEquals(
                OpenF1Dataset.values().length,
                result.downloadedDatasets());

        final Path locationFile =
                sessionCache.cacheDirectory()
                        .resolve("raw")
                        .resolve("location.json");

        final Path carDataFile =
                sessionCache.cacheDirectory()
                        .resolve("raw")
                        .resolve("car-data.json");

        assertEquals(
                0,
                objectMapper
                        .readTree(locationFile.toFile())
                        .size());

        assertEquals(
                0,
                objectMapper
                        .readTree(carDataFile.toFile())
                        .size());

        final JsonNode manifest =
                objectMapper.readTree(
                        sessionCache
                                .manifestFile()
                                .toFile());

        assertEquals(
                "raw_complete",
                manifest.path("cache_state").asText());

        assertEquals(
                0,
                manifest.path("files")
                        .path("location")
                        .path("records")
                        .asLong());

        assertEquals(
                0,
                manifest.path("files")
                        .path("car_data")
                        .path("records")
                        .asLong());
    }

    /**
     * Stores a no-results response for optional metadata as an empty array.
     *
     * @throws Exception when the test cache cannot be created
     */
    @Test
    void treatsMissingOptionalDatasetAsEmptyJsonArray()
            throws Exception {

        final ObjectMapper objectMapper =
                new ObjectMapper();

        final SessionCacheResult sessionCache =
                createSessionCache(objectMapper);

        final RawDownloadClient downloadClient =
                (uri, target) -> {
                    if (uri.getPath()
                            .endsWith("/intervals")) {

                        throw new OpenF1HttpException(
                                404,
                                "No results found.");
                    }

                    if (uri.getPath()
                            .endsWith("/drivers")) {

                        return writeDataset(
                                target,
                                "[{\"driver_number\":1}]",
                                1);
                    }

                    return writeDataset(
                            target,
                            "[{}]",
                            1);
                };

        final RawCacheDownloadResult result =
                new RawCacheService(
                        downloadClient,
                        objectMapper,
                        Clock.systemUTC())
                        .downloadAll(
                                sessionCache,
                                message -> { });

        final Path intervalsFile =
                sessionCache.cacheDirectory()
                        .resolve("raw")
                        .resolve("intervals.json");

        assertEquals(
                OpenF1Dataset.values().length,
                result.downloadedDatasets());

        assertEquals(
                "[]",
                Files.readString(
                        intervalsFile,
                        StandardCharsets.UTF_8));

        final JsonNode manifest =
                objectMapper.readTree(
                        sessionCache.manifestFile()
                                .toFile());

        assertEquals(
                0,
                manifest.path("files")
                        .path("intervals")
                        .path("records")
                        .asLong());

        assertEquals(
                sha256(intervalsFile),
                manifest.path("files")
                        .path("intervals")
                        .path("sha256")
                        .asText());
    }

    /**
     * Keeps a no-results response fatal for essential replay data.
     *
     * @throws Exception when the test cache cannot be created
     */
    @Test
    void rejectsMissingEssentialDataset()
            throws Exception {

        final ObjectMapper objectMapper =
                new ObjectMapper();

        final SessionCacheResult sessionCache =
                createSessionCache(objectMapper);

        final RawDownloadClient downloadClient =
                (uri, target) -> {
                    if (uri.getPath()
                            .endsWith("/laps")) {

                        throw new OpenF1HttpException(
                                404,
                                "No results found.");
                    }

                    return writeDataset(
                            target,
                            "[{\"driver_number\":1}]",
                            1);
                };

        final RawCacheService service =
                new RawCacheService(
                        downloadClient,
                        objectMapper,
                        Clock.systemUTC());

        final OpenF1HttpException failure =
                assertThrows(
                        OpenF1HttpException.class,
                        () -> service.downloadAll(
                                sessionCache,
                                message -> { }));

        assertEquals(
                404,
                failure.statusCode());

        assertFalse(
                Files.exists(
                        sessionCache.cacheDirectory()
                                .resolve("raw")
                                .resolve("laps.json")));
    }

    private SessionCacheResult createSessionCache(
            final ObjectMapper objectMapper)
            throws Exception {

        final Path cacheDirectory =
                temporaryDirectory.resolve(
                        "2024-testland-race-9574");

        final Path rawDirectory =
                cacheDirectory.resolve("raw");

        Files.createDirectories(rawDirectory);

        final Path sessionFile =
                rawDirectory.resolve("session.json");

        Files.writeString(
                sessionFile,
                "[]",
                StandardCharsets.UTF_8);

        final Path manifestFile =
                cacheDirectory.resolve("manifest.json");

        final ObjectNode manifest =
                objectMapper.createObjectNode();

        manifest.put(
                "schema_version",
                1);

        manifest.put(
                "cache_state",
                "session_metadata");

        final ObjectNode filesNode =
                manifest.putObject("files");

        final ObjectNode sessionNode =
                filesNode.putObject("session");

        sessionNode.put(
                "path",
                "raw/session.json");

        sessionNode.put(
                "bytes",
                Files.size(sessionFile));

        sessionNode.put(
                "sha256",
                sha256(sessionFile));

        objectMapper
                .writerWithDefaultPrettyPrinter()
                .writeValue(
                        manifestFile.toFile(),
                        manifest);

        final OpenF1Session session =
                new OpenF1Session(
                        9574,
                        1242,
                        2024,
                        "Testland",
                        "Race",
                        "Race",
                        "Example Circuit",
                        "Example Circuit",
                        Instant.parse(
                                "2024-07-28T13:00:00Z"),
                        Instant.parse(
                                "2024-07-28T15:00:00Z"),
                        false);

        return new SessionCacheResult(
                cacheDirectory,
                sessionFile,
                manifestFile,
                session);
    }

    private static String sha256(final Path file)
            throws IOException {

        try {
            final MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            return HexFormat.of()
                    .formatHex(
                            digest.digest(
                                    Files.readAllBytes(file)));
        } catch (final NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable.",
                    exception);
        }
    }

    private static RawDatasetFileInfo writeDataset(
            final Path target,
            final String json,
            final long records)
            throws IOException {

        Files.createDirectories(
                target.getParent());

        Files.writeString(
                target,
                json,
                StandardCharsets.UTF_8);

        return new RawDatasetFileInfo(
                Files.size(target),
                records,
                sha256(target));
    }
}
