package io.github.iamseverind.racereplay.openf1;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.iamseverind.racereplay.core.CancellationSupport;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/**
 * Streams large OpenF1 JSON responses directly into cache files.
 */
public final class OpenF1RawDownloadClient
        implements RawDownloadClient {

    private static final int MAX_ATTEMPTS = 5;

    private static final Duration REQUEST_TIMEOUT =
            Duration.ofMinutes(15);

    private static final long MAX_RETRY_DELAY_SECONDS = 30;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    /**
     * Creates the production downloader.
     */
    public OpenF1RawDownloadClient() {
        this(
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(20))
                        .followRedirects(
                                HttpClient.Redirect.NORMAL)
                        .build(),
                new ObjectMapper());
    }

    OpenF1RawDownloadClient(
            final HttpClient httpClient,
            final ObjectMapper objectMapper) {

        this.httpClient =
                Objects.requireNonNull(
                        httpClient,
                        "httpClient");

        this.objectMapper =
                Objects.requireNonNull(
                        objectMapper,
                        "objectMapper");
    }

    /**
     * Downloads one endpoint with retry handling.
     *
     * @param uri source URI
     * @param target final cache file
     * @return validated file metadata
     * @throws IOException when all attempts fail
     * @throws InterruptedException when interrupted
     */
    @Override
    public RawDatasetFileInfo download(
            final URI uri,
            final Path target)
            throws IOException, InterruptedException {

        Objects.requireNonNull(uri, "uri");
        Objects.requireNonNull(target, "target");

        IOException lastFailure = null;

        for (int attempt = 1;
                attempt <= MAX_ATTEMPTS;
                attempt++) {

            CancellationSupport.checkpoint();

            final HttpResponse<InputStream> response;

            try {
                response = send(uri);
            } catch (final IOException exception) {
                lastFailure = exception;

                if (attempt == MAX_ATTEMPTS) {
                    throw exception;
                }

                sleepBeforeRetry(
                        defaultRetryDelaySeconds(attempt));

                continue;
            }

            final int statusCode =
                    response.statusCode();

            if (statusCode >= 200
                    && statusCode < 300) {

                return storeResponse(
                        response.body(),
                        target);
            }

            final String responseSummary =
                    readSummary(response.body());

            final OpenF1HttpException statusFailure =
                    new OpenF1HttpException(
                            statusCode,
                            "OpenF1 request failed with HTTP "
                            + statusCode
                            + ": "
                            + responseSummary);

            if (!isRetryable(statusCode)
                    || attempt == MAX_ATTEMPTS) {

                throw statusFailure;
            }

            lastFailure = statusFailure;

            sleepBeforeRetry(
                    retryDelaySeconds(
                            response,
                            attempt));
        }

        throw new IOException(
                "OpenF1 download failed.",
                lastFailure);
    }

    private HttpResponse<InputStream> send(final URI uri)
            throws IOException, InterruptedException {

        final HttpRequest request =
                HttpRequest.newBuilder(uri)
                        .timeout(REQUEST_TIMEOUT)
                        .header(
                                "Accept",
                                "application/json")
                        .header(
                                "User-Agent",
                                "Race-Replay-Lab/0.1")
                        .GET()
                        .build();

        return httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofInputStream());
    }

    private RawDatasetFileInfo storeResponse(
            final InputStream responseBody,
            final Path target)
            throws IOException {

        Files.createDirectories(target.getParent());

        final Path temporaryFile =
                target.resolveSibling(
                        target.getFileName()
                        + ".part-"
                        + UUID.randomUUID());

        final MessageDigest digest =
                createSha256Digest();

        long byteCount = 0;

        try {
            try (
                    InputStream input = responseBody;
                    OutputStream output =
                            Files.newOutputStream(
                                    temporaryFile,
                                    StandardOpenOption.CREATE_NEW,
                                    StandardOpenOption.WRITE)) {

                final byte[] buffer =
                        new byte[64 * 1024];

                int bytesRead;

                while ((bytesRead = input.read(buffer)) != -1) {
                    CancellationSupport.checkpoint();

                    output.write(
                            buffer,
                            0,
                            bytesRead);

                    digest.update(
                            buffer,
                            0,
                            bytesRead);

                    byteCount += bytesRead;
                }
            }

            final long recordCount =
                    countTopLevelRecords(temporaryFile);

            moveAtomically(
                    temporaryFile,
                    target);

            return new RawDatasetFileInfo(
                    byteCount,
                    recordCount,
                    HexFormat.of()
                            .formatHex(digest.digest()));
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    private long countTopLevelRecords(final Path file)
            throws IOException {

        try (JsonParser parser =
                objectMapper
                        .getFactory()
                        .createParser(file.toFile())) {

            if (parser.nextToken() != JsonToken.START_ARRAY) {
                throw new IOException(
                        "OpenF1 response is not a JSON array: "
                        + file);
            }

            long records = 0;

            JsonToken token;

            while ((token = parser.nextToken())
                    != JsonToken.END_ARRAY) {

                CancellationSupport.checkpoint();

                if (token == null) {
                    throw new IOException(
                            "Unexpected end of OpenF1 JSON array: "
                            + file);
                }

                parser.skipChildren();
                records++;
            }

            if (parser.nextToken() != null) {
                throw new IOException(
                        "Unexpected data after OpenF1 JSON array: "
                        + file);
            }

            return records;
        }
    }

    private static void moveAtomically(
            final Path source,
            final Path target)
            throws IOException {

        try {
            Files.move(
                    source,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (
                final AtomicMoveNotSupportedException exception) {

            Files.move(
                    source,
                    target,
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static MessageDigest createSha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (final NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable.",
                    exception);
        }
    }

    private static boolean isRetryable(
            final int statusCode) {

        return statusCode == 408
                || statusCode == 425
                || statusCode == 429
                || statusCode == 500
                || statusCode == 502
                || statusCode == 503
                || statusCode == 504;
    }

    private static long retryDelaySeconds(
            final HttpResponse<?> response,
            final int attempt) {

        final String retryAfter =
                response.headers()
                        .firstValue("Retry-After")
                        .orElse("");

        try {
            final long requestedDelay =
                    Long.parseLong(retryAfter);

            return Math.clamp(
                    requestedDelay,
                    1,
                    MAX_RETRY_DELAY_SECONDS);
        } catch (final NumberFormatException exception) {
            return defaultRetryDelaySeconds(attempt);
        }
    }

    private static long defaultRetryDelaySeconds(
            final int attempt) {

        final long calculatedDelay =
                1L << Math.min(attempt - 1, 5);

        return Math.min(
                calculatedDelay,
                MAX_RETRY_DELAY_SECONDS);
    }

    private static void sleepBeforeRetry(
            final long delaySeconds)
            throws InterruptedException {

        Thread.sleep(
                Duration.ofSeconds(delaySeconds));
    }

    private static String readSummary(
            final InputStream responseBody)
            throws IOException {

        if (responseBody == null) {
            return "empty response body";
        }

        try (InputStream input = responseBody) {
            final byte[] bytes =
                    input.readNBytes(300);

            if (bytes.length == 0) {
                return "empty response body";
            }

            return new String(
                    bytes,
                    StandardCharsets.UTF_8)
                    .replaceAll("\\s+", " ")
                    .strip();
        }
    }
}
