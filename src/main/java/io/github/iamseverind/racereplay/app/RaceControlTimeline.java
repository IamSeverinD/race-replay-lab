package io.github.iamseverind.racereplay.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Provides race-control information for logical replay times.
 */
final class RaceControlTimeline {

    private static final int RECENT_EVENT_LIMIT = 4;

    private final List<Event> events;

    private RaceControlTimeline(
            final List<Event> events) {

        this.events =
                events.stream()
                        .sorted(
                                Comparator
                                        .comparingDouble(
                                                Event::replaySeconds)
                                        .thenComparingInt(
                                                Event::sequence))
                        .toList();
    }

    /**
     * Loads processed race-control events.
     *
     * @param eventsFile processed events file
     * @return loaded event timeline
     * @throws IOException when the file is invalid
     */
    static RaceControlTimeline load(
            final Path eventsFile)
            throws IOException {

        Objects.requireNonNull(
                eventsFile,
                "eventsFile");

        if (!Files.isRegularFile(eventsFile)) {
            return empty();
        }

        final JsonNode root =
                new ObjectMapper()
                        .readTree(
                                eventsFile.toFile());

        final JsonNode rows =
                root.path("events");

        if (!rows.isArray()) {
            throw new IOException(
                    "Race-control events must be an array.");
        }

        final List<Event> loaded =
                new ArrayList<>(
                        rows.size());

        for (final JsonNode row : rows) {
            loaded.add(
                    readEvent(row));
        }

        return new RaceControlTimeline(
                loaded);
    }

    /**
     * Creates an empty timeline.
     *
     * @return empty timeline
     */
    static RaceControlTimeline empty() {
        return new RaceControlTimeline(
                List.of());
    }

    /**
     * Returns the loaded event count.
     *
     * @return event count
     */
    int size() {
        return events.size();
    }

    /**
     * Derives the active state at one replay time.
     *
     * @param replaySeconds logical replay time
     * @return derived race-control state
     */
    State stateAt(
            final double replaySeconds) {

        if (!Double.isFinite(replaySeconds)
                || replaySeconds < 0.0) {

            throw new IllegalArgumentException(
                    "Replay time must be non-negative and finite.");
        }

        Phase phase = Phase.PRE_RACE;
        DrsState drsState = DrsState.UNKNOWN;
        Event latestEvent = null;

        final Set<Integer> yellowSectors =
                new HashSet<>();

        final List<Event> recentEvents =
                new ArrayList<>();

        for (final Event event : events) {
            if (event.replaySeconds()
                    > replaySeconds) {

                break;
            }

            latestEvent = event;
            recentEvents.add(event);

            if (recentEvents.size()
                    > RECENT_EVENT_LIMIT) {

                recentEvents.removeFirst();
            }

            drsState =
                    updateDrsState(
                            drsState,
                            event);

            phase =
                    updatePhase(
                            phase,
                            yellowSectors,
                            event);
        }

        return new State(
                phase,
                drsState,
                Optional.ofNullable(
                        latestEvent),
                recentEvents,
                yellowSectors);
    }

    private static Event readEvent(
            final JsonNode row)
            throws IOException {

        final JsonNode time =
                row.get("replay_seconds");

        final JsonNode sequence =
                row.get("sequence");

        if (time == null
                || !time.isNumber()) {

            throw new IOException(
                    "Race-control event lacks replay_seconds.");
        }

        if (sequence == null
                || !sequence.canConvertToInt()) {

            throw new IOException(
                    "Race-control event lacks sequence.");
        }

        return new Event(
                time.asDouble(),
                sequence.asInt(),
                optionalText(
                        row,
                        "category"),
                optionalText(
                        row,
                        "flag"),
                optionalText(
                        row,
                        "scope"),
                optionalInteger(
                        row,
                        "sector"),
                optionalInteger(
                        row,
                        "lap_number"),
                requiredText(
                        row,
                        "message"));
    }

    private static Phase updatePhase(
            final Phase current,
            final Set<Integer> yellowSectors,
            final Event event) {

        final String message =
                event.message()
                        .toUpperCase(
                                Locale.ROOT);

        final String flag =
                event.flag()
                        .toUpperCase(
                                Locale.ROOT);

        final String scope =
                event.scope()
                        .toUpperCase(
                                Locale.ROOT);

        if (message.contains(
                "SESSION STARTED")) {

            return Phase.GREEN;
        }

        if (message.contains(
                "VIRTUAL SAFETY CAR DEPLOYED")
                || message.contains(
                        "VSC DEPLOYED")) {

            return Phase.VIRTUAL_SAFETY_CAR;
        }

        if (message.contains(
                "SAFETY CAR DEPLOYED")) {

            return Phase.SAFETY_CAR;
        }

        if (message.contains("RED FLAG")
                || "RED".equals(flag)) {

            yellowSectors.clear();
            return Phase.RED_FLAG;
        }

        if (message.contains("CHEQUERED FLAG")
                || message.contains("CHECKERED FLAG")
                || message.contains("SESSION ENDED")
                || "CHEQUERED".equals(flag)
                || "CHECKERED".equals(flag)) {

            yellowSectors.clear();
            return Phase.CHECKERED;
        }

        if (current != Phase.PRE_RACE
                && "TRACK".equals(scope)
                && ("GREEN".equals(flag)
                || "CLEAR".equals(flag))) {

            yellowSectors.clear();
            return Phase.GREEN;
        }

        if (current != Phase.PRE_RACE
                && "TRACK".equals(scope)
                && ("YELLOW".equals(flag)
                || "DOUBLE YELLOW".equals(flag))) {

            return Phase.YELLOW;
        }

        if ("SECTOR".equals(scope)
                && ("YELLOW".equals(flag)
                || "DOUBLE YELLOW".equals(flag))) {

            if (event.sector() != null) {
                yellowSectors.add(
                        event.sector());
            }

            if (current != Phase.PRE_RACE
                    && current != Phase.SAFETY_CAR
                    && current != Phase.VIRTUAL_SAFETY_CAR
                    && current != Phase.RED_FLAG) {

                return Phase.YELLOW;
            }
        }

        if ("SECTOR".equals(scope)
                && "CLEAR".equals(flag)) {

            if (event.sector() != null) {
                yellowSectors.remove(
                        event.sector());
            }

            if (yellowSectors.isEmpty()
                    && current == Phase.YELLOW) {

                return Phase.GREEN;
            }
        }

        return current;
    }

    private static DrsState updateDrsState(
            final DrsState current,
            final Event event) {

        if (!"DRS".equalsIgnoreCase(
                event.category())) {

            return current;
        }

        final String message =
                event.message()
                        .toUpperCase(
                                Locale.ROOT);

        if (message.contains("DRS ENABLED")) {
            return DrsState.ENABLED;
        }

        if (message.contains("DRS DISABLED")) {
            return DrsState.DISABLED;
        }

        return current;
    }

    private static String requiredText(
            final JsonNode row,
            final String field)
            throws IOException {

        final JsonNode value =
                row.get(field);

        if (value == null
                || !value.isTextual()
                || value.asText().isBlank()) {

            throw new IOException(
                    "Invalid race-control field: "
                    + field);
        }

        return value.asText();
    }

    private static String optionalText(
            final JsonNode row,
            final String field) {

        final JsonNode value =
                row.get(field);

        if (value == null
                || value.isNull()) {

            return "";
        }

        return value.asText("");
    }

    private static Integer optionalInteger(
            final JsonNode row,
            final String field) {

        final JsonNode value =
                row.get(field);

        if (value == null
                || value.isNull()
                || !value.canConvertToInt()) {

            return null;
        }

        return value.asInt();
    }

    /**
     * High-level race phase.
     */
    enum Phase {
        PRE_RACE,
        GREEN,
        YELLOW,
        SAFETY_CAR,
        VIRTUAL_SAFETY_CAR,
        RED_FLAG,
        CHECKERED
    }

    /**
     * Race-control DRS availability.
     */
    enum DrsState {
        UNKNOWN,
        ENABLED,
        DISABLED
    }

    /**
     * One timestamped race-control event.
     *
     * @param replaySeconds event time
     * @param sequence deterministic sequence
     * @param category OpenF1 category
     * @param flag OpenF1 flag
     * @param scope event scope
     * @param sector track sector
     * @param lapNumber lap number
     * @param message displayed message
     */
    record Event(
            double replaySeconds,
            int sequence,
            String category,
            String flag,
            String scope,
            Integer sector,
            Integer lapNumber,
            String message) {

        Event {
            if (!Double.isFinite(replaySeconds)) {
                throw new IllegalArgumentException(
                        "Event time must be finite.");
            }

            if (sequence < 0) {
                throw new IllegalArgumentException(
                        "Event sequence must be non-negative.");
            }

            category =
                    Objects.requireNonNull(
                            category,
                            "category");

            flag =
                    Objects.requireNonNull(
                            flag,
                            "flag");

            scope =
                    Objects.requireNonNull(
                            scope,
                            "scope");

            message =
                    Objects.requireNonNull(
                            message,
                            "message");

            if (message.isBlank()) {
                throw new IllegalArgumentException(
                        "Event message must not be blank.");
            }
        }
    }

    /**
     * Derived race-control state.
     *
     * @param phase active track phase
     * @param drsState DRS availability
     * @param latestEvent latest event
     * @param recentEvents recent events
     * @param yellowSectors active local-yellow sectors
     */
    record State(
            Phase phase,
            DrsState drsState,
            Optional<Event> latestEvent,
            List<Event> recentEvents,
            Set<Integer> yellowSectors) {

        State {
            phase =
                    Objects.requireNonNull(
                            phase,
                            "phase");

            drsState =
                    Objects.requireNonNull(
                            drsState,
                            "drsState");

            latestEvent =
                    Objects.requireNonNull(
                            latestEvent,
                            "latestEvent");

            recentEvents =
                    List.copyOf(
                            Objects.requireNonNull(
                                    recentEvents,
                                    "recentEvents"));

            yellowSectors =
                    Set.copyOf(
                            Objects.requireNonNull(
                                    yellowSectors,
                                    "yellowSectors"));
        }
    }
}
