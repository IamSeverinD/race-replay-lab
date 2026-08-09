package io.github.iamseverind.racereplay.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.iamseverind.racereplay.processed.ReplayDriverState;
import io.github.iamseverind.racereplay.processed.ReplayTimelineEnricher;
import io.github.iamseverind.racereplay.processed.ReplayTimelineEnrichmentResult;
import io.github.iamseverind.racereplay.processed.ReplayTimelineReader;
import java.nio.file.Path;
import java.time.Clock;

/**
 * Enriches one processed OpenF1 replay timeline.
 */
public final class OpenF1TimelineEnrichmentApp {

    private OpenF1TimelineEnrichmentApp() {
    }

    /**
     * Enriches a cache directory supplied as the sole argument.
     *
     * @param args session cache directory
     * @throws Exception when enrichment fails
     */
    public static void main(
            final String[] args)
            throws Exception {

        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "Usage: <session-cache-directory>");
        }

        final Path cacheDirectory =
                Path.of(args[0])
                        .toAbsolutePath()
                        .normalize();

        System.out.println(
                "=== OPENF1 TIMELINE RACE METADATA ===");

        System.out.println(
                "Cache:");

        System.out.println(
                cacheDirectory);

        System.out.println();

        final ReplayTimelineEnricher enricher =
                new ReplayTimelineEnricher(
                        new ObjectMapper(),
                        Clock.systemUTC());

        final ReplayTimelineEnrichmentResult result =
                enricher.enrich(
                        cacheDirectory,
                        System.out::println);

        System.out.println();
        System.out.println(
                "=== ENRICHMENT COMPLETE ===");

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
                "Total states: "
                + result.totalStates());

        System.out.println(
                "Position-valid states: "
                + result.positionValidStates());

        System.out.println(
                "Lap-valid states: "
                + result.lapValidStates());

        System.out.println(
                "Gap-valid states: "
                + result.gapValidStates());

        System.out.println(
                "Interval-valid states: "
                + result.intervalValidStates());

        System.out.println(
                "Tyre-valid states: "
                + result.tyreValidStates());

        try (ReplayTimelineReader reader =
                new ReplayTimelineReader(
                        result.timelineFile())) {

            final int middle =
                    reader.header()
                            .frameCount() / 2;

            final int last =
                    reader.header()
                            .frameCount() - 1;

            final ReplayDriverState firstState =
                    reader.readState(
                            0,
                            0);

            final ReplayDriverState middleState =
                    reader.readState(
                            middle,
                            0);

            final ReplayDriverState finalState =
                    reader.readState(
                            last,
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
    }
}
