package io.github.iamseverind.racereplay.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Tests playback timeline calculations and formatting.
 */
final class PlaybackTimelineSupportTest {

    private static final double TOLERANCE =
            0.000_001;

    /**
     * Preserves a valid simulation duration.
     */
    @Test
    void resolvesValidDuration() {
        assertEquals(
                5_144.982,
                PlaybackTimelineSupport.resolveDuration(
                        5_144.982),
                TOLERANCE);
    }

    /**
     * Uses a finite fallback for unknown durations.
     */
    @Test
    void resolvesUnknownDuration() {
        assertEquals(
                3_600.0,
                PlaybackTimelineSupport.resolveDuration(
                        Double.NaN),
                TOLERANCE);
    }

    /**
     * Clamps times to both replay boundaries.
     */
    @Test
    void clampsToReplayBoundaries() {
        assertEquals(
                0.0,
                PlaybackTimelineSupport.clamp(
                        -20.0,
                        100.0),
                TOLERANCE);

        assertEquals(
                100.0,
                PlaybackTimelineSupport.clamp(
                        120.0,
                        100.0),
                TOLERANCE);
    }

    /**
     * Applies bounded relative jumps.
     */
    @Test
    void skipsWithinReplayBoundaries() {
        assertEquals(
                0.0,
                PlaybackTimelineSupport.skip(
                        5.0,
                        -10.0,
                        100.0),
                TOLERANCE);

        assertEquals(
                100.0,
                PlaybackTimelineSupport.skip(
                        95.0,
                        10.0,
                        100.0),
                TOLERANCE);

        assertEquals(
                60.0,
                PlaybackTimelineSupport.skip(
                        50.0,
                        10.0,
                        100.0),
                TOLERANCE);
    }

    /**
     * Formats the complete example replay duration.
     */
    @Test
    void formatsReplayTime() {
        assertEquals(
                "85:44.982",
                PlaybackTimelineSupport.formatTime(
                        5_144.982));
    }

    /**
     * Rejects invalid calculation arguments.
     */
    @Test
    void rejectsInvalidArguments() {
        assertThrows(
                IllegalArgumentException.class,
                () -> PlaybackTimelineSupport.clamp(
                        Double.NaN,
                        100.0));

        assertThrows(
                IllegalArgumentException.class,
                () -> PlaybackTimelineSupport.clamp(
                        10.0,
                        0.0));

        assertThrows(
                IllegalArgumentException.class,
                () -> PlaybackTimelineSupport.skip(
                        10.0,
                        Double.NaN,
                        100.0));
    }
}
