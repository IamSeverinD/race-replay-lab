package io.github.iamseverind.racereplay.processed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Tests smooth interpolation between processed replay frames.
 */
final class ReplayStateInterpolatorTest {

    private static final double TOLERANCE =
            0.000_1;

    /**
     * Interpolates continuous values and preserves discrete values.
     */
    @Test
    void interpolatesContinuousValues() {
        final int flags =
                ReplayTimelineFormat
                        .FLAG_LOCATION_VALID
                | ReplayTimelineFormat
                        .FLAG_TELEMETRY_VALID
                | ReplayTimelineFormat
                        .FLAG_POSITION_VALID
                | ReplayTimelineFormat
                        .FLAG_LAP_VALID
                | ReplayTimelineFormat
                        .FLAG_GAP_VALID
                | ReplayTimelineFormat
                        .FLAG_INTERVAL_VALID
                | ReplayTimelineFormat
                        .FLAG_TYRE_VALID
                | ReplayTimelineFormat
                        .FLAG_ACTIVE;

        final ReplayDriverState current =
                state(
                        0,
                        100,
                        10_000,
                        3,
                        20,
                        0,
                        0,
                        2,
                        flags,
                        1,
                        2.0f,
                        0.5f,
                        1);

        final ReplayDriverState next =
                state(
                        100,
                        200,
                        12_000,
                        5,
                        80,
                        100,
                        12,
                        1,
                        flags,
                        2,
                        1.0f,
                        0.2f,
                        2);

        final ReplayDriverState result =
                ReplayStateInterpolator.interpolate(
                        current,
                        next,
                        0.5);

        assertEquals(50, result.x());
        assertEquals(100, result.y());
        assertEquals(150, result.z());
        assertEquals(150, result.speed());
        assertEquals(11_000, result.rpm());
        assertEquals(50, result.throttle());

        assertEquals(
                1.5,
                result.gapToLeaderSeconds(),
                TOLERANCE);

        assertEquals(
                0.35,
                result.intervalSeconds(),
                TOLERANCE);

        assertEquals(3, result.gear());
        assertEquals(0, result.brake());
        assertEquals(0, result.drs());
        assertEquals(2, result.position());
        assertEquals(1, result.lapNumber());
        assertEquals(1, result.tyreCompoundCode());
        assertEquals(flags, result.flags());
    }

    /**
     * Uses exact boundary states.
     */
    @Test
    void usesExactBoundaryStates() {
        final ReplayDriverState current =
                state(
                        0,
                        100,
                        10_000,
                        3,
                        20,
                        0,
                        0,
                        2,
                        0,
                        1,
                        Float.NaN,
                        Float.NaN,
                        1);

        final ReplayDriverState next =
                state(
                        100,
                        200,
                        12_000,
                        5,
                        80,
                        100,
                        12,
                        1,
                        0,
                        2,
                        Float.NaN,
                        Float.NaN,
                        2);

        assertSame(
                current,
                ReplayStateInterpolator.interpolate(
                        current,
                        next,
                        0.0));

        assertSame(
                next,
                ReplayStateInterpolator.interpolate(
                        current,
                        next,
                        1.0));

        assertThrows(
                IllegalArgumentException.class,
                () -> ReplayStateInterpolator.interpolate(
                        current,
                        next,
                        -0.1));
    }

    private static ReplayDriverState state(
            final int coordinate,
            final int speed,
            final int rpm,
            final int gear,
            final int throttle,
            final int brake,
            final int drs,
            final int position,
            final int flags,
            final int lap,
            final float gap,
            final float interval,
            final int tyre) {

        return new ReplayDriverState(
                coordinate,
                coordinate * 2,
                coordinate * 3,
                speed,
                rpm,
                gear,
                throttle,
                brake,
                drs,
                position,
                flags,
                lap,
                gap,
                interval,
                tyre);
    }
}
