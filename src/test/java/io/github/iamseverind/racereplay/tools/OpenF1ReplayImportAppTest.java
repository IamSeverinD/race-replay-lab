package io.github.iamseverind.racereplay.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.iamseverind.racereplay.openf1.SessionQuery;
import org.junit.jupiter.api.Test;

/**
 * Tests command-line parsing without contacting OpenF1.
 */
final class OpenF1ReplayImportAppTest {

    /**
     * Preserves quoted country and session names.
     */
    @Test
    void parsesSessionQuery() {
        assertEquals(
                new SessionQuery(
                        2026,
                        "Belgium",
                        "Race"),
                OpenF1ReplayImportApp.parseQuery(
                        new String[] {
                            "2026",
                            "Belgium",
                            "Race"
                        }));
    }

    /**
     * Rejects incomplete arguments before network access.
     */
    @Test
    void rejectsIncompleteQuery() {
        assertThrows(
                IllegalArgumentException.class,
                () -> OpenF1ReplayImportApp.parseQuery(
                        new String[] {
                            "2026",
                            "Belgium"
                        }));
    }
}
