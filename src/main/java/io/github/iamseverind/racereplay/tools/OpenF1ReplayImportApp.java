package io.github.iamseverind.racereplay.tools;

import io.github.iamseverind.racereplay.cache.ActiveReplaySelection;
import io.github.iamseverind.racereplay.cache.ApplicationCacheDirectories;
import io.github.iamseverind.racereplay.openf1.OpenF1ReplayImporter;
import io.github.iamseverind.racereplay.openf1.SessionQuery;

/**
 * Command-line entry point for optional OpenF1 replay imports.
 */
public final class OpenF1ReplayImportApp {

    private static final String CLEAR_ARGUMENT =
            "--clear";

    private OpenF1ReplayImportApp() {
    }

    /**
     * Imports one session or clears the active local selection.
     *
     * @param args year, country and session name, or {@code --clear}
     * @throws Exception when discovery, download or processing fails
     */
    public static void main(final String[] args)
            throws Exception {

        if (isClearRequest(args)) {
            clearSelection();
            return;
        }

        final SessionQuery query =
                parseQuery(args);

        System.out.println(
                "=== OPTIONAL OPENF1 REPLAY IMPORT ===");

        System.out.println(
                "Third-party data is written only to your local "
                + "application cache.");

        System.out.println(
                "Query: "
                + query.year()
                + " | "
                + query.countryName()
                + " | "
                + query.sessionName());

        final OpenF1ReplayImporter.ImportResult result =
                new OpenF1ReplayImporter()
                        .importAndActivate(
                                query,
                                System.out::println);

        printSummary(result);
    }

    static SessionQuery parseQuery(
            final String[] args) {

        if (args.length != 3) {
            throw new IllegalArgumentException(
                    "Usage: <year> <country> <session-name> "
                    + "or --clear");
        }

        return new SessionQuery(
                Integer.parseInt(args[0]),
                args[1],
                args[2]);
    }

    private static boolean isClearRequest(
            final String[] args) {

        return args.length == 1
                && CLEAR_ARGUMENT.equals(args[0]);
    }

    private static void clearSelection()
            throws Exception {

        final boolean cleared =
                ActiveReplaySelection.clear(
                        ApplicationCacheDirectories
                                .openF1CacheRoot());

        System.out.println(
                cleared
                        ? "OpenF1 replay selection cleared."
                        : "No OpenF1 replay was selected.");

        System.out.println(
                "The application will use synthetic demo data.");
    }

    private static void printSummary(
            final OpenF1ReplayImporter.ImportResult result) {

        System.out.println();
        System.out.println(
                "=== IMPORT COMPLETE AND ACTIVE ===");

        System.out.println(
                "Circuit: "
                + result.session()
                        .circuitShortName());

        System.out.println(
                "Cache: "
                + result.cacheDirectory());

        System.out.println(
                "Raw records: "
                + result.rawRecords());

        System.out.println(
                "Drivers: "
                + result.driverCount());

        System.out.println(
                "Frames: "
                + result.frameCount());

        System.out.println(
                "Enriched states: "
                + result.enrichedStates());

        System.out.println();
        System.out.println(
                "Restart Race Replay Lab to open this replay.");
    }
}
