package io.github.iamseverind.racereplay.processed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests sequential reading of complete timeline frames.
 */
final class ReplayTimelineFrameReaderTest {

    @TempDir
    private Path temporaryDirectory;

    /**
     * Reads elapsed time and every driver using one frame operation.
     *
     * @throws Exception when binary timeline access fails
     */
    @Test
    void readsCompleteFrameInHeaderDriverOrder()
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
                        List.of(1, 44));

        final ReplayDriverState driverOneFrameZero =
                state(
                        100,
                        1,
                        11,
                        0);

        final ReplayDriverState driverFortyFourFrameZero =
                state(
                        200,
                        2,
                        3,
                        0);

        final ReplayDriverState driverOneFrameOne =
                state(
                        300,
                        3,
                        10,
                        1);

        final ReplayDriverState driverFortyFourFrameOne =
                state(
                        400,
                        4,
                        2,
                        1);

        try (ReplayTimelineWriter writer =
                new ReplayTimelineWriter(
                        timelineFile,
                        header)) {

            writer.writeState(
                    0,
                    0,
                    driverOneFrameZero);

            writer.writeState(
                    0,
                    1,
                    driverFortyFourFrameZero);

            writer.writeState(
                    1,
                    0,
                    driverOneFrameOne);

            writer.writeState(
                    1,
                    1,
                    driverFortyFourFrameOne);

            writer.complete();
        }

        try (ReplayTimelineReader reader =
                new ReplayTimelineReader(
                        timelineFile)) {

            final ReplayTimelineFrame frame =
                    reader.readFrame(1);

            assertEquals(
                    250,
                    frame.elapsedMillis());

            assertEquals(
                    List.of(
                            driverOneFrameOne,
                            driverFortyFourFrameOne),
                    frame.drivers());

            assertEquals(
                    driverFortyFourFrameOne,
                    reader.readState(
                            1,
                            1));

            assertThrows(
                    IndexOutOfBoundsException.class,
                    () -> reader.readFrame(2));
        }
    }

    private static ReplayDriverState state(
            final int x,
            final int gear,
            final int position,
            final int lapNumber) {

        return new ReplayDriverState(
                x,
                x + 10,
                x + 20,
                200 + x / 10,
                10_000 + x,
                gear,
                80,
                0,
                12,
                position,
                ReplayTimelineFormat
                        .FLAG_LOCATION_VALID
                        | ReplayTimelineFormat
                                .FLAG_TELEMETRY_VALID
                        | ReplayTimelineFormat
                                .FLAG_POSITION_VALID
                        | ReplayTimelineFormat
                                .FLAG_ACTIVE,
                lapNumber,
                Float.NaN,
                Float.NaN,
                2);
    }
}
