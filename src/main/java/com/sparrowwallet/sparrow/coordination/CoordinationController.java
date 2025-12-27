package com.sparrowwallet.sparrow.coordination;

import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.control.CoordinationDialog;
import com.sparrowwallet.sparrow.event.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

/**
 * Main controller for coordination wizard.
 *
 * Manages navigation between different steps of the coordination process
 * and coordinates UI updates based on EventBus events.
 */
public class CoordinationController implements Initializable {
    private static final Logger log = LoggerFactory.getLogger(CoordinationController.class);

    @FXML
    private StackPane contentPane;

    @FXML
    private Label stepLabel;

    @FXML
    private Button backButton;

    @FXML
    private Button nextButton;

    private Wallet wallet;
    private CoordinationDialog dialog;
    private CoordinationStep currentStep;

    // Step controllers
    private Object currentStepController;

    /**
     * Coordination wizard steps
     */
    public enum CoordinationStep {
        CREATE_OR_JOIN("/com/sparrowwallet/sparrow/control/coordination/session-start.fxml", "Create or Join Session"),
        WAITING_PARTICIPANTS("/com/sparrowwallet/sparrow/control/coordination/waiting-participants.fxml", "Waiting for Participants"),
        OUTPUT_PROPOSAL("/com/sparrowwallet/sparrow/control/coordination/output-proposal.fxml", "Propose Outputs"),
        FEE_AGREEMENT("/com/sparrowwallet/sparrow/control/coordination/fee-agreement.fxml", "Agree on Fee"),
        FINALIZATION("/com/sparrowwallet/sparrow/control/coordination/finalization.fxml", "Finalize Transaction");

        private final String fxmlPath;
        private final String title;

        CoordinationStep(String fxmlPath, String title) {
            this.fxmlPath = fxmlPath;
            this.title = title;
        }

        public String getFxmlPath() {
            return fxmlPath;
        }

        public String getTitle() {
            return title;
        }
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Will be initialized when initializeWizard() is called
    }

    /**
     * Initialize the wizard with wallet and dialog reference
     */
    public void initializeWizard(Wallet wallet, CoordinationDialog dialog) {
        this.wallet = wallet;
        this.dialog = dialog;

        // Load first step
        loadStep(CoordinationStep.CREATE_OR_JOIN);

        // Setup button handlers
        backButton.setOnAction(e -> goBack());
        nextButton.setOnAction(e -> goNext());
    }

    /**
     * Load a wizard step
     */
    private void loadStep(CoordinationStep step) {
        try {
            log.error("=== LOADING STEP: {} ===", step.getTitle());
            log.error("FXML path: {}", step.getFxmlPath());

            // Load FXML for this step
            FXMLLoader loader = new FXMLLoader(getClass().getResource(step.getFxmlPath()));
            log.error("FXMLLoader created, loading...");
            Parent stepPane = loader.load();
            log.error("FXML loaded successfully");

            // Get controller
            currentStepController = loader.getController();
            log.error("Controller obtained: {}", currentStepController != null ? currentStepController.getClass().getName() : "null");

            // Initialize controller if needed
            if(currentStepController instanceof StepController) {
                log.error("Initializing StepController...");
                ((StepController) currentStepController).initializeStep(wallet, dialog);
                log.error("StepController initialized");
            }

            // Update UI
            contentPane.getChildren().setAll(stepPane);
            stepLabel.setText(step.getTitle());
            currentStep = step;
            log.error("UI updated, currentStep now: {}", currentStep);

            // Update button states
            updateButtonStates();
            log.error("=== STEP LOADED SUCCESSFULLY ===");

        } catch(IOException e) {
            log.error("=== FAILED TO LOAD STEP: {} ===", step, e);
            e.printStackTrace();
        } catch(Exception e) {
            log.error("=== UNEXPECTED ERROR LOADING STEP: {} ===", step, e);
            e.printStackTrace();
        }
    }

    /**
     * Update back/next button states based on current step
     */
    private void updateButtonStates() {
        // Back button disabled on first step
        backButton.setDisable(currentStep == CoordinationStep.CREATE_OR_JOIN);

        // Next button text changes on last step
        if(currentStep == CoordinationStep.FINALIZATION) {
            nextButton.setText("Create PSBT");
        } else {
            nextButton.setText("Next");
        }
    }

    /**
     * Go to previous step
     */
    @FXML
    private void goBack() {
        CoordinationStep prevStep = getPreviousStep(currentStep);
        if(prevStep != null) {
            loadStep(prevStep);
        }
    }

    /**
     * Go to next step
     */
    @FXML
    private void goNext() {
        log.error("=== NEXT BUTTON CLICKED === Current step: {}", currentStep);

        // Validate current step before proceeding
        if(currentStepController instanceof StepController) {
            boolean valid = ((StepController) currentStepController).validateStep();
            log.error("Step validation result: {}", valid);
            if(!valid) {
                log.error("Step validation FAILED - cannot proceed");
                return;
            }
        } else {
            log.error("Current controller is NOT a StepController: {}",
                currentStepController != null ? currentStepController.getClass().getName() : "null");
        }

        // Move to next step
        CoordinationStep nextStep = getNextStep(currentStep);
        log.error("Next step: {}", nextStep);
        if(nextStep != null) {
            loadStep(nextStep);
        } else {
            log.error("No next step available!");
        }
    }

    /**
     * Get previous step in workflow
     */
    private CoordinationStep getPreviousStep(CoordinationStep current) {
        CoordinationStep[] steps = CoordinationStep.values();
        for(int i = 1; i < steps.length; i++) {
            if(steps[i] == current) {
                return steps[i - 1];
            }
        }
        return null;
    }

    /**
     * Get next step in workflow
     */
    private CoordinationStep getNextStep(CoordinationStep current) {
        CoordinationStep[] steps = CoordinationStep.values();
        for(int i = 0; i < steps.length - 1; i++) {
            if(steps[i] == current) {
                return steps[i + 1];
            }
        }
        return null;
    }

    /**
     * Cleanup resources
     */
    public void cleanup() {
        // Cleanup will be implemented when needed
        log.debug("Cleaning up coordination controller");
    }

    // Event handlers (called by CoordinationDialog)

    public void onSessionCreated(CoordinationSessionCreatedEvent event) {
        log.error("=== Session created event received for session: {} ===", event.getSession().getSessionId());

        // Check if this is a remote session (discovered via Nostr) vs local session we created
        boolean isRemoteSession = dialog.getSessionManager() != null &&
                                  dialog.getSessionManager().getSession(event.getSession().getSessionId()) != null &&
                                  !dialog.getSessionManager().isLocalSession(event.getSession().getSessionId());

        log.error("=== Is remote session: {} ===", isRemoteSession);

        // Auto-advance to waiting participants step
        loadStep(CoordinationStep.WAITING_PARTICIPANTS);

        // If this is a remote session, automatically join it
        if(isRemoteSession && wallet != null) {
            log.error("=== Auto-joining remote session: {} ===", event.getSession().getSessionId());
            try {
                // Extract participant info from wallet
                String participantName = wallet.getName();
                String xpub = wallet.getKeystores().get(0).getExtendedPublicKey().toString();

                // Get participant pubkey (same as Nostr pubkey)
                String participantPubkey = dialog.getSessionManager().getMyNostrPubkey();

                // Join the session
                dialog.getSessionManager().joinSession(
                    event.getSession().getSessionId(),
                    wallet,
                    participantPubkey
                );

                log.error("=== Successfully auto-joined session ===");
            } catch(Exception e) {
                log.error("Failed to auto-join remote session", e);
            }
        }
    }

    public void onParticipantJoined(CoordinationParticipantJoinedEvent event) {
        log.debug("Participant joined: {}", event.getParticipant().getName());
        // Update participant list in current step
        if(currentStepController instanceof StepController) {
            ((StepController) currentStepController).onEventReceived(event);
        }

        // Check if we're in waiting participants step and all have joined
        if(currentStep == CoordinationStep.WAITING_PARTICIPANTS && dialog.getSession() != null) {
            CoordinationSession session = dialog.getSession();
            if(session.getParticipants().size() >= session.getExpectedParticipants()) {
                log.info("All participants joined, auto-advancing to output proposal");
                // Auto-advance to output proposal step
                Platform.runLater(() -> loadStep(CoordinationStep.OUTPUT_PROPOSAL));
            }
        }
    }

    public void onOutputProposed(CoordinationOutputProposedEvent event) {
        log.debug("Output proposed");
        if(currentStepController instanceof StepController) {
            ((StepController) currentStepController).onEventReceived(event);
        }
    }

    public void onFeeProposed(CoordinationFeeProposedEvent event) {
        log.debug("Fee proposed: {}", event.getFeeProposal().getFeeRate());
        if(currentStepController instanceof StepController) {
            ((StepController) currentStepController).onEventReceived(event);
        }
    }

    public void onFeeAgreed(CoordinationFeeAgreedEvent event) {
        log.debug("Fee agreed: {}", event.getAgreedFeeRate());
        if(currentStepController instanceof StepController) {
            ((StepController) currentStepController).onEventReceived(event);
        }
    }

    public void onSessionFinalized(CoordinationFinalizedEvent event) {
        log.debug("Session finalized");
        // Auto-advance to finalization step
        loadStep(CoordinationStep.FINALIZATION);
    }

    public void onPSBTCreated(CoordinationPSBTCreatedEvent event) {
        log.debug("PSBT created");
        if(currentStepController instanceof StepController) {
            ((StepController) currentStepController).onEventReceived(event);
        }
    }

    public void onSessionStateChanged(CoordinationSessionStateChangedEvent event) {
        log.debug("Session state changed to: {}", event.getNewState());
        if(currentStepController instanceof StepController) {
            ((StepController) currentStepController).onEventReceived(event);
        }
    }

    /**
     * Interface for step controllers
     */
    public interface StepController {
        void initializeStep(Wallet wallet, CoordinationDialog dialog);
        boolean validateStep();
        void onEventReceived(Object event);
    }
}
