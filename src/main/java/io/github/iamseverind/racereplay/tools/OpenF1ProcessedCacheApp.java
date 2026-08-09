package io.github.iamseverind.racereplay.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.iamseverind.racereplay.cache.ApplicationCacheDirectories;
import io.github.iamseverind.racereplay.openf1.SessionQuery;
import io.github.iamseverind.racereplay.processed.ProcessedReplayBuildResult;
import io.github.iamseverind.racereplay.processed.ProcessedReplayCacheBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds processed replay metadata from an offline raw cache.
 */
public final class OpenF1ProcessedCacheApp {

    private OpenF1ProcessedCacheApp() {
    }

    /**
     * Builds processed metadata for one cached session.
     *
     * @param args optional year, country and session name
     * @throws Exception when the cache cannot be located or processed
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
                "=== OPENF1 PROCESSED REPLAY METADATA ===");

        System.out.println(
                "Query: "
                + query.year()
                + " | "
                + query.countryName()
                + " | "
                + query.sessionName());

        System.out.println(
                "Raw cache:");

        System.out.println(
                cacheDirectory);

        final ProcessedReplayCacheBuilder builder =
                new ProcessedReplayCacheBuilder(
                        objectMapper,
                        Clock.systemUTC());

        final ProcessedReplayBuildResult result =
                builder.build(
                        cacheDirectory);

        System.out.println();
        System.out.println(
                "=== PROCESSED METADATA COMPLETE ===");

        System.out.println(
                "Replay start: "
                + result.replayStart());

        System.out.println(
                "Replay end: "
                + result.replayEnd());

        System.out.println(
                "Frame interval: "
                + result.frameIntervalMillis()
                + " ms");

        System.out.println(
                "Planned frames: "
                + result.frameCount());

        System.out.println(
                "Drivers: "
                + result.driverCount());

        System.out.println(
                "Processed directory:");

        System.out.println(
                result.processedDirectory());

        System.out.println(
                "Manifest:");

        System.out.println(
                result.manifestFile());

        System.out.println(
                "Drivers:");

        System.out.println(
                result.driversFile());
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

                final Path manifestFile =
                        directory.resolve(
                                "manifest.json");

                if (!Files.isRegularFile(manifestFile)) {
                    continue;
                }

                final JsonNode manifest =
                        objectMapper.readTree(
                                manifestFile.toFile());

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

        if (matches.isEmpty()) {
            throw new IOException(
                    "No complete raw cache found for "
                    + query.year()
                    + " "
                    + query.countryName()
                    + " "
                    + query.sessionName()
                    + ".");
        }

        if (matches.size() > 1) {
            throw new IOException(
                    "Multiple complete raw caches found: "
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
