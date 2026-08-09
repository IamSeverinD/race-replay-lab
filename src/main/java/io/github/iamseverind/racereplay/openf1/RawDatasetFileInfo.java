package io.github.iamseverind.racereplay.openf1;

import java.util.Objects;

/**
 * Metadata for one downloaded raw JSON dataset.
 *
 * @param bytes file size in bytes
 * @param records number of top-level array elements
 * @param sha256 SHA-256 checksum
 */
public record RawDatasetFileInfo(
        long bytes,
        long records,
        String sha256) {

    /**
     * Validates downloaded file metadata.
     */
    public RawDatasetFileInfo {
        if (bytes < 0) {
            throw new IllegalArgumentException(
                    "bytes must not be negative.");
        }

        if (records < 0) {
            throw new IllegalArgumentException(
                    "records must not be negative.");
        }

        sha256 =
                Objects.requireNonNull(sha256, "sha256")
                        .strip();

        if (sha256.length() != 64) {
            throw new IllegalArgumentException(
                    "sha256 must contain 64 hexadecimal characters.");
        }
    }
}
