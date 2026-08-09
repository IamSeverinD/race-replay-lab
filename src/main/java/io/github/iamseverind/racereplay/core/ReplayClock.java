package io.github.iamseverind.racereplay.core;

/**
 * Controls the logical replay time independently from the user interface.
 */
public final class ReplayClock {

    private double replaySeconds;
    private double playbackSpeed = 1.0;
    private boolean paused;

    /**
     * Advances the logical replay time.
     *
     * @param realElapsedSeconds elapsed wall-clock time in seconds
     */
    public void advance(final double realElapsedSeconds) {
        validateElapsedSeconds(realElapsedSeconds);

        if (!paused) {
            replaySeconds += realElapsedSeconds * playbackSpeed;
        }
    }

    /**
     * Returns the current logical replay time.
     *
     * @return replay time in seconds
     */
    public double getReplaySeconds() {
        return replaySeconds;
    }

    /**
     * Sets the logical replay time.
     *
     * @param replaySeconds new non-negative replay time
     */
    public void seekTo(final double replaySeconds) {
        validateElapsedSeconds(replaySeconds);
        this.replaySeconds = replaySeconds;
    }

    /**
     * Returns the current playback multiplier.
     *
     * @return positive playback multiplier
     */
    public double getPlaybackSpeed() {
        return playbackSpeed;
    }

    /**
     * Changes the playback multiplier.
     *
     * @param playbackSpeed positive finite multiplier
     */
    public void setPlaybackSpeed(final double playbackSpeed) {
        if (!Double.isFinite(playbackSpeed) || playbackSpeed <= 0.0) {
            throw new IllegalArgumentException(
                    "Playback speed must be positive and finite.");
        }

        this.playbackSpeed = playbackSpeed;
    }

    /**
     * Returns whether replay progression is paused.
     *
     * @return true when paused
     */
    public boolean isPaused() {
        return paused;
    }

    /**
     * Pauses or resumes the replay.
     *
     * @param paused true to pause
     */
    public void setPaused(final boolean paused) {
        this.paused = paused;
    }

    /**
     * Toggles between paused and running.
     */
    public void togglePaused() {
        paused = !paused;
    }

    private static void validateElapsedSeconds(final double seconds) {
        if (!Double.isFinite(seconds) || seconds < 0.0) {
            throw new IllegalArgumentException(
                    "Time must be non-negative and finite.");
        }
    }
}
