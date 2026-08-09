package io.github.iamseverind.racereplay.cache;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

/**
 * Resolves user-specific application and cache directories.
 */
public final class ApplicationCacheDirectories {

    private static final String APPLICATION_NAME =
            "Race Replay Lab";

    private ApplicationCacheDirectories() {
    }

    /**
     * Returns the OpenF1 cache root for the current operating system.
     *
     * @return user-specific OpenF1 cache root
     */
    public static Path openF1CacheRoot() {
        final String osName =
                System.getProperty("os.name");

        final Path userHome =
                Path.of(System.getProperty("user.home"));

        final String localAppData =
                System.getenv("LOCALAPPDATA");

        final String xdgDataHome =
                System.getenv("XDG_DATA_HOME");

        return resolveApplicationDataRoot(
                osName,
                userHome,
                localAppData,
                xdgDataHome)
                .resolve("cache")
                .resolve("openf1");
    }

    static Path resolveApplicationDataRoot(
            final String osName,
            final Path userHome,
            final String localAppData,
            final String xdgDataHome) {

        final String normalizedOs =
                Objects.requireNonNull(osName, "osName")
                        .toLowerCase(Locale.ROOT);

        final Path normalizedHome =
                Objects.requireNonNull(userHome, "userHome");

        if (normalizedOs.contains("mac")) {
            return normalizedHome
                    .resolve("Library")
                    .resolve("Application Support")
                    .resolve(APPLICATION_NAME);
        }

        if (normalizedOs.contains("win")) {
            if (localAppData != null
                    && !localAppData.isBlank()) {

                return Path.of(localAppData)
                        .resolve(APPLICATION_NAME);
            }

            return normalizedHome
                    .resolve("AppData")
                    .resolve("Local")
                    .resolve(APPLICATION_NAME);
        }

        if (xdgDataHome != null
                && !xdgDataHome.isBlank()) {

            return Path.of(xdgDataHome)
                    .resolve("race-replay-lab");
        }

        return normalizedHome
                .resolve(".local")
                .resolve("share")
                .resolve("race-replay-lab");
    }
}
