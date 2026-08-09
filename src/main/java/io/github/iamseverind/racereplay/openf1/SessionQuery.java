package io.github.iamseverind.racereplay.openf1;

import java.util.Objects;

/**
 * Identifies one motorsport session to discover through OpenF1.
 *
 * @param year season year
 * @param countryName OpenF1 country name
 * @param sessionName OpenF1 session name
 */
public record SessionQuery(
        int year,
        String countryName,
        String sessionName) {

    /**
     * Validates and normalizes the query.
     */
    public SessionQuery {
        if (year < 2023) {
            throw new IllegalArgumentException(
                    "OpenF1 historical sessions begin in 2023.");
        }

        countryName = requireText(
                countryName,
                "countryName");

        sessionName = requireText(
                sessionName,
                "sessionName");
    }

    private static String requireText(
            final String value,
            final String fieldName) {

        final String normalized =
                Objects.requireNonNull(value, fieldName).strip();

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(
                    fieldName + " must not be empty.");
        }

        return normalized;
    }
}
