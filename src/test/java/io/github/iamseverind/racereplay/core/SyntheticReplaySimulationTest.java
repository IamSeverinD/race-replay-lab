package io.github.iamseverind.racereplay.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Tests the deterministic synthetic replay simulation.
 */
final class SyntheticReplaySimulationTest {

    /**
     * The spike must always expose twenty ranked drivers.
     */
    @Test
    void createsTwentyRankedDrivers() {
        final SyntheticReplaySimulation simulation =
                new SyntheticReplaySimulation();

        final ReplaySnapshot snapshot =
                simulation.snapshotAt(120.0);

        assertEquals(20, snapshot.drivers().size());

        final Set<Integer> positions = new HashSet<>();

        for (final DriverSnapshot driver : snapshot.drivers()) {
            positions.add(driver.position());

            assertTrue(driver.lapProgress() >= 0.0);
            assertTrue(driver.lapProgress() < 1.0);
            assertTrue(driver.speedKph() >= 0.0);
            assertTrue(driver.gear() >= 1);
            assertTrue(driver.gear() <= 8);
        }

        assertEquals(20, positions.size());
    }

    /**
     * Equal time inputs must create equal snapshots.
     */
    @Test
    void isDeterministicForEqualReplayTime() {
        final SyntheticReplaySimulation simulation =
                new SyntheticReplaySimulation();

        assertEquals(
                simulation.snapshotAt(42.5),
                simulation.snapshotAt(42.5));
    }

    /**
     * Negative replay times must be rejected.
     */
    @Test
    void rejectsNegativeReplayTime() {
        final SyntheticReplaySimulation simulation =
                new SyntheticReplaySimulation();

        assertThrows(
                IllegalArgumentException.class,
                () -> simulation.snapshotAt(-1.0));
    }
}
