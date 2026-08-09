package io.github.iamseverind.racereplay.openf1;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;

/**
 * Raw OpenF1 datasets required by the replay application.
 */
public enum OpenF1Dataset {

    DRIVERS(
            "drivers",
            "drivers.json"),

    LAPS(
            "laps",
            "laps.json"),

    INTERVALS(
            "intervals",
            "intervals.json"),

    POSITION(
            "position",
            "position.json"),

    STINTS(
            "stints",
            "stints.json"),

    PIT(
            "pit",
            "pit.json"),

    RACE_CONTROL(
            "race_control",
            "race-control.json"),

    WEATHER(
            "weather",
            "weather.json"),

    LOCATION(
            "location",
            "location.json"),

    CAR_DATA(
            "car_data",
            "car-data.json");

    private static final String API_BASE_URI =
            "https://api.openf1.org/v1/";

    private final String endpoint;
    private final String fileName;

    OpenF1Dataset(
            final String endpoint,
            final String fileName) {

        this.endpoint = endpoint;
        this.fileName = fileName;
    }

    /**
     * Returns the OpenF1 endpoint name.
     *
     * @return endpoint name
     */
    public String endpoint() {
        return endpoint;
    }

    /**
     * Returns the local raw-cache filename.
     *
     * @return cache filename
     */
    public String fileName() {
        return fileName;
    }

    /**
     * Returns the manifest key.
     *
     * @return manifest key
     */
    public String manifestKey() {
        return endpoint;
    }

    /**
     * Indicates whether this endpoint must be partitioned.
     *
     * @return true for high-frequency datasets
     */
    public boolean requiresPartitioning() {
        return this == LOCATION
                || this == CAR_DATA;
    }

    /**
     * Indicates whether OpenF1 may legitimately return no rows.
     *
     * @return true when a 404 no-results response represents an empty array
     */
    public boolean allowsEmptyResponse() {
        return this == INTERVALS
                || this == PIT
                || this == RACE_CONTROL
                || this == WEATHER;
    }

    /**
     * Builds the complete-session API URI.
     *
     * @param sessionKey OpenF1 session key
     * @return endpoint URI
     */
    public URI buildUri(final int sessionKey) {
        validateSessionKey(sessionKey);

        return URI.create(
                API_BASE_URI
                + endpoint
                + "?session_key="
                + sessionKey);
    }

    /**
     * Builds a driver and time partition URI.
     *
     * <p>The encoded query represents:
     * {@code date>=start} and {@code date<end}.</p>
     *
     * @param sessionKey OpenF1 session key
     * @param driverNumber provider-specific driver number
     * @param startInclusive partition start in UTC
     * @param endExclusive partition end in UTC
     * @return partitioned endpoint URI
     */
    public URI buildPartitionUri(
            final int sessionKey,
            final int driverNumber,
            final Instant startInclusive,
            final Instant endExclusive) {

        validateSessionKey(sessionKey);

        if (driverNumber <= 0) {
            throw new IllegalArgumentException(
                    "driverNumber must be positive.");
        }

        Objects.requireNonNull(
                startInclusive,
                "startInclusive");

        Objects.requireNonNull(
                endExclusive,
                "endExclusive");

        if (!startInclusive.isBefore(endExclusive)) {
            throw new IllegalArgumentException(
                    "Partition start must be before its end.");
        }

        return URI.create(
                API_BASE_URI
                + endpoint
                + "?session_key="
                + sessionKey
                + "&driver_number="
                + driverNumber
                + "&date%3E="
                + encode(startInclusive.toString())
                + "&date%3C"
                + encode(endExclusive.toString()));
    }

    private static void validateSessionKey(
            final int sessionKey) {

        if (sessionKey <= 0) {
            throw new IllegalArgumentException(
                    "sessionKey must be positive.");
        }
    }

    private static String encode(final String value) {
        return URLEncoder.encode(
                value,
                StandardCharsets.UTF_8);
    }
}
