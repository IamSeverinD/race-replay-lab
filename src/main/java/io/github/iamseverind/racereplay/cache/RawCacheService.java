package io.github.iamseverind.racereplay.cache;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.iamseverind.racereplay.core.CancellationSupport;
import io.github.iamseverind.racereplay.openf1.OpenF1Dataset;
import io.github.iamseverind.racereplay.openf1.OpenF1HttpException;
import io.github.iamseverind.racereplay.openf1.RawDatasetFileInfo;
import io.github.iamseverind.racereplay.openf1.RawDownloadClient;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Downloads all raw OpenF1 datasets required by the replay.
 */
public final class RawCacheService {

    private static final Duration PARTITION_DURATION =
            Duration.ofMinutes(30);

    private final RawDownloadClient downloadClient;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * Creates a raw-cache service.
     *
     * @param downloadClient streaming download client
     * @param objectMapper JSON mapper
     * @param clock manifest clock
     */
    public RawCacheService(
            final RawDownloadClient downloadClient,
            final ObjectMapper objectMapper,
            final Clock clock) {

        this.downloadClient =
                Objects.requireNonNull(
                        downloadClient,
                        "downloadClient");

        this.objectMapper =
                Objects.requireNonNull(
                        objectMapper,
                        "objectMapper");

        this.clock =
                Objects.requireNonNull(clock, "clock");
    }

    /**
     * Downloads or reuses every required raw dataset.
     *
     * @param sessionCache existing session metadata cache
     * @param progressListener progress receiver
     * @return completed download summary
     * @throws IOException when a file cannot be downloaded or verified
     * @throws InterruptedException when interrupted
     */
    public RawCacheDownloadResult downloadAll(
            final SessionCacheResult sessionCache,
            final RawDownloadProgressListener progressListener)
            throws IOException, InterruptedException {

        Objects.requireNonNull(
                sessionCache,
                "sessionCache");

        Objects.requireNonNull(
                progressListener,
                "progressListener");

        final ObjectNode manifest =
                readManifest(
                        sessionCache.manifestFile());

        final ObjectNode filesNode =
                requireFilesNode(manifest);

        final Path rawDirectory =
                sessionCache.cacheDirectory()
                        .resolve("raw");

        Files.createDirectories(rawDirectory);

        int downloadedDatasets = 0;
        int reusedDatasets = 0;
        long totalBytes = 0;
        long totalRecords = 0;

        final OpenF1Dataset[] datasets =
                OpenF1Dataset.values();

        for (int index = 0;
                index < datasets.length;
                index++) {

            CancellationSupport.checkpoint();

            final OpenF1Dataset dataset =
                    datasets[index];

            final int currentNumber =
                    index + 1;

            final Path target =
                    rawDirectory.resolve(
                            dataset.fileName());

            final RawDatasetFileInfo existingInfo =
                    readReusableFileInfo(
                            filesNode,
                            dataset,
                            target);

            if (existingInfo != null) {
                reusedDatasets++;
                totalBytes += existingInfo.bytes();
                totalRecords += existingInfo.records();

                progressListener.onProgress(
                        progressPrefix(
                                currentNumber,
                                datasets.length,
                                dataset)
                        + "wiederverwendet"
                        + " | "
                        + existingInfo.records()
                        + " Datensätze"
                        + " | "
                        + humanBytes(existingInfo.bytes()));

                continue;
            }

            progressListener.onProgress(
                    progressPrefix(
                            currentNumber,
                            datasets.length,
                            dataset)
                    + "Download gestartet");

            final RawDatasetFileInfo downloadedInfo;

            if (dataset.requiresPartitioning()) {
                downloadedInfo =
                        downloadPartitionedDataset(
                                sessionCache,
                                dataset,
                                target,
                                currentNumber,
                                datasets.length,
                                progressListener);
            } else {
                downloadedInfo =
                        downloadSingleDataset(
                                sessionCache,
                                dataset,
                                target,
                                currentNumber,
                                datasets.length,
                                progressListener);
            }

            downloadedDatasets++;
            totalBytes += downloadedInfo.bytes();
            totalRecords += downloadedInfo.records();

            updateDatasetManifest(
                    filesNode,
                    sessionCache
                            .session()
                            .sessionKey(),
                    sessionCache.cacheDirectory(),
                    dataset,
                    target,
                    downloadedInfo);

            manifest.put(
                    "cache_state",
                    "raw_partial");

            manifest.put(
                    "last_updated_at",
                    Instant.now(clock).toString());

            writeManifest(
                    sessionCache.manifestFile(),
                    manifest);

            progressListener.onProgress(
                    progressPrefix(
                            currentNumber,
                            datasets.length,
                            dataset)
                    + "abgeschlossen"
                    + " | "
                    + downloadedInfo.records()
                    + " Datensätze"
                    + " | "
                    + humanBytes(downloadedInfo.bytes()));
        }

        manifest.put(
                "cache_state",
                "raw_complete");

        manifest.put(
                "raw_dataset_count",
                datasets.length);

        manifest.put(
                "raw_dataset_bytes",
                totalBytes);

        manifest.put(
                "raw_dataset_records",
                totalRecords);

        manifest.put(
                "raw_completed_at",
                Instant.now(clock).toString());

        manifest.put(
                "last_updated_at",
                Instant.now(clock).toString());

        writeManifest(
                sessionCache.manifestFile(),
                manifest);

        return new RawCacheDownloadResult(
                sessionCache.cacheDirectory(),
                downloadedDatasets,
                reusedDatasets,
                totalBytes,
                totalRecords);
    }

    private RawDatasetFileInfo downloadSingleDataset(
            final SessionCacheResult sessionCache,
            final OpenF1Dataset dataset,
            final Path target,
            final int datasetNumber,
            final int datasetTotal,
            final RawDownloadProgressListener progressListener)
            throws IOException, InterruptedException {

        try {
            return downloadClient.download(
                    dataset.buildUri(
                            sessionCache
                                    .session()
                                    .sessionKey()),
                    target);
        } catch (final OpenF1HttpException exception) {
            if (exception.statusCode() != 404
                    || !dataset.allowsEmptyResponse()) {

                throw exception;
            }

            final RawDatasetFileInfo emptyInfo =
                    writeEmptyJsonArray(target);

            progressListener.onProgress(
                    progressPrefix(
                            datasetNumber,
                            datasetTotal,
                            dataset)
                    + "keine Datensätze"
                    + " | leeres Dataset gespeichert");

            return emptyInfo;
        }
    }

    private RawDatasetFileInfo downloadPartitionedDataset(
            final SessionCacheResult sessionCache,
            final OpenF1Dataset dataset,
            final Path target,
            final int datasetNumber,
            final int datasetTotal,
            final RawDownloadProgressListener progressListener)
            throws IOException, InterruptedException {

        final Path rawDirectory =
                target.getParent();

        final Path driversFile =
                rawDirectory.resolve(
                        OpenF1Dataset.DRIVERS.fileName());

        final List<Integer> driverNumbers =
                readDriverNumbers(driversFile);

        final List<TimePartition> timePartitions =
                createTimePartitions(
                        sessionCache
                                .session()
                                .dateStart(),
                        sessionCache
                                .session()
                                .dateEnd());

        final int partitionCount =
                Math.multiplyExact(
                        driverNumbers.size(),
                        timePartitions.size());

        final Path chunksRoot =
                rawDirectory.resolve(".chunks");

        final Path datasetChunkDirectory =
                chunksRoot.resolve(
                        dataset.endpoint());

        Files.createDirectories(
                datasetChunkDirectory);

        final List<Path> chunkFiles =
                new ArrayList<>(partitionCount);

        int partitionNumber = 0;

        for (final int driverNumber : driverNumbers) {
            for (int windowIndex = 0;
                    windowIndex < timePartitions.size();
                    windowIndex++) {

                CancellationSupport.checkpoint();

                partitionNumber++;

                final TimePartition timePartition =
                        timePartitions.get(windowIndex);

                final Path chunkFile =
                        datasetChunkDirectory.resolve(
                                String.format(
                                        "driver-%03d-window-%03d.json",
                                        driverNumber,
                                        windowIndex + 1));

                RawDatasetFileInfo chunkInfo =
                        inspectReusableChunk(
                                chunkFile);

                final String partitionPrefix =
                        progressPrefix(
                                datasetNumber,
                                datasetTotal,
                                dataset)
                        + "Teil "
                        + partitionNumber
                        + "/"
                        + partitionCount
                        + " | Fahrer #"
                        + driverNumber
                        + " | ";

                if (chunkInfo == null) {
                    progressListener.onProgress(
                            partitionPrefix
                            + "Download");

                    try {
                        chunkInfo =
                                downloadClient.download(
                                        dataset.buildPartitionUri(
                                                sessionCache
                                                        .session()
                                                        .sessionKey(),
                                                driverNumber,
                                                timePartition.start(),
                                                timePartition.end()),
                                        chunkFile);
                    } catch (
                            final OpenF1HttpException exception) {

                        if (exception.statusCode() != 404) {
                            throw exception;
                        }

                        chunkInfo =
                                writeEmptyJsonArray(
                                        chunkFile);

                        progressListener.onProgress(
                                partitionPrefix
                                + "keine Datensätze"
                                + " | leere Partition gespeichert");
                    }

                    progressListener.onProgress(
                            partitionPrefix
                            + chunkInfo.records()
                            + " Datensätze"
                            + " | "
                            + humanBytes(chunkInfo.bytes()));
                } else {
                    progressListener.onProgress(
                            partitionPrefix
                            + "Teilcache wiederverwendet"
                            + " | "
                            + chunkInfo.records()
                            + " Datensätze");
                }

                chunkFiles.add(chunkFile);
            }
        }

        progressListener.onProgress(
                progressPrefix(
                        datasetNumber,
                        datasetTotal,
                        dataset)
                + "Teilcache wird zusammengeführt");

        final RawDatasetFileInfo mergedInfo =
                mergeJsonArrays(
                        chunkFiles,
                        target);

        deleteRecursively(
                datasetChunkDirectory);

        deleteDirectoryWhenEmpty(
                chunksRoot);

        return mergedInfo;
    }

    private List<Integer> readDriverNumbers(
            final Path driversFile)
            throws IOException {

        if (!Files.isRegularFile(driversFile)) {
            throw new IOException(
                    "Drivers cache file is missing: "
                    + driversFile);
        }

        final JsonNode root =
                objectMapper.readTree(
                        driversFile.toFile());

        if (!root.isArray()) {
            throw new IOException(
                    "Drivers cache is not a JSON array: "
                    + driversFile);
        }

        final TreeSet<Integer> driverNumbers =
                new TreeSet<>();

        for (final JsonNode driverNode : root) {
            final int driverNumber =
                    driverNode
                            .path("driver_number")
                            .asInt(-1);

            if (driverNumber > 0) {
                driverNumbers.add(driverNumber);
            }
        }

        if (driverNumbers.isEmpty()) {
            throw new IOException(
                    "Drivers cache contains no driver numbers: "
                    + driversFile);
        }

        return List.copyOf(driverNumbers);
    }

    private static List<TimePartition> createTimePartitions(
            final Instant sessionStart,
            final Instant sessionEnd)
            throws IOException {

        if (!sessionStart.isBefore(sessionEnd)) {
            throw new IOException(
                    "Session start must be before session end.");
        }

        final List<TimePartition> partitions =
                new ArrayList<>();

        Instant currentStart =
                sessionStart;

        while (currentStart.isBefore(sessionEnd)) {
            final Instant calculatedEnd =
                    currentStart.plus(
                            PARTITION_DURATION);

            final Instant currentEnd =
                    calculatedEnd.isBefore(sessionEnd)
                            ? calculatedEnd
                            : sessionEnd;

            partitions.add(
                    new TimePartition(
                            currentStart,
                            currentEnd));

            currentStart =
                    currentEnd;
        }

        return List.copyOf(partitions);
    }

    private RawDatasetFileInfo writeEmptyJsonArray(
            final Path target)
            throws IOException {

        Files.createDirectories(
                target.getParent());

        final Path temporaryFile =
                target.resolveSibling(
                        target.getFileName()
                        + ".part-"
                        + UUID.randomUUID());

        try {
            Files.writeString(
                    temporaryFile,
                    "[]",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);

            moveAtomically(
                    temporaryFile,
                    target);

            return inspectJsonArrayFile(target);
        } finally {
            Files.deleteIfExists(
                    temporaryFile);
        }
    }

    private RawDatasetFileInfo inspectReusableChunk(
            final Path chunkFile)
            throws IOException {

        if (!Files.isRegularFile(chunkFile)) {
            return null;
        }

        try {
            return inspectJsonArrayFile(chunkFile);
        } catch (final IOException exception) {
            Files.deleteIfExists(chunkFile);
            return null;
        }
    }

    private RawDatasetFileInfo inspectJsonArrayFile(
            final Path file)
            throws IOException {

        return new RawDatasetFileInfo(
                Files.size(file),
                countTopLevelRecords(file),
                sha256(file));
    }

    private long countTopLevelRecords(final Path file)
            throws IOException {

        try (JsonParser parser =
                objectMapper
                        .getFactory()
                        .createParser(file.toFile())) {

            if (parser.nextToken()
                    != JsonToken.START_ARRAY) {

                throw new IOException(
                        "JSON file is not an array: "
                        + file);
            }

            long records = 0;

            JsonToken token;

            while ((token = parser.nextToken())
                    != JsonToken.END_ARRAY) {

                if (token == null) {
                    throw new IOException(
                            "Unexpected end of JSON array: "
                            + file);
                }

                parser.skipChildren();
                records++;
            }

            if (parser.nextToken() != null) {
                throw new IOException(
                        "Unexpected data after JSON array: "
                        + file);
            }

            return records;
        }
    }

    private RawDatasetFileInfo mergeJsonArrays(
            final List<Path> chunkFiles,
            final Path target)
            throws IOException {

        Files.createDirectories(
                target.getParent());

        final Path temporaryFile =
                target.resolveSibling(
                        target.getFileName()
                        + ".part-"
                        + UUID.randomUUID());

        final MessageDigest digest =
                createSha256Digest();

        long records = 0;

        try {
            try (
                    OutputStream fileOutput =
                            Files.newOutputStream(
                                    temporaryFile,
                                    StandardOpenOption.CREATE_NEW,
                                    StandardOpenOption.WRITE);

                    DigestOutputStream digestOutput =
                            new DigestOutputStream(
                                    fileOutput,
                                    digest);

                    JsonGenerator generator =
                            objectMapper
                                    .getFactory()
                                    .createGenerator(
                                            digestOutput)) {

                generator.writeStartArray();

                for (final Path chunkFile : chunkFiles) {
                    CancellationSupport.checkpoint();

                    try (JsonParser parser =
                            objectMapper
                                    .getFactory()
                                    .createParser(
                                            chunkFile.toFile())) {

                        if (parser.nextToken()
                                != JsonToken.START_ARRAY) {

                            throw new IOException(
                                    "Chunk is not a JSON array: "
                                    + chunkFile);
                        }

                        JsonToken token;

                        while ((token = parser.nextToken())
                                != JsonToken.END_ARRAY) {

                            CancellationSupport.checkpoint();

                            if (token == null) {
                                throw new IOException(
                                        "Unexpected end of chunk: "
                                        + chunkFile);
                            }

                            generator.copyCurrentStructure(
                                    parser);

                            records++;
                        }

                        if (parser.nextToken() != null) {
                            throw new IOException(
                                    "Unexpected data after chunk array: "
                                    + chunkFile);
                        }
                    }
                }

                generator.writeEndArray();
            }

            final long bytes =
                    Files.size(temporaryFile);

            final String checksum =
                    HexFormat.of()
                            .formatHex(
                                    digest.digest());

            moveAtomically(
                    temporaryFile,
                    target);

            return new RawDatasetFileInfo(
                    bytes,
                    records,
                    checksum);
        } finally {
            Files.deleteIfExists(
                    temporaryFile);
        }
    }

    private ObjectNode readManifest(final Path manifestFile)
            throws IOException {

        final JsonNode root =
                objectMapper.readTree(
                        manifestFile.toFile());

        if (!(root instanceof ObjectNode objectNode)) {
            throw new IOException(
                    "Cache manifest is not a JSON object: "
                    + manifestFile);
        }

        return objectNode;
    }

    private static ObjectNode requireFilesNode(
            final ObjectNode manifest)
            throws IOException {

        final JsonNode filesNode =
                manifest.get("files");

        if (!(filesNode instanceof ObjectNode objectNode)) {
            throw new IOException(
                    "Cache manifest has no files object.");
        }

        return objectNode;
    }

    private static RawDatasetFileInfo readReusableFileInfo(
            final ObjectNode filesNode,
            final OpenF1Dataset dataset,
            final Path target)
            throws IOException {

        final JsonNode metadataNode =
                filesNode.get(
                        dataset.manifestKey());

        if (!(metadataNode instanceof ObjectNode metadata)
                || !Files.isRegularFile(target)) {

            return null;
        }

        final long expectedBytes =
                metadata.path("bytes").asLong(-1);

        final long expectedRecords =
                metadata.path("records").asLong(-1);

        final String expectedSha256 =
                metadata.path("sha256").asText("");

        if (expectedBytes < 0
                || expectedRecords < 0
                || expectedSha256.length() != 64) {

            return null;
        }

        if (Files.size(target) != expectedBytes) {
            return null;
        }

        final String actualSha256 =
                sha256(target);

        if (!actualSha256.equals(expectedSha256)) {
            return null;
        }

        return new RawDatasetFileInfo(
                expectedBytes,
                expectedRecords,
                expectedSha256);
    }

    private static void updateDatasetManifest(
            final ObjectNode filesNode,
            final int sessionKey,
            final Path cacheDirectory,
            final OpenF1Dataset dataset,
            final Path target,
            final RawDatasetFileInfo fileInfo) {

        final ObjectNode fileNode =
                filesNode.putObject(
                        dataset.manifestKey());

        fileNode.put(
                "endpoint",
                dataset.endpoint());

        fileNode.put(
                "request_uri",
                dataset.buildUri(sessionKey)
                        .toString());

        fileNode.put(
                "download_strategy",
                dataset.requiresPartitioning()
                        ? "driver_and_time_partitions"
                        : "single_request");

        fileNode.put(
                "path",
                cacheDirectory
                        .relativize(target)
                        .toString()
                        .replace(
                                File.separatorChar,
                                '/'));

        fileNode.put(
                "bytes",
                fileInfo.bytes());

        fileNode.put(
                "records",
                fileInfo.records());

        fileNode.put(
                "sha256",
                fileInfo.sha256());
    }

    private void writeManifest(
            final Path target,
            final ObjectNode manifest)
            throws IOException {

        final String content =
                objectMapper
                        .writerWithDefaultPrettyPrinter()
                        .writeValueAsString(manifest)
                + System.lineSeparator();

        final Path temporaryFile =
                target.resolveSibling(
                        target.getFileName()
                        + ".part-"
                        + UUID.randomUUID());

        try {
            Files.writeString(
                    temporaryFile,
                    content,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);

            moveAtomically(
                    temporaryFile,
                    target);
        } finally {
            Files.deleteIfExists(
                    temporaryFile);
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
            return MessageDigest.getInstance(
                    "SHA-256");
        } catch (final NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable.",
                    exception);
        }
    }

    private static String sha256(final Path file)
            throws IOException {

        final MessageDigest digest =
                createSha256Digest();

        try (var input =
                Files.newInputStream(file)) {

            final byte[] buffer =
                    new byte[64 * 1024];

            int bytesRead;

            while ((bytesRead = input.read(buffer)) != -1) {
                digest.update(
                        buffer,
                        0,
                        bytesRead);
            }
        }

        return HexFormat.of()
                .formatHex(
                        digest.digest());
    }

    private static void deleteRecursively(
            final Path directory)
            throws IOException {

        if (!Files.exists(directory)) {
            return;
        }

        try (var paths = Files.walk(directory)) {
            for (final Path path :
                    paths.sorted(
                            Comparator.reverseOrder())
                            .toList()) {

                Files.deleteIfExists(path);
            }
        }
    }

    private static void deleteDirectoryWhenEmpty(
            final Path directory)
            throws IOException {

        if (!Files.isDirectory(directory)) {
            return;
        }

        try (var entries =
                Files.list(directory)) {

            if (entries.findAny().isEmpty()) {
                Files.deleteIfExists(directory);
            }
        }
    }

    private static String progressPrefix(
            final int current,
            final int total,
            final OpenF1Dataset dataset) {

        return "["
                + current
                + "/"
                + total
                + "] "
                + dataset.endpoint()
                + ": ";
    }

    private static String humanBytes(final long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }

        final double kibibytes =
                bytes / 1024.0;

        if (kibibytes < 1024) {
            return String.format(
                    "%.1f KiB",
                    kibibytes);
        }

        final double mebibytes =
                kibibytes / 1024.0;

        return String.format(
                "%.1f MiB",
                mebibytes);
    }

    private record TimePartition(
            Instant start,
            Instant end) {

        private TimePartition {
            Objects.requireNonNull(
                    start,
                    "start");

            Objects.requireNonNull(
                    end,
                    "end");

            if (!start.isBefore(end)) {
                throw new IllegalArgumentException(
                        "Time partition start must precede end.");
            }
        }
    }
}
