package com.sparrowwallet.sparrow.coordination;

import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.control.CoordinationDialog;
import com.sparrowwallet.sparrow.control.QRScanDialog;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.UUID;

/**
 * Controller for the session start step (create or join).
 */
public class SessionStartController implements Initializable, CoordinationController.StepController {
    private static final Logger log = LoggerFactory.getLogger(SessionStartController.class);

    @FXML
    private Spinner<Integer> participantCountSpinner;

    @FXML
    private Button createSessionButton;

    @FXML
    private TextField sessionIdField;

    @FXML
    private Button scanQRButton;

    @FXML
    private Button joinSessionButton;

    @FXML
    private Label walletNameLabel;

    @FXML
    private Label walletTypeLabel;

    private Wallet wallet;
    private CoordinationDialog dialog;
    private CoordinationSessionManager sessionManager;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Setup spinner
        SpinnerValueFactory<Integer> valueFactory =
            new SpinnerValueFactory.IntegerSpinnerValueFactory(2, 15, 2);
        participantCountSpinner.setValueFactory(valueFactory);

        // Enable join button when session ID is entered
        sessionIdField.textProperty().addListener((obs, oldVal, newVal) -> {
            joinSessionButton.setDisable(newVal == null || newVal.trim().isEmpty());
        });
    }

    @Override
    public void initializeStep(Wallet wallet, CoordinationDialog dialog) {
        this.wallet = wallet;
        this.dialog = dialog;

        // Create session manager (for now, create a new instance)
        // TODO: Get from AppServices singleton when integrated
        this.sessionManager = new CoordinationSessionManager();

        // Display wallet info
        walletNameLabel.setText(wallet.getFullDisplayName());
        walletTypeLabel.setText(wallet.getPolicyType().getName() + " wallet");

        // Check wallet compatibility
        if(!isWalletCompatible(wallet)) {
            showWarning("This wallet may not be suitable for coordination.\n" +
                       "Coordination works best with multisig wallets.");
        }
    }

    @Override
    public boolean validateStep() {
        // This step doesn't need validation - user creates/joins via buttons
        return true;
    }

    @Override
    public void onEventReceived(Object event) {
        // No events handled in this step
    }

    /**
     * Create a new coordination session
     */
    @FXML
    private void createSession() {
        try {
            int participantCount = participantCountSpinner.getValue();

            log.info("Creating coordination session with {} participants", participantCount);

            // Create session
            CoordinationSession session = sessionManager.createSession(wallet, participantCount);

            log.info("Session created: {}", session.getSessionId());

            // Store in dialog
            dialog.setSession(session);

            // Session created event will be posted, triggering navigation to next step

        } catch(Exception e) {
            log.error("Failed to create session", e);
            e.printStackTrace();
            showError("Failed to create session: " + e.getMessage());
        }
    }

    /**
     * Join an existing coordination session
     */
    @FXML
    private void joinSession() {
        String sessionId = sessionIdField.getText().trim();

        if(sessionId.isEmpty()) {
            showWarning("Please enter a session ID");
            return;
        }

        try {
            // Validate UUID format
            UUID.fromString(sessionId);

            log.info("Joining coordination session: {}", sessionId);

            // TODO: Generate or derive participant pubkey from wallet
            String participantPubkey = "temp-pubkey"; // Placeholder

            // Join session
            sessionManager.joinSession(sessionId, wallet, participantPubkey);

            log.info("Joined session: {}", sessionId);

            // Participant joined event will be posted

        } catch(IllegalArgumentException e) {
            showError("Invalid session ID format.\nSession ID should be a UUID.");
        } catch(Exception e) {
            log.error("Failed to join session", e);
            showError("Failed to join session: " + e.getMessage());
        }
    }

    /**
     * Scan QR code to get session ID
     */
    @FXML
    private void scanQRCode() {
        try {
            QRScanDialog qrScanDialog = new QRScanDialog();
            Optional<QRScanDialog.Result> result = qrScanDialog.showAndWait();

            result.ifPresent(scanResult -> {
                // Get scanned data from result
                String scannedData = scanResult.payload != null ? scanResult.payload : "";

                // Extract session ID from scanned data
                String sessionId = extractSessionId(scannedData);
                if(sessionId != null) {
                    sessionIdField.setText(sessionId);
                } else {
                    showWarning("Could not extract session ID from QR code");
                }
            });

        } catch(Exception e) {
            log.error("Failed to scan QR code", e);
            showError("Failed to scan QR code: " + e.getMessage());
        }
    }

    /**
     * Extract session ID from scanned data
     */
    private String extractSessionId(String scannedData) {
        // Try to parse as plain UUID
        try {
            UUID.fromString(scannedData.trim());
            return scannedData.trim();
        } catch(IllegalArgumentException e) {
            // Not a plain UUID
        }

        // Try to extract from URL format (nostr://coordinate/<session-id>)
        if(scannedData.startsWith("nostr://coordinate/")) {
            String sessionId = scannedData.substring("nostr://coordinate/".length());
            try {
                UUID.fromString(sessionId);
                return sessionId;
            } catch(IllegalArgumentException e) {
                // Invalid UUID
            }
        }

        return null;
    }

    /**
     * Check if wallet is compatible with coordination
     */
    private boolean isWalletCompatible(Wallet wallet) {
        // Coordination works with any wallet, but multisig is recommended
        return true;
    }

    /**
     * Show error dialog
     */
    private void showError(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("Coordination Error");
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    /**
     * Show warning dialog
     */
    private void showWarning(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Warning");
            alert.setHeaderText("Coordination Warning");
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
}
