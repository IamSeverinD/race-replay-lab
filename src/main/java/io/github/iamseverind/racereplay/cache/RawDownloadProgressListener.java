package io.github.iamseverind.racereplay.cache;

/**
 * Receives human-readable raw-cache progress updates.
 */
@FunctionalInterface
public interface RawDownloadProgressListener {

    /**
     * Receives one progress message.
     *
     * @param message progress message
     */
    void onProgress(String message);
}
