package io.github.iamseverind.racereplay.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.iamseverind.racereplay.cache.ApplicationCacheDirectories;
import io.github.iamseverind.racereplay.cache.ActiveReplaySelection;
import io.github.iamseverind.racereplay.core.ReplaySimulation;
import io.github.iamseverind.racereplay.core.SyntheticReplaySimulation;
import io.github.iamseverind.racereplay.processed.ProcessedReplaySimulation;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Selects the replay data source used by the JavaFX application.
 */
final class ReplaySimulationLoader {

    static final String PROCESSED_SUBTITLE =
            "USER-PROVIDED PROCESSED DATA";

    static final String SYNTHETIC_SUBTITLE =
            "GENERIC CIRCUIT · SYNTHETIC DEMO DATA";

    static final double PROCESSED_INITIAL_REPLAY_SECONDS =
            0.0;

    static final double SYNTHETIC_INITIAL_REPLAY_SECONDS =
            0.0;

    static final int SYNTHETIC_RACE_LAPS = 50;

    private ReplaySimulationLoader() {
    }

    /**
     * Loads the application's default replay source.
     *
     * @return selected replay source
     * @throws IOException when an existing processed cache is incomplete
     */
    static LoadedReplaySimulation loadDefault()
            throws IOException {

        final Path sessionCacheDirectory =
                ActiveReplaySelection.resolve(
                        ApplicationCacheDirectories
                                .openF1CacheRoot());

        return load(
                sessionCacheDirectory);
    }

    /**
     * Loads processed data or uses a synthetic fallback when no cache exists.
     *
     * <p>A partially existing cache is treated as an error. This prevents
     * corrupt processed data from being hidden by a silent fallback.</p>
     *
     * @param sessionCacheDirectory session cache directory
     * @return selected replay source
     * @throws IOException when an existing processed cache is incomplete
     */
    static LoadedReplaySimulation load(
            final Path sessionCacheDirectory)
            throws IOException {

        Objects.requireNonNull(
                sessionCacheDirectory,
                "sessionCacheDirectory");

        final Path processedDirectory =
                sessionCacheDirectory.resolve(
                        "processed");

        final Path timelineFile =
                processedDirectory.resolve(
                        "timeline.bin");

        final Path driversFile =
                processedDirectory.resolve(
                        "drivers.json");

        final boolean timelineExists =
                Files.exists(
                        timelineFile);

        final boolean driversExist =
                Files.exists(
                        driversFile);

        if (!timelineExists && !driversExist) {
            return new LoadedReplaySimulation(
                    new SyntheticReplaySimulation(),
                    SYNTHETIC_SUBTITLE,
                    sessionCacheDirectory,
                    SYNTHETIC_INITIAL_REPLAY_SECONDS,
                    0.0,
                    SYNTHETIC_RACE_LAPS,
                    true,
                    ReplayTrackGeometry.synthetic(),
                    Map.of(),
                    false);
        }

        if (!Files.isRegularFile(timelineFile)
                || !Files.isRegularFile(driversFile)) {

            throw new IOException(
                    "Processed replay cache is incomplete: "
                    + sessionCacheDirectory);
        }

        final ProcessedDisplayMetadata metadata =
                readProcessedMetadata(
                        processedDirectory.resolve(
                                "replay-manifest.json"));

        return new LoadedReplaySimulation(
                new ProcessedReplaySimulation(
                        sessionCacheDirectory),
                metadata.subtitle(),
                sessionCacheDirectory,
                PROCESSED_INITIAL_REPLAY_SECONDS,
                metadata.raceStartSeconds(),
                metadata.scheduledLaps(),
                metadata.drsAvailable(),
                ReplayTrackGeometry.load(
                        timelineFile),
                readTeamColors(
                        driversFile),
                true);
    }

    private static Map<String, String> readTeamColors(
            final Path driversFile)
            throws IOException {

        final JsonNode drivers =
                new ObjectMapper()
                        .readTree(
                                driversFile.toFile());

        if (!drivers.isArray()) {
            return Map.of();
        }

        final Map<String, String> colors =
                new LinkedHashMap<>();

        for (final JsonNode driver : drivers) {
            final String team =
                    driver.path("team_name")
                            .asText("")
                            .strip();

            final String color =
                    driver.path("team_colour")
                            .asText("")
                            .strip();

            if (!team.isBlank()
                    && color.matches("(?i)[0-9a-f]{6}")) {

                colors.putIfAbsent(
                        team,
                        "#" + color);
            }
        }

        return Map.copyOf(colors);
    }

    private static ProcessedDisplayMetadata readProcessedMetadata(
            final Path manifestFile)
            throws IOException {

        if (!Files.isRegularFile(manifestFile)) {
            return ProcessedDisplayMetadata.fallback();
        }

        final JsonNode manifest =
                new ObjectMapper()
                        .readTree(
                                manifestFile.toFile());

        final JsonNode session =
                manifest.path("session");

        final JsonNode replay =
                manifest.path("replay");

        final JsonNode timeline =
                manifest.path("timeline");

        final int year =
                session.path("year")
                        .asInt(-1);

        final String subtitle =
                createProcessedSubtitle(
                        year,
                        session.path("country_name")
                                .asText(""),
                        session.path("session_name")
                                .asText(""),
                        session.path("circuit_short_name")
                                .asText(""));

        final double raceStartSeconds =
                calculateRaceStartSeconds(
                        replay.path("start")
                                .asText(""),
                        timeline.path("race_start")
                                .asText(""));

        final int scheduledLaps =
                Math.max(
                        0,
                        replay.path("scheduled_laps")
                                .asInt(0));

        return new ProcessedDisplayMetadata(
                subtitle,
                raceStartSeconds,
                scheduledLaps,
                year < 2026);
    }

    private static String createProcessedSubtitle(
            final int year,
            final String country,
            final String session,
            final String circuit) {

        if (year < 0
                || country.isBlank()
                || session.isBlank()
                || circuit.isBlank()) {

            return PROCESSED_SUBTITLE;
        }

        return String.join(
                " · ",
                Integer.toString(year),
                country,
                session,
                circuit,
                "OpenF1")
                .toUpperCase(
                        Locale.ROOT);
    }

    private static double calculateRaceStartSeconds(
            final String replayStartText,
            final String raceStartText)
            throws IOException {

        if (replayStartText.isBlank()
                || raceStartText.isBlank()) {

            return 0.0;
        }

        try {
            final Instant replayStart =
                    Instant.parse(
                            replayStartText);

            final Instant raceStart =
                    Instant.parse(
                            raceStartText);

            return Math.max(
                    0.0,
                    Duration.between(
                            replayStart,
                            raceStart)
                            .toMillis()
                            / 1_000.0);
        } catch (final RuntimeException exception) {
            throw new IOException(
                    "Processed replay contains invalid timestamps.",
                    exception);
        }
    }

    /**
     * Loaded simulation and the information shown by the application.
     *
     * @param simulation selected replay simulation
     * @param subtitle user-interface data-source label
     * @param cacheDirectory selected session cache directory
     * @param initialReplaySeconds initial visible replay time
     * @param raceStartSeconds race start within the replay
     * @param scheduledLaps scheduled race laps or zero
     * @param drsAvailable whether DRS applies to this session
     * @param trackGeometry selected circuit outline and projection
     * @param teamColors OpenF1 team name to display-color mapping
     * @param processed whether processed OpenF1 data is active
     */
    record LoadedReplaySimulation(
            ReplaySimulation simulation,
            String subtitle,
            Path cacheDirectory,
            double initialReplaySeconds,
            double raceStartSeconds,
            int scheduledLaps,
            boolean drsAvailable,
            ReplayTrackGeometry trackGeometry,
            Map<String, String> teamColors,
            boolean processed)
            implements AutoCloseable {

        LoadedReplaySimulation {
            simulation =
                    Objects.requireNonNull(
                            simulation,
                            "simulation");

            subtitle =
                    Objects.requireNonNull(
                            subtitle,
                            "subtitle");

            cacheDirectory =
                    Objects.requireNonNull(
                            cacheDirectory,
                            "cacheDirectory");

            trackGeometry =
                    Objects.requireNonNull(
                            trackGeometry,
                            "trackGeometry");

            teamColors =
                    Map.copyOf(
                            Objects.requireNonNull(
                                    teamColors,
                                    "teamColors"));

            if (!Double.isFinite(initialReplaySeconds)
                    || initialReplaySeconds < 0.0) {

                throw new IllegalArgumentException(
                        "Initial replay time must be "
                        + "non-negative and finite.");
            }

            if (!Double.isFinite(raceStartSeconds)
                    || raceStartSeconds < 0.0) {

                throw new IllegalArgumentException(
                        "Race start must be non-negative and finite.");
            }

            if (scheduledLaps < 0) {
                throw new IllegalArgumentException(
                        "Scheduled laps must not be negative.");
            }
        }

        /**
         * Closes the selected simulation when it owns resources.
         *
         * @throws IOException when closing processed replay data fails
         */
        @Override
        public void close()
                throws IOException {

            if (simulation
                    instanceof ProcessedReplaySimulation processed) {

                processed.close();
            }
        }
    }

    private record ProcessedDisplayMetadata(
            String subtitle,
            double raceStartSeconds,
            int scheduledLaps,
            boolean drsAvailable) {

        private static ProcessedDisplayMetadata fallback() {
            return new ProcessedDisplayMetadata(
                    PROCESSED_SUBTITLE,
                    0.0,
                    0,
                    true);
        }
    }
}
