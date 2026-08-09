package io.github.iamseverind.racereplay.processed;

import java.util.Objects;

/**
 * Creates smooth intermediate states between two replay frames.
 */
final class ReplayStateInterpolator {

    private ReplayStateInterpolator() {
    }

    /**
     * Interpolates continuous values while preserving discrete race states.
     *
     * @param current current timeline state
     * @param next following timeline state
     * @param fraction interpolation fraction from zero to one
     * @return interpolated state
     */
    static ReplayDriverState interpolate(
            final ReplayDriverState current,
            final ReplayDriverState next,
            final double fraction) {

        Objects.requireNonNull(
                current,
                "current");

        Objects.requireNonNull(
                next,
                "next");

        if (!Double.isFinite(fraction)
                || fraction < 0.0
                || fraction > 1.0) {

            throw new IllegalArgumentException(
                    "Interpolation fraction must be "
                    + "between zero and one.");
        }

        if (fraction <= 0.0) {
            return current;
        }

        if (fraction >= 1.0) {
            return next;
        }

        final boolean locationInterpolatable =
                hasFlag(
                        current,
                        ReplayTimelineFormat
                                .FLAG_LOCATION_VALID)
                && hasFlag(
                        next,
                        ReplayTimelineFormat
                                .FLAG_LOCATION_VALID);

        final boolean telemetryInterpolatable =
                hasFlag(
                        current,
                        ReplayTimelineFormat
                                .FLAG_TELEMETRY_VALID)
                && hasFlag(
                        next,
                        ReplayTimelineFormat
                                .FLAG_TELEMETRY_VALID);

        final boolean gapInterpolatable =
                hasFlag(
                        current,
                        ReplayTimelineFormat
                                .FLAG_GAP_VALID)
                && hasFlag(
                        next,
                        ReplayTimelineFormat
                                .FLAG_GAP_VALID)
                && Float.isFinite(
                        current.gapToLeaderSeconds())
                && Float.isFinite(
                        next.gapToLeaderSeconds());

        final boolean intervalInterpolatable =
                hasFlag(
                        current,
                        ReplayTimelineFormat
                                .FLAG_INTERVAL_VALID)
                && hasFlag(
                        next,
                        ReplayTimelineFormat
                                .FLAG_INTERVAL_VALID)
                && Float.isFinite(
                        current.intervalSeconds())
                && Float.isFinite(
                        next.intervalSeconds());

        return new ReplayDriverState(
                locationInterpolatable
                        ? interpolateInt(
                                current.x(),
                                next.x(),
                                fraction)
                        : current.x(),
                locationInterpolatable
                        ? interpolateInt(
                                current.y(),
                                next.y(),
                                fraction)
                        : current.y(),
                locationInterpolatable
                        ? interpolateInt(
                                current.z(),
                                next.z(),
                                fraction)
                        : current.z(),
                telemetryInterpolatable
                        ? interpolateInt(
                                current.speed(),
                                next.speed(),
                                fraction)
                        : current.speed(),
                telemetryInterpolatable
                        ? interpolateInt(
                                current.rpm(),
                                next.rpm(),
                                fraction)
                        : current.rpm(),
                current.gear(),
                telemetryInterpolatable
                        ? interpolateInt(
                                current.throttle(),
                                next.throttle(),
                                fraction)
                        : current.throttle(),
                current.brake(),
                current.drs(),
                current.position(),
                current.flags(),
                current.lapNumber(),
                gapInterpolatable
                        ? interpolateFloat(
                                current.gapToLeaderSeconds(),
                                next.gapToLeaderSeconds(),
                                fraction)
                        : current.gapToLeaderSeconds(),
                intervalInterpolatable
                        ? interpolateFloat(
                                current.intervalSeconds(),
                                next.intervalSeconds(),
                                fraction)
                        : current.intervalSeconds(),
                current.tyreCompoundCode());
    }

    private static int interpolateInt(
            final int start,
            final int end,
            final double fraction) {

        return (int) Math.round(
                start
                + (end - start) * fraction);
    }

    private static float interpolateFloat(
            final float start,
            final float end,
            final double fraction) {

        return (float) (
                start
                + (end - start) * fraction);
    }

    private static boolean hasFlag(
            final ReplayDriverState state,
            final int flag) {

        return (state.flags() & flag) != 0;
    }
}
