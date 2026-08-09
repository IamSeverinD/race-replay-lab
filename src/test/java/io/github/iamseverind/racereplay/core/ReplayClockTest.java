package io.github.iamseverind.racereplay.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Tests the UI-independent replay clock.
 */
final class ReplayClockTest {

    private static final double TOLERANCE = 0.000_001;

    /**
     * Playback speed must scale the logical replay time.
     */
    @Test
    void advancesUsingPlaybackSpeed() {
        final ReplayClock clock = new ReplayClock();

        clock.setPlaybackSpeed(2.0);
        clock.advance(1.5);

        assertEquals(
                3.0,
                clock.getReplaySeconds(),
                TOLERANCE);
    }

    /**
     * A paused replay must not advance.
     */
    @Test
    void doesNotAdvanceWhilePaused() {
        final ReplayClock clock = new ReplayClock();

        clock.seekTo(12.0);
        clock.setPaused(true);
        clock.advance(5.0);

        assertEquals(
                12.0,
                clock.getReplaySeconds(),
                TOLERANCE);
    }

    /**
     * Invalid playback speeds must be rejected.
     */
    @Test
    void rejectsInvalidPlaybackSpeed() {
        final ReplayClock clock = new ReplayClock();

        assertThrows(
                IllegalArgumentException.class,
                () -> clock.setPlaybackSpeed(0.0));

        assertThrows(
                IllegalArgumentException.class,
                () -> clock.setPlaybackSpeed(Double.NaN));
    }
}
