package com.sparrowwallet.sparrow.coordination;

import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.*;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages coordination sessions for multi-party transaction construction.
 * Handles session lifecycle, Nostr event publishing/receiving, and state synchronization.
 *
 * This is a JavaFX Service that integrates with Sparrow's event-driven architecture.
 */
public class CoordinationSessionManager extends Service<Void> {
    private static final Logger log = LoggerFactory.getLogger(CoordinationSessionManager.class);

    private final Map<String, CoordinationSession> sessions;
    private final Map<String, Wallet> sessionWallets;

    public CoordinationSessionManager() {
        this.sessions = new ConcurrentHashMap<>();
        this.sessionWallets = new ConcurrentHashMap<>();

        // Register for event bus
        EventManager.get().register(this);
    }

    @Override
    protected Task<Void> createTask() {
        return new Task<>() {
            @Override
            protected Void call() throws Exception {
                // Background task for session management
                // This runs periodically to check for expired sessions
                cleanupExpiredSessions();
                return null;
            }
        };
    }

    /**
     * Create a new coordination session
     */
    public CoordinationSession createSession(Wallet wallet, int expectedParticipants) {
        if(wallet == null) {
            throw new IllegalArgumentException("Wallet cannot be null");
        }

        // Generate unique session ID
        String sessionId = UUID.randomUUID().toString();

        // Get wallet descriptor (for multisig coordination)
        String walletDescriptor = wallet.getKeystores().isEmpty()
            ? "single-sig"
            : wallet.getDefaultPolicy().getName();

        // Get network
        Network network = wallet.getNetwork();

        log.info("Creating coordination session: {} for wallet: {} with {} expected participants",
                sessionId, wallet.getName(), expectedParticipants);

        // Create session
        CoordinationSession session = new CoordinationSession(
            sessionId,
            walletDescriptor,
            network,
            expectedParticipants
        );

        // Store session
        sessions.put(sessionId, session);
        sessionWallets.put(sessionId, wallet);

        // Fire event
        EventManager.get().post(new CoordinationSessionCreatedEvent(session));

        log.info("Coordination session created: {}", sessionId);

        // TODO Phase 3: Publish session-create event to Nostr

        return session;
    }

    /**
     * Join an existing coordination session
     */
    public void joinSession(String sessionId, Wallet wallet, String participantPubkey) {
        CoordinationSession session = sessions.get(sessionId);
        if(session == null) {
            throw new IllegalArgumentException("Session not found: " + sessionId);
        }

        if(session.isExpired()) {
            throw new IllegalStateException("Session has expired: " + sessionId);
        }

        log.info("Joining session: {} with participant: {}", sessionId, participantPubkey);

        // Create participant
        CoordinationParticipant participant = new CoordinationParticipant(participantPubkey);
        participant.setName(wallet.getName());

        // Add to session
        session.addParticipant(participant);

        // Store wallet
        sessionWallets.put(sessionId, wallet);

        // Fire event
        EventManager.get().post(new CoordinationParticipantJoinedEvent(sessionId, participant));

        log.info("Participant joined session: {}", sessionId);

        // TODO Phase 3: Publish session-join event to Nostr
    }

    /**
     * Propose an output for a session
     */
    public void proposeOutput(String sessionId, Address address, long amount, String label, String participantPubkey) {
        CoordinationSession session = sessions.get(sessionId);
        if(session == null) {
            throw new IllegalArgumentException("Session not found: " + sessionId);
        }

        log.info("Proposing output for session {}: {} sats to {}", sessionId, amount, address);

        // Create output
        CoordinationOutput output = new CoordinationOutput(address, amount, label, participantPubkey);

        // Add to session
        session.proposeOutput(output);

        // Add to participant
        CoordinationParticipant participant = session.getParticipant(participantPubkey);
        if(participant != null) {
            participant.addProposedOutput(output);
        }

        log.info("Output proposed for session: {}", sessionId);

        // TODO Phase 3: Publish output-proposal event to Nostr
    }

    /**
     * Propose a fee rate for a session
     */
    public void proposeFee(String sessionId, double feeRate, String participantPubkey) {
        CoordinationSession session = sessions.get(sessionId);
        if(session == null) {
            throw new IllegalArgumentException("Session not found: " + sessionId);
        }

        log.info("Proposing fee for session {}: {} sat/vB by {}", sessionId, feeRate, participantPubkey);

        // Create fee proposal
        CoordinationFeeProposal feeProposal = new CoordinationFeeProposal(participantPubkey, feeRate);

        // Add to session
        session.proposeFee(feeProposal);

        log.info("Fee proposed for session: {}", sessionId);

        // Check if all participants have proposed fees
        if(session.allParticipantsProposedFees()) {
            // Automatically select highest fee rate
            Optional<Double> highestFee = session.getHighestProposedFeeRate();
            if(highestFee.isPresent()) {
                session.agreeFee(highestFee.get());
                log.info("Fee agreed for session {}: {} sat/vB (highest proposed)",
                        sessionId, highestFee.get());

                // TODO Phase 3: Publish fee-agreed event to Nostr
            }
        }

        // TODO Phase 3: Publish fee-proposal event to Nostr
    }

    /**
     * Finalize a session (lock it for PSBT creation)
     */
    public void finalizeSession(String sessionId) {
        CoordinationSession session = sessions.get(sessionId);
        if(session == null) {
            throw new IllegalArgumentException("Session not found: " + sessionId);
        }

        log.info("Finalizing session: {}", sessionId);

        SessionState oldState = session.getState();
        session.finalizeSession();
        SessionState newState = session.getState();

        // Fire state change event
        EventManager.get().post(new CoordinationSessionStateChangedEvent(sessionId, oldState, newState));

        log.info("Session finalized: {}", sessionId);

        // TODO Phase 3: Publish session-finalize event to Nostr
    }

    /**
     * Get a session by ID
     */
    public CoordinationSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    /**
     * Get all active sessions
     */
    public List<CoordinationSession> getActiveSessions() {
        return sessions.values().stream()
                .filter(session -> !session.isExpired()
                                && session.getState() != SessionState.COMPLETED)
                .toList();
    }

    /**
     * Get wallet associated with a session
     */
    public Wallet getSessionWallet(String sessionId) {
        return sessionWallets.get(sessionId);
    }

    /**
     * Remove a session
     */
    public void removeSession(String sessionId) {
        log.info("Removing session: {}", sessionId);
        sessions.remove(sessionId);
        sessionWallets.remove(sessionId);
    }

    /**
     * Cleanup expired sessions
     */
    private void cleanupExpiredSessions() {
        List<String> expiredSessions = sessions.entrySet().stream()
                .filter(entry -> entry.getValue().isExpired())
                .map(Map.Entry::getKey)
                .toList();

        if(!expiredSessions.isEmpty()) {
            log.info("Cleaning up {} expired sessions", expiredSessions.size());
            expiredSessions.forEach(this::removeSession);
        }
    }

    /**
     * Handle Nostr message events (TODO Phase 3)
     */
    @Subscribe
    public void onNostrMessage(NostrMessageReceivedEvent event) {
        // TODO Phase 3: Parse and handle Nostr coordination messages
        // - session-create
        // - session-join
        // - output-proposal
        // - fee-proposal
        // - fee-agreed
        // - session-finalize
        log.debug("Received Nostr message: {}", event.getEventData());
    }

    /**
     * Shutdown the manager
     */
    public void shutdown() {
        log.info("Shutting down CoordinationSessionManager");
        EventManager.get().unregister(this);
        cancel();
    }
}
