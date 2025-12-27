package com.sparrowwallet.sparrow.p2p.identity;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * Controller for the Identity Manager dialog.
 *
 * Allows users to:
 * - View all identities (ephemeral and persistent)
 * - Create new identities
 * - Switch active identity
 * - Delete identities
 * - Export keys for backup
 * - View reputation details
 */
public class IdentityManagerController implements Initializable {
    private static final Logger log = LoggerFactory.getLogger(IdentityManagerController.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm");

    // Active identity section
    @FXML
    private Label activeIdentityNameLabel;

    @FXML
    private Label activeIdentityNpubLabel;

    @FXML
    private Label activeIdentityInfoLabel;

    @FXML
    private Button switchActiveButton;

    @FXML
    private Button deleteActiveButton;

    @FXML
    private Button exportActiveButton;

    // Persistent identities section
    @FXML
    private ListView<NostrIdentity> persistentListView;

    @FXML
    private VBox persistentEmptyState;

    // Ephemeral identities section
    @FXML
    private ListView<NostrIdentity> ephemeralListView;

    @FXML
    private VBox ephemeralEmptyState;

    // Create new identity section
    @FXML
    private RadioButton persistentTypeRadio;

    @FXML
    private RadioButton ephemeralTypeRadio;

    @FXML
    private ToggleGroup identityTypeGroup;

    @FXML
    private TextField displayNameField;

    @FXML
    private CheckBox randomNameCheckbox;

    @FXML
    private CheckBox importKeysCheckbox;

    @FXML
    private TextArea importNsecArea;

    @FXML
    private Button createIdentityButton;

    @FXML
    private Button cancelButton;

    // Manager and state
    private NostrIdentityManager identityManager;
    private NostrIdentity selectedIdentity;
    private Stage dialogStage;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        log.info("Initializing Identity Manager dialog");

        identityManager = NostrIdentityManager.getInstance();

        // Setup active identity section
        setupActiveIdentitySection();

        // Setup identity lists
        setupIdentityLists();

        // Setup create section
        setupCreateSection();

        // Load identities
        refreshIdentities();

        // Setup buttons
        cancelButton.setOnAction(e -> closeDialog());
    }

    /**
     * Setup active identity section
     */
    private void setupActiveIdentitySection() {
        NostrIdentity active = identityManager.getActiveIdentity();
        updateActiveIdentityDisplay(active);

        switchActiveButton.setOnAction(e -> switchActiveIdentity());
        deleteActiveButton.setOnAction(e -> deleteActiveIdentity());
        exportActiveButton.setOnAction(e -> exportActiveIdentity());
    }

    /**
     * Update active identity display
     */
    private void updateActiveIdentityDisplay(NostrIdentity identity) {
        if (identity == null) {
            activeIdentityNameLabel.setText("No active identity");
            activeIdentityNpubLabel.setText("");
            activeIdentityInfoLabel.setText("Create or select an identity to activate");
            switchActiveButton.setDisable(true);
            deleteActiveButton.setDisable(true);
            exportActiveButton.setDisable(true);
            return;
        }

        activeIdentityNameLabel.setText(identity.getDisplayName() + " (" + identity.getType() + ")");
        activeIdentityNpubLabel.setText(identity.getNpub());

        String info = "Created: " + identity.getCreatedAt().format(DATE_FORMATTER);
        if (identity.isPersistent()) {
            info += " • Trades: " + identity.getCompletedTrades();
            if (identity.getAverageRating() != null && identity.getAverageRating() > 0) {
                info += " • Rating: " + String.format("%.1f", identity.getAverageRating());
            }
        } else {
            info += " • Expires: After trade completion";
        }
        activeIdentityInfoLabel.setText(info);

        switchActiveButton.setDisable(false);
        deleteActiveButton.setDisable(false);
        exportActiveButton.setDisable(false);
    }

    /**
     * Setup identity lists
     */
    private void setupIdentityLists() {
        // Custom cell factory for persistent identities
        persistentListView.setCellFactory(lv -> new IdentityListCell(true));
        persistentListView.getSelectionModel().selectedItemProperty().addListener(
            (obs, oldVal, newVal) -> onIdentitySelected(newVal)
        );

        // Custom cell factory for ephemeral identities
        ephemeralListView.setCellFactory(lv -> new IdentityListCell(false));
        ephemeralListView.getSelectionModel().selectedItemProperty().addListener(
            (obs, oldVal, newVal) -> onIdentitySelected(newVal)
        );
    }

    /**
     * Setup create identity section
     */
    private void setupCreateSection() {
        // Radio buttons
        identityTypeGroup = new ToggleGroup();
        persistentTypeRadio.setToggleGroup(identityTypeGroup);
        ephemeralTypeRadio.setToggleGroup(identityTypeGroup);
        ephemeralTypeRadio.setSelected(true);

        // Type change listener
        identityTypeGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            boolean isPersistent = newVal == persistentTypeRadio;
            randomNameCheckbox.setDisable(isPersistent);
            if (isPersistent) {
                randomNameCheckbox.setSelected(false);
                displayNameField.setDisable(false);
            }
        });

        // Random name checkbox
        randomNameCheckbox.setSelected(true);
        randomNameCheckbox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            displayNameField.setDisable(newVal);
            if (newVal) {
                displayNameField.clear();
            }
        });

        // Import keys checkbox
        importKeysCheckbox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            importNsecArea.setVisible(newVal);
            importNsecArea.setManaged(newVal);
        });
        importNsecArea.setVisible(false);
        importNsecArea.setManaged(false);

        // Create button
        createIdentityButton.setOnAction(e -> createNewIdentity());
    }

    /**
     * Refresh identity lists
     */
    private void refreshIdentities() {
        // Get identities
        List<NostrIdentity> persistent = identityManager.getPersistentIdentities();
        List<NostrIdentity> ephemeral = identityManager.getEphemeralIdentities();

        // Update persistent list
        persistentListView.getItems().clear();
        persistentListView.getItems().addAll(persistent);
        persistentEmptyState.setVisible(persistent.isEmpty());
        persistentEmptyState.setManaged(persistent.isEmpty());
        persistentListView.setVisible(!persistent.isEmpty());
        persistentListView.setManaged(!persistent.isEmpty());

        // Update ephemeral list
        ephemeralListView.getItems().clear();
        ephemeralListView.getItems().addAll(ephemeral);
        ephemeralEmptyState.setVisible(ephemeral.isEmpty());
        ephemeralEmptyState.setManaged(ephemeral.isEmpty());
        ephemeralListView.setVisible(!ephemeral.isEmpty());
        ephemeralListView.setManaged(!ephemeral.isEmpty());

        log.info("Refreshed identities: {} persistent, {} ephemeral", persistent.size(), ephemeral.size());
    }

    /**
     * Handle identity selection
     */
    private void onIdentitySelected(NostrIdentity identity) {
        selectedIdentity = identity;
        log.debug("Selected identity: {}", identity != null ? identity.getDisplayName() : "none");
    }

    /**
     * Switch active identity
     */
    private void switchActiveIdentity() {
        if (selectedIdentity == null) {
            showError("Please select an identity to activate");
            return;
        }

        try {
            identityManager.setActiveIdentity(selectedIdentity);
            updateActiveIdentityDisplay(selectedIdentity);
            showInfo("Active identity changed to: " + selectedIdentity.getDisplayName());
            log.info("Switched active identity to: {}", selectedIdentity.getDisplayName());
        } catch (Exception e) {
            log.error("Failed to switch identity", e);
            showError("Failed to switch identity: " + e.getMessage());
        }
    }

    /**
     * Delete active identity
     */
    private void deleteActiveIdentity() {
        NostrIdentity active = identityManager.getActiveIdentity();
        if (active == null) {
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Identity");
        confirm.setHeaderText("Delete " + active.getDisplayName() + "?");
        confirm.setContentText("This action cannot be undone.\nMake sure you have backed up your keys if needed.");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                identityManager.deleteIdentity(active.getId());
                updateActiveIdentityDisplay(null);
                refreshIdentities();
                showInfo("Identity deleted successfully");
                log.info("Deleted identity: {}", active.getDisplayName());
            } catch (Exception e) {
                log.error("Failed to delete identity", e);
                showError("Failed to delete identity: " + e.getMessage());
            }
        }
    }

    /**
     * Export active identity
     */
    private void exportActiveIdentity() {
        NostrIdentity active = identityManager.getActiveIdentity();
        if (active == null) {
            return;
        }

        try {
            var export = identityManager.exportIdentity(active.getId());

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Export Identity");
            alert.setHeaderText("Identity Keys");
            alert.setContentText(
                "Name: " + export.get("displayName") + "\n" +
                "Type: " + export.get("type") + "\n\n" +
                "Public key (npub):\n" + export.get("npub") + "\n\n" +
                "Private key (nsec):\n" + export.get("nsec") + "\n\n" +
                "⚠️ Keep your private key (nsec) secret!"
            );
            alert.setResizable(true);
            alert.getDialogPane().setPrefWidth(600);
            alert.showAndWait();

            log.info("Exported identity: {}", active.getDisplayName());
        } catch (Exception e) {
            log.error("Failed to export identity", e);
            showError("Failed to export identity: " + e.getMessage());
        }
    }

    /**
     * Create new identity
     */
    private void createNewIdentity() {
        try {
            boolean isPersistent = persistentTypeRadio.isSelected();
            String displayName = displayNameField.getText().trim();

            // Validate
            if (isPersistent && displayName.isEmpty()) {
                showError("Persistent identities require a display name");
                return;
            }

            // Check if importing keys
            if (importKeysCheckbox.isSelected()) {
                String nsec = importNsecArea.getText().trim();
                if (nsec.isEmpty()) {
                    showError("Please provide an nsec key to import");
                    return;
                }

                NostrIdentity imported = identityManager.importIdentity(
                    nsec,
                    displayName.isEmpty() ? null : displayName,
                    isPersistent ? IdentityType.PERSISTENT : IdentityType.EPHEMERAL
                );

                showInfo("Identity imported successfully: " + imported.getDisplayName());
                refreshIdentities();
                clearCreateForm();
                return;
            }

            // Create new identity
            NostrIdentity newIdentity;
            if (isPersistent) {
                newIdentity = identityManager.createPersistentIdentity(displayName);
            } else {
                newIdentity = identityManager.createEphemeralIdentity(
                    displayName.isEmpty() ? null : displayName
                );
            }

            showInfo("Identity created successfully: " + newIdentity.getDisplayName());
            refreshIdentities();
            clearCreateForm();

            log.info("Created new {} identity: {}",
                isPersistent ? "persistent" : "ephemeral",
                newIdentity.getDisplayName());

        } catch (Exception e) {
            log.error("Failed to create identity", e);
            showError("Failed to create identity: " + e.getMessage());
        }
    }

    /**
     * Clear create form
     */
    private void clearCreateForm() {
        displayNameField.clear();
        importNsecArea.clear();
        ephemeralTypeRadio.setSelected(true);
        randomNameCheckbox.setSelected(true);
        importKeysCheckbox.setSelected(false);
    }

    /**
     * Close dialog
     */
    private void closeDialog() {
        if (dialogStage != null) {
            dialogStage.close();
        }
    }

    /**
     * Set dialog stage
     */
    public void setDialogStage(Stage stage) {
        this.dialogStage = stage;
    }

    /**
     * Show error alert
     */
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Show info alert
     */
    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Success");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Custom list cell for identities
     */
    private static class IdentityListCell extends ListCell<NostrIdentity> {
        private final boolean showReputation;

        public IdentityListCell(boolean showReputation) {
            this.showReputation = showReputation;
        }

        @Override
        protected void updateItem(NostrIdentity identity, boolean empty) {
            super.updateItem(identity, empty);

            if (empty || identity == null) {
                setText(null);
                setGraphic(null);
                return;
            }

            StringBuilder text = new StringBuilder();
            text.append(identity.getDisplayName());

            if (showReputation && identity.isPersistent()) {
                String stars = identity.getReputationStars();
                if (!stars.isEmpty()) {
                    text.append(" ").append(stars);
                }
            }

            text.append("\n");
            text.append(identity.getShortNpub());
            text.append("\n");
            text.append("Created: ").append(identity.getCreatedAt().format(DATE_FORMATTER));

            if (showReputation && identity.isPersistent()) {
                text.append(" • Trades: ").append(identity.getCompletedTrades());
                if (identity.getAverageRating() != null && identity.getAverageRating() > 0) {
                    text.append(" • Rating: ").append(String.format("%.1f", identity.getAverageRating()));
                }
            } else if (identity.isEphemeral()) {
                text.append(" • Status: ").append(identity.getStatus());
            }

            setText(text.toString());
        }
    }
}
