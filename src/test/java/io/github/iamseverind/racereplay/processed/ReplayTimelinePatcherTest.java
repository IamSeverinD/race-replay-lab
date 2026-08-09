package io.github.iamseverind.racereplay.processed;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests atomic timeline metadata patching.
 */
final class ReplayTimelinePatcherTest {

    @TempDir
    private Path temporaryDirectory;

    /**
     * Patches metadata while preserving location and telemetry.
     *
     * @throws Exception when timeline access fails
     */
    @Test
    void patchesMetadataWithoutChangingBaseState()
            throws Exception {

        final Path timelineFile =
                createTimeline();

        final ReplayTimelineMetadata metadata =
                new ReplayTimelineMetadata(
                        1,
                        true,
                        12,
                        true,
                        0.0F,
                        true,
                        Float.NaN,
                        false,
                        3,
                        true);

        try (ReplayTimelinePatcher patcher =
                new ReplayTimelinePatcher(
                        timelineFile)) {

            patcher.patchMetadata(
                    1,
                    0,
                    metadata);

            patcher.complete();
        }

        try (ReplayTimelineReader reader =
                new ReplayTimelineReader(
                        timelineFile)) {

            final ReplayDriverState state =
                    reader.readState(
                            1,
                            0);

            assertEquals(100, state.x());
            assertEquals(200, state.y());
            assertEquals(300, state.z());
            assertEquals(250, state.speed());
            assertEquals(12_000, state.rpm());
            assertEquals(8, state.gear());
            assertEquals(100, state.throttle());
            assertEquals(0, state.brake());
            assertEquals(12, state.drs());

            assertEquals(1, state.position());
            assertEquals(12, state.lapNumber());
            assertEquals(0.0F, state.gapToLeaderSeconds());
            assertTrue(
                    Float.isNaN(
                            state.intervalSeconds()));

            assertEquals(3, state.tyreCompoundCode());

            assertTrue(
                    (state.flags()
                            & ReplayTimelineFormat
                                    .FLAG_LOCATION_VALID)
                            != 0);

            assertTrue(
                    (state.flags()
                            & ReplayTimelineFormat
                                    .FLAG_TELEMETRY_VALID)
                            != 0);

            assertTrue(
                    (state.flags()
                            & ReplayTimelineFormat
                                    .FLAG_ACTIVE)
                            != 0);

            assertTrue(
                    (state.flags()
                            & ReplayTimelineFormat
                                    .FLAG_POSITION_VALID)
                            != 0);

            assertTrue(
                    (state.flags()
                            & ReplayTimelineFormat
                                    .FLAG_LAP_VALID)
                            != 0);

            assertTrue(
                    (state.flags()
                            & ReplayTimelineFormat
                                    .FLAG_GAP_VALID)
                            != 0);

            assertFalse(
                    (state.flags()
                            & ReplayTimelineFormat
                                    .FLAG_INTERVAL_VALID)
                            != 0);

            assertTrue(
                    (state.flags()
                            & ReplayTimelineFormat
                                    .FLAG_TYRE_VALID)
                            != 0);
        }
    }

    /**
     * Closing without completion preserves the original file.
     *
     * @throws Exception when timeline access fails
     */
    @Test
    void abortKeepsOriginalTimeline()
            throws Exception {

        final Path timelineFile =
                createTimeline();

        final byte[] originalBytes =
                Files.readAllBytes(
                        timelineFile);

        try (ReplayTimelinePatcher patcher =
                new ReplayTimelinePatcher(
                        timelineFile)) {

            patcher.patchMetadata(
                    0,
                    0,
                    new ReplayTimelineMetadata(
                            2,
                            true,
                            5,
                            true,
                            1.5F,
                            true,
                            0.7F,
                            true,
                            2,
                            true));
        }

        assertArrayEquals(
                originalBytes,
                Files.readAllBytes(
                        timelineFile));

        try (var files =
                Files.list(
                        temporaryDirectory)) {

            assertEquals(
                    1,
                    files.count());
        }
    }

    private Path createTimeline()
            throws Exception {

        final Path timelineFile =
                temporaryDirectory.resolve(
                        "timeline.bin");

        final ReplayTimelineHeader header =
                new ReplayTimelineHeader(
                        ReplayTimelineFormat.VERSION,
                        Instant.parse(
                                "2024-07-28T13:00:00Z"),
                        250,
                        2,
                        List.of(1));

        final ReplayDriverState state =
                new ReplayDriverState(
                        100,
                        200,
                        300,
                        250,
                        12_000,
                        8,
                        100,
                        0,
                        12,
                        0,
                        ReplayTimelineFormat
                                .FLAG_LOCATION_VALID
                                | ReplayTimelineFormat
                                        .FLAG_TELEMETRY_VALID
                                | ReplayTimelineFormat
                                        .FLAG_ACTIVE,
                        0,
                        Float.NaN,
                        Float.NaN,
                        0);

        try (ReplayTimelineWriter writer =
                new ReplayTimelineWriter(
                        timelineFile,
                        header)) {

            writer.writeState(
                    0,
                    0,
                    state);

            writer.writeState(
                    1,
                    0,
                    state);

            writer.complete();
        }

        return timelineFile;
    }
}
