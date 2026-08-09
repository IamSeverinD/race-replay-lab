package io.github.iamseverind.racereplay.openf1;

import java.net.URI;
import java.util.Objects;

/**
 * Contains normalized metadata and the original OpenF1 response.
 *
 * @param session normalized session
 * @param requestUri exact request URI
 * @param rawJson unmodified JSON response
 */
public record SessionDiscoveryResult(
        OpenF1Session session,
        URI requestUri,
        String rawJson) {

    /**
     * Validates the discovery result.
     */
    public SessionDiscoveryResult {
        session = Objects.requireNonNull(session, "session");
        requestUri = Objects.requireNonNull(requestUri, "requestUri");
        rawJson = Objects.requireNonNull(rawJson, "rawJson");
    }
}
