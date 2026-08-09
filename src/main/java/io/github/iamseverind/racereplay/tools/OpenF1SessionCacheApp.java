package io.github.iamseverind.racereplay.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.iamseverind.racereplay.cache.ApplicationCacheDirectories;
import io.github.iamseverind.racereplay.cache.SessionCacheResult;
import io.github.iamseverind.racereplay.cache.SessionCacheService;
import io.github.iamseverind.racereplay.openf1.OpenF1Client;
import io.github.iamseverind.racereplay.openf1.SessionQuery;
import java.time.Clock;

/**
 * Downloads OpenF1 session metadata into the application cache.
 */
public final class OpenF1SessionCacheApp {

    private OpenF1SessionCacheApp() {
    }

    /**
     * Downloads one session.
     *
     * @param args optional year, country and session name
     * @throws Exception when the download or cache write fails
     */
    public static void main(final String[] args)
            throws Exception {

        final SessionQuery query =
                parseQuery(args);

        final SessionCacheService service =
                new SessionCacheService(
                        ApplicationCacheDirectories
                                .openF1CacheRoot(),
                        new OpenF1Client(),
                        new ObjectMapper(),
                        Clock.systemUTC());

        System.out.println(
                "=== OPENF1 SESSION CACHE ===");

        System.out.println(
                "Query: "
                + query.year()
                + " | "
                + query.countryName()
                + " | "
                + query.sessionName());

        final SessionCacheResult result =
                service.downloadSessionMetadata(query);

        System.out.println();
        System.out.println("Download successful.");
        System.out.println(
                "session_key="
                + result.session().sessionKey());

        System.out.println(
                "meeting_key="
                + result.session().meetingKey());

        System.out.println(
                "circuit="
                + result.session().circuitShortName());

        System.out.println(
                "start="
                + result.session().dateStart());

        System.out.println(
                "end="
                + result.session().dateEnd());

        System.out.println();
        System.out.println(
                "Cache directory:");

        System.out.println(
                result.cacheDirectory());

        System.out.println();
        System.out.println(
                "Raw session file:");

        System.out.println(
                result.rawSessionFile());

        System.out.println();
        System.out.println("Manifest:");

        System.out.println(
                result.manifestFile());
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
