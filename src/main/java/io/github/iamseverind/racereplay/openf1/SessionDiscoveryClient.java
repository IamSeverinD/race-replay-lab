package io.github.iamseverind.racereplay.openf1;

import java.io.IOException;

/**
 * Discovers session metadata from an external data source.
 */
@FunctionalInterface
public interface SessionDiscoveryClient {

    /**
     * Discovers exactly one session.
     *
     * @param query requested session
     * @return discovery result
     * @throws IOException when the response cannot be retrieved or parsed
     * @throws InterruptedException when the request is interrupted
     */
    SessionDiscoveryResult discoverSession(SessionQuery query)
            throws IOException, InterruptedException;
}
