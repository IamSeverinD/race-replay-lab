package io.github.iamseverind.racereplay.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Tests platform-specific application data directories.
 */
final class ApplicationCacheDirectoriesTest {

    /**
     * macOS uses Library/Application Support.
     */
    @Test
    void resolvesMacApplicationDataDirectory() {
        assertEquals(
                Path.of(
                        "/Users/tester/Library/Application Support/"
                        + "Race Replay Lab"),
                ApplicationCacheDirectories
                        .resolveApplicationDataRoot(
                                "Mac OS X",
                                Path.of("/Users/tester"),
                                null,
                                null));
    }

    /**
     * Windows prefers LOCALAPPDATA.
     */
    @Test
    void resolvesWindowsApplicationDataDirectory() {
        assertEquals(
                Path.of(
                        "C:/Users/tester/AppData/Local/"
                        + "Race Replay Lab"),
                ApplicationCacheDirectories
                        .resolveApplicationDataRoot(
                                "Windows 11",
                                Path.of("C:/Users/tester"),
                                "C:/Users/tester/AppData/Local",
                                null));
    }

    /**
     * Linux supports XDG_DATA_HOME.
     */
    @Test
    void resolvesLinuxXdgApplicationDataDirectory() {
        assertEquals(
                Path.of(
                        "/home/tester/custom-data/"
                        + "race-replay-lab"),
                ApplicationCacheDirectories
                        .resolveApplicationDataRoot(
                                "Linux",
                                Path.of("/home/tester"),
                                null,
                                "/home/tester/custom-data"));
    }
}
