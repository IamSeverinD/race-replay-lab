package io.github.iamseverind.racereplay.app;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Selects driver labels that can be shown without close collisions.
 */
final class DriverLabelLayout {

    private DriverLabelLayout() {
    }

    /**
     * Returns labels that may be rendered for one screen frame.
     *
     * @param anchors driver positions in screen coordinates
     * @param minimumDistance minimum allowed distance in pixels
     * @return immutable set of visible driver codes
     */
    static Set<String> visibleLabels(
            final List<LabelAnchor> anchors,
            final double minimumDistance) {

        Objects.requireNonNull(
                anchors,
                "anchors");

        if (!Double.isFinite(minimumDistance)
                || minimumDistance <= 0.0) {

            throw new IllegalArgumentException(
                    "Minimum distance must be positive and finite.");
        }

        final Set<String> visibleCodes =
                new HashSet<>();

        for (final LabelAnchor anchor : anchors) {
            if (!visibleCodes.add(anchor.code())) {
                throw new IllegalArgumentException(
                        "Driver codes must be unique.");
            }
        }

        for (int driverIndex = 0;
                driverIndex < anchors.size();
                driverIndex++) {

            final LabelAnchor driver =
                    anchors.get(driverIndex);

            if (driver.selected()) {
                continue;
            }

            for (int otherIndex = 0;
                    otherIndex < anchors.size();
                    otherIndex++) {

                if (driverIndex == otherIndex) {
                    continue;
                }

                final LabelAnchor other =
                        anchors.get(otherIndex);

                final double distance =
                        Math.hypot(
                                driver.x() - other.x(),
                                driver.y() - other.y());

                if (distance < minimumDistance) {
                    visibleCodes.remove(
                            driver.code());

                    break;
                }
            }
        }

        return Set.copyOf(
                visibleCodes);
    }

    /**
     * One driver's projected screen position.
     *
     * @param code unique driver code
     * @param x horizontal screen coordinate
     * @param y vertical screen coordinate
     * @param selected whether this is the selected driver
     */
    record LabelAnchor(
            String code,
            double x,
            double y,
            boolean selected) {

        LabelAnchor {
            code = Objects.requireNonNull(
                    code,
                    "code");

            if (code.isBlank()) {
                throw new IllegalArgumentException(
                        "Driver code must not be blank.");
            }

            if (!Double.isFinite(x)
                    || !Double.isFinite(y)) {

                throw new IllegalArgumentException(
                        "Screen coordinates must be finite.");
            }
        }
    }
}
