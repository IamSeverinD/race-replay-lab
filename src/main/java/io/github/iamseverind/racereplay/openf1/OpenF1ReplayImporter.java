package io.github.iamseverind.racereplay.openf1;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.iamseverind.racereplay.cache.ApplicationCacheDirectories;
import io.github.iamseverind.racereplay.cache.RawCacheDownloadResult;
import io.github.iamseverind.racereplay.cache.RawCacheService;
import io.github.iamseverind.racereplay.cache.ReplayImportTransaction;
import io.github.iamseverind.racereplay.cache.SessionCacheResult;
import io.github.iamseverind.racereplay.cache.SessionCacheService;
import io.github.iamseverind.racereplay.core.CancellationSupport;
import io.github.iamseverind.racereplay.processed.ProcessedReplayBuildResult;
import io.github.iamseverind.racereplay.processed.ProcessedReplayCacheBuilder;
import io.github.iamseverind.racereplay.processed.ReplayTimelineBuildResult;
import io.github.iamseverind.racereplay.processed.ReplayTimelineBuilder;
import io.github.iamseverind.racereplay.processed.ReplayTimelineEnricher;
import io.github.iamseverind.racereplay.processed.ReplayTimelineEnrichmentResult;
import io.github.iamseverind.racereplay.processed.ReplayRaceControlEventBuilder;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Runs the complete local OpenF1 replay import transaction.
 */
public final class OpenF1ReplayImporter {

    /**
     * Downloads, processes and activates one explicitly selected session.
     *
     * <p>The active selection is changed only after every processing step
     * succeeds.</p>
     *
     * @param query selected completed session
     * @param progress progress message receiver
     * @return completed import summary
     * @throws Exception when download or processing fails
     */
    public ImportResult importAndActivate(
            final SessionQuery query,
            final Consumer<String> progress)
            throws Exception {

        Objects.requireNonNull(query, "query");

        final OpenF1Client client =
                new OpenF1Client();

        return importAndActivate(
                query,
                client,
                progress);
    }

    /**
     * Downloads an exact catalog selection and activates it locally.
     *
     * @param selectedSession exact completed catalog session
     * @param progress progress message receiver
     * @return completed import summary
     * @throws Exception when download or processing fails
     */
    public ImportResult importAndActivate(
            final OpenF1Session selectedSession,
            final Consumer<String> progress)
            throws Exception {

        final OpenF1Session session =
                Objects.requireNonNull(
                        selectedSession,
                        "selectedSession");

        final OpenF1Client client =
                new OpenF1Client();

        final SessionQuery query =
                new SessionQuery(
                        session.year(),
                        session.countryName(),
                        session.sessionName());

        return importAndActivate(
                query,
                ignored -> client.discoverSession(
                        session.sessionKey()),
                progress);
    }

    private ImportResult importAndActivate(
            final SessionQuery query,
            final SessionDiscoveryClient discoveryClient,
            final Consumer<String> progress)
            throws Exception {

        final Consumer<String> progressReceiver =
                Objects.requireNonNull(
                        progress,
                        "progress");

        final Path cacheRoot =
                ApplicationCacheDirectories
                        .openF1CacheRoot();

        final ObjectMapper objectMapper =
                new ObjectMapper();

        final Clock clock =
                Clock.systemUTC();

        try (ReplayImportTransaction transaction =
                ReplayImportTransaction.begin(cacheRoot)) {

            CancellationSupport.checkpoint();

            progressReceiver.accept(
                    "Discovering the selected session...");

            final SessionCacheResult sessionCache =
                    new SessionCacheService(
                            transaction.stagingCacheRoot(),
                            discoveryClient,
                            objectMapper,
                            clock)
                            .downloadSessionMetadata(
                                    query);

            CancellationSupport.checkpoint();

            progressReceiver.accept(
                    "Downloading OpenF1 datasets...");

            final RawCacheDownloadResult rawCache =
                    new RawCacheService(
                            new OpenF1RawDownloadClient(),
                            objectMapper,
                            clock)
                            .downloadAll(
                                    sessionCache,
                                    progressReceiver::accept);

            CancellationSupport.checkpoint();

            progressReceiver.accept(
                    "Building replay metadata...");

            final ProcessedReplayBuildResult processed =
                    new ProcessedReplayCacheBuilder(
                            objectMapper,
                            clock)
                            .build(
                                    sessionCache
                                            .cacheDirectory());

            CancellationSupport.checkpoint();

            progressReceiver.accept(
                    "Building the replay timeline...");

            final ReplayTimelineBuildResult timeline =
                    new ReplayTimelineBuilder(
                            objectMapper,
                            clock)
                            .build(
                                    sessionCache
                                            .cacheDirectory(),
                                    progressReceiver::accept);

            CancellationSupport.checkpoint();

            progressReceiver.accept(
                    "Adding race metadata...");

            final ReplayTimelineEnrichmentResult enrichment =
                    new ReplayTimelineEnricher(
                            objectMapper,
                            clock)
                            .enrich(
                                    sessionCache
                                            .cacheDirectory(),
                                    progressReceiver::accept);

            CancellationSupport.checkpoint();

            progressReceiver.accept(
                    "Normalizing race-control events...");

            new ReplayRaceControlEventBuilder(
                    objectMapper,
                    clock)
                    .build(
                            sessionCache
                                    .cacheDirectory());

            CancellationSupport.checkpoint();

            progressReceiver.accept(
                    "Publishing validated replay...");

            final Path publishedCache =
                    transaction.publish(
                            sessionCache.cacheDirectory());

            transaction.activatePublished();

            progressReceiver.accept(
                    "Import complete.");

            return new ImportResult(
                    sessionCache.session(),
                    publishedCache,
                    rawCache.totalRecords(),
                    processed.driverCount(),
                    timeline.frameCount(),
                    enrichment.totalStates());
        }
    }

    /**
     * Summary of a completed and activated local import.
     *
     * @param session imported OpenF1 session
     * @param cacheDirectory local session cache
     * @param rawRecords downloaded record count
     * @param driverCount processed driver count
     * @param frameCount replay frame count
     * @param enrichedStates enriched state count
     */
    public record ImportResult(
            OpenF1Session session,
            Path cacheDirectory,
            long rawRecords,
            int driverCount,
            int frameCount,
            long enrichedStates) {

        /**
         * Validates a completed import summary.
         */
        public ImportResult {
            session =
                    Objects.requireNonNull(
                            session,
                            "session");

            cacheDirectory =
                    Objects.requireNonNull(
                            cacheDirectory,
                            "cacheDirectory");
        }
    }
}
