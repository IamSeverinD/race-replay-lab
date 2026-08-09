package io.github.iamseverind.racereplay.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Tests visible race-control text formatting.
 */
final class RaceControlTextFormatterTest {

    /**
     * Formats an empty pre-race state.
     */
    @Test
    void formatsEmptyPreRaceState() {
        final RaceControlTimeline.State state =
                state(
                        RaceControlTimeline.Phase.PRE_RACE,
                        RaceControlTimeline.DrsState.UNKNOWN,
                        Optional.empty(),
                        Set.of());

        assertEquals(
                "PRE-RACE",
                RaceControlTextFormatter.phase(
                        state));

        assertEquals(
                "DRS —",
                RaceControlTextFormatter.drs(
                        state));

        assertEquals(
                "NO RACE CONTROL DATA",
                RaceControlTextFormatter.message(
                        state));
    }

    /**
     * Formats a local yellow sector.
     */
    @Test
    void formatsYellowSector() {
        final RaceControlTimeline.Event event =
                new RaceControlTimeline.Event(
                        30.0,
                        1,
                        "Flag",
                        "DOUBLE YELLOW",
                        "Sector",
                        6,
                        3,
                        "DOUBLE YELLOW IN TRACK SECTOR 6");

        final RaceControlTimeline.State state =
                state(
                        RaceControlTimeline.Phase.YELLOW,
                        RaceControlTimeline.DrsState.ENABLED,
                        Optional.of(
                                event),
                        Set.of(6));

        assertEquals(
                "YELLOW FLAG · SECTOR 6",
                RaceControlTextFormatter.phase(
                        state));

        assertEquals(
                "DRS ENABLED",
                RaceControlTextFormatter.drs(
                        state));

        assertEquals(
                "00:30.000 · "
                + "DOUBLE YELLOW IN TRACK SECTOR 6",
                RaceControlTextFormatter.message(
                        state));
    }

    /**
     * Formats events before the replay start.
     */
    @Test
    void formatsPreReplayEvent() {
        final RaceControlTimeline.Event event =
                new RaceControlTimeline.Event(
                        -179.348,
                        0,
                        "Drs",
                        "",
                        "",
                        null,
                        1,
                        "DRS DISABLED");

        final RaceControlTimeline.State state =
                state(
                        RaceControlTimeline.Phase.PRE_RACE,
                        RaceControlTimeline.DrsState.DISABLED,
                        Optional.of(
                                event),
                        Set.of());

        assertEquals(
                "PRE-RACE · DRS DISABLED",
                RaceControlTextFormatter.message(
                        state));

        assertTrue(
                RaceControlTextFormatter
                        .phaseStyle(
                                state.phase())
                        .contains(
                                "#4b5563"));
    }

    private static RaceControlTimeline.State state(
            final RaceControlTimeline.Phase phase,
            final RaceControlTimeline.DrsState drsState,
            final Optional<RaceControlTimeline.Event> event,
            final Set<Integer> yellowSectors) {

        return new RaceControlTimeline.State(
                phase,
                drsState,
                event,
                event.map(List::of)
                        .orElseGet(List::of),
                yellowSectors);
    }
}
