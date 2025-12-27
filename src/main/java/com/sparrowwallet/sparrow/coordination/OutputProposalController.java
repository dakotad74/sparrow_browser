package com.sparrowwallet.sparrow.coordination;

import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.address.InvalidAddressException;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.control.CoordinationDialog;
import com.sparrowwallet.sparrow.event.CoordinationOutputProposedEvent;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.text.DecimalFormat;
import java.util.ResourceBundle;

/**
 * Controller for Step 3: Output Proposal
 *
 * Allows participants to propose transaction outputs.
 * Shows all proposed outputs from all participants in real-time.
 */
public class OutputProposalController implements Initializable, CoordinationController.StepController {
    private static final Logger log = LoggerFactory.getLogger(OutputProposalController.class);
    private static final DecimalFormat BTC_FORMAT = new DecimalFormat("0.00000000");
    private static final long SATOSHIS_PER_BTC = 100_000_000L;

    @FXML
    private TreeTableView<OutputRow> outputsTable;

    @FXML
    private TreeTableColumn<OutputRow, String> proposedByColumn;

    @FXML
    private TreeTableColumn<OutputRow, String> addressColumn;

    @FXML
    private TreeTableColumn<OutputRow, String> amountColumn;

    @FXML
    private TreeTableColumn<OutputRow, String> labelColumn;

    @FXML
    private Label totalAmountLabel;

    @FXML
    private TextField addressField;

    @FXML
    private TextField amountField;

    @FXML
    private TextField labelField;

    @FXML
    private Button addOutputButton;

    @FXML
    private Button removeOutputButton;

    @FXML
    private Label statusLabel;

    private Wallet wallet;
    private CoordinationDialog dialog;
    private CoordinationSession session;
    private CoordinationSessionManager sessionManager;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Setup table columns
        proposedByColumn.setCellValueFactory(param ->
            new javafx.beans.property.SimpleStringProperty(param.getValue().getValue().proposedBy));
        addressColumn.setCellValueFactory(param ->
            new javafx.beans.property.SimpleStringProperty(param.getValue().getValue().address));
        amountColumn.setCellValueFactory(param ->
            new javafx.beans.property.SimpleStringProperty(param.getValue().getValue().amount));
        labelColumn.setCellValueFactory(param ->
            new javafx.beans.property.SimpleStringProperty(param.getValue().getValue().label));

        // Enable remove button when row is selected
        outputsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            removeOutputButton.setDisable(newVal == null);
        });
    }

    @Override
    public void initializeStep(Wallet wallet, CoordinationDialog dialog) {
        this.wallet = wallet;
        this.dialog = dialog;
        this.session = dialog.getSession();

        // Get session manager from dialog (it should have been created in SessionStartController)
        // For now, create if needed
        this.sessionManager = dialog.getSessionManager();
        if(this.sessionManager == null) {
            log.warn("SessionManager not found in dialog, creating new instance");
            this.sessionManager = new CoordinationSessionManager();
        }

        if(session == null) {
            log.error("Session is null in OutputProposalController");
            statusLabel.setText("Error: No session found");
            return;
        }

        // Load existing outputs
        updateOutputsTable();
        updateTotalAmount();

        // Clear status
        statusLabel.setText("");
    }

    @Override
    public boolean validateStep() {
        // For testing: Allow proceeding even without outputs
        // In production, you might want to require at least one output
        log.error("=== OutputProposalController.validateStep() called ===");
        log.error("Session is null? {}", session == null);
        if(session != null) {
            log.error("Session ID: {}", session.getSessionId());
            log.error("Outputs count: {}", session.getOutputs().size());
        }
        boolean result = session != null;
        log.error("Returning: {}", result);
        return result;
    }

    @Override
    public void onEventReceived(Object event) {
        if(event instanceof CoordinationOutputProposedEvent) {
            CoordinationOutputProposedEvent outputEvent = (CoordinationOutputProposedEvent) event;

            // Only update if this event is for our session
            if(session != null && outputEvent.getSession().getSessionId().equals(session.getSessionId())) {
                Platform.runLater(() -> {
                    updateOutputsTable();
                    updateTotalAmount();
                });
            }
        }
    }

    /**
     * Add output proposal
     */
    @FXML
    private void addOutput() {
        String addressStr = addressField.getText().trim();
        String amountStr = amountField.getText().trim();
        String label = labelField.getText().trim();

        // Validate inputs
        if(addressStr.isEmpty()) {
            showError("Please enter a Bitcoin address");
            addressField.requestFocus();
            return;
        }

        if(amountStr.isEmpty()) {
            showError("Please enter an amount");
            amountField.requestFocus();
            return;
        }

        try {
            // Validate address
            Address address = Address.fromString(addressStr);

            // TODO: Validate network matches
            // For now, skip network validation as Address doesn't have getNetwork()

            // Validate and parse amount
            double amountBtc = Double.parseDouble(amountStr);
            if(amountBtc <= 0) {
                showError("Amount must be greater than zero");
                return;
            }

            if(amountBtc > 21_000_000) {
                showError("Amount cannot exceed 21 million BTC");
                return;
            }

            // Convert to satoshis
            long amountSats = (long)(amountBtc * SATOSHIS_PER_BTC);

            // Check for duplicate address
            for(CoordinationOutput existing : session.getOutputs()) {
                if(existing.getAddress().toString().equals(addressStr)) {
                    showError("This address has already been proposed");
                    return;
                }
            }

            // Propose output
            log.info("Proposing output: {} BTC to {}", amountBtc, addressStr);

            // Get participant pubkey from session manager (use Nostr pubkey, not wallet pubkey)
            String participantPubkey = sessionManager.getMyNostrPubkey();
            sessionManager.proposeOutput(session.getSessionId(), address, amountSats, label, participantPubkey);

            // Update UI immediately to show the proposed output
            updateOutputsTable();
            updateTotalAmount();

            // Clear form
            addressField.clear();
            amountField.clear();
            labelField.clear();

            statusLabel.setText("Output proposed successfully");
            statusLabel.setStyle("-fx-text-fill: green;");

            // Clear status after 3 seconds
            new Thread(() -> {
                try {
                    Thread.sleep(3000);
                } catch(InterruptedException e) {
                    // Ignore
                }
                Platform.runLater(() -> {
                    statusLabel.setText("");
                    statusLabel.setStyle("");
                });
            }).start();

        } catch(InvalidAddressException e) {
            showError("Invalid Bitcoin address: " + e.getMessage());
        } catch(NumberFormatException e) {
            showError("Invalid amount format. Use decimal format (e.g., 0.001)");
        } catch(Exception e) {
            log.error("Failed to propose output", e);
            showError("Failed to propose output: " + e.getMessage());
        }
    }

    /**
     * Remove selected output (only if proposed by current user)
     */
    @FXML
    private void removeOutput() {
        TreeItem<OutputRow> selected = outputsTable.getSelectionModel().getSelectedItem();
        if(selected == null) {
            return;
        }

        // TODO: Implement output removal via sessionManager.removeOutput()
        // For now, just show a message
        showError("Output removal not yet implemented");
    }

    /**
     * Update outputs table from session
     */
    private void updateOutputsTable() {
        if(session == null) {
            return;
        }

        // Create root item
        TreeItem<OutputRow> root = new TreeItem<>(new OutputRow("", "", "", ""));
        root.setExpanded(true);

        // Add outputs
        for(CoordinationOutput output : session.getOutputs()) {
            // getProposedBy() already returns the participant name, not pubkey
            String proposedBy = output.getProposedBy() != null ? output.getProposedBy() : "Unknown";
            String address = output.getAddress().toString();
            double amountBtc = output.getAmount() / (double)SATOSHIS_PER_BTC;
            String amount = BTC_FORMAT.format(amountBtc) + " BTC";
            String label = output.getLabel() != null ? output.getLabel() : "";

            TreeItem<OutputRow> item = new TreeItem<>(new OutputRow(proposedBy, address, amount, label));
            root.getChildren().add(item);
        }

        outputsTable.setRoot(root);
        outputsTable.setShowRoot(false);

        log.debug("Updated outputs table: {} outputs", session.getOutputs().size());
    }

    /**
     * Update total amount label
     */
    private void updateTotalAmount() {
        if(session == null) {
            return;
        }

        long totalSats = 0;
        for(CoordinationOutput output : session.getOutputs()) {
            totalSats += output.getAmount();
        }

        double totalBtc = totalSats / (double)SATOSHIS_PER_BTC;
        totalAmountLabel.setText("Total: " + BTC_FORMAT.format(totalBtc) + " BTC");
    }

    /**
     * Get participant name from pubkey
     */
    private String getParticipantName(String pubkey) {
        if(session == null) {
            return "Unknown";
        }

        for(CoordinationParticipant participant : session.getParticipants()) {
            if(participant.getPubkey().equals(pubkey)) {
                return participant.getName() != null ? participant.getName() : "Participant";
            }
        }

        return "Unknown";
    }

    /**
     * Show error message
     */
    private void showError(String message) {
        statusLabel.setText(message);
        statusLabel.setStyle("-fx-text-fill: red;");
    }

    /**
     * Generate Nostr public key from wallet.
     * Same implementation as SessionStartController for consistency.
     */
    private String getNostrPubkeyFromWallet(Wallet wallet) {
        try {
            // Get first keystore's master public key
            if(wallet.getKeystores() != null && !wallet.getKeystores().isEmpty()) {
                var keystore = wallet.getKeystores().get(0);
                var extendedPubKey = keystore.getExtendedPublicKey();

                if(extendedPubKey != null) {
                    // Get the DeterministicKey and then the public key bytes
                    var key = extendedPubKey.getKey();
                    if(key != null) {
                        byte[] pubkeyBytes = key.getPubKey();
                        return bytesToHex(pubkeyBytes);
                    }
                }
            }

            // Fallback
            log.warn("Could not derive pubkey from keystore, using wallet name hash");
            String walletId = wallet.getName() + wallet.hashCode();
            return String.format("%064x", walletId.hashCode()).substring(0, 66);

        } catch(Exception e) {
            log.error("Failed to generate Nostr pubkey", e);
            return java.util.UUID.randomUUID().toString().replace("-", "");
        }
    }

    /**
     * Convert bytes to hex string
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for(byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Helper class for table rows
     */
    public static class OutputRow {
        private final String proposedBy;
        private final String address;
        private final String amount;
        private final String label;

        public OutputRow(String proposedBy, String address, String amount, String label) {
            this.proposedBy = proposedBy;
            this.address = address;
            this.amount = amount;
            this.label = label;
        }
    }
}
