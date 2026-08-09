package io.github.iamseverind.racereplay.processed;

/**
 * Receives timeline build progress messages.
 */
@FunctionalInterface
public interface ReplayTimelineProgressListener {

    /**
     * Receives one progress message.
     *
     * @param message progress description
     */
    void onProgress(String message);
}
