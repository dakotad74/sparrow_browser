package com.sparrowwallet.sparrow.coordination;

import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.control.CoordinationDialog;
import com.sparrowwallet.sparrow.event.CoordinationFeeAgreedEvent;
import com.sparrowwallet.sparrow.event.CoordinationFeeProposedEvent;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;

/**
 * Controller for Step 4: Fee Agreement
 *
 * Allows participants to propose fee rates.
 * Shows all fee proposals from all participants.
 * Automatically selects the highest fee rate (safest for confirmation).
 */
public class FeeAgreementController implements Initializable, CoordinationController.StepController {
    private static final Logger log = LoggerFactory.getLogger(FeeAgreementController.class);
    private static final DecimalFormat FEE_FORMAT = new DecimalFormat("0.0");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final double MIN_FEE_RATE = 1.0;
    private static final double MAX_FEE_RATE = 1000.0;
    private static final double DEFAULT_FEE_RATE = 10.0;

    @FXML
    private TableView<FeeProposalRow> feeProposalsTable;

    @FXML
    private TableColumn<FeeProposalRow, String> participantColumn;

    @FXML
    private TableColumn<FeeProposalRow, String> feeRateColumn;

    @FXML
    private TableColumn<FeeProposalRow, String> proposedAtColumn;

    @FXML
    private Label proposalCountLabel;

    @FXML
    private Slider feeRateSlider;

    @FXML
    private TextField feeRateField;

    @FXML
    private Label feeEstimateLabel;

    @FXML
    private Button proposeFeeButton;

    @FXML
    private Label proposalStatusLabel;

    @FXML
    private Label agreedFeeLabel;

    @FXML
    private Label agreedFeeExplanation;

    @FXML
    private Label statusLabel;

    @FXML
    private Label readyStatusLabel;

    private Wallet wallet;
    private CoordinationDialog dialog;
    private CoordinationSession session;
    private CoordinationSessionManager sessionManager;
    private boolean hasProposedFee = false;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Setup table columns
        participantColumn.setCellValueFactory(param ->
            new javafx.beans.property.SimpleStringProperty(param.getValue().participant));
        feeRateColumn.setCellValueFactory(param ->
            new javafx.beans.property.SimpleStringProperty(param.getValue().feeRate));
        proposedAtColumn.setCellValueFactory(param ->
            new javafx.beans.property.SimpleStringProperty(param.getValue().proposedAt));

        // Setup slider and field binding
        feeRateSlider.setMin(MIN_FEE_RATE);
        feeRateSlider.setMax(MAX_FEE_RATE);
        feeRateSlider.setValue(DEFAULT_FEE_RATE);

        // Bind slider to text field
        feeRateSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            feeRateField.setText(FEE_FORMAT.format(newVal.doubleValue()));
            updateFeeEstimate(newVal.doubleValue());
        });

        // Bind text field to slider
        feeRateField.textProperty().addListener((obs, oldVal, newVal) -> {
            try {
                double value = Double.parseDouble(newVal);
                if(value >= MIN_FEE_RATE && value <= MAX_FEE_RATE) {
                    feeRateSlider.setValue(value);
                }
            } catch(NumberFormatException e) {
                // Ignore invalid input
            }
        });

        // Initialize with default value
        feeRateField.setText(FEE_FORMAT.format(DEFAULT_FEE_RATE));
        updateFeeEstimate(DEFAULT_FEE_RATE);
    }

    @Override
    public void initializeStep(Wallet wallet, CoordinationDialog dialog) {
        this.wallet = wallet;
        this.dialog = dialog;
        this.session = dialog.getSession();

        // Create session manager (temporary workaround)
        // TODO: Get from AppServices singleton
        this.sessionManager = new CoordinationSessionManager();

        if(session == null) {
            log.error("Session is null in FeeAgreementController");
            statusLabel.setText("Error: No session found");
            return;
        }

        // Load existing fee proposals
        updateFeeProposalsTable();
        updateAgreedFee();

        // Clear status
        statusLabel.setText("");
    }

    @Override
    public boolean validateStep() {
        // Can proceed if fee has been agreed
        return session != null && session.getAgreedFeeRate() != null;
    }

    @Override
    public void onEventReceived(Object event) {
        if(event instanceof CoordinationFeeProposedEvent) {
            CoordinationFeeProposedEvent feeEvent = (CoordinationFeeProposedEvent) event;

            // Only update if this event is for our session
            if(session != null && feeEvent.getSession().getSessionId().equals(session.getSessionId())) {
                Platform.runLater(() -> {
                    updateFeeProposalsTable();
                    checkAndAgreeFee();
                });
            }
        } else if(event instanceof CoordinationFeeAgreedEvent) {
            CoordinationFeeAgreedEvent agreedEvent = (CoordinationFeeAgreedEvent) event;

            if(session != null && agreedEvent.getSession().getSessionId().equals(session.getSessionId())) {
                Platform.runLater(() -> {
                    updateAgreedFee();
                });
            }
        }
    }

    /**
     * Propose fee rate
     */
    @FXML
    private void proposeFee() {
        try {
            double feeRate = Double.parseDouble(feeRateField.getText());

            if(feeRate < MIN_FEE_RATE) {
                showError("Fee rate must be at least " + MIN_FEE_RATE + " sat/vB");
                return;
            }

            if(feeRate > MAX_FEE_RATE) {
                showError("Fee rate cannot exceed " + MAX_FEE_RATE + " sat/vB");
                return;
            }

            log.info("Proposing fee rate: {} sat/vB", feeRate);

            // TODO: Get participant pubkey from wallet
            String participantPubkey = "temp-pubkey";
            sessionManager.proposeFee(session.getSessionId(), feeRate, participantPubkey);

            hasProposedFee = true;
            proposeFeeButton.setDisable(true);
            proposalStatusLabel.setText("Fee rate proposed: " + FEE_FORMAT.format(feeRate) + " sat/vB");
            proposalStatusLabel.setStyle("-fx-text-fill: green;");

            log.info("Fee rate proposed successfully");

        } catch(NumberFormatException e) {
            showError("Invalid fee rate format");
        } catch(Exception e) {
            log.error("Failed to propose fee", e);
            showError("Failed to propose fee: " + e.getMessage());
        }
    }

    /**
     * Update fee proposals table
     */
    private void updateFeeProposalsTable() {
        if(session == null) {
            return;
        }

        feeProposalsTable.getItems().clear();

        for(CoordinationParticipant participant : session.getParticipants()) {
            if(participant.getFeeProposal() != null) {
                CoordinationFeeProposal proposal = participant.getFeeProposal();

                String participantName = participant.getName() != null ?
                                        participant.getName() : "Participant";
                String feeRate = FEE_FORMAT.format(proposal.getFeeRate()) + " sat/vB";
                String proposedAt = proposal.getProposedAt() != null ?
                                   proposal.getProposedAt().format(TIME_FORMATTER) : "";

                feeProposalsTable.getItems().add(
                    new FeeProposalRow(participantName, feeRate, proposedAt)
                );
            }
        }

        int proposalCount = feeProposalsTable.getItems().size();
        int expectedCount = session.getExpectedParticipants();
        proposalCountLabel.setText(proposalCount + " / " + expectedCount + " proposals received");

        log.debug("Updated fee proposals table: {} proposals", proposalCount);
    }

    /**
     * Check if all participants have proposed fees and auto-agree
     */
    private void checkAndAgreeFee() {
        if(session == null) {
            return;
        }

        // Count participants who have proposed fees
        int proposalCount = 0;
        for(CoordinationParticipant participant : session.getParticipants()) {
            if(participant.getFeeProposal() != null) {
                proposalCount++;
            }
        }

        int expectedCount = session.getExpectedParticipants();

        if(proposalCount >= expectedCount && session.getAgreedFeeRate() == null) {
            // All participants have proposed - automatically agree on highest fee
            double highestFee = 0.0;
            for(CoordinationParticipant participant : session.getParticipants()) {
                if(participant.getFeeProposal() != null) {
                    double feeRate = participant.getFeeProposal().getFeeRate();
                    if(feeRate > highestFee) {
                        highestFee = feeRate;
                    }
                }
            }

            if(highestFee > 0) {
                log.info("All participants proposed fees. Highest fee: {} sat/vB", highestFee);
                // Fee agreement happens automatically in sessionManager.proposeFee()
                // when it detects all participants have proposed
            }
        }

        updateAgreedFee();
    }

    /**
     * Update agreed fee display
     */
    private void updateAgreedFee() {
        if(session == null) {
            return;
        }

        Double agreedFee = session.getAgreedFeeRate();

        if(agreedFee != null) {
            agreedFeeLabel.setText(FEE_FORMAT.format(agreedFee) + " sat/vB");
            agreedFeeLabel.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
            agreedFeeExplanation.setText("(highest proposed fee - ensures timely confirmation)");
            readyStatusLabel.setText("Fee agreed! Ready to proceed to finalization.");
            readyStatusLabel.setStyle("-fx-text-fill: green;");
        } else {
            // Check if all have proposed
            int proposalCount = 0;
            for(CoordinationParticipant participant : session.getParticipants()) {
                if(participant.getFeeProposal() != null) {
                    proposalCount++;
                }
            }

            int expectedCount = session.getExpectedParticipants();
            int remaining = expectedCount - proposalCount;

            if(remaining > 0) {
                agreedFeeLabel.setText("Not yet agreed");
                agreedFeeExplanation.setText("(waiting for " + remaining + " more proposal(s))");
                readyStatusLabel.setText("Waiting for all participants to propose fees...");
            }
        }
    }

    /**
     * Estimate fee based on fee rate and expected tx size
     */
    private void updateFeeEstimate(double feeRate) {
        // Rough estimate: assume ~250 vBytes for a typical coordinated tx
        // This is very approximate - actual size depends on inputs/outputs
        int estimatedVBytes = 250;

        if(session != null) {
            // Better estimate based on outputs
            int outputCount = session.getOutputs().size();
            // Each output adds ~43 vBytes, plus overhead
            estimatedVBytes = 150 + (outputCount * 43);
        }

        long estimatedFeeSats = (long)(feeRate * estimatedVBytes);
        double estimatedFeeBtc = estimatedFeeSats / 100_000_000.0;

        feeEstimateLabel.setText(String.format("Estimated fee: ~%.8f BTC (%d sats)",
                                              estimatedFeeBtc, estimatedFeeSats));
    }

    /**
     * Show error message
     */
    private void showError(String message) {
        statusLabel.setText(message);
        statusLabel.setStyle("-fx-text-fill: red;");
    }

    /**
     * Helper class for table rows
     */
    public static class FeeProposalRow {
        private final String participant;
        private final String feeRate;
        private final String proposedAt;

        public FeeProposalRow(String participant, String feeRate, String proposedAt) {
            this.participant = participant;
            this.feeRate = feeRate;
            this.proposedAt = proposedAt;
        }
    }
}
