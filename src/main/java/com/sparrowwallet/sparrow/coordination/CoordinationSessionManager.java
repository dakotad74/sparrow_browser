package com.sparrowwallet.sparrow.coordination;

import com.google.common.eventbus.Subscribe;
import com.google.gson.Gson;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.*;
import com.sparrowwallet.sparrow.nostr.NostrEvent;
import com.sparrowwallet.sparrow.nostr.NostrRelayManager;
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
    private NostrRelayManager nostrRelayManager;
    private final Gson gson;

    // TODO: Generate or load from wallet's signing key
    private String myNostrPubkey = "temporary-pubkey-placeholder";

    public CoordinationSessionManager() {
        this.sessions = new ConcurrentHashMap<>();
        this.sessionWallets = new ConcurrentHashMap<>();
        this.gson = new Gson();

        // Register for event bus
        EventManager.get().register(this);
    }

    /**
     * Set the Nostr relay manager for publishing events
     */
    public void setNostrRelayManager(NostrRelayManager relayManager) {
        this.nostrRelayManager = relayManager;
    }

    /**
     * Set the user's Nostr public key (derived from wallet signing key)
     */
    public void setMyNostrPubkey(String pubkey) {
        this.myNostrPubkey = pubkey;
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

        // Publish session-create event to Nostr
        publishSessionCreateEvent(session);

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

        // Publish session-join event to Nostr
        publishSessionJoinEvent(sessionId, participant);
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

        // Publish output-proposal event to Nostr
        publishOutputProposalEvent(sessionId, output);
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

        // Publish fee-proposal event to Nostr
        publishFeeProposalEvent(sessionId, feeProposal);

        // Check if all participants have proposed fees
        if(session.allParticipantsProposedFees()) {
            // Automatically select highest fee rate
            Optional<Double> highestFee = session.getHighestProposedFeeRate();
            if(highestFee.isPresent()) {
                session.agreeFee(highestFee.get());
                log.info("Fee agreed for session {}: {} sat/vB (highest proposed)",
                        sessionId, highestFee.get());

                // Publish fee-agreed event to Nostr
                publishFeeAgreedEvent(sessionId, highestFee.get());
            }
        }
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

        // Publish session-finalize event to Nostr
        publishSessionFinalizeEvent(sessionId, session);
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
     * Handle Nostr message events
     */
    @Subscribe
    public void onNostrMessage(NostrMessageReceivedEvent event) {
        // TODO Phase 3 Part 3: Parse and handle Nostr coordination messages based on tags
        // - session-create: ["d", "session-create"]
        // - session-join: ["d", "session-join"]
        // - output-proposal: ["d", "output-proposal"]
        // - fee-proposal: ["d", "fee-proposal"]
        // - fee-agreed: ["d", "fee-agreed"]
        // - session-finalize: ["d", "session-finalize"]
        log.debug("Received Nostr message: {}", event.getNostrEvent());

        String messageType = event.getNostrEvent().getTagValue("d");
        if(messageType != null) {
            log.debug("Coordination message type: {}", messageType);
            // TODO Phase 3 Part 3: Route to appropriate handler based on messageType
        }
    }

    // Nostr Event Publishing Methods

    /**
     * Publish session-create event to Nostr
     */
    private void publishSessionCreateEvent(CoordinationSession session) {
        if(nostrRelayManager == null || !nostrRelayManager.isConnected()) {
            log.warn("Cannot publish session-create event - Nostr not connected");
            return;
        }

        try {
            NostrEvent event = new NostrEvent(myNostrPubkey, NostrEvent.KIND_COORDINATION, "");

            // Add tags
            event.addTag("d", "session-create");
            event.addTag("session-id", session.getSessionId());
            event.addTag("network", session.getNetwork().toString());
            event.addTag("expected-participants", String.valueOf(session.getExpectedParticipants()));

            // Add content (could be encrypted in future)
            Map<String, Object> content = new HashMap<>();
            content.put("wallet_descriptor", session.getWalletDescriptor());
            content.put("created_at", session.getCreatedAt().toString());
            event.setContent(gson.toJson(content));

            nostrRelayManager.publishEvent(event);
            log.info("Published session-create event for session: {}", session.getSessionId());

        } catch(Exception e) {
            log.error("Failed to publish session-create event", e);
        }
    }

    /**
     * Publish session-join event to Nostr
     */
    private void publishSessionJoinEvent(String sessionId, CoordinationParticipant participant) {
        if(nostrRelayManager == null || !nostrRelayManager.isConnected()) {
            log.warn("Cannot publish session-join event - Nostr not connected");
            return;
        }

        try {
            NostrEvent event = new NostrEvent(myNostrPubkey, NostrEvent.KIND_COORDINATION, "");

            event.addTag("d", "session-join");
            event.addTag("session-id", sessionId);
            event.addTag("participant-pubkey", participant.getPubkey());

            Map<String, Object> content = new HashMap<>();
            content.put("name", participant.getName());
            content.put("xpub", participant.getXpub());
            event.setContent(gson.toJson(content));

            nostrRelayManager.publishEvent(event);
            log.info("Published session-join event for session: {}", sessionId);

        } catch(Exception e) {
            log.error("Failed to publish session-join event", e);
        }
    }

    /**
     * Publish output-proposal event to Nostr
     */
    private void publishOutputProposalEvent(String sessionId, CoordinationOutput output) {
        if(nostrRelayManager == null || !nostrRelayManager.isConnected()) {
            log.warn("Cannot publish output-proposal event - Nostr not connected");
            return;
        }

        try {
            NostrEvent event = new NostrEvent(myNostrPubkey, NostrEvent.KIND_COORDINATION, "");

            event.addTag("d", "output-proposal");
            event.addTag("session-id", sessionId);

            Map<String, Object> content = new HashMap<>();
            content.put("address", output.getAddress().toString());
            content.put("amount", output.getAmount());
            content.put("label", output.getLabel());
            content.put("proposed_by", output.getProposedBy());
            event.setContent(gson.toJson(content));

            nostrRelayManager.publishEvent(event);
            log.info("Published output-proposal event for session: {}", sessionId);

        } catch(Exception e) {
            log.error("Failed to publish output-proposal event", e);
        }
    }

    /**
     * Publish fee-proposal event to Nostr
     */
    private void publishFeeProposalEvent(String sessionId, CoordinationFeeProposal feeProposal) {
        if(nostrRelayManager == null || !nostrRelayManager.isConnected()) {
            log.warn("Cannot publish fee-proposal event - Nostr not connected");
            return;
        }

        try {
            NostrEvent event = new NostrEvent(myNostrPubkey, NostrEvent.KIND_COORDINATION, "");

            event.addTag("d", "fee-proposal");
            event.addTag("session-id", sessionId);
            event.addTag("fee-rate", String.valueOf(feeProposal.getFeeRate()));

            Map<String, Object> content = new HashMap<>();
            content.put("proposed_by", feeProposal.getProposedBy());
            content.put("fee_rate", feeProposal.getFeeRate());
            event.setContent(gson.toJson(content));

            nostrRelayManager.publishEvent(event);
            log.info("Published fee-proposal event for session: {}", sessionId);

        } catch(Exception e) {
            log.error("Failed to publish fee-proposal event", e);
        }
    }

    /**
     * Publish fee-agreed event to Nostr
     */
    private void publishFeeAgreedEvent(String sessionId, double agreedFeeRate) {
        if(nostrRelayManager == null || !nostrRelayManager.isConnected()) {
            log.warn("Cannot publish fee-agreed event - Nostr not connected");
            return;
        }

        try {
            NostrEvent event = new NostrEvent(myNostrPubkey, NostrEvent.KIND_COORDINATION, "");

            event.addTag("d", "fee-agreed");
            event.addTag("session-id", sessionId);
            event.addTag("agreed-fee-rate", String.valueOf(agreedFeeRate));

            Map<String, Object> content = new HashMap<>();
            content.put("fee_rate", agreedFeeRate);
            event.setContent(gson.toJson(content));

            nostrRelayManager.publishEvent(event);
            log.info("Published fee-agreed event for session: {}", sessionId);

        } catch(Exception e) {
            log.error("Failed to publish fee-agreed event", e);
        }
    }

    /**
     * Publish session-finalize event to Nostr
     */
    private void publishSessionFinalizeEvent(String sessionId, CoordinationSession session) {
        if(nostrRelayManager == null || !nostrRelayManager.isConnected()) {
            log.warn("Cannot publish session-finalize event - Nostr not connected");
            return;
        }

        try {
            NostrEvent event = new NostrEvent(myNostrPubkey, NostrEvent.KIND_COORDINATION, "");

            event.addTag("d", "session-finalize");
            event.addTag("session-id", sessionId);

            Map<String, Object> content = new HashMap<>();
            content.put("total_output_amount", session.getTotalOutputAmount());
            content.put("agreed_fee_rate", session.getAgreedFeeRate());
            content.put("output_count", session.getOutputs().size());
            content.put("participant_count", session.getParticipants().size());
            event.setContent(gson.toJson(content));

            nostrRelayManager.publishEvent(event);
            log.info("Published session-finalize event for session: {}", sessionId);

        } catch(Exception e) {
            log.error("Failed to publish session-finalize event", e);
        }
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
