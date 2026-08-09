package io.github.iamseverind.racereplay.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.iamseverind.racereplay.openf1.OpenF1Session;
import io.github.iamseverind.racereplay.openf1.SessionDiscoveryClient;
import io.github.iamseverind.racereplay.openf1.SessionDiscoveryResult;
import io.github.iamseverind.racereplay.openf1.SessionQuery;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests safe session cache creation.
 */
final class SessionCacheServiceTest {

    @TempDir
    private Path temporaryDirectory;

    /**
     * The service writes raw data and a versioned manifest.
     *
     * @throws Exception when the test cache cannot be written
     */
    @Test
    void writesRawSessionAndManifest() throws Exception {
        final String rawJson = "[{\"session_key\":9574}]";

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

        final SessionDiscoveryClient client =
                query -> new SessionDiscoveryResult(
                        session,
                        URI.create(
                                "https://api.openf1.org/v1/sessions"
                                + "?year=2024"),
                        rawJson);

        final ObjectMapper objectMapper =
                new ObjectMapper();

        final SessionCacheService service =
                new SessionCacheService(
                        temporaryDirectory,
                        client,
                        objectMapper,
                        Clock.fixed(
                                Instant.parse(
                                        "2026-07-19T04:00:00Z"),
                                ZoneOffset.UTC));

        final SessionCacheResult result =
                service.downloadSessionMetadata(
                        new SessionQuery(
                                2024,
                                "Testland",
                                "Race"));

        assertTrue(
                Files.isRegularFile(
                        result.rawSessionFile()));

        assertTrue(
                Files.isRegularFile(
                        result.manifestFile()));

        assertEquals(
                rawJson,
                Files.readString(
                        result.rawSessionFile()));

        final JsonNode manifest =
                objectMapper.readTree(
                        result.manifestFile().toFile());

        assertEquals(
                1,
                manifest.path("schema_version").asInt());

        assertEquals(
                9574,
                manifest.path("session")
                        .path("session_key")
                        .asInt());

        assertEquals(
                "2026-07-19T04:00:00Z",
                manifest.path("fetched_at").asText());

        final ObjectNode partialManifest =
                (ObjectNode) manifest.deepCopy();

        partialManifest.put(
                "cache_state",
                "raw_partial");

        final ObjectNode carDataMetadata =
                ((ObjectNode) partialManifest.path("files"))
                        .putObject("car_data");

        carDataMetadata.put(
                "path",
                "raw/car-data.json");

        carDataMetadata.put(
                "bytes",
                1234);

        carDataMetadata.put(
                "records",
                42);

        carDataMetadata.put(
                "sha256",
                "0123456789abcdef"
                + "0123456789abcdef"
                + "0123456789abcdef"
                + "0123456789abcdef");

        objectMapper
                .writerWithDefaultPrettyPrinter()
                .writeValue(
                        result.manifestFile().toFile(),
                        partialManifest);

        final SessionCacheResult refreshedResult =
                service.downloadSessionMetadata(
                        new SessionQuery(
                                2024,
                                "Testland",
                                "Race"));

        final JsonNode refreshedManifest =
                objectMapper.readTree(
                        refreshedResult
                                .manifestFile()
                                .toFile());

        assertTrue(
                refreshedManifest
                        .path("files")
                        .has("car_data"));

        assertEquals(
                "raw/car-data.json",
                refreshedManifest
                        .path("files")
                        .path("car_data")
                        .path("path")
                        .asText());

        assertFalse(
                Files.list(result.cacheDirectory())
                        .anyMatch(path ->
                                path.getFileName()
                                        .toString()
                                        .contains(".part-")));
    }

    /**
     * Rejects sessions without a common race timeline before writing data.
     *
     * @throws Exception when the temporary directory cannot be inspected
     */
    @Test
    void rejectsNonRaceSessionBeforeCreatingCache()
            throws Exception {

        final OpenF1Session qualifying =
                new OpenF1Session(
                        11330,
                        1290,
                        2026,
                        "Belgium",
                        "Qualifying",
                        "Qualifying",
                        "Spa-Francorchamps",
                        "Spa-Francorchamps",
                        Instant.parse(
                                "2026-07-18T14:00:00Z"),
                        Instant.parse(
                                "2026-07-18T15:00:00Z"),
                        false);

        final SessionDiscoveryClient client =
                query -> new SessionDiscoveryResult(
                        qualifying,
                        URI.create(
                                "https://api.openf1.org/v1/sessions"
                                + "?session_key=11330"),
                        "[{\"session_key\":11330}]");

        final SessionCacheService service =
                new SessionCacheService(
                        temporaryDirectory,
                        client,
                        new ObjectMapper(),
                        Clock.systemUTC());

        final IOException failure =
                assertThrows(
                        IOException.class,
                        () -> service.downloadSessionMetadata(
                                new SessionQuery(
                                        2026,
                                        "Belgium",
                                        "Qualifying")));

        assertTrue(
                failure.getMessage()
                        .contains("race and sprint"));

        try (var children =
                Files.list(temporaryDirectory)) {

            assertTrue(children.findAny().isEmpty());
        }
    }
}
