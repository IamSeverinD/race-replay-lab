package io.github.iamseverind.racereplay.app;

import io.github.iamseverind.racereplay.processed.ReplayDriverState;
import io.github.iamseverind.racereplay.processed.ReplayTimelineFormat;
import io.github.iamseverind.racereplay.processed.ReplayTimelineFrame;
import io.github.iamseverind.racereplay.processed.ReplayTimelineReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Immutable normalized circuit geometry used by the replay renderer.
 */
final class ReplayTrackGeometry {

    private static final int MINIMUM_LAP_POINTS = 120;
    private static final int MAXIMUM_RENDER_POINTS = 600;
    private static final double NORMALIZED_SIZE = 0.84;

    private static final List<Point> SYNTHETIC_POINTS =
            List.of(
                    new Point(0.10, 0.72),
                    new Point(0.18, 0.82),
                    new Point(0.33, 0.86),
                    new Point(0.50, 0.80),
                    new Point(0.66, 0.70),
                    new Point(0.83, 0.70),
                    new Point(0.91, 0.56),
                    new Point(0.87, 0.39),
                    new Point(0.73, 0.30),
                    new Point(0.61, 0.16),
                    new Point(0.43, 0.12),
                    new Point(0.28, 0.20),
                    new Point(0.20, 0.34),
                    new Point(0.08, 0.40),
                    new Point(0.12, 0.55),
                    new Point(0.27, 0.60),
                    new Point(0.39, 0.52),
                    new Point(0.55, 0.48),
                    new Point(0.68, 0.55),
                    new Point(0.53, 0.64),
                    new Point(0.35, 0.68));

    private final List<Point> points;
    private final boolean locationBased;
    private final double rawCenterX;
    private final double rawCenterY;
    private final double rawScale;

    private ReplayTrackGeometry(
            final List<Point> points,
            final boolean locationBased,
            final double rawCenterX,
            final double rawCenterY,
            final double rawScale) {

        this.points =
                List.copyOf(
                        Objects.requireNonNull(
                                points,
                                "points"));

        this.locationBased = locationBased;
        this.rawCenterX = rawCenterX;
        this.rawCenterY = rawCenterY;
        this.rawScale = rawScale;
    }

    /**
     * Returns the generic circuit used for the synthetic demonstration.
     *
     * @return synthetic geometry
     */
    static ReplayTrackGeometry synthetic() {
        return new ReplayTrackGeometry(
                SYNTHETIC_POINTS,
                false,
                0.0,
                0.0,
                0.0);
    }

    /**
     * Derives a circuit outline from one completed lap in a replay timeline.
     *
     * <p>The shortest plausible completed lap is preferred so pit-lane and
     * stationary samples do not define the visible circuit.</p>
     *
     * @param timelineFile processed replay timeline
     * @return location-based geometry or the synthetic fallback
     * @throws IOException when the timeline cannot be read
     */
    static ReplayTrackGeometry load(
            final Path timelineFile)
            throws IOException {

        Objects.requireNonNull(
                timelineFile,
                "timelineFile");

        try (ReplayTimelineReader reader =
                new ReplayTimelineReader(
                        timelineFile)) {

            final LapAccumulator[] accumulators =
                    new LapAccumulator[
                            reader.header()
                                    .driverCount()];

            for (int index = 0;
                    index < accumulators.length;
                    index++) {

                accumulators[index] =
                        new LapAccumulator();
            }

            List<Point> bestLap = List.of();

            for (int frameIndex = 0;
                    frameIndex
                            < reader.header()
                                    .frameCount();
                    frameIndex++) {

                final ReplayTimelineFrame frame =
                        reader.readFrame(
                                frameIndex);

                for (int driverIndex = 0;
                        driverIndex < accumulators.length;
                        driverIndex++) {

                    final List<Point> completedLap =
                            accumulators[driverIndex]
                                    .accept(
                                            frame.drivers()
                                                    .get(driverIndex));

                    if (isBetterLap(
                            completedLap,
                            bestLap)) {

                        bestLap = completedLap;
                    }
                }
            }

            return fromRawPoints(
                    bestLap);
        }
    }

    static ReplayTrackGeometry fromRawPoints(
            final List<Point> rawPoints) {

        Objects.requireNonNull(
                rawPoints,
                "rawPoints");

        if (!isPlausibleLap(rawPoints)) {
            return synthetic();
        }

        double minimumX = Double.POSITIVE_INFINITY;
        double maximumX = Double.NEGATIVE_INFINITY;
        double minimumY = Double.POSITIVE_INFINITY;
        double maximumY = Double.NEGATIVE_INFINITY;

        for (final Point point : rawPoints) {
            minimumX = Math.min(minimumX, point.x());
            maximumX = Math.max(maximumX, point.x());
            minimumY = Math.min(minimumY, point.y());
            maximumY = Math.max(maximumY, point.y());
        }

        final double centerX =
                (minimumX + maximumX) / 2.0;

        final double centerY =
                (minimumY + maximumY) / 2.0;

        final double scale =
                NORMALIZED_SIZE
                / Math.max(
                        maximumX - minimumX,
                        maximumY - minimumY);

        final List<Point> thinnedPoints =
                thin(
                        rawPoints,
                        MAXIMUM_RENDER_POINTS);

        final List<Point> normalizedPoints =
                thinnedPoints.stream()
                        .map(point ->
                                normalize(
                                        point,
                                        centerX,
                                        centerY,
                                        scale))
                        .toList();

        return new ReplayTrackGeometry(
                normalizedPoints,
                true,
                centerX,
                centerY,
                scale);
    }

    List<Point> points() {
        return points;
    }

    boolean locationBased() {
        return locationBased;
    }

    Point project(
            final int locationX,
            final int locationY) {

        if (!locationBased) {
            throw new IllegalStateException(
                    "Synthetic geometry cannot project raw coordinates.");
        }

        return normalize(
                new Point(locationX, locationY),
                rawCenterX,
                rawCenterY,
                rawScale);
    }

    private static Point normalize(
            final Point point,
            final double centerX,
            final double centerY,
            final double scale) {

        return new Point(
                0.5 + (point.x() - centerX) * scale,
                0.5 - (point.y() - centerY) * scale);
    }

    private static List<Point> thin(
            final List<Point> source,
            final int maximumPoints) {

        if (source.size() <= maximumPoints) {
            return List.copyOf(source);
        }

        final List<Point> result =
                new ArrayList<>(
                        maximumPoints);

        for (int index = 0;
                index < maximumPoints;
                index++) {

            final int sourceIndex =
                    (int) Math.round(
                            index
                            * (source.size() - 1.0)
                            / (maximumPoints - 1.0));

            result.add(
                    source.get(sourceIndex));
        }

        return List.copyOf(result);
    }

    private static boolean isBetterLap(
            final List<Point> candidate,
            final List<Point> currentBest) {

        return isPlausibleLap(candidate)
                && (currentBest.isEmpty()
                || candidate.size() < currentBest.size());
    }

    private static boolean isPlausibleLap(
            final List<Point> candidate) {

        if (candidate.size() < MINIMUM_LAP_POINTS) {
            return false;
        }

        double minimumX = Double.POSITIVE_INFINITY;
        double maximumX = Double.NEGATIVE_INFINITY;
        double minimumY = Double.POSITIVE_INFINITY;
        double maximumY = Double.NEGATIVE_INFINITY;
        double pathLength = 0.0;
        double maximumSegmentLength = 0.0;

        Point previous = null;

        for (final Point point : candidate) {
            minimumX = Math.min(minimumX, point.x());
            maximumX = Math.max(maximumX, point.x());
            minimumY = Math.min(minimumY, point.y());
            maximumY = Math.max(maximumY, point.y());

            if (previous != null) {
                final double segmentLength =
                        previous.distanceTo(point);

                pathLength += segmentLength;
                maximumSegmentLength =
                        Math.max(
                                maximumSegmentLength,
                                segmentLength);
            }

            previous = point;
        }

        final double diagonal =
                Math.hypot(
                        maximumX - minimumX,
                        maximumY - minimumY);

        if (diagonal < 100.0
                || pathLength < diagonal * 1.8
                || maximumSegmentLength > diagonal * 0.05) {

            return false;
        }

        return candidate.getFirst()
                .distanceTo(candidate.getLast())
                <= diagonal * 0.20;
    }

    /**
     * One two-dimensional circuit coordinate.
     *
     * @param x horizontal value
     * @param y vertical value
     */
    record Point(double x, double y) {

        Point {
            if (!Double.isFinite(x)
                    || !Double.isFinite(y)) {

                throw new IllegalArgumentException(
                        "Track coordinates must be finite.");
            }
        }

        double distanceTo(final Point other) {
            return Math.hypot(
                    other.x - x,
                    other.y - y);
        }
    }

    private static final class LapAccumulator {

        private int currentLap;
        private final List<Point> points =
                new ArrayList<>();

        private List<Point> accept(
                final ReplayDriverState state) {

            final boolean lapValid =
                    hasFlag(
                            state,
                            ReplayTimelineFormat
                                    .FLAG_LAP_VALID);

            if (!lapValid
                    || state.lapNumber() <= 0) {

                return List.of();
            }

            List<Point> completedLap = List.of();

            if (currentLap == 0) {
                currentLap = state.lapNumber();
            } else if (state.lapNumber() != currentLap) {
                if (state.lapNumber() == currentLap + 1
                        && currentLap >= 2) {

                    completedLap =
                            List.copyOf(points);
                }

                points.clear();
                currentLap = state.lapNumber();
            }

            if (hasFlag(
                    state,
                    ReplayTimelineFormat
                            .FLAG_LOCATION_VALID)) {

                final Point point =
                        new Point(
                                state.x(),
                                state.y());

                if (points.isEmpty()
                        || points.getLast()
                                .distanceTo(point) >= 1.0) {

                    points.add(point);
                }
            }

            return completedLap;
        }
    }

    private static boolean hasFlag(
            final ReplayDriverState state,
            final int flag) {

        return (state.flags() & flag) != 0;
    }
}
