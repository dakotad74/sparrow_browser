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
        NostrEvent nostrEvent = event.getNostrEvent();

        // Only process coordination events
        if(nostrEvent.getKind() != NostrEvent.KIND_COORDINATION) {
            return;
        }

        log.debug("Received Nostr coordination message: {}", nostrEvent);

        String messageType = nostrEvent.getTagValue("d");
        if(messageType == null) {
            log.warn("Received coordination event without message type tag");
            return;
        }

        // Ignore our own messages
        if(myNostrPubkey.equals(nostrEvent.getPubkey())) {
            log.debug("Ignoring own message: {}", messageType);
            return;
        }

        try {
            // Route to appropriate handler based on message type
            switch(messageType) {
                case "session-create":
                    handleSessionCreateMessage(nostrEvent);
                    break;
                case "session-join":
                    handleSessionJoinMessage(nostrEvent);
                    break;
                case "output-proposal":
                    handleOutputProposalMessage(nostrEvent);
                    break;
                case "fee-proposal":
                    handleFeeProposalMessage(nostrEvent);
                    break;
                case "fee-agreed":
                    handleFeeAgreedMessage(nostrEvent);
                    break;
                case "session-finalize":
                    handleSessionFinalizeMessage(nostrEvent);
                    break;
                default:
                    log.warn("Unknown coordination message type: {}", messageType);
            }
        } catch(Exception e) {
            log.error("Error handling Nostr message type {}: {}", messageType, e.getMessage(), e);
        }
    }

    // Nostr Message Handling Methods

    /**
     * Handle incoming session-create messages
     */
    private void handleSessionCreateMessage(NostrEvent event) {
        String sessionId = event.getTagValue("session-id");
        if(sessionId == null) {
            log.warn("session-create message missing session-id tag");
            return;
        }

        // Check if we already know about this session
        if(sessions.containsKey(sessionId)) {
            log.debug("Session already exists: {}", sessionId);
            return;
        }

        log.info("Discovered new session via Nostr: {}", sessionId);

        // Parse content
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> content = gson.fromJson(event.getContent(), Map.class);

            String walletDescriptor = (String) content.get("wallet_descriptor");
            String networkStr = event.getTagValue("network");
            String expectedParticipantsStr = event.getTagValue("expected-participants");

            if(walletDescriptor == null || networkStr == null || expectedParticipantsStr == null) {
                log.warn("session-create message missing required fields");
                return;
            }

            Network network = Network.valueOf(networkStr);
            int expectedParticipants = Integer.parseInt(expectedParticipantsStr);

            // Create local representation of remote session
            CoordinationSession session = new CoordinationSession(
                sessionId,
                walletDescriptor,
                network,
                expectedParticipants
            );

            sessions.put(sessionId, session);

            // Fire event so UI can show discovered session
            EventManager.get().post(new CoordinationSessionCreatedEvent(session));

            log.info("Remote session added: {}", sessionId);

        } catch(Exception e) {
            log.error("Error parsing session-create message", e);
        }
    }

    /**
     * Handle incoming session-join messages
     */
    private void handleSessionJoinMessage(NostrEvent event) {
        String sessionId = event.getTagValue("session-id");
        String participantPubkey = event.getTagValue("participant-pubkey");

        if(sessionId == null || participantPubkey == null) {
            log.warn("session-join message missing required tags");
            return;
        }

        CoordinationSession session = sessions.get(sessionId);
        if(session == null) {
            log.debug("Received join for unknown session: {}", sessionId);
            return;
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> content = gson.fromJson(event.getContent(), Map.class);

            String name = (String) content.get("name");
            String xpub = (String) content.get("xpub");

            CoordinationParticipant participant = new CoordinationParticipant(participantPubkey, name, xpub);

            // Add participant if not already in session
            if(session.getParticipant(participantPubkey) == null) {
                session.addParticipant(participant);

                // Fire event
                EventManager.get().post(new CoordinationParticipantJoinedEvent(sessionId, participant));

                log.info("Participant {} joined session {}", participantPubkey.substring(0, 8), sessionId);
            }

        } catch(Exception e) {
            log.error("Error parsing session-join message", e);
        }
    }

    /**
     * Handle incoming output-proposal messages
     */
    private void handleOutputProposalMessage(NostrEvent event) {
        String sessionId = event.getTagValue("session-id");

        if(sessionId == null) {
            log.warn("output-proposal message missing session-id tag");
            return;
        }

        CoordinationSession session = sessions.get(sessionId);
        if(session == null) {
            log.debug("Received output proposal for unknown session: {}", sessionId);
            return;
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> content = gson.fromJson(event.getContent(), Map.class);

            String addressStr = (String) content.get("address");
            Number amountNum = (Number) content.get("amount");
            String label = (String) content.get("label");
            String proposedBy = (String) content.get("proposed_by");

            if(addressStr == null || amountNum == null || proposedBy == null) {
                log.warn("output-proposal message missing required fields");
                return;
            }

            Address address = Address.fromString(addressStr);
            long amount = amountNum.longValue();

            CoordinationOutput output = new CoordinationOutput(address, amount, label, proposedBy);

            // Check for duplicate before adding
            boolean duplicate = session.getOutputs().stream()
                    .anyMatch(existing -> existing.getAddress().equals(output.getAddress()));

            if(!duplicate) {
                session.proposeOutput(output);

                // Add to participant's outputs
                CoordinationParticipant participant = session.getParticipant(proposedBy);
                if(participant != null) {
                    participant.addProposedOutput(output);
                }

                log.info("Output proposed for session {}: {} sats to {}",
                        sessionId, amount, addressStr);
            }

        } catch(Exception e) {
            log.error("Error parsing output-proposal message", e);
        }
    }

    /**
     * Handle incoming fee-proposal messages
     */
    private void handleFeeProposalMessage(NostrEvent event) {
        String sessionId = event.getTagValue("session-id");
        String feeRateStr = event.getTagValue("fee-rate");

        if(sessionId == null || feeRateStr == null) {
            log.warn("fee-proposal message missing required tags");
            return;
        }

        CoordinationSession session = sessions.get(sessionId);
        if(session == null) {
            log.debug("Received fee proposal for unknown session: {}", sessionId);
            return;
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> content = gson.fromJson(event.getContent(), Map.class);

            String proposedBy = (String) content.get("proposed_by");
            Number feeRateNum = (Number) content.get("fee_rate");

            if(proposedBy == null || feeRateNum == null) {
                log.warn("fee-proposal message missing required fields");
                return;
            }

            double feeRate = feeRateNum.doubleValue();

            CoordinationFeeProposal feeProposal = new CoordinationFeeProposal(proposedBy, feeRate);
            session.proposeFee(feeProposal);

            log.info("Fee proposed for session {}: {} sat/vB by {}",
                    sessionId, feeRate, proposedBy.substring(0, 8));

            // Check if all participants have now proposed fees
            if(session.allParticipantsProposedFees()) {
                Optional<Double> highestFee = session.getHighestProposedFeeRate();
                if(highestFee.isPresent()) {
                    session.agreeFee(highestFee.get());
                    log.info("All fees collected for session {}, agreed on: {} sat/vB",
                            sessionId, highestFee.get());
                }
            }

        } catch(Exception e) {
            log.error("Error parsing fee-proposal message", e);
        }
    }

    /**
     * Handle incoming fee-agreed messages
     */
    private void handleFeeAgreedMessage(NostrEvent event) {
        String sessionId = event.getTagValue("session-id");
        String agreedFeeRateStr = event.getTagValue("agreed-fee-rate");

        if(sessionId == null || agreedFeeRateStr == null) {
            log.warn("fee-agreed message missing required tags");
            return;
        }

        CoordinationSession session = sessions.get(sessionId);
        if(session == null) {
            log.debug("Received fee agreement for unknown session: {}", sessionId);
            return;
        }

        try {
            double agreedFeeRate = Double.parseDouble(agreedFeeRateStr);

            // Only update if we haven't already agreed on a fee
            if(session.getAgreedFeeRate() == null) {
                session.agreeFee(agreedFeeRate);
                log.info("Fee agreed for session {}: {} sat/vB", sessionId, agreedFeeRate);
            }

        } catch(Exception e) {
            log.error("Error parsing fee-agreed message", e);
        }
    }

    /**
     * Handle incoming session-finalize messages
     */
    private void handleSessionFinalizeMessage(NostrEvent event) {
        String sessionId = event.getTagValue("session-id");

        if(sessionId == null) {
            log.warn("session-finalize message missing session-id tag");
            return;
        }

        CoordinationSession session = sessions.get(sessionId);
        if(session == null) {
            log.debug("Received finalize for unknown session: {}", sessionId);
            return;
        }

        try {
            // Only finalize if we're ready
            if(session.isReadyToFinalize()) {
                SessionState oldState = session.getState();
                session.finalizeSession();
                SessionState newState = session.getState();

                // Fire state change event
                EventManager.get().post(new CoordinationSessionStateChangedEvent(sessionId, oldState, newState));

                log.info("Session finalized remotely: {}", sessionId);
            } else {
                log.warn("Received finalize for session {} but not ready (state: {})",
                        sessionId, session.getState());
            }

        } catch(Exception e) {
            log.error("Error handling session-finalize message", e);
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
