package io.github.iamseverind.racereplay.app;

import java.util.Objects;

/**
 * Formats race-control states for the JavaFX user interface.
 */
final class RaceControlTextFormatter {

    private RaceControlTextFormatter() {
    }

    /**
     * Formats the active race phase.
     *
     * @param state race-control state
     * @return visible phase text
     */
    static String phase(
            final RaceControlTimeline.State state) {

        Objects.requireNonNull(
                state,
                "state");

        final String phaseText =
                switch (state.phase()) {
                    case PRE_RACE ->
                            "PRE-RACE";

                    case GREEN ->
                            "GREEN FLAG";

                    case YELLOW ->
                            "YELLOW FLAG";

                    case SAFETY_CAR ->
                            "SAFETY CAR";

                    case VIRTUAL_SAFETY_CAR ->
                            "VIRTUAL SAFETY CAR";

                    case RED_FLAG ->
                            "RED FLAG";

                    case CHECKERED ->
                            "CHEQUERED FLAG";
                };

        if (state.phase()
                != RaceControlTimeline.Phase.YELLOW
                || state.yellowSectors()
                        .isEmpty()) {

            return phaseText;
        }

        final StringBuilder sectors =
                new StringBuilder();

        for (final Integer sector
                : state.yellowSectors()
                        .stream()
                        .sorted()
                        .toList()) {

            if (!sectors.isEmpty()) {
                sectors.append(",");
            }

            sectors.append(sector);
        }

        return phaseText
                + " · SECTOR "
                + sectors;
    }

    /**
     * Formats global DRS availability.
     *
     * @param state race-control state
     * @return visible DRS text
     */
    static String drs(
            final RaceControlTimeline.State state) {

        Objects.requireNonNull(
                state,
                "state");

        return switch (state.drsState()) {
            case UNKNOWN ->
                    "DRS —";

            case ENABLED ->
                    "DRS ENABLED";

            case DISABLED ->
                    "DRS DISABLED";
        };
    }

    /**
     * Formats the latest race-control message.
     *
     * @param state race-control state
     * @return visible message
     */
    static String message(
            final RaceControlTimeline.State state) {

        Objects.requireNonNull(
                state,
                "state");

        return state.latestEvent()
                .map(
                        RaceControlTextFormatter
                                ::formatEvent)
                .orElse(
                        "NO RACE CONTROL DATA");
    }

    /**
     * Returns the JavaFX style for one race phase.
     *
     * @param phase active phase
     * @return JavaFX CSS declaration
     */
    static String phaseStyle(
            final RaceControlTimeline.Phase phase) {

        Objects.requireNonNull(
                phase,
                "phase");

        final String colors =
                switch (phase) {
                    case PRE_RACE ->
                            "-fx-background-color: #4b5563;"
                            + "-fx-text-fill: white;";

                    case GREEN ->
                            "-fx-background-color: #198754;"
                            + "-fx-text-fill: white;";

                    case YELLOW,
                            SAFETY_CAR,
                            VIRTUAL_SAFETY_CAR ->
                            "-fx-background-color: #f2c94c;"
                            + "-fx-text-fill: #111111;";

                    case RED_FLAG ->
                            "-fx-background-color: #d90429;"
                            + "-fx-text-fill: white;";

                    case CHECKERED ->
                            "-fx-background-color: white;"
                            + "-fx-text-fill: #111111;";
                };

        return colors
                + "-fx-font-weight: bold;"
                + "-fx-padding: 5 10 5 10;"
                + "-fx-background-radius: 4;";
    }

    private static String formatEvent(
            final RaceControlTimeline.Event event) {

        final String time =
                event.replaySeconds() < 0.0
                        ? "PRE-RACE"
                        : PlaybackTimelineSupport
                                .formatTime(
                                        event.replaySeconds());

        return time
                + " · "
                + event.message();
    }
}
