package io.github.iamseverind.racereplay.cache;

import io.github.iamseverind.racereplay.openf1.OpenF1Session;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Describes files produced by a session metadata download.
 *
 * @param cacheDirectory session cache directory
 * @param rawSessionFile unmodified OpenF1 response
 * @param manifestFile cache manifest
 * @param session normalized session metadata
 */
public record SessionCacheResult(
        Path cacheDirectory,
        Path rawSessionFile,
        Path manifestFile,
        OpenF1Session session) {

    /**
     * Validates the cache result.
     */
    public SessionCacheResult {
        cacheDirectory =
                Objects.requireNonNull(
                        cacheDirectory,
                        "cacheDirectory");

        rawSessionFile =
                Objects.requireNonNull(
                        rawSessionFile,
                        "rawSessionFile");

        manifestFile =
                Objects.requireNonNull(
                        manifestFile,
                        "manifestFile");

        session =
                Objects.requireNonNull(session, "session");
    }
}
