package io.github.iamseverind.racereplay.app;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.iamseverind.racereplay.core.DriverSnapshot;
import org.junit.jupiter.api.Test;

/**
 * Tests user-facing telemetry formatting.
 */
final class TelemetryTextFormatterTest {

    /**
     * Formats available real telemetry.
     */
    @Test
    void formatsAvailableTelemetry() {
        final DriverSnapshot driver =
                new DriverSnapshot(
                        2,
                        "A01",
                        "Apex Dynamics",
                        "MEDIUM",
                        true,
                        0.5,
                        10.5,
                        308.0,
                        12_000,
                        8,
                        100,
                        0,
                        true,
                        true,
                        11,
                        true,
                        1.842,
                        true,
                        0.394,
                        true,
                        100,
                        200,
                        300,
                        true);

        assertEquals(
                "308 km/h",
                TelemetryTextFormatter.speed(driver));

        assertEquals(
                "12000 rpm",
                TelemetryTextFormatter.rpm(driver));

        assertEquals(
                "8",
                TelemetryTextFormatter.gear(driver));

        assertEquals(
                "100 %",
                TelemetryTextFormatter.throttle(driver));

        assertEquals(
                "OFF",
                TelemetryTextFormatter.brake(driver));

        assertEquals(
                "OPEN",
                TelemetryTextFormatter.drs(
                        driver,
                        true));

        assertEquals(
                "N/A · ACTIVE AERO",
                TelemetryTextFormatter.drs(
                        driver,
                        false));

        assertEquals(
                "MEDIUM",
                TelemetryTextFormatter.tyre(driver));

        assertEquals(
                "11 / 44",
                TelemetryTextFormatter.lap(
                        driver,
                        44));

        assertEquals(
                "+1.842 s",
                TelemetryTextFormatter.gap(driver));

        assertEquals(
                "+0.394 s",
                TelemetryTextFormatter.interval(driver));
    }

    /**
     * Uses placeholders for unavailable values.
     */
    @Test
    void formatsUnavailableTelemetry() {
        final DriverSnapshot driver =
                new DriverSnapshot(
                        5,
                        "A01",
                        "Apex Dynamics",
                        "UNKNOWN",
                        false,
                        0.0,
                        0.0,
                        0.0,
                        0,
                        0,
                        0,
                        0,
                        false,
                        false,
                        0,
                        false,
                        Double.NaN,
                        false,
                        Double.NaN,
                        false,
                        0,
                        0,
                        0,
                        false);

        assertEquals(
                "—",
                TelemetryTextFormatter.speed(driver));

        assertEquals(
                "—",
                TelemetryTextFormatter.rpm(driver));

        assertEquals(
                "—",
                TelemetryTextFormatter.gear(driver));

        assertEquals(
                "—",
                TelemetryTextFormatter.throttle(driver));

        assertEquals(
                "—",
                TelemetryTextFormatter.brake(driver));

        assertEquals(
                "—",
                TelemetryTextFormatter.drs(
                        driver,
                        true));

        assertEquals(
                "—",
                TelemetryTextFormatter.tyre(driver));

        assertEquals(
                "— / —",
                TelemetryTextFormatter.lap(
                        driver,
                        0));

        assertEquals(
                "—",
                TelemetryTextFormatter.gap(driver));

        assertEquals(
                "—",
                TelemetryTextFormatter.interval(driver));
    }
}
