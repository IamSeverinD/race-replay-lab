package io.github.iamseverind.racereplay.cache;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Summary of a completed raw-cache operation.
 *
 * @param cacheDirectory session cache directory
 * @param downloadedDatasets newly downloaded dataset count
 * @param reusedDatasets reused valid dataset count
 * @param totalBytes total raw dataset size
 * @param totalRecords total top-level JSON records
 */
public record RawCacheDownloadResult(
        Path cacheDirectory,
        int downloadedDatasets,
        int reusedDatasets,
        long totalBytes,
        long totalRecords) {

    /**
     * Validates the result.
     */
    public RawCacheDownloadResult {
        cacheDirectory =
                Objects.requireNonNull(
                        cacheDirectory,
                        "cacheDirectory");

        if (downloadedDatasets < 0
                || reusedDatasets < 0
                || totalBytes < 0
                || totalRecords < 0) {

            throw new IllegalArgumentException(
                    "Raw cache totals must not be negative.");
        }
    }
}
