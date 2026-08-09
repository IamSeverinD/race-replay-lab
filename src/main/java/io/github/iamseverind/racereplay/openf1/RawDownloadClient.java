package io.github.iamseverind.racereplay.openf1;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;

/**
 * Downloads one JSON endpoint into an atomic cache file.
 */
@FunctionalInterface
public interface RawDownloadClient {

    /**
     * Downloads and validates a JSON array.
     *
     * @param uri source URI
     * @param target target cache file
     * @return metadata for the completed file
     * @throws IOException when downloading or validation fails
     * @throws InterruptedException when the request is interrupted
     */
    RawDatasetFileInfo download(
            URI uri,
            Path target)
            throws IOException, InterruptedException;
}
