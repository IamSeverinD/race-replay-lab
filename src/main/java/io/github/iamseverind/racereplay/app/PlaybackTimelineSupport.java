package io.github.iamseverind.racereplay.app;

import java.util.Locale;

/**
 * Provides UI-independent playback timeline calculations.
 */
final class PlaybackTimelineSupport {

    private static final double DEFAULT_DURATION_SECONDS =
            3_600.0;

    private PlaybackTimelineSupport() {
    }

    /**
     * Resolves a usable finite timeline duration.
     *
     * @param durationSeconds reported simulation duration
     * @return positive finite duration
     */
    static double resolveDuration(
            final double durationSeconds) {

        if (Double.isFinite(durationSeconds)
                && durationSeconds > 0.0) {

            return durationSeconds;
        }

        return DEFAULT_DURATION_SECONDS;
    }

    /**
     * Clamps a requested time to the replay range.
     *
     * @param replaySeconds requested time
     * @param durationSeconds replay duration
     * @return clamped replay time
     */
    static double clamp(
            final double replaySeconds,
            final double durationSeconds) {

        requireDuration(durationSeconds);

        if (!Double.isFinite(replaySeconds)) {
            throw new IllegalArgumentException(
                    "Replay time must be finite.");
        }

        return Math.max(
                0.0,
                Math.min(
                        durationSeconds,
                        replaySeconds));
    }

    /**
     * Calculates a bounded relative time jump.
     *
     * @param replaySeconds current replay time
     * @param deltaSeconds signed jump size
     * @param durationSeconds replay duration
     * @return bounded target time
     */
    static double skip(
            final double replaySeconds,
            final double deltaSeconds,
            final double durationSeconds) {

        if (!Double.isFinite(deltaSeconds)) {
            throw new IllegalArgumentException(
                    "Jump size must be finite.");
        }

        return clamp(
                replaySeconds + deltaSeconds,
                durationSeconds);
    }

    /**
     * Formats a replay time as minutes, seconds and milliseconds.
     *
     * @param replaySeconds non-negative replay time
     * @return formatted replay time
     */
    static String formatTime(
            final double replaySeconds) {

        if (!Double.isFinite(replaySeconds)
                || replaySeconds < 0.0) {

            throw new IllegalArgumentException(
                    "Replay time must be non-negative and finite.");
        }

        final long totalMillis =
                Math.round(
                        replaySeconds * 1_000.0);

        final long minutes =
                totalMillis / 60_000L;

        final long seconds =
                totalMillis / 1_000L % 60L;

        final long milliseconds =
                totalMillis % 1_000L;

        return String.format(
                Locale.ROOT,
                "%02d:%02d.%03d",
                minutes,
                seconds,
                milliseconds);
    }

    private static void requireDuration(
            final double durationSeconds) {

        if (!Double.isFinite(durationSeconds)
                || durationSeconds <= 0.0) {

            throw new IllegalArgumentException(
                    "Replay duration must be positive and finite.");
        }
    }
}
