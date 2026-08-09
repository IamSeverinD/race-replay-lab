package io.github.iamseverind.racereplay.openf1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Tests OpenF1 session discovery without internet access.
 */
final class OpenF1ClientTest {

    private static final String SESSION_JSON = """
            [
              {
                "circuit_key": 7,
                "circuit_short_name": "Example Circuit",
                "country_code": "BEL",
                "country_key": 16,
                "country_name": "Testland",
                "date_end": "2024-07-28T15:00:00+00:00",
                "date_start": "2024-07-28T13:00:00+00:00",
                "gmt_offset": "02:00:00",
                "is_cancelled": false,
                "location": "Example Circuit",
                "meeting_key": 1242,
                "session_key": 9574,
                "session_name": "Race",
                "session_type": "Race",
                "year": 2024
              }
            ]
            """;

    /**
     * Parses Example 2024 and constructs the expected URI.
     *
     * @throws Exception when discovery unexpectedly fails
     */
    @Test
    void discoversExample2024Race() throws Exception {
        final AtomicReference<URI> requestedUri =
                new AtomicReference<>();

        final OpenF1Client client =
                new OpenF1Client(
                        uri -> {
                            requestedUri.set(uri);
                            return SESSION_JSON;
                        },
                        new ObjectMapper());

        final SessionDiscoveryResult result =
                client.discoverSession(
                        new SessionQuery(
                                2024,
                                "Testland",
                                "Race"));

        assertEquals(
                9574,
                result.session().sessionKey());

        assertEquals(
                "Example Circuit",
                result.session().circuitShortName());

        assertTrue(
                requestedUri.get()
                        .toString()
                        .contains("year=2024"));

        assertTrue(
                requestedUri.get()
                        .toString()
                        .contains("country_name=Testland"));

        assertTrue(
                requestedUri.get()
                        .toString()
                        .contains("session_name=Race"));
    }

    /**
     * Empty API results must be reported as an error.
     */
    @Test
    void rejectsMissingSession() {
        final OpenF1Client client =
                new OpenF1Client(
                        uri -> "[]",
                        new ObjectMapper());

        assertThrows(
                IOException.class,
                () -> client.discoverSession(
                        new SessionQuery(
                                2024,
                                "Testland",
                                "Race")));
    }

    /**
     * Lists only sessions completed at the captured catalog time.
     *
     * @throws Exception when parsing unexpectedly fails
     */
    @Test
    void filtersCompletedCatalogSessions()
            throws Exception {

        final String catalogJson = """
                [
                  {
                    "circuit_short_name":"Past Circuit",
                    "country_name":"Testland",
                    "date_end":"2026-07-19T15:00:00Z",
                    "date_start":"2026-07-19T13:00:00Z",
                    "is_cancelled":false,
                    "location":"Past City",
                    "meeting_key":2001,
                    "session_key":3001,
                    "session_name":"Race",
                    "session_type":"Race",
                    "year":2026
                  },
                  {
                    "circuit_short_name":"Future Circuit",
                    "country_name":"Futureland",
                    "date_end":"2026-09-01T15:00:00Z",
                    "date_start":"2026-09-01T13:00:00Z",
                    "is_cancelled":false,
                    "location":"Future City",
                    "meeting_key":2002,
                    "session_key":3002,
                    "session_name":"Race",
                    "session_type":"Race",
                    "year":2026
                  },
                  {
                    "circuit_short_name":"Cancelled Circuit",
                    "country_name":"Testland",
                    "date_end":"2026-06-01T15:00:00Z",
                    "date_start":"2026-06-01T13:00:00Z",
                    "is_cancelled":true,
                    "location":"Cancelled City",
                    "meeting_key":2003,
                    "session_key":3003,
                    "session_name":"Race",
                    "session_type":"Race",
                    "year":2026
                  },
                  {
                    "circuit_short_name":"Qualifying Circuit",
                    "country_name":"Testland",
                    "date_end":"2026-07-18T15:00:00Z",
                    "date_start":"2026-07-18T14:00:00Z",
                    "is_cancelled":false,
                    "location":"Past City",
                    "meeting_key":2001,
                    "session_key":3004,
                    "session_name":"Qualifying",
                    "session_type":"Qualifying",
                    "year":2026
                  },
                  {
                    "circuit_short_name":"Sprint Circuit",
                    "country_name":"Sprintland",
                    "date_end":"2026-07-04T12:00:00Z",
                    "date_start":"2026-07-04T11:00:00Z",
                    "is_cancelled":false,
                    "location":"Sprint City",
                    "meeting_key":2005,
                    "session_key":3005,
                    "session_name":"Sprint",
                    "session_type":"Race",
                    "year":2026
                  }
                ]
                """;

        final OpenF1Client client =
                new OpenF1Client(
                        uri -> catalogJson,
                        new ObjectMapper());

        final List<OpenF1Session> sessions =
                client.listCompletedSessions(
                        2026,
                        Instant.parse(
                                "2026-08-07T12:00:00Z"));

        assertEquals(2, sessions.size());
        assertEquals(
                3001,
                sessions.getFirst()
                        .sessionKey());

        assertEquals(
                "Sprint",
                sessions.get(1)
                        .sessionName());
    }

    /**
     * Exact session discovery uses the stable OpenF1 key.
     *
     * @throws Exception when parsing unexpectedly fails
     */
    @Test
    void discoversExactSessionKey()
            throws Exception {

        final AtomicReference<URI> requestedUri =
                new AtomicReference<>();

        final OpenF1Client client =
                new OpenF1Client(
                        uri -> {
                            requestedUri.set(uri);
                            return SESSION_JSON;
                        },
                        new ObjectMapper());

        assertEquals(
                9574,
                client.discoverSession(9574)
                        .session()
                        .sessionKey());

        assertTrue(
                requestedUri.get()
                        .toString()
                        .endsWith("session_key=9574"));
    }
}
