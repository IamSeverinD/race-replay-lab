package io.github.iamseverind.racereplay.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.iamseverind.racereplay.cache.ApplicationCacheDirectories;
import io.github.iamseverind.racereplay.cache.RawCacheDownloadResult;
import io.github.iamseverind.racereplay.cache.RawCacheService;
import io.github.iamseverind.racereplay.cache.SessionCacheResult;
import io.github.iamseverind.racereplay.cache.SessionCacheService;
import io.github.iamseverind.racereplay.openf1.OpenF1Client;
import io.github.iamseverind.racereplay.openf1.OpenF1RawDownloadClient;
import io.github.iamseverind.racereplay.openf1.SessionQuery;
import java.time.Clock;

/**
 * Downloads a complete OpenF1 raw cache.
 */
public final class OpenF1RawCacheApp {

    private OpenF1RawCacheApp() {
    }

    /**
     * Downloads all raw datasets for one session.
     *
     * @param args optional year, country and session name
     * @throws Exception when discovery or download fails
     */
    public static void main(final String[] args)
            throws Exception {

        final SessionQuery query =
                parseQuery(args);

        final ObjectMapper objectMapper =
                new ObjectMapper();

        final Clock clock =
                Clock.systemUTC();

        final SessionCacheService sessionService =
                new SessionCacheService(
                        ApplicationCacheDirectories
                                .openF1CacheRoot(),
                        new OpenF1Client(),
                        objectMapper,
                        clock);

        System.out.println(
                "=== OPENF1 COMPLETE RAW CACHE ===");

        System.out.println(
                "Query: "
                + query.year()
                + " | "
                + query.countryName()
                + " | "
                + query.sessionName());

        System.out.println();
        System.out.println(
                "Discovering session...");

        final SessionCacheResult sessionCache =
                sessionService
                        .downloadSessionMetadata(query);

        System.out.println(
                "session_key="
                + sessionCache
                        .session()
                        .sessionKey());

        System.out.println(
                "cache="
                + sessionCache.cacheDirectory());

        System.out.println();
        System.out.println(
                "Downloading raw datasets...");

        final RawCacheService rawCacheService =
                new RawCacheService(
                        new OpenF1RawDownloadClient(),
                        objectMapper,
                        clock);

        final RawCacheDownloadResult result =
                rawCacheService.downloadAll(
                        sessionCache,
                        System.out::println);

        System.out.println();
        System.out.println(
                "=== RAW CACHE COMPLETE ===");

        System.out.println(
                "Downloaded datasets: "
                + result.downloadedDatasets());

        System.out.println(
                "Reused datasets: "
                + result.reusedDatasets());

        System.out.println(
                "Total bytes: "
                + result.totalBytes());

        System.out.println(
                "Total records: "
                + result.totalRecords());

        System.out.println(
                "Cache directory:");

        System.out.println(
                result.cacheDirectory());
    }

    private static SessionQuery parseQuery(
            final String[] args) {

        if (args.length != 3) {
            throw new IllegalArgumentException(
                    "Usage: <year> <country> <session-name>");
        }

        return new SessionQuery(
                Integer.parseInt(args[0]),
                args[1],
                args[2]);
    }
}
