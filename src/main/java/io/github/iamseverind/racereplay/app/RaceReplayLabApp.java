package io.github.iamseverind.racereplay.app;

import io.github.iamseverind.racereplay.core.DriverSnapshot;
import io.github.iamseverind.racereplay.core.ReplayClock;
import io.github.iamseverind.racereplay.core.ReplaySnapshot;
import io.github.iamseverind.racereplay.core.ReplaySimulation;
import java.net.URL;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.StringConverter;

/**
 * JavaFX desktop application for the Race Replay Lab project.
 */
public final class RaceReplayLabApp extends Application {

    private static final double INITIAL_WIDTH = 1280.0;
    private static final double INITIAL_HEIGHT = 760.0;
    private static final long UI_REFRESH_NANOS = 100_000_000L;

    private final ReplayClock replayClock = new ReplayClock();

    private ReplaySimulationLoader.LoadedReplaySimulation loadedSimulation;
    private ReplaySimulation simulation;
    private RaceControlTimeline raceControlTimeline =
            RaceControlTimeline.empty();
    private String replaySourceSubtitle;

    private final ObservableList<DriverSnapshot> leaderboardItems =
            FXCollections.observableArrayList();

    private final TrackCanvas trackCanvas = new TrackCanvas();
    private final TableView<DriverSnapshot> leaderboard =
            new TableView<>(leaderboardItems);

    private final Label replayTimeLabel = new Label();
    private final Label framesPerSecondLabel = new Label();
    private final Label selectedDriverLabel = new Label();
    private final Label speedLabel = new Label();
    private final Label rpmLabel = new Label();
    private final Label gearLabel = new Label();
    private final Label throttleLabel = new Label();
    private final Label brakeLabel = new Label();
    private final Label drsLabel = new Label();
    private final Label tyreLabel = new Label();
    private final Label lapLabel = new Label();
    private final Label gapLabel = new Label();
    private final Label intervalLabel = new Label();
    private final Label activeDriversLabel = new Label();

    private final Label racePhaseLabel = new Label();
    private final Label raceControlDrsLabel = new Label();
    private final Label raceControlMessageLabel = new Label();

    private final Slider timelineSlider = new Slider();
    private final Label timelineCurrentLabel = new Label();
    private final Label timelineDurationLabel = new Label();

    private AnimationTimer animationTimer;
    private Stage primaryStage;
    private Instant applicationOpenedAt;
    private long lastFrameNanos;
    private long lastUiRefreshNanos;
    private double smoothedFramesPerSecond = 60.0;
    private String selectedDriverCode = "A01";
    private double replayDurationSeconds;
    private double raceStartSeconds;
    private int scheduledRaceLaps;
    private boolean drsAvailable;
    private boolean updatingTimelineSlider;

    /**
     * Builds and starts the JavaFX user interface.
     *
     * @param stage primary window
     */
    @Override
    public void start(final Stage stage)
            throws Exception {

        primaryStage = stage;
        applicationOpenedAt = Instant.now();

        loadedSimulation =
                ReplaySimulationLoader
                        .loadDefault();

        simulation =
                loadedSimulation
                        .simulation();

        replayDurationSeconds =
                PlaybackTimelineSupport.resolveDuration(
                        simulation.durationSeconds());

        replaySourceSubtitle =
                loadedSimulation
                        .subtitle();

        raceStartSeconds =
                loadedSimulation
                        .raceStartSeconds();

        scheduledRaceLaps =
                loadedSimulation
                        .scheduledLaps();

        drsAvailable =
                loadedSimulation
                        .drsAvailable();

        trackCanvas.setTrackGeometry(
                loadedSimulation
                        .trackGeometry());

        trackCanvas.setTeamColors(
                loadedSimulation
                        .teamColors());

        raceControlTimeline =
                RaceControlTimeline.load(
                        loadedSimulation
                                .cacheDirectory()
                                .resolve("processed")
                                .resolve("events.json"));

        replayClock.advance(
                loadedSimulation
                        .initialReplaySeconds());

        configureLeaderboard();

        final BorderPane root = new BorderPane();
        root.setTop(
                new VBox(
                        createHeader(),
                        createRaceControlPanel()));

        root.setCenter(createMainContent());
        root.setBottom(createPlaybackControls());
        root.setStyle("-fx-background-color: #0b0e13;");

        final Scene scene =
                new Scene(root, INITIAL_WIDTH, INITIAL_HEIGHT);

        addApplicationStylesheet(scene);

        stage.setTitle("Race Replay Lab");
        stage.setMinWidth(980.0);
        stage.setMinHeight(620.0);
        stage.setScene(scene);
        stage.show();

        startAnimation();
    }

    /**
     * Stops the active render loop.
     */
    @Override
    public void stop()
            throws Exception {

        if (animationTimer != null) {
            animationTimer.stop();
        }

        if (loadedSimulation != null) {
            loadedSimulation.close();
        }
    }

    private void addApplicationStylesheet(
            final Scene scene) {

        final URL stylesheet =
                RaceReplayLabApp.class.getResource(
                        "race-replay.css");

        if (stylesheet == null) {
            throw new IllegalStateException(
                    "Missing application stylesheet.");
        }

        scene.getStylesheets()
                .add(
                        stylesheet.toExternalForm());
    }

    private HBox createHeader() {
        final Label title =
                new Label("RACE REPLAY LAB");

        title.setStyle(
                "-fx-font-size: 25px;"
                + "-fx-font-weight: 900;"
                + "-fx-text-fill: white;");

        final Label subtitle =
                new Label(
                        replaySourceSubtitle);

        subtitle.setStyle(
                "-fx-font-size: 12px;"
                + "-fx-text-fill: #8d98a7;");

        final VBox titles =
                new VBox(2.0, title, subtitle);

        final HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        replayTimeLabel.setStyle(
                "-fx-font-size: 20px;"
                + "-fx-font-weight: bold;"
                + "-fx-text-fill: white;");

        framesPerSecondLabel.setStyle(
                "-fx-font-size: 12px;"
                + "-fx-text-fill: #76d7a7;");

        final Button raceStartButton =
                new Button("RACE START");

        raceStartButton.setOnAction(
                event -> seekTo(
                        raceStartSeconds));

        raceStartButton.setStyle(
                "-fx-background-color: #e10600;"
                + "-fx-text-fill: white;"
                + "-fx-font-weight: bold;"
                + "-fx-cursor: hand;");

        final Button openF1Button =
                new Button("IMPORT OPENF1");

        openF1Button.setOnAction(
                event -> OpenF1ReplayDialog.show(
                        primaryStage,
                        applicationOpenedAt));

        openF1Button.setStyle(
                "-fx-background-color: #2d3642;"
                + "-fx-text-fill: white;"
                + "-fx-font-weight: bold;"
                + "-fx-cursor: hand;");

        final HBox actions =
                new HBox(
                        8.0,
                        raceStartButton,
                        openF1Button);

        final VBox status =
                new VBox(
                        5.0,
                        replayTimeLabel,
                        framesPerSecondLabel,
                        actions);

        status.setAlignment(Pos.CENTER_RIGHT);

        final HBox header =
                new HBox(18.0, titles, spacer, status);

        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(14.0, 18.0, 14.0, 18.0));
        header.setStyle(
                "-fx-background-color: #161b22;"
                + "-fx-border-color: transparent transparent #2c333d transparent;"
                + "-fx-border-width: 0 0 1 0;");

        return header;
    }

    private HBox createRaceControlPanel() {
        final Label title =
                new Label("RACE CONTROL");

        title.setStyle(
                "-fx-text-fill: #aeb7c2;"
                + "-fx-font-size: 11px;"
                + "-fx-font-weight: bold;");

        racePhaseLabel.setMinWidth(125.0);

        raceControlDrsLabel.setMinWidth(105.0);
        raceControlDrsLabel.setStyle(
                "-fx-text-fill: #d6dde6;"
                + "-fx-font-size: 12px;"
                + "-fx-font-weight: bold;");

        raceControlMessageLabel.setStyle(
                "-fx-text-fill: white;"
                + "-fx-font-size: 12px;");

        raceControlMessageLabel.setMaxWidth(
                Double.MAX_VALUE);

        HBox.setHgrow(
                raceControlMessageLabel,
                Priority.ALWAYS);

        final HBox panel =
                new HBox(
                        14.0,
                        title,
                        racePhaseLabel,
                        raceControlDrsLabel,
                        raceControlMessageLabel);

        panel.setAlignment(
                Pos.CENTER_LEFT);

        panel.setPadding(
                new Insets(
                        8.0,
                        18.0,
                        8.0,
                        18.0));

        panel.setStyle(
                "-fx-background-color: #0f141a;"
                + "-fx-border-color: transparent "
                + "transparent #2c333d transparent;"
                + "-fx-border-width: 0 0 1 0;");

        updateRaceControl(
                replayClock.getReplaySeconds());

        return panel;
    }

    private SplitPane createMainContent() {
        final VBox leaderboardCard =
                new VBox(
                        12.0,
                        createLeaderboardHeader(),
                        leaderboard);

        leaderboardCard.getStyleClass()
                .add("timing-card");

        VBox.setVgrow(
                leaderboard,
                Priority.ALWAYS);

        final VBox telemetryCard =
                createTelemetryPanel();

        telemetryCard.getStyleClass()
                .add("timing-card");

        final VBox sidebarContent =
                new VBox(
                        14.0,
                        leaderboardCard,
                        telemetryCard);

        sidebarContent.getStyleClass()
                .add("timing-sidebar-content");

        sidebarContent.setPadding(new Insets(16.0));

        final ScrollPane rightPanel =
                new ScrollPane(sidebarContent);

        rightPanel.getStyleClass()
                .add("timing-sidebar");

        rightPanel.setFitToWidth(true);
        rightPanel.setHbarPolicy(
                ScrollPane.ScrollBarPolicy.NEVER);
        rightPanel.setVbarPolicy(
                ScrollPane.ScrollBarPolicy.AS_NEEDED);
        rightPanel.setMinHeight(0.0);
        rightPanel.setMinWidth(340.0);
        rightPanel.setPrefWidth(380.0);

        VBox.setVgrow(
                leaderboardCard,
                Priority.ALWAYS);

        final SplitPane splitPane =
                new SplitPane(trackCanvas, rightPanel);

        splitPane.setDividerPositions(0.70);
        splitPane.setMinHeight(0.0);
        splitPane.setStyle("-fx-background-color: #0b0e13;");

        return splitPane;
    }

    private HBox createLeaderboardHeader() {
        final Label overline =
                new Label("RACE CLASSIFICATION");

        overline.getStyleClass()
                .add("section-overline");

        final Label title = new Label("LIVE TIMING");

        title.getStyleClass()
                .add("section-title");

        final VBox titles =
                new VBox(
                        2.0,
                        overline,
                        title);

        final HBox spacer = new HBox();

        HBox.setHgrow(
                spacer,
                Priority.ALWAYS);

        activeDriversLabel.getStyleClass()
                .add("live-badge");

        activeDriversLabel.setText("00 ACTIVE");

        final HBox header =
                new HBox(
                        10.0,
                        titles,
                        spacer,
                        activeDriversLabel);

        header.setAlignment(
                Pos.CENTER_LEFT);

        return header;
    }

    private VBox createTelemetryPanel() {
        final Label overline =
                new Label("ONBOARD DATA");

        overline.getStyleClass()
                .add("section-overline");

        final Label heading =
                new Label("SELECTED DRIVER");

        heading.getStyleClass()
                .add("section-title");

        selectedDriverLabel.getStyleClass()
                .add("selected-driver-name");

        final VBox header =
                new VBox(
                        2.0,
                        overline,
                        heading,
                        selectedDriverLabel);

        final HBox primaryMetrics =
                new HBox(
                        10.0,
                        createMetricTile(
                                "SPEED",
                                speedLabel),
                        createMetricTile(
                                "GEAR",
                                gearLabel));

        for (final javafx.scene.Node metric
                : primaryMetrics.getChildren()) {

            HBox.setHgrow(
                    metric,
                    Priority.ALWAYS);
        }

        final Separator divider =
                new Separator();

        divider.getStyleClass()
                .add("timing-divider");

        final GridPane grid = new GridPane();

        grid.setHgap(18.0);
        grid.setVgap(9.0);

        addTelemetryRow(
                grid,
                0,
                "RPM",
                rpmLabel);

        addTelemetryRow(
                grid,
                1,
                "Throttle",
                throttleLabel);

        addTelemetryRow(
                grid,
                2,
                "Brake",
                brakeLabel);

        addTelemetryRow(
                grid,
                3,
                "Aero",
                drsLabel);

        addTelemetryRow(
                grid,
                4,
                "Tyre",
                tyreLabel);

        addTelemetryRow(
                grid,
                5,
                "Lap",
                lapLabel);

        addTelemetryRow(
                grid,
                6,
                "Gap",
                gapLabel);

        addTelemetryRow(
                grid,
                7,
                "Interval",
                intervalLabel);

        final VBox panel =
                new VBox(
                        14.0,
                        header,
                        primaryMetrics,
                        divider,
                        grid);

        return panel;
    }

    private VBox createMetricTile(
            final String name,
            final Label valueLabel) {

        final Label nameLabel = new Label(name);

        nameLabel.getStyleClass()
                .add("metric-name");

        valueLabel.getStyleClass()
                .add("metric-value");

        final VBox tile =
                new VBox(
                        3.0,
                        nameLabel,
                        valueLabel);

        tile.getStyleClass()
                .add("metric-tile");

        tile.setMaxWidth(
                Double.MAX_VALUE);

        return tile;
    }

    private void addTelemetryRow(
            final GridPane grid,
            final int row,
            final String name,
            final Label valueLabel) {

        final Label nameLabel = new Label(name);

        nameLabel.getStyleClass()
                .add("telemetry-name");

        valueLabel.getStyleClass()
                .add("telemetry-value");

        grid.add(nameLabel, 0, row);
        grid.add(valueLabel, 1, row);
    }

    private VBox createPlaybackControls() {
        final Button pauseButton =
                new Button("Pause");

        pauseButton.setOnAction(event -> {
            replayClock.togglePaused();

            pauseButton.setText(
                    replayClock.isPaused()
                            ? "Fortsetzen"
                            : "Pause");
        });

        final Button restartButton =
                new Button("Zum Start");

        restartButton.setOnAction(
                event -> seekTo(0.0));

        final Button backwardButton =
                new Button("−10 s");

        backwardButton.setOnAction(
                event -> skipBy(-10.0));

        final Button forwardButton =
                new Button("+10 s");

        forwardButton.setOnAction(
                event -> skipBy(10.0));

        final ComboBox<Double> speedChoice =
                new ComboBox<>();

        speedChoice.getItems().setAll(
                List.of(
                        0.5,
                        1.0,
                        2.0,
                        4.0,
                        8.0));

        speedChoice.setValue(1.0);

        speedChoice.setConverter(
                new StringConverter<>() {
                    @Override
                    public String toString(
                            final Double value) {

                        if (value == null) {
                            return "";
                        }

                        return String.format(
                                Locale.ROOT,
                                "%.1fx",
                                value);
                    }

                    @Override
                    public Double fromString(
                            final String text) {

                        return Double.valueOf(
                                text.replace(
                                        "x",
                                        ""));
                    }
                });

        speedChoice.valueProperty().addListener(
                (observable, oldValue, newValue) -> {
                    if (newValue != null) {
                        replayClock.setPlaybackSpeed(
                                newValue);
                    }
                });

        final Label speedTitle =
                new Label("Wiedergabe:");

        speedTitle.setStyle(
                "-fx-text-fill: #aeb7c2;");

        final Label hint =
                new Label(
                        "Timeline ziehen oder Fahrer "
                        + "im Leaderboard auswählen.");

        hint.setStyle(
                "-fx-text-fill: #8d98a7;");

        final HBox controlSpacer =
                new HBox();

        HBox.setHgrow(
                controlSpacer,
                Priority.ALWAYS);

        final HBox buttonRow =
                new HBox(
                        10.0,
                        pauseButton,
                        restartButton,
                        backwardButton,
                        forwardButton,
                        speedTitle,
                        speedChoice,
                        controlSpacer,
                        hint);

        buttonRow.setAlignment(
                Pos.CENTER_LEFT);

        timelineSlider.setMin(0.0);
        timelineSlider.setMax(
                replayDurationSeconds);
        timelineSlider.setValue(
                replayClock.getReplaySeconds());
        timelineSlider.setBlockIncrement(10.0);
        timelineSlider.setMajorTickUnit(600.0);
        timelineSlider.setShowTickLabels(false);
        timelineSlider.setShowTickMarks(false);

        HBox.setHgrow(
                timelineSlider,
                Priority.ALWAYS);

        timelineSlider.valueProperty()
                .addListener(
                        (observable, oldValue, newValue) -> {
                            if (!updatingTimelineSlider) {
                                seekTo(
                                        newValue.doubleValue());
                            }
                        });

        timelineCurrentLabel.setMinWidth(88.0);
        timelineCurrentLabel.setStyle(
                "-fx-text-fill: white;"
                + "-fx-font-family: monospace;");

        timelineDurationLabel.setMinWidth(88.0);
        timelineDurationLabel.setAlignment(
                Pos.CENTER_RIGHT);
        timelineDurationLabel.setStyle(
                "-fx-text-fill: #aeb7c2;"
                + "-fx-font-family: monospace;");

        final HBox timelineRow =
                new HBox(
                        10.0,
                        timelineCurrentLabel,
                        timelineSlider,
                        timelineDurationLabel);

        timelineRow.setAlignment(
                Pos.CENTER_LEFT);

        final VBox controls =
                new VBox(
                        8.0,
                        timelineRow,
                        buttonRow);

        controls.setPadding(
                new Insets(
                        10.0,
                        18.0,
                        10.0,
                        18.0));

        controls.setStyle(
                "-fx-background-color: #161b22;"
                + "-fx-border-color: #2c333d "
                + "transparent transparent transparent;"
                + "-fx-border-width: 1 0 0 0;");

        updatePlaybackTimeline(
                replayClock.getReplaySeconds());

        return controls;
    }

    private void configureLeaderboard() {
        final TableColumn<DriverSnapshot, Number> positionColumn =
                new TableColumn<>("POS");

        positionColumn.setCellValueFactory(
                row -> new SimpleIntegerProperty(
                        row.getValue().position()));

        positionColumn.setPrefWidth(42.0);
        positionColumn.setMaxWidth(48.0);

        final TableColumn<DriverSnapshot, String> driverColumn =
                new TableColumn<>("DRIVER");

        driverColumn.setCellValueFactory(
                row -> new SimpleStringProperty(
                        row.getValue().code()));

        driverColumn.setPrefWidth(76.0);

        final TableColumn<DriverSnapshot, String> tyreColumn =
                new TableColumn<>("TYRE");

        tyreColumn.setCellValueFactory(
                row -> new SimpleStringProperty(
                        row.getValue().tyre()));

        tyreColumn.setPrefWidth(82.0);

        final TableColumn<DriverSnapshot, String> speedColumn =
                new TableColumn<>("SPEED");

        speedColumn.setCellValueFactory(
                row -> new SimpleStringProperty(
                        String.format(
                                Locale.ROOT,
                                "%.0f",
                                row.getValue().speedKph())));

        speedColumn.setPrefWidth(72.0);

        leaderboard.getColumns().setAll(
                List.of(
                        positionColumn,
                        driverColumn,
                        tyreColumn,
                        speedColumn));

        for (final TableColumn<DriverSnapshot, ?> column
                : leaderboard.getColumns()) {

            column.setSortable(false);
            column.setReorderable(false);
        }

        leaderboard.setColumnResizePolicy(
                TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        leaderboard.getStyleClass()
                .add("live-timing-table");

        leaderboard.setFixedCellSize(36.0);
        leaderboard.setMinHeight(210.0);
        leaderboard.setFocusTraversable(false);
        leaderboard.setPlaceholder(
                new Label("NO ACTIVE DRIVERS"));

        leaderboard.setRowFactory(table ->
                new TableRow<>() {
                    @Override
                    protected void updateItem(
                            final DriverSnapshot driver,
                            final boolean empty) {

                        super.updateItem(driver, empty);

                        if (empty || driver == null) {
                            setStyle("");
                            return;
                        }

                        final String teamColor =
                                loadedSimulation
                                        .teamColors()
                                        .getOrDefault(
                                                driver.team(),
                                                "#65707e");

                        setStyle(
                                "-fx-border-color: transparent "
                                + "transparent transparent "
                                + teamColor
                                + "; -fx-border-width: 0 0 0 3;");
                    }
                });

        leaderboard.getSelectionModel()
                .selectedItemProperty()
                .addListener(
                        (observable, oldValue, newValue) -> {
                            if (newValue != null) {
                                selectedDriverCode =
                                        newValue.code();

                                trackCanvas.setSelectedDriverCode(
                                        selectedDriverCode);

                                updateTelemetry(newValue);
                            }
                        });
    }

    private void startAnimation() {
        animationTimer = new AnimationTimer() {
            @Override
            public void handle(final long now) {
                renderFrame(now);
            }
        };

        animationTimer.start();
    }

    private void renderFrame(final long now) {
        if (lastFrameNanos == 0L) {
            lastFrameNanos = now;
            return;
        }

        final double elapsedSeconds =
                Math.min(
                        (now - lastFrameNanos)
                        / 1_000_000_000.0,
                        0.1);

        lastFrameNanos = now;

        replayClock.advance(elapsedSeconds);

        final ReplaySnapshot snapshot =
                simulation.snapshotAt(
                        replayClock.getReplaySeconds());

        updateFramesPerSecond(elapsedSeconds);

        trackCanvas.setSnapshot(snapshot);
        trackCanvas.setSelectedDriverCode(selectedDriverCode);

        if (now - lastUiRefreshNanos >= UI_REFRESH_NANOS) {
            lastUiRefreshNanos = now;
            refreshUserInterface(snapshot);
        }
    }

    private void updateFramesPerSecond(
            final double elapsedSeconds) {

        if (elapsedSeconds <= 0.0) {
            return;
        }

        final double currentFramesPerSecond =
                1.0 / elapsedSeconds;

        smoothedFramesPerSecond =
                smoothedFramesPerSecond * 0.90
                + currentFramesPerSecond * 0.10;
    }

    private void refreshUserInterface(
            final ReplaySnapshot snapshot) {

        leaderboardItems.setAll(snapshot.drivers());

        activeDriversLabel.setText(
                String.format(
                        Locale.ROOT,
                        "%02d ACTIVE",
                        snapshot.drivers()
                                .size()));

        updateReplayStatus(snapshot);

        if (snapshot.drivers().isEmpty()) {
            leaderboard.getSelectionModel()
                    .clearSelection();

            clearTelemetry();
            return;
        }

        final DriverSnapshot selectedDriver =
                snapshot.drivers()
                        .stream()
                        .filter(driver ->
                                driver.code()
                                        .equals(selectedDriverCode))
                        .findFirst()
                        .orElse(snapshot.drivers().getFirst());

        if (!selectedDriver.code()
                .equals(selectedDriverCode)) {
            selectedDriverCode =
                    selectedDriver.code();
        }

        leaderboard.getSelectionModel()
                .select(selectedDriver);

        updateTelemetry(selectedDriver);
    }

    private void updateReplayStatus(
            final ReplaySnapshot snapshot) {

        replayTimeLabel.setText(
                formatReplayTime(
                        snapshot.replaySeconds()));

        updatePlaybackTimeline(
                snapshot.replaySeconds());

        framesPerSecondLabel.setText(
                String.format(
                        Locale.ROOT,
                        "%.0f FPS · %.1fx%s",
                        smoothedFramesPerSecond,
                        replayClock.getPlaybackSpeed(),
                        replayClock.isPaused()
                                ? " · PAUSED"
                                : ""));

        updateRaceControl(
                snapshot.replaySeconds());
    }

    private void clearTelemetry() {
        selectedDriverLabel.setText("—");
        speedLabel.setText("—");
        rpmLabel.setText("—");
        gearLabel.setText("—");
        throttleLabel.setText("—");
        brakeLabel.setText("—");
        drsLabel.setText(
                drsAvailable
                        ? "—"
                        : "N/A · ACTIVE AERO");
        tyreLabel.setText("—");
        lapLabel.setText("—");
        gapLabel.setText("—");
        intervalLabel.setText("—");
    }

    private void seekTo(
            final double requestedSeconds) {

        replayClock.seekTo(
                PlaybackTimelineSupport.clamp(
                        requestedSeconds,
                        replayDurationSeconds));
    }

    private void skipBy(
            final double deltaSeconds) {

        seekTo(
                PlaybackTimelineSupport.skip(
                        replayClock.getReplaySeconds(),
                        deltaSeconds,
                        replayDurationSeconds));
    }

    private void updatePlaybackTimeline(
            final double replaySeconds) {

        final double clampedSeconds =
                PlaybackTimelineSupport.clamp(
                        replaySeconds,
                        replayDurationSeconds);

        timelineCurrentLabel.setText(
                formatReplayTime(
                        clampedSeconds));

        timelineDurationLabel.setText(
                formatReplayTime(
                        replayDurationSeconds));

        if (timelineSlider.isValueChanging()) {
            return;
        }

        updatingTimelineSlider = true;

        try {
            timelineSlider.setValue(
                    clampedSeconds);
        } finally {
            updatingTimelineSlider = false;
        }
    }

    private void updateRaceControl(
            final double replaySeconds) {

        final RaceControlTimeline.State state =
                raceControlTimeline.stateAt(
                        replaySeconds);

        racePhaseLabel.setText(
                RaceControlTextFormatter.phase(
                        state));

        racePhaseLabel.setStyle(
                RaceControlTextFormatter
                        .phaseStyle(
                                state.phase()));

        raceControlDrsLabel.setText(
                drsAvailable
                        ? RaceControlTextFormatter.drs(
                                state)
                        : "ACTIVE AERO");

        raceControlMessageLabel.setText(
                RaceControlTextFormatter.message(
                        state));
    }

    private void updateTelemetry(
            final DriverSnapshot driver) {

        selectedDriverLabel.setText(
                driver.code()
                + " · P"
                + driver.position());

        speedLabel.setText(
                TelemetryTextFormatter.speed(
                        driver));

        rpmLabel.setText(
                TelemetryTextFormatter.rpm(
                        driver));

        gearLabel.setText(
                TelemetryTextFormatter.gear(
                        driver));

        throttleLabel.setText(
                TelemetryTextFormatter.throttle(
                        driver));

        brakeLabel.setText(
                TelemetryTextFormatter.brake(
                        driver));

        drsLabel.setText(
                TelemetryTextFormatter.drs(
                        driver,
                        drsAvailable));

        tyreLabel.setText(
                TelemetryTextFormatter.tyre(
                        driver));

        lapLabel.setText(
                TelemetryTextFormatter.lap(
                        driver,
                        scheduledRaceLaps));

        gapLabel.setText(
                TelemetryTextFormatter.gap(
                        driver));

        intervalLabel.setText(
                TelemetryTextFormatter.interval(
                        driver));
    }

    private String formatReplayTime(
            final double replaySeconds) {

        return PlaybackTimelineSupport.formatTime(
                replaySeconds);
    }

    /**
     * Starts the application.
     *
     * @param args command-line arguments
     */
    public static void main(final String[] args) {
        launch(args);
    }
}
