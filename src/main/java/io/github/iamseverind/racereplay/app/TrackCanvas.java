package io.github.iamseverind.racereplay.app;

import io.github.iamseverind.racereplay.core.DriverSnapshot;
import io.github.iamseverind.racereplay.core.ReplaySnapshot;
import java.util.List;
import java.util.Map;
import javafx.geometry.Point2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

/**
 * Responsive Canvas renderer for replay data on its selected circuit.
 */
public final class TrackCanvas extends Region {

    private static final double PADDING = 55.0;
    private static final double DRIVER_RADIUS = 6.5;

    private final Canvas canvas = new Canvas();

    private ReplaySnapshot snapshot;
    private String selectedDriverCode = "A01";
    private ReplayTrackGeometry trackGeometry =
            ReplayTrackGeometry.synthetic();
    private Map<String, String> teamColors = Map.of();

    /**
     * Creates a resize-aware Canvas region.
     */
    public TrackCanvas() {
        getChildren().add(canvas);

        widthProperty().addListener(
                (observable, oldValue, newValue) ->
                        resizeCanvas());

        heightProperty().addListener(
                (observable, oldValue, newValue) ->
                        resizeCanvas());

        setMinSize(480.0, 280.0);
        setPrefSize(850.0, 680.0);
    }

    /**
     * Updates the replay state shown by the renderer.
     *
     * @param newSnapshot current replay snapshot
     */
    public void setSnapshot(final ReplaySnapshot newSnapshot) {
        snapshot = newSnapshot;
        draw();
    }

    /**
     * Changes the highlighted driver.
     *
     * @param driverCode selected three-letter code
     */
    public void setSelectedDriverCode(final String driverCode) {
        selectedDriverCode = driverCode;
        draw();
    }

    /**
     * Changes the circuit outline and coordinate projection.
     *
     * @param geometry selected replay geometry
     */
    void setTrackGeometry(
            final ReplayTrackGeometry geometry) {

        trackGeometry = geometry;
        draw();
    }

    /**
     * Updates optional team colors supplied by the selected data source.
     *
     * @param colors team name to CSS color mapping
     */
    void setTeamColors(
            final Map<String, String> colors) {

        teamColors = Map.copyOf(colors);
        draw();
    }

    /**
     * Returns the preferred width.
     *
     * @param height requested height
     * @return preferred width
     */
    @Override
    protected double computePrefWidth(final double height) {
        return 850.0;
    }

    /**
     * Returns the preferred height.
     *
     * @param width requested width
     * @return preferred height
     */
    @Override
    protected double computePrefHeight(final double width) {
        return 680.0;
    }

    /**
     * Positions the internal Canvas.
     */
    @Override
    protected void layoutChildren() {
        canvas.relocate(0.0, 0.0);
    }

    private void resizeCanvas() {
        final double newWidth = Math.max(1.0, getWidth());
        final double newHeight = Math.max(1.0, getHeight());

        if (canvas.getWidth() != newWidth) {
            canvas.setWidth(newWidth);
        }

        if (canvas.getHeight() != newHeight) {
            canvas.setHeight(newHeight);
        }

        draw();
    }

    private void draw() {
        final GraphicsContext graphics = canvas.getGraphicsContext2D();
        final double width = canvas.getWidth();
        final double height = canvas.getHeight();

        graphics.setFill(Color.web("#0b0e13"));
        graphics.fillRect(0.0, 0.0, width, height);

        if (width < 10.0 || height < 10.0) {
            return;
        }

        final TrackViewport viewport =
                createViewport(
                        width,
                        height);

        drawTrack(
                graphics,
                viewport);

        drawStartLine(
                graphics,
                viewport);

        if (snapshot != null) {
            drawDrivers(
                    graphics,
                    viewport);
        }
    }

    private void drawTrack(
            final GraphicsContext graphics,
            final TrackViewport viewport) {

        graphics.setLineCap(StrokeLineCap.ROUND);
        graphics.setLineJoin(StrokeLineJoin.ROUND);

        graphics.setStroke(Color.web("#303946"));
        graphics.setLineWidth(30.0);
        drawClosedPolyline(graphics, viewport);

        graphics.setStroke(Color.web("#e8edf2"));
        graphics.setLineWidth(22.0);
        drawClosedPolyline(graphics, viewport);

        graphics.setStroke(Color.web("#242a32"));
        graphics.setLineWidth(15.0);
        drawClosedPolyline(graphics, viewport);

        graphics.setStroke(Color.web("#353d47"));
        graphics.setLineWidth(1.2);
        graphics.setLineDashes(7.0, 8.0);
        drawClosedPolyline(graphics, viewport);
        graphics.setLineDashes();
    }

    private void drawClosedPolyline(
            final GraphicsContext graphics,
            final TrackViewport viewport) {

        final List<ReplayTrackGeometry.Point> points =
                trackGeometry.points();

        final Point2D first =
                transform(points.getFirst(), viewport);

        graphics.beginPath();
        graphics.moveTo(first.getX(), first.getY());

        for (int index = 1;
                index < points.size();
                index++) {

            final Point2D point =
                    transform(points.get(index), viewport);

            graphics.lineTo(point.getX(), point.getY());
        }

        graphics.closePath();
        graphics.stroke();
    }

    private void drawStartLine(
            final GraphicsContext graphics,
            final TrackViewport viewport) {

        final Point2D start =
                transform(
                        trackGeometry.points()
                                .getFirst(),
                        viewport);

        graphics.setStroke(Color.WHITE);
        graphics.setLineWidth(3.0);

        graphics.strokeLine(
                start.getX() - 10.0,
                start.getY() - 10.0,
                start.getX() + 10.0,
                start.getY() + 10.0);
    }

    private void drawDrivers(
            final GraphicsContext graphics,
            final TrackViewport viewport) {

        final List<DriverRenderState> renderStates =
                snapshot.drivers()
                        .stream()
                        .map(
                                driver ->
                                        createDriverRenderState(
                                                driver,
                                                viewport))
                        .toList();

        for (final DriverRenderState state : renderStates) {
            if (!state.selected()) {
                drawDriver(
                        graphics,
                        state.driver(),
                        state.position(),
                        false,
                        false);
            }
        }

        for (final DriverRenderState state : renderStates) {
            if (state.selected()) {
                drawDriver(
                        graphics,
                        state.driver(),
                        state.position(),
                        true,
                        true);
            }
        }
    }

    private DriverRenderState createDriverRenderState(
            final DriverSnapshot driver,
            final TrackViewport viewport) {

        final Point2D position;

        if (trackGeometry.locationBased()
                && driver.locationValid()) {

            position =
                    transform(
                            trackGeometry.project(
                                    driver.locationX(),
                                    driver.locationY()),
                            viewport);
        } else {
            position =
                    pointAtProgress(
                            driver.lapProgress(),
                            viewport);
        }

        final boolean selected =
                driver.code()
                        .equals(selectedDriverCode);

        return new DriverRenderState(
                driver,
                position,
                selected);
    }

    private void drawDriver(
            final GraphicsContext graphics,
            final DriverSnapshot driver,
            final Point2D position,
            final boolean selected,
            final boolean showLabel) {

        if (selected) {
            graphics.setFill(
                    Color.color(
                            1.0,
                            1.0,
                            1.0,
                            0.22));

            graphics.fillOval(
                    position.getX() - 13.0,
                    position.getY() - 13.0,
                    26.0,
                    26.0);
        }

        graphics.setFill(
                teamColor(
                        driver.team()));

        graphics.fillOval(
                position.getX() - DRIVER_RADIUS,
                position.getY() - DRIVER_RADIUS,
                DRIVER_RADIUS * 2.0,
                DRIVER_RADIUS * 2.0);

        graphics.setStroke(
                selected
                        ? Color.WHITE
                        : Color.web("#111419"));

        graphics.setLineWidth(
                selected
                        ? 2.4
                        : 1.2);

        graphics.strokeOval(
                position.getX() - DRIVER_RADIUS,
                position.getY() - DRIVER_RADIUS,
                DRIVER_RADIUS * 2.0,
                DRIVER_RADIUS * 2.0);

        if (!showLabel) {
            return;
        }

        graphics.setFont(
                Font.font(
                        "System",
                        selected
                                ? FontWeight.EXTRA_BOLD
                                : FontWeight.BOLD,
                        selected
                                ? 13.0
                                : 11.0));

        graphics.setTextAlign(
                TextAlignment.CENTER);

        graphics.setFill(
                Color.WHITE);

        graphics.fillText(
                driver.code(),
                position.getX(),
                position.getY() - 11.0);
    }

    private Point2D pointAtProgress(
            final double progress,
            final TrackViewport viewport) {

        final List<ReplayTrackGeometry.Point> points =
                trackGeometry.points();

        final double[] segmentLengths =
                new double[points.size()];

        double totalLength = 0.0;

        for (int index = 0;
                index < points.size();
                index++) {

            final int nextIndex =
                    (index + 1) % points.size();

            final double deltaX =
                    points.get(nextIndex).x()
                    - points.get(index).x();

            final double deltaY =
                    points.get(nextIndex).y()
                    - points.get(index).y();

            final double length =
                    Math.hypot(deltaX, deltaY);

            segmentLengths[index] = length;
            totalLength += length;
        }

        double remainingDistance =
                progress * totalLength;

        for (int index = 0;
                index < points.size();
                index++) {

            if (remainingDistance <= segmentLengths[index]) {
                final int nextIndex =
                            (index + 1) % points.size();

                final double ratio =
                        segmentLengths[index] == 0.0
                                ? 0.0
                                : remainingDistance
                                / segmentLengths[index];

                final double normalizedX =
                        points.get(index).x()
                        + (points.get(nextIndex).x()
                        - points.get(index).x())
                        * ratio;

                final double normalizedY =
                        points.get(index).y()
                        + (points.get(nextIndex).y()
                        - points.get(index).y())
                        * ratio;

                return transform(
                        new ReplayTrackGeometry.Point(
                                normalizedX,
                                normalizedY),
                        viewport);
            }

            remainingDistance -= segmentLengths[index];
        }

        return transform(points.getFirst(), viewport);
    }

    private TrackViewport createViewport(
            final double width,
            final double height) {

        final double availableWidth =
                Math.max(1.0, width - PADDING * 2.0);

        final double availableHeight =
                Math.max(1.0, height - PADDING * 2.0);

        final double scale =
                Math.min(availableWidth, availableHeight);

        final double offsetX =
                (width - scale) / 2.0;

        final double offsetY =
                (height - scale) / 2.0;

        return new TrackViewport(
                scale,
                offsetX,
                offsetY);
    }

    private Point2D transform(
            final ReplayTrackGeometry.Point point,
            final TrackViewport viewport) {

        return new Point2D(
                viewport.offsetX()
                        + point.x() * viewport.scale(),
                viewport.offsetY()
                        + point.y() * viewport.scale());
    }

    private Color teamColor(final String team) {
        final String importedColor =
                teamColors.get(team);

        if (importedColor != null) {
            return Color.web(importedColor);
        }

        return switch (team) {
            case "Apex Dynamics" -> Color.web("#3671c6");
            case "Vector Motorsport" -> Color.web("#27f4d2");
            case "Northstar Racing" -> Color.web("#e80020");
            case "Helix Competition" -> Color.web("#ff8000");
            case "Emerald Works" -> Color.web("#229971");
            case "Summit Motors" -> Color.web("#ff87bc");
            case "Orbit Racing" -> Color.web("#64c4ff");
            case "Cobalt Engineering" -> Color.web("#6692ff");
            case "Lumen Autosport" -> Color.web("#52e252");
            case "Titan Motorsport" -> Color.web("#b6babd");
            default -> Color.WHITE;
        };
    }

    private record DriverRenderState(
            DriverSnapshot driver,
            Point2D position,
            boolean selected) {
    }

    private record TrackViewport(
            double scale,
            double offsetX,
            double offsetY) {
    }
}
