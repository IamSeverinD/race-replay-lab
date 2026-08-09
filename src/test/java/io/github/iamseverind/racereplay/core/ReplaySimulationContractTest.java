package io.github.iamseverind.racereplay.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.Test;

/**
 * Tests the common replay-simulation contract.
 */
final class ReplaySimulationContractTest {

    /**
     * Uses the synthetic simulation through the common interface.
     */
    @Test
    void syntheticSimulationImplementsReplayContract() {
        final ReplaySimulation simulation =
                new SyntheticReplaySimulation();

        assertInstanceOf(
                SyntheticReplaySimulation.class,
                simulation);

        final ReplaySnapshot snapshot =
                simulation.snapshotAt(12.5);

        assertEquals(
                12.5,
                snapshot.replaySeconds());

        assertEquals(
                20,
                snapshot.drivers().size());
    }
}
