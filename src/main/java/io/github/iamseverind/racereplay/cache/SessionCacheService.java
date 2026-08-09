package io.github.iamseverind.racereplay.cache;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.iamseverind.racereplay.openf1.OpenF1Session;
import io.github.iamseverind.racereplay.openf1.SessionDiscoveryClient;
import io.github.iamseverind.racereplay.openf1.SessionDiscoveryResult;
import io.github.iamseverind.racereplay.openf1.SessionQuery;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Downloads and safely stores OpenF1 session metadata.
 */
public final class SessionCacheService {

    private static final int CACHE_SCHEMA_VERSION = 1;

    private final Path openF1CacheRoot;
    private final SessionDiscoveryClient discoveryClient;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * Creates a session cache service.
     *
     * @param openF1CacheRoot OpenF1 cache root
     * @param discoveryClient session discovery source
     * @param objectMapper JSON mapper
     * @param clock clock used for manifest timestamps
     */
    public SessionCacheService(
            final Path openF1CacheRoot,
            final SessionDiscoveryClient discoveryClient,
            final ObjectMapper objectMapper,
            final Clock clock) {

        this.openF1CacheRoot =
                Objects.requireNonNull(
                        openF1CacheRoot,
                        "openF1CacheRoot");

        this.discoveryClient =
                Objects.requireNonNull(
                        discoveryClient,
                        "discoveryClient");

        this.objectMapper =
                Objects.requireNonNull(
                        objectMapper,
                        "objectMapper");

        this.clock =
                Objects.requireNonNull(clock, "clock");
    }

    /**
     * Downloads metadata and writes the raw response and manifest.
     *
     * @param query requested session
     * @return produced cache files
     * @throws IOException when network or file processing fails
     * @throws InterruptedException when the request is interrupted
     */
    public SessionCacheResult downloadSessionMetadata(
            final SessionQuery query)
            throws IOException, InterruptedException {

        final SessionDiscoveryResult discovery =
                discoveryClient.discoverSession(query);

        final OpenF1Session session =
                discovery.session();

        if (!session.supportsRaceReplay()) {
            throw new IOException(
                    "Only OpenF1 race and sprint sessions "
                    + "can be imported: "
                    + session.sessionName());
        }

        final Path cacheDirectory =
                openF1CacheRoot.resolve(
                        createDirectoryName(query, session));

        final Path rawDirectory =
                cacheDirectory.resolve("raw");

        final Path rawSessionFile =
                rawDirectory.resolve("session.json");

        final Path manifestFile =
                cacheDirectory.resolve("manifest.json");

        Files.createDirectories(rawDirectory);

        atomicWrite(
                rawSessionFile,
                discovery.rawJson());

        final String rawSessionSha256 =
                sha256(rawSessionFile);

        final ObjectNode manifest =
                createManifest(
                        query,
                        discovery,
                        rawSessionFile,
                        rawSessionSha256);

        preserveExistingDatasetMetadata(
                manifestFile,
                manifest);

        final String manifestJson =
                objectMapper
                        .writerWithDefaultPrettyPrinter()
                        .writeValueAsString(manifest)
                + System.lineSeparator();

        atomicWrite(
                manifestFile,
                manifestJson);

        return new SessionCacheResult(
                cacheDirectory,
                rawSessionFile,
                manifestFile,
                session);
    }

    private void preserveExistingDatasetMetadata(
            final Path manifestFile,
            final ObjectNode newManifest)
            throws IOException {

        if (!Files.isRegularFile(manifestFile)) {
            return;
        }

        final JsonNode existingRoot =
                objectMapper.readTree(
                        manifestFile.toFile());

        final JsonNode existingFilesNode =
                existingRoot.path("files");

        final JsonNode newFilesNode =
                newManifest.path("files");

        if (!(existingFilesNode
                instanceof ObjectNode existingFiles)
                || !(newFilesNode
                instanceof ObjectNode newFiles)) {

            return;
        }

        final Iterator<String> fieldNames =
                existingFiles.fieldNames();

        while (fieldNames.hasNext()) {
            final String fieldName =
                    fieldNames.next();

            if (!"session".equals(fieldName)) {
                newFiles.set(
                        fieldName,
                        existingFiles
                                .get(fieldName)
                                .deepCopy());
            }
        }
    }

    private ObjectNode createManifest(
            final SessionQuery query,
            final SessionDiscoveryResult discovery,
            final Path rawSessionFile,
            final String rawSessionSha256)
            throws IOException {

        final OpenF1Session session =
                discovery.session();

        final ObjectNode manifest =
                objectMapper.createObjectNode();

        manifest.put(
                "schema_version",
                CACHE_SCHEMA_VERSION);

        manifest.put(
                "cache_state",
                "session_metadata");

        manifest.put(
                "source",
                "OpenF1");

        manifest.put(
                "fetched_at",
                Instant.now(clock).toString());

        manifest.put(
                "request_uri",
                discovery.requestUri().toString());

        final ObjectNode queryNode =
                manifest.putObject("query");

        queryNode.put("year", query.year());
        queryNode.put(
                "country_name",
                query.countryName());

        queryNode.put(
                "session_name",
                query.sessionName());

        final ObjectNode sessionNode =
                manifest.putObject("session");

        sessionNode.put(
                "session_key",
                session.sessionKey());

        sessionNode.put(
                "meeting_key",
                session.meetingKey());

        sessionNode.put("year", session.year());

        sessionNode.put(
                "country_name",
                session.countryName());

        sessionNode.put(
                "session_name",
                session.sessionName());

        sessionNode.put(
                "session_type",
                session.sessionType());

        sessionNode.put(
                "circuit_short_name",
                session.circuitShortName());

        sessionNode.put(
                "location",
                session.location());

        sessionNode.put(
                "date_start",
                session.dateStart().toString());

        sessionNode.put(
                "date_end",
                session.dateEnd().toString());

        sessionNode.put(
                "cancelled",
                session.cancelled());

        final ObjectNode filesNode =
                manifest.putObject("files");

        final ObjectNode sessionFileNode =
                filesNode.putObject("session");

        sessionFileNode.put(
                "path",
                "raw/session.json");

        sessionFileNode.put(
                "bytes",
                Files.size(rawSessionFile));

        sessionFileNode.put(
                "sha256",
                rawSessionSha256);

        return manifest;
    }

    private static String createDirectoryName(
            final SessionQuery query,
            final OpenF1Session session) {

        return query.year()
                + "-"
                + slugify(query.countryName())
                + "-"
                + slugify(query.sessionName())
                + "-"
                + session.sessionKey();
    }

    private static String slugify(final String value) {
        final String withoutDiacritics =
                Normalizer.normalize(
                        value,
                        Normalizer.Form.NFD)
                        .replaceAll("\\p{M}", "");

        return withoutDiacritics
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
    }

    private static void atomicWrite(
            final Path target,
            final String content)
            throws IOException {

        Files.createDirectories(target.getParent());

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

            try {
                Files.move(
                        temporaryFile,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (
                    final AtomicMoveNotSupportedException exception) {

                Files.move(
                        temporaryFile,
                        target,
                        StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    private static String sha256(final Path file)
            throws IOException {

        try {
            final MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            final byte[] hash =
                    digest.digest(Files.readAllBytes(file));

            return HexFormat.of().formatHex(hash);
        } catch (final NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable.",
                    exception);
        }
    }
}
