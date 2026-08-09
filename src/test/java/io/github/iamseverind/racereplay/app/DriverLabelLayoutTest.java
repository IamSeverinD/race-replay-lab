package io.github.iamseverind.racereplay.app;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Tests collision-aware driver-label visibility.
 */
final class DriverLabelLayoutTest {

    private static final double MINIMUM_DISTANCE = 36.0;

    /**
     * Isolated driver labels remain visible.
     */
    @Test
    void keepsIsolatedLabelsVisible() {
        final Set<String> visible =
                DriverLabelLayout.visibleLabels(
                        List.of(
                                anchor(
                                        "A01",
                                        100.0,
                                        100.0,
                                        false),
                                anchor(
                                        "V01",
                                        200.0,
                                        100.0,
                                        false)),
                        MINIMUM_DISTANCE);

        assertTrue(
                visible.contains("A01"));

        assertTrue(
                visible.contains("V01"));
    }

    /**
     * Close non-selected drivers retain points but lose labels.
     */
    @Test
    void hidesLabelsInsideCloseDriverGroup() {
        final Set<String> visible =
                DriverLabelLayout.visibleLabels(
                        List.of(
                                anchor(
                                        "H01",
                                        100.0,
                                        100.0,
                                        false),
                                anchor(
                                        "H02",
                                        120.0,
                                        100.0,
                                        false),
                                anchor(
                                        "E01",
                                        220.0,
                                        100.0,
                                        false)),
                        MINIMUM_DISTANCE);

        assertFalse(
                visible.contains("H01"));

        assertFalse(
                visible.contains("H02"));

        assertTrue(
                visible.contains("E01"));
    }

    /**
     * Selected driver remains labelled inside a close group.
     */
    @Test
    void alwaysKeepsSelectedDriverVisible() {
        final Set<String> visible =
                DriverLabelLayout.visibleLabels(
                        List.of(
                                anchor(
                                        "A01",
                                        100.0,
                                        100.0,
                                        true),
                                anchor(
                                        "V01",
                                        110.0,
                                        100.0,
                                        false),
                                anchor(
                                        "N01",
                                        115.0,
                                        100.0,
                                        false)),
                        MINIMUM_DISTANCE);

        assertTrue(
                visible.contains("A01"));

        assertFalse(
                visible.contains("V01"));

        assertFalse(
                visible.contains("N01"));
    }

    /**
     * The exact threshold remains visible.
     */
    @Test
    void keepsLabelsAtExactMinimumDistance() {
        final Set<String> visible =
                DriverLabelLayout.visibleLabels(
                        List.of(
                                anchor(
                                        "V02",
                                        100.0,
                                        100.0,
                                        false),
                                anchor(
                                        "N02",
                                        136.0,
                                        100.0,
                                        false)),
                        MINIMUM_DISTANCE);

        assertTrue(
                visible.contains("V02"));

        assertTrue(
                visible.contains("N02"));
    }

    private static DriverLabelLayout.LabelAnchor anchor(
            final String code,
            final double x,
            final double y,
            final boolean selected) {

        return new DriverLabelLayout.LabelAnchor(
                code,
                x,
                y,
                selected);
    }
}
