package io.github.iamseverind.racereplay.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests time-based race-control state derivation.
 */
final class RaceControlTimelineTest {

    @TempDir
    private Path temporaryDirectory;

    /**
     * Derives green, yellow and DRS states.
     *
     * @throws Exception when fixture creation fails
     */
    @Test
    void derivesTrackAndDrsStates()
            throws Exception {

        final RaceControlTimeline timeline =
                loadFixture();

        assertEquals(
                9,
                timeline.size());

        final RaceControlTimeline.State preRace =
                timeline.stateAt(0.0);

        assertEquals(
                RaceControlTimeline.Phase.PRE_RACE,
                preRace.phase());

        assertEquals(
                RaceControlTimeline.DrsState.DISABLED,
                preRace.drsState());

        final RaceControlTimeline.State green =
                timeline.stateAt(20.0);

        assertEquals(
                RaceControlTimeline.Phase.GREEN,
                green.phase());

        assertEquals(
                RaceControlTimeline.DrsState.ENABLED,
                green.drsState());

        final RaceControlTimeline.State yellow =
                timeline.stateAt(31.0);

        assertEquals(
                RaceControlTimeline.Phase.YELLOW,
                yellow.phase());

        assertTrue(
                yellow.yellowSectors()
                        .contains(6));

        final RaceControlTimeline.State clear =
                timeline.stateAt(41.0);

        assertEquals(
                RaceControlTimeline.Phase.GREEN,
                clear.phase());

        assertTrue(
                clear.yellowSectors()
                        .isEmpty());
    }

    /**
     * Recognizes VSC and Safety-Car messages.
     *
     * @throws Exception when fixture creation fails
     */
    @Test
    void derivesSafetyCarPhases()
            throws Exception {

        final RaceControlTimeline timeline =
                loadFixture();

        assertEquals(
                RaceControlTimeline.Phase
                        .VIRTUAL_SAFETY_CAR,
                timeline.stateAt(51.0)
                        .phase());

        assertEquals(
                RaceControlTimeline.Phase
                        .SAFETY_CAR,
                timeline.stateAt(61.0)
                        .phase());

        assertEquals(
                RaceControlTimeline.Phase.GREEN,
                timeline.stateAt(71.0)
                        .phase());
    }

    /**
     * Retains the four latest messages.
     *
     * @throws Exception when fixture creation fails
     */
    @Test
    void returnsRecentEvents()
            throws Exception {

        final RaceControlTimeline.State state =
                loadFixture()
                        .stateAt(100.0);

        assertEquals(
                4,
                state.recentEvents()
                        .size());

        assertEquals(
                "GREEN FLAG",
                state.latestEvent()
                        .orElseThrow()
                        .message());
    }

    /**
     * Rejects malformed event data.
     *
     * @throws Exception when fixture creation fails
     */
    @Test
    void rejectsMalformedEvents()
            throws Exception {

        final Path invalid =
                temporaryDirectory.resolve(
                        "invalid-events.json");

        Files.writeString(
                invalid,
                """
                {
                  "events": [
                    {
                      "sequence": 0,
                      "message": "BROKEN"
                    }
                  ]
                }
                """,
                StandardCharsets.UTF_8);

        assertThrows(
                IOException.class,
                () -> RaceControlTimeline.load(
                        invalid));
    }

    private RaceControlTimeline loadFixture()
            throws Exception {

        final Path file =
                temporaryDirectory.resolve(
                        "events.json");

        Files.writeString(
                file,
                """
                {
                  "events": [
                    {
                      "sequence": 0,
                      "replay_seconds": -10.0,
                      "category": "Drs",
                      "flag": null,
                      "scope": null,
                      "sector": null,
                      "lap_number": 1,
                      "message": "DRS DISABLED"
                    },
                    {
                      "sequence": 1,
                      "replay_seconds": 5.0,
                      "category": "SessionStatus",
                      "flag": null,
                      "scope": null,
                      "sector": null,
                      "lap_number": 1,
                      "message": "SESSION STARTED"
                    },
                    {
                      "sequence": 2,
                      "replay_seconds": 10.0,
                      "category": "Flag",
                      "flag": "GREEN",
                      "scope": "Track",
                      "sector": null,
                      "lap_number": 1,
                      "message": "GREEN FLAG"
                    },
                    {
                      "sequence": 3,
                      "replay_seconds": 15.0,
                      "category": "Drs",
                      "flag": null,
                      "scope": null,
                      "sector": null,
                      "lap_number": 2,
                      "message": "DRS ENABLED"
                    },
                    {
                      "sequence": 4,
                      "replay_seconds": 30.0,
                      "category": "Flag",
                      "flag": "DOUBLE YELLOW",
                      "scope": "Sector",
                      "sector": 6,
                      "lap_number": 3,
                      "message": "DOUBLE YELLOW IN TRACK SECTOR 6"
                    },
                    {
                      "sequence": 5,
                      "replay_seconds": 40.0,
                      "category": "Flag",
                      "flag": "CLEAR",
                      "scope": "Sector",
                      "sector": 6,
                      "lap_number": 3,
                      "message": "CLEAR IN TRACK SECTOR 6"
                    },
                    {
                      "sequence": 6,
                      "replay_seconds": 50.0,
                      "category": "Other",
                      "flag": null,
                      "scope": null,
                      "sector": null,
                      "lap_number": 4,
                      "message": "VIRTUAL SAFETY CAR DEPLOYED"
                    },
                    {
                      "sequence": 7,
                      "replay_seconds": 60.0,
                      "category": "Other",
                      "flag": null,
                      "scope": null,
                      "sector": null,
                      "lap_number": 5,
                      "message": "SAFETY CAR DEPLOYED"
                    },
                    {
                      "sequence": 8,
                      "replay_seconds": 70.0,
                      "category": "Flag",
                      "flag": "GREEN",
                      "scope": "Track",
                      "sector": null,
                      "lap_number": 6,
                      "message": "GREEN FLAG"
                    }
                  ]
                }
                """,
                StandardCharsets.UTF_8);

        return RaceControlTimeline.load(
                file);
    }
}
