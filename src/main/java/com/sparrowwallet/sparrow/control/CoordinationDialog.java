package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.coordination.CoordinationController;
import com.sparrowwallet.sparrow.coordination.CoordinationSession;
import com.sparrowwallet.sparrow.event.*;
import com.google.common.eventbus.Subscribe;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Main dialog for transaction coordination workflow.
 *
 * Implements a multi-step wizard for coordinating Bitcoin transactions
 * between multiple participants using Nostr protocol.
 *
 * Steps:
 * 1. CREATE_OR_JOIN - Choose to create new session or join existing
 * 2. WAITING_PARTICIPANTS - Display QR code, wait for others to join
 * 3. OUTPUT_PROPOSAL - Propose transaction outputs
 * 4. FEE_AGREEMENT - Agree on fee rate
 * 5. FINALIZATION - Review and create PSBT
 */
public class CoordinationDialog extends Dialog<PSBT> {
    private static final Logger log = LoggerFactory.getLogger(CoordinationDialog.class);

    private final Wallet wallet;
    private final StackPane contentPane;
    private CoordinationController controller;

    private CoordinationSession session;
    private com.sparrowwallet.sparrow.coordination.CoordinationSessionManager sessionManager;
    private PSBT createdPSBT;

    private final ButtonType finishButtonType;
    private final ButtonType cancelButtonType;

    public CoordinationDialog(Wallet wallet) {
        this.wallet = wallet;
        this.contentPane = new StackPane();

        // Register for events
        EventManager.get().register(this);

        // Setup dialog
        setTitle("Coordinate Transaction");
        setHeaderText("Multi-party Bitcoin Transaction Coordination");

        // Create button types
        finishButtonType = new ButtonType("Finish", ButtonBar.ButtonData.FINISH);
        cancelButtonType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

        getDialogPane().getButtonTypes().addAll(cancelButtonType);

        // Disable finish button initially
        getDialogPane().lookupButton(finishButtonType);

        // Load main FXML
        try {
            // Use absolute path from classpath root
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/sparrowwallet/sparrow/control/coordination/coordination.fxml"));
            if(loader.getLocation() == null) {
                throw new IOException("FXML resource not found: /com/sparrowwallet/sparrow/control/coordination/coordination.fxml");
            }

            getDialogPane().setContent(loader.load());

            controller = loader.getController();
            controller.initializeWizard(wallet, this);

        } catch(IOException e) {
            log.error("Failed to load coordination UI", e);
            setContentText("Error loading coordination interface: " + e.getMessage());
        }

        // Set result converter
        setResultConverter(dialogButton -> {
            if(dialogButton == finishButtonType) {
                return createdPSBT;
            }
            return null;
        });

        // Cleanup on close
        setOnCloseRequest(event -> {
            EventManager.get().unregister(this);
            if(controller != null) {
                controller.cleanup();
            }
        });

        // Set dialog size - larger to accommodate all steps and navigation buttons
        getDialogPane().setPrefWidth(900);
        getDialogPane().setPrefHeight(750);
        getDialogPane().setMinWidth(800);
        getDialogPane().setMinHeight(650);
    }

    /**
     * Set the current coordination session
     */
    public void setSession(CoordinationSession session) {
        this.session = session;
    }

    /**
     * Get the current coordination session
     */
    public CoordinationSession getSession() {
        return session;
    }

    /**
     * Set the session manager
     */
    public void setSessionManager(com.sparrowwallet.sparrow.coordination.CoordinationSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    /**
     * Get the session manager
     */
    public com.sparrowwallet.sparrow.coordination.CoordinationSessionManager getSessionManager() {
        return sessionManager;
    }

    /**
     * Set the created PSBT (called when finalization is complete)
     */
    public void setPSBT(PSBT psbt) {
        this.createdPSBT = psbt;

        // Enable finish button
        Platform.runLater(() -> {
            if(!getDialogPane().getButtonTypes().contains(finishButtonType)) {
                getDialogPane().getButtonTypes().add(finishButtonType);
            }
        });
    }

    /**
     * Get the created PSBT
     */
    public PSBT getPSBT() {
        return createdPSBT;
    }

    // Event handlers for real-time updates

    @Subscribe
    public void onSessionCreated(CoordinationSessionCreatedEvent event) {
        Platform.runLater(() -> {
            this.session = event.getSession();
            if(controller != null) {
                controller.onSessionCreated(event);
            }
        });
    }

    @Subscribe
    public void onParticipantJoined(CoordinationParticipantJoinedEvent event) {
        Platform.runLater(() -> {
            if(controller != null) {
                controller.onParticipantJoined(event);
            }
        });
    }

    @Subscribe
    public void onOutputProposed(CoordinationOutputProposedEvent event) {
        Platform.runLater(() -> {
            if(controller != null) {
                controller.onOutputProposed(event);
            }
        });
    }

    @Subscribe
    public void onFeeProposed(CoordinationFeeProposedEvent event) {
        Platform.runLater(() -> {
            if(controller != null) {
                controller.onFeeProposed(event);
            }
        });
    }

    @Subscribe
    public void onFeeAgreed(CoordinationFeeAgreedEvent event) {
        Platform.runLater(() -> {
            if(controller != null) {
                controller.onFeeAgreed(event);
            }
        });
    }

    @Subscribe
    public void onSessionFinalized(CoordinationFinalizedEvent event) {
        Platform.runLater(() -> {
            if(controller != null) {
                controller.onSessionFinalized(event);
            }
        });
    }

    @Subscribe
    public void onPSBTCreated(CoordinationPSBTCreatedEvent event) {
        Platform.runLater(() -> {
            setPSBT(event.getPsbt());
            if(controller != null) {
                controller.onPSBTCreated(event);
            }
        });
    }

    @Subscribe
    public void onSessionStateChanged(CoordinationSessionStateChangedEvent event) {
        Platform.runLater(() -> {
            if(controller != null) {
                controller.onSessionStateChanged(event);
            }
        });
    }
}
