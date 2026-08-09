package io.github.iamseverind.racereplay.app;

import io.github.iamseverind.racereplay.core.DriverSnapshot;
import java.util.Locale;

/**
 * Formats optional driver telemetry for the JavaFX interface.
 */
final class TelemetryTextFormatter {

    private TelemetryTextFormatter() {
    }

    static String speed(
            final DriverSnapshot driver) {

        if (!driver.telemetryValid()) {
            return "—";
        }

        return String.format(
                Locale.ROOT,
                "%.0f km/h",
                driver.speedKph());
    }

    static String rpm(
            final DriverSnapshot driver) {

        if (!driver.telemetryValid()) {
            return "—";
        }

        return driver.rpm() + " rpm";
    }

    static String gear(
            final DriverSnapshot driver) {

        if (!driver.telemetryValid()) {
            return "—";
        }

        return driver.gear() == 0
                ? "N"
                : Integer.toString(driver.gear());
    }

    static String throttle(
            final DriverSnapshot driver) {

        if (!driver.telemetryValid()) {
            return "—";
        }

        return driver.throttle() + " %";
    }

    static String brake(
            final DriverSnapshot driver) {

        if (!driver.telemetryValid()) {
            return "—";
        }

        return driver.brake() == 0
                ? "OFF"
                : driver.brake() + " %";
    }

    static String drs(
            final DriverSnapshot driver,
            final boolean drsAvailable) {

        if (!drsAvailable) {
            return "N/A · ACTIVE AERO";
        }

        if (!driver.telemetryValid()) {
            return "—";
        }

        return driver.drs()
                ? "OPEN"
                : "CLOSED";
    }

    static String tyre(
            final DriverSnapshot driver) {

        return driver.tyreValid()
                ? driver.tyre()
                : "—";
    }

    static String lap(
            final DriverSnapshot driver,
            final int scheduledLaps) {

        final String total =
                scheduledLaps > 0
                        ? Integer.toString(
                                scheduledLaps)
                        : "—";

        if (!driver.lapValid()) {
            return "— / " + total;
        }

        return driver.lapNumber()
                + " / "
                + total;
    }

    static String gap(
            final DriverSnapshot driver) {

        if (driver.position() == 1) {
            return "LEADER";
        }

        if (!driver.gapValid()) {
            return "—";
        }

        return String.format(
                Locale.ROOT,
                "+%.3f s",
                driver.gapToLeaderSeconds());
    }

    static String interval(
            final DriverSnapshot driver) {

        if (driver.position() == 1
                || !driver.intervalValid()) {

            return "—";
        }

        return String.format(
                Locale.ROOT,
                "+%.3f s",
                driver.intervalSeconds());
    }
}
