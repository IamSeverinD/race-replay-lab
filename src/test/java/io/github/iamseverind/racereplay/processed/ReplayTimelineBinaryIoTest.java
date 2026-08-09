package io.github.iamseverind.racereplay.processed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests timeline binary writing and reading.
 */
final class ReplayTimelineBinaryIoTest {

    @TempDir
    private Path temporaryDirectory;

    /**
     * Writes and reads deterministic random-access states.
     *
     * @throws Exception when the fixture file cannot be used
     */
    @Test
    void writesAndReadsTimeline()
            throws Exception {

        final ReplayTimelineHeader header =
                new ReplayTimelineHeader(
                        ReplayTimelineFormat.VERSION,
                        Instant.parse(
                                "2024-07-28T13:00:00.348Z"),
                        250,
                        3,
                        List.of(1, 44));

        final Path timelineFile =
                temporaryDirectory.resolve(
                        "timeline.bin");

        final ReplayDriverState firstState =
                new ReplayDriverState(
                        -209,
                        966,
                        4129,
                        321,
                        12_500,
                        8,
                        100,
                        0,
                        12,
                        1,
                        ReplayTimelineFormat
                                .FLAG_LOCATION_VALID
                                | ReplayTimelineFormat
                                        .FLAG_TELEMETRY_VALID,
                        10,
                        0.0F,
                        Float.NaN,
                        3);

        final ReplayDriverState finalState =
                new ReplayDriverState(
                        100,
                        200,
                        300,
                        275,
                        11_200,
                        7,
                        90,
                        10,
                        1,
                        2,
                        ReplayTimelineFormat
                                .FLAG_LOCATION_VALID,
                        11,
                        2.5F,
                        0.8F,
                        2);

        try (ReplayTimelineWriter writer =
                new ReplayTimelineWriter(
                        timelineFile,
                        header)) {

            writer.writeState(
                    0,
                    0,
                    firstState);

            writer.writeState(
                    2,
                    1,
                    finalState);

            writer.complete();
        }

        assertEquals(
                header.expectedFileSizeBytes(),
                Files.size(timelineFile));

        try (ReplayTimelineReader reader =
                new ReplayTimelineReader(
                        timelineFile)) {

            assertEquals(
                    header,
                    reader.header());

            assertEquals(
                    0,
                    reader.readElapsedMillis(0));

            assertEquals(
                    500,
                    reader.readElapsedMillis(2));

            assertEquals(
                    firstState,
                    reader.readState(
                            0,
                            0));

            assertEquals(
                    finalState,
                    reader.readState(
                            2,
                            1));
        }
    }

    /**
     * Invalid magic bytes must be rejected.
     *
     * @throws Exception when the fixture file cannot be used
     */
    @Test
    void rejectsInvalidMagic()
            throws Exception {

        final ReplayTimelineHeader header =
                new ReplayTimelineHeader(
                        ReplayTimelineFormat.VERSION,
                        Instant.parse(
                                "2024-07-28T13:00:00Z"),
                        250,
                        1,
                        List.of(1));

        final Path timelineFile =
                temporaryDirectory.resolve(
                        "invalid-magic.bin");

        try (ReplayTimelineWriter writer =
                new ReplayTimelineWriter(
                        timelineFile,
                        header)) {

            writer.writeState(
                    0,
                    0,
                    ReplayDriverState.empty());

            writer.complete();
        }

        try (RandomAccessFile file =
                new RandomAccessFile(
                        timelineFile.toFile(),
                        "rw")) {

            file.seek(0);
            file.writeByte('X');
        }

        assertThrows(
                IOException.class,
                () -> new ReplayTimelineReader(
                        timelineFile));
    }
}
