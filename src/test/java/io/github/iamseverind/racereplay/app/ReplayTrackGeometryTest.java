package io.github.iamseverind.racereplay.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.iamseverind.racereplay.processed.ReplayDriverState;
import io.github.iamseverind.racereplay.processed.ReplayTimelineFormat;
import io.github.iamseverind.racereplay.processed.ReplayTimelineHeader;
import io.github.iamseverind.racereplay.processed.ReplayTimelineWriter;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests circuit geometry derived from processed location samples.
 */
final class ReplayTrackGeometryTest {

    private static final int LAP_POINT_COUNT = 240;

    @TempDir
    private Path temporaryDirectory;

    /**
     * Uses the generic circuit when too few points are available.
     */
    @Test
    void insufficientPointsUseSyntheticGeometry() {
        final ReplayTrackGeometry geometry =
                ReplayTrackGeometry.fromRawPoints(
                        List.of(
                                new ReplayTrackGeometry.Point(
                                        0.0,
                                        0.0)));

        assertFalse(
                geometry.locationBased());
    }

    /**
     * Extracts and normalizes one confirmed completed lap.
     *
     * @throws Exception when the timeline fixture cannot be written
     */
    @Test
    void completedLapCreatesLocationBasedGeometry()
            throws Exception {

        final Path timelineFile =
                temporaryDirectory.resolve(
                        "timeline.bin");

        final int frameCount =
                LAP_POINT_COUNT + 2;

        final ReplayTimelineHeader header =
                new ReplayTimelineHeader(
                        ReplayTimelineFormat.VERSION,
                        Instant.parse(
                                "2026-07-19T13:00:00Z"),
                        250,
                        frameCount,
                        List.of(1));

        try (ReplayTimelineWriter writer =
                new ReplayTimelineWriter(
                        timelineFile,
                        header)) {

            writer.writeState(
                    0,
                    0,
                    state(
                            1,
                            1_000,
                            0));

            for (int index = 0;
                    index < LAP_POINT_COUNT;
                    index++) {

                final double angle =
                        Math.PI * 2.0
                        * index
                        / LAP_POINT_COUNT;

                writer.writeState(
                        index + 1,
                        0,
                        state(
                                2,
                                (int) Math.round(
                                        Math.cos(angle)
                                        * 1_000.0),
                                (int) Math.round(
                                        Math.sin(angle)
                                        * 500.0)));
            }

            writer.writeState(
                    frameCount - 1,
                    0,
                    state(
                            3,
                            1_000,
                            0));

            writer.complete();
        }

        final ReplayTrackGeometry geometry =
                ReplayTrackGeometry.load(
                        timelineFile);

        assertTrue(
                geometry.locationBased());

        assertEquals(
                LAP_POINT_COUNT,
                geometry.points().size());

        final ReplayTrackGeometry.Point projected =
                geometry.project(
                        1_000,
                        0);

        assertEquals(
                0.92,
                projected.x(),
                0.000_001);

        assertEquals(
                0.5,
                projected.y(),
                0.000_001);
    }

    private static ReplayDriverState state(
            final int lap,
            final int x,
            final int y) {

        return new ReplayDriverState(
                x,
                y,
                0,
                200,
                10_000,
                6,
                100,
                0,
                0,
                1,
                ReplayTimelineFormat.FLAG_LOCATION_VALID
                | ReplayTimelineFormat.FLAG_LAP_VALID
                | ReplayTimelineFormat.FLAG_ACTIVE,
                lap,
                Float.NaN,
                Float.NaN,
                0);
    }
}
