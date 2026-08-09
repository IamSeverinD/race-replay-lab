package io.github.iamseverind.racereplay.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.iamseverind.racereplay.cache.ApplicationCacheDirectories;
import io.github.iamseverind.racereplay.openf1.SessionQuery;
import io.github.iamseverind.racereplay.processed.ReplayDriverState;
import io.github.iamseverind.racereplay.processed.ReplayTimelineBuildResult;
import io.github.iamseverind.racereplay.processed.ReplayTimelineBuilder;
import io.github.iamseverind.racereplay.processed.ReplayTimelineReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the binary timeline for an offline OpenF1 cache.
 */
public final class OpenF1TimelineCacheApp {

    private OpenF1TimelineCacheApp() {
    }

    /**
     * Builds or reuses a binary timeline.
     *
     * @param args optional year, country and session name
     * @throws Exception when cache processing fails
     */
    public static void main(final String[] args)
            throws Exception {

        final SessionQuery query =
                parseQuery(args);

        final ObjectMapper objectMapper =
                new ObjectMapper();

        final Path cacheDirectory =
                findCacheDirectory(
                        ApplicationCacheDirectories
                                .openF1CacheRoot(),
                        query,
                        objectMapper);

        System.out.println(
                "=== OPENF1 BINARY REPLAY TIMELINE ===");

        System.out.println(
                "Cache:");

        System.out.println(
                cacheDirectory);

        System.out.println();

        final ReplayTimelineBuilder builder =
                new ReplayTimelineBuilder(
                        objectMapper,
                        Clock.systemUTC());

        final ReplayTimelineBuildResult result =
                builder.build(
                        cacheDirectory,
                        System.out::println);

        System.out.println();
        System.out.println(
                "=== TIMELINE COMPLETE ===");

        System.out.println(
                "Reused: "
                + result.reused());

        System.out.println(
                "Bytes: "
                + result.bytes());

        System.out.println(
                "SHA-256: "
                + result.sha256());

        System.out.println(
                "Frames: "
                + result.frameCount());

        System.out.println(
                "Drivers: "
                + result.driverCount());

        System.out.println(
                "Total states: "
                + result.totalStates());

        System.out.println(
                "Location-valid states: "
                + result.locationValidStates());

        System.out.println(
                "Telemetry-valid states: "
                + result.telemetryValidStates());

        System.out.println(
                "Fully-valid states: "
                + result.fullyValidStates());

        System.out.println(
                "Rejected raw telemetry records: "
                + result.sourceInvalidTelemetryRecords());

        try (ReplayTimelineReader reader =
                new ReplayTimelineReader(
                        result.timelineFile())) {

            final int middleFrame =
                    reader.header().frameCount() / 2;

            final ReplayDriverState firstState =
                    reader.readState(
                            0,
                            0);

            final ReplayDriverState middleState =
                    reader.readState(
                            middleFrame,
                            0);

            final ReplayDriverState finalState =
                    reader.readState(
                            reader.header()
                                    .frameCount() - 1,
                            0);

            System.out.println();
            System.out.println(
                    "First driver: #"
                    + reader.header()
                            .driverNumbers()
                            .getFirst());

            System.out.println(
                    "First state: "
                    + firstState);

            System.out.println(
                    "Middle state: "
                    + middleState);

            System.out.println(
                    "Final state: "
                    + finalState);
        }

        System.out.println();
        System.out.println(
                "Timeline:");

        System.out.println(
                result.timelineFile());
    }

    private static Path findCacheDirectory(
            final Path cacheRoot,
            final SessionQuery query,
            final ObjectMapper objectMapper)
            throws IOException {

        if (!Files.isDirectory(cacheRoot)) {
            throw new IOException(
                    "OpenF1 cache root does not exist: "
                    + cacheRoot);
        }

        final List<Path> matches =
                new ArrayList<>();

        try (var entries =
                Files.list(cacheRoot)) {

            for (final Path directory :
                    entries.filter(
                            Files::isDirectory)
                            .toList()) {

                final Path rawManifest =
                        directory.resolve(
                                "manifest.json");

                final Path processedManifest =
                        directory.resolve(
                                "processed")
                                .resolve(
                                        "replay-manifest.json");

                if (!Files.isRegularFile(rawManifest)
                        || !Files.isRegularFile(
                                processedManifest)) {

                    continue;
                }

                final JsonNode manifest =
                        objectMapper.readTree(
                                rawManifest.toFile());

                final JsonNode manifestQuery =
                        manifest.path("query");

                if (manifestQuery.path("year")
                                .asInt(-1)
                        == query.year()
                        && manifestQuery
                                .path("country_name")
                                .asText()
                                .equalsIgnoreCase(
                                        query.countryName())
                        && manifestQuery
                                .path("session_name")
                                .asText()
                                .equalsIgnoreCase(
                                        query.sessionName())
                        && "raw_complete".equals(
                                manifest.path("cache_state")
                                        .asText())) {

                    matches.add(directory);
                }
            }
        }

        if (matches.size() != 1) {
            throw new IOException(
                    "Expected exactly one processed cache, found "
                    + matches.size()
                    + ": "
                    + matches);
        }

        return matches.getFirst();
    }

    private static SessionQuery parseQuery(
            final String[] args) {

        if (args.length != 3) {
            throw new IllegalArgumentException(
                    "Usage: <year> <country> <session-name>");
        }

        return new SessionQuery(
                Integer.parseInt(args[0]),
                args[1],
                args[2]);
    }
}
