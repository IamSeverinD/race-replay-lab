package io.github.iamseverind.racereplay.openf1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Tests raw OpenF1 endpoint definitions.
 */
final class OpenF1DatasetTest {

    /**
     * The replay requires ten raw datasets.
     */
    @Test
    void containsAllRequiredDatasets() {
        assertEquals(
                10,
                OpenF1Dataset.values().length);
    }

    /**
     * Endpoint URIs contain the selected session key.
     */
    @Test
    void buildsSessionEndpointUri() {
        final String uri =
                OpenF1Dataset.CAR_DATA
                        .buildUri(9574)
                        .toString();

        assertTrue(
                uri.endsWith(
                        "/car_data?session_key=9574"));

        assertEquals(
                "car-data.json",
                OpenF1Dataset.CAR_DATA.fileName());
    }

    /**
     * High-frequency data uses driver and time partitions.
     */
    @Test
    void buildsPartitionedLocationUri() {
        final String uri =
                OpenF1Dataset.LOCATION
                        .buildPartitionUri(
                                9574,
                                44,
                                Instant.parse(
                                        "2024-07-28T13:00:00Z"),
                                Instant.parse(
                                        "2024-07-28T13:30:00Z"))
                        .toString();

        assertTrue(
                OpenF1Dataset.LOCATION
                        .requiresPartitioning());

        assertTrue(
                OpenF1Dataset.CAR_DATA
                        .requiresPartitioning());

        assertTrue(
                uri.contains(
                        "driver_number=44"));

        assertTrue(
                uri.contains(
                        "date%253E")
                || uri.contains(
                        "date%3E"));

        assertTrue(
                uri.contains(
                        "2024-07-28T13%253A00%253A00Z")
                || uri.contains(
                        "2024-07-28T13%3A00%3A00Z"));
    }

    /**
     * Distinguishes optional metadata from essential replay datasets.
     */
    @Test
    void identifiesDatasetsThatMayBeEmpty() {
        assertTrue(
                OpenF1Dataset.INTERVALS
                        .allowsEmptyResponse());

        assertTrue(
                OpenF1Dataset.PIT
                        .allowsEmptyResponse());

        assertTrue(
                OpenF1Dataset.RACE_CONTROL
                        .allowsEmptyResponse());

        assertTrue(
                OpenF1Dataset.WEATHER
                        .allowsEmptyResponse());

        assertFalse(
                OpenF1Dataset.DRIVERS
                        .allowsEmptyResponse());

        assertFalse(
                OpenF1Dataset.LAPS
                        .allowsEmptyResponse());

        assertFalse(
                OpenF1Dataset.LOCATION
                        .allowsEmptyResponse());

        assertFalse(
                OpenF1Dataset.CAR_DATA
                        .allowsEmptyResponse());
    }
}
