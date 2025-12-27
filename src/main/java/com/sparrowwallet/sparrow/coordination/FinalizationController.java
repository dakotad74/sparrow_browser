package com.sparrowwallet.sparrow.coordination;

import com.sparrowwallet.drongo.SecureString;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.psbt.PSBTInput;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.control.CoordinationDialog;
import com.sparrowwallet.sparrow.control.WalletPasswordDialog;
import com.sparrowwallet.sparrow.event.CoordinationPSBTCreatedEvent;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.text.DecimalFormat;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * Controller for Step 5: Finalization
 *
 * Displays summary of coordinated transaction.
 * Creates PSBT with coordinated outputs and agreed fee rate.
 * Returns PSBT to dialog for signing workflow.
 */
public class FinalizationController implements Initializable, CoordinationController.StepController {
    private static final Logger log = LoggerFactory.getLogger(FinalizationController.class);
    private static final DecimalFormat BTC_FORMAT = new DecimalFormat("0.00000000");
    private static final DecimalFormat FEE_FORMAT = new DecimalFormat("0.0");
    private static final long SATOSHIS_PER_BTC = 100_000_000L;

    @FXML
    private Label sessionIdLabel;

    @FXML
    private Label participantCountLabel;

    @FXML
    private Label outputCountLabel;

    @FXML
    private Label totalAmountLabel;

    @FXML
    private Label feeRateLabel;

    @FXML
    private Label estimatedFeeLabel;

    @FXML
    private ListView<String> outputsList;

    // COMMENTED OUT - participantsList doesn't exist in finalization.fxml
    // @FXML
    // private ListView<String> participantsList;

    @FXML
    private Label statusLabel;

    @FXML
    private Button createPSBTButton;

    @FXML
    private ProgressBar progressBar;

    @FXML
    private Label progressLabel;

    private Wallet wallet;
    private CoordinationDialog dialog;
    private CoordinationSession session;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Nothing to initialize at load time
    }

    @Override
    public void initializeStep(Wallet wallet, CoordinationDialog dialog) {
        this.wallet = wallet;
        this.dialog = dialog;
        this.session = dialog.getSession();

        if(session == null) {
            log.error("Session is null in FinalizationController");
            statusLabel.setText("Error: No session found");
            statusLabel.setStyle("-fx-text-fill: red;");
            createPSBTButton.setDisable(true);
            return;
        }

        // Display summary
        displaySessionSummary();
        displayOutputs();
        displayParticipants();

        // Update status
        statusLabel.setText("Ready to create PSBT");
        statusLabel.setStyle("-fx-text-fill: green;");
    }

    @Override
    public boolean validateStep() {
        // This is the final step - no validation needed
        return true;
    }

    @Override
    public void onEventReceived(Object event) {
        if(event instanceof CoordinationPSBTCreatedEvent) {
            CoordinationPSBTCreatedEvent psbtEvent = (CoordinationPSBTCreatedEvent) event;

            if(session != null && psbtEvent.getSession().getSessionId().equals(session.getSessionId())) {
                Platform.runLater(() -> {
                    handlePSBTCreated(psbtEvent.getPsbt());
                });
            }
        }
    }

    /**
     * Create PSBT from coordinated session
     */
    @FXML
    private void createPSBT() {
        if(session == null) {
            showError("No session found");
            return;
        }

        // Validate session is ready
        if(session.getOutputs().isEmpty()) {
            showError("No outputs have been proposed");
            return;
        }

        if(session.getAgreedFeeRate() == null) {
            showError("Fee rate has not been agreed");
            return;
        }

        // Disable button and show progress
        createPSBTButton.setDisable(true);
        progressBar.setVisible(true);
        progressLabel.setVisible(true);
        progressLabel.setText("Creating PSBT...");
        statusLabel.setText("Building coordinated transaction...");

        // Create PSBT in background thread to avoid blocking UI
        new Thread(() -> {
            try {
                log.info("Creating PSBT for session: {}", session.getSessionId());

                // Build PSBT using CoordinationPSBTBuilder
                // Get current block height from AppServices
                Integer currentBlockHeight = AppServices.getCurrentBlockHeight();
                if(currentBlockHeight == null) {
                    log.warn("Current block height is null, using 0 as fallback");
                    currentBlockHeight = 0;
                }
                PSBT psbt = CoordinationPSBTBuilder.buildPSBT(session, wallet, currentBlockHeight);

                log.info("PSBT created successfully: {} inputs, {} outputs",
                        psbt.getPsbtInputs().size(),
                        psbt.getPsbtOutputs().size());

                // Update UI on JavaFX thread
                Platform.runLater(() -> {
                    handlePSBTCreated(psbt);
                });

            } catch(Exception e) {
                log.error("Failed to create PSBT", e);
                Platform.runLater(() -> {
                    progressBar.setVisible(false);
                    progressLabel.setVisible(false);
                    createPSBTButton.setDisable(false);
                    showError("Failed to create PSBT: " + e.getMessage());
                });
            }
        }).start();
    }

    /**
     * Handle PSBT creation success
     */
    private void handlePSBTCreated(PSBT psbt) {
        progressBar.setVisible(false);
        progressLabel.setVisible(false);

        // Store PSBT in dialog (will be returned when dialog closes)
        dialog.setPSBT(psbt);

        log.info("PSBT created, now signing and publishing to participants");

        // Automatically sign and publish the PSBT
        signAndPublishPSBT(psbt);
    }

    /**
     * Sign the PSBT with the wallet and publish to Nostr
     */
    private void signAndPublishPSBT(PSBT psbt) {
        progressBar.setVisible(true);
        progressLabel.setVisible(true);
        progressLabel.setText("Signing PSBT...");
        statusLabel.setText("Signing your PSBT...");

        new Thread(() -> {
            try {
                // Sign the PSBT with the wallet
                Wallet signingWallet = wallet.copy();

                if(signingWallet.isEncrypted()) {
                    Platform.runLater(() -> {
                        promptForPasswordAndSign(psbt, signingWallet);
                    });
                } else {
                    signAndPublish(psbt, signingWallet);
                }

            } catch(Exception e) {
                log.error("Failed to sign PSBT", e);
                Platform.runLater(() -> {
                    progressBar.setVisible(false);
                    progressLabel.setVisible(false);
                    showError("Failed to sign PSBT: " + e.getMessage());
                });
            }
        }).start();
    }

    /**
     * Prompt for wallet password and sign
     */
    private void promptForPasswordAndSign(PSBT psbt, Wallet encryptedWallet) {
        WalletPasswordDialog dlg = new WalletPasswordDialog(encryptedWallet.getMasterName(),
            WalletPasswordDialog.PasswordRequirement.LOAD);
        Optional<SecureString> password = dlg.showAndWait();

        if(password.isPresent()) {
            progressLabel.setText("Decrypting wallet...");

            new Thread(() -> {
                try {
                    Wallet decryptedWallet = encryptedWallet.copy();
                    decryptedWallet.decrypt(password.get());
                    signAndPublish(psbt, decryptedWallet);
                } catch(Exception e) {
                    log.error("Failed to decrypt wallet", e);
                    Platform.runLater(() -> {
                        progressBar.setVisible(false);
                        progressLabel.setVisible(false);
                        showError("Failed to decrypt wallet: " + e.getMessage());
                    });
                }
            }).start();
        } else {
            progressBar.setVisible(false);
            progressLabel.setVisible(false);
            statusLabel.setText("Signing cancelled. Close dialog to sign manually.");
        }
    }

    /**
     * Sign the PSBT and publish to Nostr
     */
    private void signAndPublish(PSBT psbt, Wallet unencryptedWallet) {
        try {
            log.info("Signing PSBT with wallet: {}", unencryptedWallet.getName());

            // Sign the PSBT
            Map<PSBTInput, WalletNode> signingNodes = unencryptedWallet.getSigningNodes(psbt);
            unencryptedWallet.sign(signingNodes);

            log.info("PSBT signed successfully");

            // Publish to Nostr
            Platform.runLater(() -> {
                progressLabel.setText("Publishing to participants...");
            });

            CoordinationSessionManager sessionManager = dialog.getSessionManager();
            if(sessionManager != null) {
                sessionManager.publishSignedPSBT(session.getSessionId(), psbt);
                log.info("Published signed PSBT to Nostr");

                Platform.runLater(() -> {
                    progressBar.setVisible(false);
                    progressLabel.setVisible(false);
                    statusLabel.setText("PSBT signed and published! Waiting for other participants...");
                    statusLabel.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");

                    showInfo("PSBT Signed and Published",
                        "Your PSBT has been signed and published to participants.\n\n" +
                        "Waiting for other participants to sign their PSBTs.\n" +
                        "The transaction will be automatically combined and finalized " +
                        "when all participants have signed.");
                });
            } else {
                log.error("SessionManager is null, cannot publish PSBT");
                Platform.runLater(() -> {
                    progressBar.setVisible(false);
                    progressLabel.setVisible(false);
                    showError("Session manager not available");
                });
            }

        } catch(Exception e) {
            log.error("Failed to sign or publish PSBT", e);
            Platform.runLater(() -> {
                progressBar.setVisible(false);
                progressLabel.setVisible(false);
                showError("Failed to sign or publish PSBT: " + e.getMessage());
            });
        }
    }

    /**
     * Display session summary
     */
    private void displaySessionSummary() {
        // Session ID
        sessionIdLabel.setText(session.getSessionId());

        // Participant count
        int participantCount = session.getParticipants().size();
        int expectedCount = session.getExpectedParticipants();
        participantCountLabel.setText(participantCount + " / " + expectedCount);

        // Output count
        outputCountLabel.setText(String.valueOf(session.getOutputs().size()));

        // Total amount
        long totalSats = 0;
        for(CoordinationOutput output : session.getOutputs()) {
            totalSats += output.getAmount();
        }
        double totalBtc = totalSats / (double)SATOSHIS_PER_BTC;
        totalAmountLabel.setText(BTC_FORMAT.format(totalBtc) + " BTC");

        // Fee rate
        Double agreedFee = session.getAgreedFeeRate();
        if(agreedFee != null) {
            feeRateLabel.setText(FEE_FORMAT.format(agreedFee) + " sat/vB");

            // Estimate fee
            int estimatedVBytes = estimateTransactionSize();
            long estimatedFeeSats = (long)(agreedFee * estimatedVBytes);
            double estimatedFeeBtc = estimatedFeeSats / (double)SATOSHIS_PER_BTC;
            estimatedFeeLabel.setText("~" + BTC_FORMAT.format(estimatedFeeBtc) + " BTC (" + estimatedFeeSats + " sats)");
        } else {
            feeRateLabel.setText("Not agreed");
            estimatedFeeLabel.setText("Unknown");
        }
    }

    /**
     * Display outputs list
     */
    private void displayOutputs() {
        outputsList.getItems().clear();

        for(CoordinationOutput output : session.getOutputs()) {
            double amountBtc = output.getAmount() / (double)SATOSHIS_PER_BTC;
            String participantName = getParticipantName(output.getProposedBy());
            String label = output.getLabel() != null ? " (" + output.getLabel() + ")" : "";

            String outputStr = String.format("%s BTC → %s%s [by %s]",
                                            BTC_FORMAT.format(amountBtc),
                                            output.getAddress().toString(),
                                            label,
                                            participantName);

            outputsList.getItems().add(outputStr);
        }
    }

    /**
     * Display participants list
     */
    private void displayParticipants() {
        // TODO: participantsList element doesn't exist in finalization.fxml
        // For now, just update the participant count label
        if(session != null && session.getParticipants() != null) {
            participantCountLabel.setText(String.valueOf(session.getParticipants().size()));
        }

        /* COMMENTED OUT - participantsList doesn't exist in FXML
        participantsList.getItems().clear();

        for(CoordinationParticipant participant : session.getParticipants()) {
            String name = participant.getName() != null ? participant.getName() : "Participant";
            String status = participant.getStatus() != null ? participant.getStatus().toString() : "JOINED";

            String participantStr = String.format("%s - %s", name, status);
            participantsList.getItems().add(participantStr);
        }
        */
    }

    /**
     * Get participant name from pubkey
     */
    private String getParticipantName(String pubkey) {
        log.error("=== getParticipantName called with pubkey: {} ===", pubkey);
        for(CoordinationParticipant participant : session.getParticipants()) {
            log.error("=== Checking participant: pubkey={}, name={} ===", participant.getPubkey(), participant.getName());
            if(participant.getPubkey().equals(pubkey)) {
                String name = participant.getName() != null ? participant.getName() : "Participant";
                log.error("=== Found participant, returning name: {} ===", name);
                return name;
            }
        }
        log.error("=== Participant not found, returning 'Unknown' ===");
        return "Unknown";
    }

    /**
     * Estimate transaction size in vBytes
     */
    private int estimateTransactionSize() {
        // Very rough estimate
        // Base: ~10 vBytes
        // Each input: ~68 vBytes (P2WPKH) to ~91 vBytes (P2SH-P2WPKH)
        // Each output: ~31 vBytes (P2WPKH) to ~43 vBytes (P2SH)

        int outputCount = session.getOutputs().size();
        int participantCount = session.getParticipants().size();

        // Assume each participant contributes 1 input on average
        int estimatedInputs = participantCount;
        // Outputs = coordinated outputs + change outputs (1 per participant)
        int estimatedOutputs = outputCount + participantCount;

        int baseSize = 10;
        int inputSize = estimatedInputs * 80; // Average
        int outputSize = estimatedOutputs * 35; // Average

        return baseSize + inputSize + outputSize;
    }

    /**
     * Show error message
     */
    private void showError(String message) {
        statusLabel.setText(message);
        statusLabel.setStyle("-fx-text-fill: red;");

        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("PSBT Creation Error");
        alert.setHeaderText("Failed to Create PSBT");
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Show info message
     */
    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText("PSBT Created Successfully");
        alert.setContentText(message);
        alert.showAndWait();
    }
}
