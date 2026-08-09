package io.github.iamseverind.racereplay.openf1;

import java.time.Instant;
import java.util.Objects;

/**
 * Normalized metadata describing one OpenF1 session.
 *
 * @param sessionKey unique OpenF1 session identifier
 * @param meetingKey unique OpenF1 meeting identifier
 * @param year season year
 * @param countryName country name
 * @param sessionName session name
 * @param sessionType session type
 * @param circuitShortName circuit name
 * @param location event location
 * @param dateStart session start in UTC
 * @param dateEnd session end in UTC
 * @param cancelled whether the session was cancelled
 */
public record OpenF1Session(
        int sessionKey,
        int meetingKey,
        int year,
        String countryName,
        String sessionName,
        String sessionType,
        String circuitShortName,
        String location,
        Instant dateStart,
        Instant dateEnd,
        boolean cancelled) {

    /**
     * Validates session metadata.
     */
    public OpenF1Session {
        if (sessionKey <= 0) {
            throw new IllegalArgumentException(
                    "sessionKey must be positive.");
        }

        if (meetingKey <= 0) {
            throw new IllegalArgumentException(
                    "meetingKey must be positive.");
        }

        if (year < 2023) {
            throw new IllegalArgumentException(
                    "year must be at least 2023.");
        }

        countryName =
                Objects.requireNonNull(countryName, "countryName");

        sessionName =
                Objects.requireNonNull(sessionName, "sessionName");

        sessionType =
                Objects.requireNonNull(sessionType, "sessionType");

        circuitShortName =
                Objects.requireNonNull(
                        circuitShortName,
                        "circuitShortName");

        location =
                Objects.requireNonNull(location, "location");

        dateStart =
                Objects.requireNonNull(dateStart, "dateStart");

        dateEnd =
                Objects.requireNonNull(dateEnd, "dateEnd");
    }

    /**
     * Indicates whether the session has race-replay semantics.
     *
     * <p>OpenF1 classifies both Grands Prix and Sprints as race sessions.</p>
     *
     * @return true for an importable race or sprint session
     */
    public boolean supportsRaceReplay() {
        return "Race".equalsIgnoreCase(
                sessionType);
    }
}
