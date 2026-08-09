package io.github.iamseverind.racereplay.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests optional OpenF1 coordinates in driver snapshots.
 */
final class DriverSnapshotLocationTest {

    /**
     * The compatibility constructor has no real location.
     */
    @Test
    void compatibilityConstructorHasNoRealLocation() {
        final DriverSnapshot snapshot =
                new DriverSnapshot(
                        1,
                        "A01",
                        "Apex Dynamics",
                        "MEDIUM",
                        0.5,
                        10.5,
                        300.0,
                        8,
                        true);

        assertFalse(snapshot.locationValid());
        assertEquals(0, snapshot.locationX());
        assertEquals(0, snapshot.locationY());
        assertEquals(0, snapshot.locationZ());
    }

    /**
     * The complete constructor preserves OpenF1 coordinates.
     */
    @Test
    void completeConstructorPreservesRealLocation() {
        final DriverSnapshot snapshot =
                new DriverSnapshot(
                        5,
                        "A01",
                        "Apex Dynamics",
                        "HARD",
                        0.25,
                        21.25,
                        313.0,
                        8,
                        false,
                        3467,
                        235,
                        3940,
                        true);

        assertTrue(snapshot.locationValid());
        assertEquals(3467, snapshot.locationX());
        assertEquals(235, snapshot.locationY());
        assertEquals(3940, snapshot.locationZ());
    }
}
