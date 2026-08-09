package io.github.iamseverind.racereplay.openf1;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Minimal OpenF1 client used for session discovery.
 */
public final class OpenF1Client implements SessionDiscoveryClient {

    private static final String SESSIONS_ENDPOINT =
            "https://api.openf1.org/v1/sessions";

    private static final Duration REQUEST_TIMEOUT =
            Duration.ofSeconds(60);

    private final JsonHttpTransport transport;
    private final ObjectMapper objectMapper;

    /**
     * Creates a production client using the JDK HTTP client.
     */
    public OpenF1Client() {
        this(
                new JdkJsonHttpTransport(),
                new ObjectMapper());
    }

    OpenF1Client(
            final JsonHttpTransport transport,
            final ObjectMapper objectMapper) {

        this.transport =
                Objects.requireNonNull(transport, "transport");

        this.objectMapper =
                Objects.requireNonNull(objectMapper, "objectMapper");
    }

    /**
     * Retrieves and validates one OpenF1 session.
     *
     * @param query requested session
     * @return normalized session and raw JSON
     * @throws IOException when retrieval or parsing fails
     * @throws InterruptedException when the request is interrupted
     */
    @Override
    public SessionDiscoveryResult discoverSession(
            final SessionQuery query)
            throws IOException, InterruptedException {

        Objects.requireNonNull(query, "query");

        final URI requestUri = buildSessionUri(query);
        final SessionDiscoveryResult discovery =
                discoverSingleSession(requestUri);

        final OpenF1Session session =
                discovery.session();

        if (session.year() != query.year()
                || !session.countryName().equalsIgnoreCase(
                        query.countryName())
                || !session.sessionName().equalsIgnoreCase(
                        query.sessionName())) {

            throw new IOException(
                    "OpenF1 returned no matching session for "
                    + query.year()
                    + " "
                    + query.countryName()
                    + " "
                    + query.sessionName()
                    + ".");
        }

        return discovery;
    }

    /**
     * Retrieves one exact OpenF1 session by its stable key.
     *
     * @param sessionKey OpenF1 session key
     * @return normalized session and raw JSON
     * @throws IOException when retrieval or parsing fails
     * @throws InterruptedException when the request is interrupted
     */
    public SessionDiscoveryResult discoverSession(
            final int sessionKey)
            throws IOException, InterruptedException {

        if (sessionKey <= 0) {
            throw new IllegalArgumentException(
                    "Session key must be positive.");
        }

        return discoverSingleSession(
                buildSessionKeyUri(
                        sessionKey));
    }

    private SessionDiscoveryResult discoverSingleSession(
            final URI requestUri)
            throws IOException, InterruptedException {

        final String rawJson = transport.get(requestUri);
        final JsonNode root = objectMapper.readTree(rawJson);

        if (!root.isArray()) {
            throw new IOException(
                    "OpenF1 sessions response is not a JSON array.");
        }

        final List<OpenF1Session> matches =
                new ArrayList<>();

        for (final JsonNode sessionNode : root) {
            matches.add(
                    parseSession(sessionNode));
        }

        if (matches.isEmpty()) {
            throw new IOException(
                    "OpenF1 returned no matching session.");
        }

        if (matches.size() > 1) {
            throw new IOException(
                    "OpenF1 returned multiple matching sessions: "
                    + matches.size());
        }

        return new SessionDiscoveryResult(
                matches.getFirst(),
                requestUri,
                rawJson);
    }

    /**
     * Lists completed, non-cancelled race sessions for one season.
     *
     * @param year requested season year
     * @param completedBefore catalog cutoff captured by the caller
     * @return completed sessions ordered newest first
     * @throws IOException when retrieval or parsing fails
     * @throws InterruptedException when the request is interrupted
     */
    public List<OpenF1Session> listCompletedSessions(
            final int year,
            final Instant completedBefore)
            throws IOException, InterruptedException {

        if (year < 2023) {
            throw new IllegalArgumentException(
                    "OpenF1 historical sessions begin in 2023.");
        }

        Objects.requireNonNull(
                completedBefore,
                "completedBefore");

        final String rawJson =
                transport.get(
                        buildYearUri(year));

        final JsonNode root =
                objectMapper.readTree(rawJson);

        if (!root.isArray()) {
            throw new IOException(
                    "OpenF1 sessions response is not a JSON array.");
        }

        final List<OpenF1Session> completed =
                new ArrayList<>();

        for (final JsonNode sessionNode : root) {
            final OpenF1Session session =
                    parseSession(sessionNode);

            if (session.year() == year
                    && !session.cancelled()
                    && session.supportsRaceReplay()
                    && !session.dateEnd()
                            .isAfter(completedBefore)) {

                completed.add(session);
            }
        }

        completed.sort(
                Comparator.comparing(
                        OpenF1Session::dateEnd)
                        .reversed());

        return List.copyOf(completed);
    }

    static URI buildSessionUri(final SessionQuery query) {
        final String country =
                encode(query.countryName());

        final String session =
                encode(query.sessionName());

        return URI.create(
                SESSIONS_ENDPOINT
                + "?year="
                + query.year()
                + "&country_name="
                + country
                + "&session_name="
                + session);
    }

    static URI buildYearUri(final int year) {
        return URI.create(
                SESSIONS_ENDPOINT
                + "?year="
                + year);
    }

    static URI buildSessionKeyUri(
            final int sessionKey) {

        return URI.create(
                SESSIONS_ENDPOINT
                + "?session_key="
                + sessionKey);
    }

    private OpenF1Session parseSession(
            final JsonNode node)
            throws IOException {

        return new OpenF1Session(
                requiredInt(node, "session_key"),
                requiredInt(node, "meeting_key"),
                requiredInt(node, "year"),
                requiredText(node, "country_name"),
                requiredText(node, "session_name"),
                requiredText(node, "session_type"),
                requiredText(node, "circuit_short_name"),
                requiredText(node, "location"),
                requiredInstant(node, "date_start"),
                requiredInstant(node, "date_end"),
                node.path("is_cancelled").asBoolean(false));
    }

    private static int requiredInt(
            final JsonNode node,
            final String field)
            throws IOException {

        final JsonNode value = node.get(field);

        if (value == null || !value.canConvertToInt()) {
            throw new IOException(
                    "Missing or invalid OpenF1 field: " + field);
        }

        return value.intValue();
    }

    private static String requiredText(
            final JsonNode node,
            final String field)
            throws IOException {

        final JsonNode value = node.get(field);

        if (value == null
                || !value.isTextual()
                || value.textValue().isBlank()) {

            throw new IOException(
                    "Missing or invalid OpenF1 field: " + field);
        }

        return value.textValue();
    }

    private static Instant requiredInstant(
            final JsonNode node,
            final String field)
            throws IOException {

        final String value = requiredText(node, field);

        try {
            return Instant.parse(value);
        } catch (final RuntimeException exception) {
            throw new IOException(
                    "Invalid OpenF1 timestamp in field "
                    + field
                    + ": "
                    + value,
                    exception);
        }
    }

    private static String encode(final String value) {
        return URLEncoder.encode(
                value,
                StandardCharsets.UTF_8);
    }

    @FunctionalInterface
    interface JsonHttpTransport {

        String get(URI uri)
                throws IOException, InterruptedException;
    }

    private static final class JdkJsonHttpTransport
            implements JsonHttpTransport {

        private final HttpClient httpClient =
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(15))
                        .followRedirects(
                                HttpClient.Redirect.NORMAL)
                        .build();

        @Override
        public String get(final URI uri)
                throws IOException, InterruptedException {

            final HttpRequest request =
                    HttpRequest.newBuilder(uri)
                            .timeout(REQUEST_TIMEOUT)
                            .header(
                                    "Accept",
                                    "application/json")
                            .header(
                                    "User-Agent",
                                    "Race-Replay-Lab/0.1")
                            .GET()
                            .build();

            final HttpResponse<String> response =
                    httpClient.send(
                            request,
                            HttpResponse.BodyHandlers.ofString(
                                    StandardCharsets.UTF_8));

            if (response.statusCode() < 200
                    || response.statusCode() >= 300) {

                throw new IOException(
                        "OpenF1 request failed with HTTP "
                        + response.statusCode()
                        + ": "
                        + summarize(response.body()));
            }

            return response.body();
        }

        private static String summarize(final String body) {
            if (body == null || body.isBlank()) {
                return "empty response body";
            }

            final String singleLine =
                    body.replaceAll("\\s+", " ").strip();

            if (singleLine.length() <= 300) {
                return singleLine;
            }

            return singleLine.substring(0, 300) + "...";
        }
    }
}
