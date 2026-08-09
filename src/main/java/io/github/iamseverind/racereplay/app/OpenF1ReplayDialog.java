package io.github.iamseverind.racereplay.app;

import io.github.iamseverind.racereplay.openf1.OpenF1Client;
import io.github.iamseverind.racereplay.openf1.OpenF1ReplayImporter;
import io.github.iamseverind.racereplay.openf1.OpenF1Session;
import java.time.Clock;
import java.time.Instant;
import java.time.Year;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.StringConverter;

/**
 * Selects and imports completed OpenF1 races without blocking JavaFX.
 */
final class OpenF1ReplayDialog {

    private static final int FIRST_OPENF1_YEAR = 2023;

    private final Stage owner;
    private final OpenF1Client client;
    private final Clock clock;
    private final Instant completedBefore;

    private List<OpenF1Session> completedSessions =
            List.of();

    private OpenF1ReplayDialog(
            final Stage owner,
            final OpenF1Client client,
            final Clock clock,
            final Instant completedBefore) {

        this.owner =
                Objects.requireNonNull(
                        owner,
                        "owner");

        this.client =
                Objects.requireNonNull(
                        client,
                        "client");

        this.clock =
                Objects.requireNonNull(
                        clock,
                        "clock");

        this.completedBefore =
                Objects.requireNonNull(
                        completedBefore,
                        "completedBefore");
    }

    /**
     * Opens the completed-session selector.
     *
     * @param owner owning application stage
     * @param applicationOpenedAt UTC application-start cutoff
     */
    static void show(
            final Stage owner,
            final Instant applicationOpenedAt) {

        new OpenF1ReplayDialog(
                owner,
                new OpenF1Client(),
                Clock.systemUTC(),
                applicationOpenedAt)
                .showSelector();
    }

    private void showSelector() {
        final Dialog<OpenF1Session> dialog =
                new Dialog<>();

        dialog.initOwner(owner);
        dialog.setTitle("Import OpenF1 replay");
        dialog.setHeaderText(
                "Choose a completed Grand Prix or Sprint");

        final ButtonType importButtonType =
                new ButtonType(
                        "Import",
                        ButtonBar.ButtonData.OK_DONE);

        dialog.getDialogPane()
                .getButtonTypes()
                .addAll(
                        importButtonType,
                        ButtonType.CANCEL);

        final ComboBox<Integer> yearBox =
                new ComboBox<>();

        final ComboBox<String> countryBox =
                new ComboBox<>();

        final ComboBox<OpenF1Session> sessionBox =
                new ComboBox<>();

        configureSessionText(sessionBox);

        yearBox.setMaxWidth(Double.MAX_VALUE);
        countryBox.setMaxWidth(Double.MAX_VALUE);
        sessionBox.setMaxWidth(Double.MAX_VALUE);

        final Node importButton =
                dialog.getDialogPane()
                        .lookupButton(
                                importButtonType);

        importButton.setDisable(true);

        final ProgressIndicator progress =
                new ProgressIndicator();

        progress.setPrefSize(22.0, 22.0);

        final Label status =
                new Label(
                        "Loading completed races from OpenF1...");

        final HBox loadingRow =
                new HBox(
                        10.0,
                        progress,
                        status);

        final GridPane fields =
                createFields(
                        yearBox,
                        countryBox,
                        sessionBox);

        final Label notice =
                new Label(
                        "Only completed, non-cancelled Grands Prix and "
                        + "Sprints whose OpenF1 date_end is not later "
                        + "than the time this application was opened "
                        + "are listed. Data is downloaded only after "
                        + "you press Import.");

        notice.setWrapText(true);

        final VBox content =
                new VBox(
                        14.0,
                        loadingRow,
                        fields,
                        notice);

        content.setPadding(
                new Insets(4.0));

        content.setPrefWidth(520.0);

        dialog.getDialogPane()
                .setContent(content);

        yearBox.valueProperty()
                .addListener(
                        (observable, previous, selected) ->
                                updateCountries(
                                        selected,
                                        countryBox,
                                        sessionBox));

        countryBox.valueProperty()
                .addListener(
                        (observable, previous, selected) ->
                                updateSessions(
                                        yearBox.getValue(),
                                        selected,
                                        sessionBox));

        sessionBox.valueProperty()
                .addListener(
                        (observable, previous, selected) ->
                                importButton.setDisable(
                                        selected == null));

        dialog.setResultConverter(
                button -> button == importButtonType
                        ? sessionBox.getValue()
                        : null);

        loadCatalog(
                completedBefore,
                yearBox,
                progress,
                status);

        dialog.showAndWait()
                .ifPresent(
                        this::confirmImport);
    }

    private void loadCatalog(
            final Instant openedAt,
            final ComboBox<Integer> yearBox,
            final ProgressIndicator progress,
            final Label status) {

        final Task<List<OpenF1Session>> task =
                new Task<>() {
                    @Override
                    protected List<OpenF1Session> call()
                            throws Exception {

                        final List<OpenF1Session> sessions =
                                new ArrayList<>();

                        final int latestYear =
                                Year.now(clock)
                                        .getValue();

                        for (int year = latestYear;
                                year >= FIRST_OPENF1_YEAR;
                                year--) {

                            updateMessage(
                                    "Loading completed sessions for "
                                    + year
                                    + "...");

                            sessions.addAll(
                                    client.listCompletedSessions(
                                            year,
                                            openedAt));
                        }

                        return List.copyOf(sessions);
                    }
                };

        status.textProperty()
                .bind(
                        task.messageProperty());

        task.setOnSucceeded(
                event -> {
                    completedSessions =
                            task.getValue();

                    status.textProperty()
                            .unbind();

                    progress.setVisible(false);

                    if (completedSessions.isEmpty()) {
                        status.setText(
                                "OpenF1 returned no completed sessions.");
                        return;
                    }

                    status.setText(
                            "Completed sessions available: "
                            + completedSessions.size());

                    final List<Integer> years =
                            completedSessions.stream()
                                    .map(
                                            OpenF1Session::year)
                                    .distinct()
                                    .sorted(
                                            Comparator.reverseOrder())
                                    .toList();

                    yearBox.getItems()
                            .setAll(years);

                    yearBox.getSelectionModel()
                            .selectFirst();
                });

        task.setOnFailed(
                event -> {
                    status.textProperty()
                            .unbind();

                    progress.setVisible(false);

                    status.setText(
                            "Could not load the OpenF1 catalog: "
                            + errorMessage(
                                    task.getException()));
                });

        Thread.startVirtualThread(task);
    }

    private void updateCountries(
            final Integer year,
            final ComboBox<String> countryBox,
            final ComboBox<OpenF1Session> sessionBox) {

        countryBox.getItems()
                .clear();

        sessionBox.getItems()
                .clear();

        if (year == null) {
            return;
        }

        final List<String> countries =
                completedSessions.stream()
                        .filter(
                                session -> session.year()
                                        == year)
                        .map(
                                OpenF1Session::countryName)
                        .distinct()
                        .sorted(
                                String.CASE_INSENSITIVE_ORDER)
                        .toList();

        countryBox.getItems()
                .setAll(countries);

        countryBox.getSelectionModel()
                .selectFirst();
    }

    private void updateSessions(
            final Integer year,
            final String country,
            final ComboBox<OpenF1Session> sessionBox) {

        sessionBox.getItems()
                .clear();

        if (year == null || country == null) {
            return;
        }

        final List<OpenF1Session> sessions =
                completedSessions.stream()
                        .filter(
                                session -> session.year()
                                        == year)
                        .filter(
                                session -> session
                                        .countryName()
                                        .equals(country))
                        .sorted(
                                Comparator.comparing(
                                        OpenF1Session::dateEnd)
                                        .reversed())
                        .toList();

        sessionBox.getItems()
                .setAll(sessions);

        sessionBox.getSelectionModel()
                .selectFirst();
    }

    private void confirmImport(
            final OpenF1Session session) {

        final Alert confirmation =
                new Alert(
                        Alert.AlertType.CONFIRMATION);

        confirmation.initOwner(owner);
        confirmation.setTitle("Confirm OpenF1 import");
        confirmation.setHeaderText(
                session.year()
                + " · "
                + session.countryName()
                + " · "
                + session.sessionName());

        confirmation.setContentText(
                "Circuit: "
                + session.circuitShortName()
                + System.lineSeparator()
                + System.lineSeparator()
                + "This may download a large amount of third-party "
                + "data to your local application cache. Continue?");

        confirmation.showAndWait()
                .filter(
                        ButtonType.OK::equals)
                .ifPresent(
                        ignored -> importSession(
                                session));
    }

    private void importSession(
            final OpenF1Session session) {

        final Dialog<Void> progressDialog =
                new Dialog<>();

        progressDialog.initOwner(owner);
        progressDialog.setTitle("Importing OpenF1 replay");
        progressDialog.setHeaderText(
                session.countryName()
                + " · "
                + session.sessionName());

        progressDialog.getDialogPane()
                .getButtonTypes()
                .add(ButtonType.CANCEL);

        final ProgressIndicator progress =
                new ProgressIndicator();

        final Label message =
                new Label(
                        "Preparing import...");

        message.setWrapText(true);

        final VBox content =
                new VBox(
                        14.0,
                        progress,
                        message);

        content.setPrefWidth(460.0);

        progressDialog.getDialogPane()
                .setContent(content);

        final Task<OpenF1ReplayImporter.ImportResult> task =
                new Task<>() {
                    @Override
                    protected OpenF1ReplayImporter.ImportResult call()
                            throws Exception {

                        return new OpenF1ReplayImporter()
                                .importAndActivate(
                                        session,
                                        this::updateMessage);
                    }
                };

        message.textProperty()
                .bind(
                        task.messageProperty());

        task.setOnSucceeded(
                event -> {
                    progressDialog.close();
                    showImportComplete(
                            task.getValue());
                });

        task.setOnFailed(
                event -> {
                    progressDialog.close();
                    showImportError(
                            task.getException());
                });

        task.setOnCancelled(
                event -> progressDialog.close());

        progressDialog.setOnCloseRequest(
                event -> task.cancel(true));

        Thread.startVirtualThread(task);
        progressDialog.show();
    }

    private void showImportComplete(
            final OpenF1ReplayImporter.ImportResult result) {

        final Alert alert =
                new Alert(
                        Alert.AlertType.INFORMATION);

        alert.initOwner(owner);
        alert.setTitle("OpenF1 import complete");
        alert.setHeaderText(
                "Replay imported and selected");

        alert.setContentText(
                result.session()
                        .circuitShortName()
                + System.lineSeparator()
                + result.driverCount()
                + " drivers · "
                + result.frameCount()
                + " frames"
                + System.lineSeparator()
                + System.lineSeparator()
                + "Restart Race Replay Lab to load the replay.");

        alert.showAndWait();
    }

    private void showImportError(
            final Throwable throwable) {

        final Alert alert =
                new Alert(
                        Alert.AlertType.ERROR);

        alert.initOwner(owner);
        alert.setTitle("OpenF1 import failed");
        alert.setHeaderText(
                "The previous replay selection was not changed");

        alert.setContentText(
                errorMessage(throwable));

        alert.showAndWait();
    }

    private static GridPane createFields(
            final ComboBox<Integer> yearBox,
            final ComboBox<String> countryBox,
            final ComboBox<OpenF1Session> sessionBox) {

        final GridPane fields =
                new GridPane();

        fields.setHgap(12.0);
        fields.setVgap(10.0);

        fields.addRow(
                0,
                new Label("Year"),
                yearBox);

        fields.addRow(
                1,
                new Label("Country"),
                countryBox);

        fields.addRow(
                2,
                new Label("Session"),
                sessionBox);

        GridPane.setHgrow(
                yearBox,
                Priority.ALWAYS);

        GridPane.setHgrow(
                countryBox,
                Priority.ALWAYS);

        GridPane.setHgrow(
                sessionBox,
                Priority.ALWAYS);

        return fields;
    }

    private static void configureSessionText(
            final ComboBox<OpenF1Session> sessionBox) {

        sessionBox.setConverter(
                new StringConverter<>() {
                    @Override
                    public String toString(
                            final OpenF1Session session) {

                        if (session == null) {
                            return "";
                        }

                        return session.sessionName()
                                + " · "
                                + session.circuitShortName();
                    }

                    @Override
                    public OpenF1Session fromString(
                            final String value) {

                        throw new UnsupportedOperationException(
                                "Session selection is read-only.");
                    }
                });
    }

    private static String errorMessage(
            final Throwable throwable) {

        if (throwable == null) {
            return "Unknown error.";
        }

        final String message =
                throwable.getMessage();

        if (message == null || message.isBlank()) {
            return throwable.getClass()
                    .getSimpleName();
        }

        return String.format(
                Locale.ROOT,
                "%s",
                message);
    }
}
