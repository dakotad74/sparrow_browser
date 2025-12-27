package com.sparrowwallet.sparrow.coordination;

import com.google.common.eventbus.Subscribe;
import com.google.gson.Gson;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.psbt.PSBT;
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

    /**
     * Get the user's Nostr public key
     */
    public String getMyNostrPubkey() {
        return myNostrPubkey;
    }

    /**
     * Check if a session was created locally (vs discovered remotely)
     */
    public boolean isLocalSession(String sessionId) {
        // Local sessions have a wallet associated from creation
        // Remote sessions don't have a wallet until we join
        return sessionWallets.containsKey(sessionId);
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

        log.error("=== Creating coordination session: {} for wallet: {} with {} expected participants ===",
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

        // Creator automatically joins as first participant
        log.error("=== Creator auto-joining session as first participant ===");
        CoordinationParticipant creator = new CoordinationParticipant(myNostrPubkey);
        creator.setName(wallet.getName());
        session.addParticipant(creator);
        log.error("=== Creator added to session ===");

        // Fire event
        EventManager.get().post(new CoordinationSessionCreatedEvent(session));

        log.error("=== Coordination session created: {}, about to publish to Nostr ===", sessionId);

        // Publish session-create event to Nostr
        publishSessionCreateEvent(session);

        // Publish session-join event for creator
        log.error("=== Publishing creator's session-join event ===");
        publishSessionJoinEvent(sessionId, creator);

        return session;
    }

    /**
     * Join an existing coordination session
     */
    public void joinSession(String sessionId, Wallet wallet, String participantPubkey) {
        CoordinationSession session = sessions.get(sessionId);

        // WORKAROUND: If session doesn't exist locally, create a stub session
        // This allows joining sessions discovered via manual Session ID entry
        // when Nostr event signing is not yet implemented
        if(session == null) {
            log.warn("Session {} not found locally - creating stub session for join", sessionId);

            // Create a minimal session stub
            // We don't know the exact expected participant count, so use a reasonable default
            session = new CoordinationSession(
                sessionId,
                wallet.getDefaultPolicy().getName(),
                wallet.getNetwork(),
                2  // Default to 2 participants - will be updated when we receive full session info
            );

            sessions.put(sessionId, session);
            log.info("Created stub session: {}", sessionId);
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

        // Store session in dialog for UI access
        log.error("=== Posting CoordinationSessionCreatedEvent for session: {} ===", sessionId);
        EventManager.get().post(new CoordinationSessionCreatedEvent(session));

        // Fire participant joined event
        log.error("=== Posting CoordinationParticipantJoinedEvent - Participant: {} joined session: {} ===",
                  participant.getName(), sessionId);
        EventManager.get().post(new CoordinationParticipantJoinedEvent(sessionId, participant));

        log.info("Participant {} joined session: {}", participant.getName(), sessionId);

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

        // IMPORTANT: participantPubkey might be wallet pubkey, not Nostr pubkey
        // If participant not found, assume it's the local user and use myNostrPubkey
        CoordinationParticipant participant = session.getParticipant(participantPubkey);

        String actualPubkey = participantPubkey;
        if(participant == null) {
            // Participant not found with this pubkey - likely a wallet pubkey
            // Use myNostrPubkey for local user
            actualPubkey = myNostrPubkey;
            participant = session.getParticipant(actualPubkey);
            log.error("=== Participant not found with pubkey {}, using myNostrPubkey: {} ===", participantPubkey, actualPubkey);
        }

        log.error("=== Creating local output with pubkey: {} ===", actualPubkey);

        // Create output with pubkey (not name) - UI will get name from participant
        CoordinationOutput output = new CoordinationOutput(address, amount, label, actualPubkey);

        // Add to session
        session.proposeOutput(output);

        // Add to participant
        if(participant != null) {
            participant.addProposedOutput(output);
        }

        log.info("Output proposed for session: {}", sessionId);

        // Publish output-proposal event to Nostr (send actual Nostr pubkey for identification)
        publishOutputProposalEvent(sessionId, output, actualPubkey);
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

        // IMPORTANT: participantPubkey might be wallet pubkey, not Nostr pubkey
        // If participant not found, assume it's the local user and use myNostrPubkey
        CoordinationParticipant participant = session.getParticipant(participantPubkey);

        String actualPubkey = participantPubkey;
        if(participant == null) {
            // Participant not found with this pubkey - likely a wallet pubkey
            // Use myNostrPubkey for local user
            actualPubkey = myNostrPubkey;
            participant = session.getParticipant(actualPubkey);
            log.error("=== Participant not found with pubkey {}, using myNostrPubkey: {} ===", participantPubkey, actualPubkey);
        }

        log.error("=== Creating local fee proposal with pubkey: {} ===", actualPubkey);

        // Create fee proposal with pubkey (not name) so it can be associated with participant
        CoordinationFeeProposal feeProposal = new CoordinationFeeProposal(actualPubkey, feeRate);

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

                // Automatically finalize session when fee is agreed
                if(session.isReadyToFinalize()) {
                    log.info("Session is ready to finalize, auto-finalizing session: {}", sessionId);
                    finalizeSession(sessionId);
                }
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
     * Handle incoming Nostr event from relay manager
     */
    public void handleNostrEvent(NostrEvent nostrEvent) {
        processNostrEvent(nostrEvent);
    }

    /**
     * Handle Nostr message events
     */
    @Subscribe
    public void onNostrMessage(NostrMessageReceivedEvent event) {
        NostrEvent nostrEvent = event.getNostrEvent();
        processNostrEvent(nostrEvent);
    }

    /**
     * Process a Nostr event (common logic for both direct and event-based handling)
     */
    private void processNostrEvent(NostrEvent nostrEvent) {

        // Only process coordination events
        if(nostrEvent.getKind() != NostrEvent.KIND_COORDINATION) {
            return;
        }

        log.error("=== Processing Nostr coordination event, ID: {} ===", nostrEvent.getId());
        log.error("=== Event tags: {} ===", nostrEvent.getTags());

        String messageType = nostrEvent.getTagValue("d");
        log.error("=== Message type from tag 'd': {} ===", messageType);

        if(messageType == null) {
            log.error("=== Received coordination event without message type tag ===");
            return;
        }

        // Ignore our own messages
        if(myNostrPubkey != null && myNostrPubkey.equals(nostrEvent.getPubkey())) {
            log.debug("Ignoring own message: {}", messageType);
            return;
        }

        try {
            // Route to appropriate handler based on message type
            log.error("=== Routing message type: {} ===", messageType);
            switch(messageType) {
                case "session-create":
                    log.error("=== Calling handleSessionCreateMessage ===");
                    handleSessionCreateMessage(nostrEvent);
                    break;
                case "session-join":
                    log.error("=== Calling handleSessionJoinMessage ===");
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
                case "psbt-signed":
                    handlePSBTSignedMessage(nostrEvent);
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

            Network network = Network.valueOf(networkStr.toUpperCase());
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
        log.error("=== handleSessionJoinMessage() called ===");
        String sessionId = event.getTagValue("session-id");
        String participantPubkey = event.getTagValue("participant-pubkey");

        log.error("=== Session ID: {}, Participant pubkey: {} ===", sessionId, participantPubkey);

        if(sessionId == null || participantPubkey == null) {
            log.error("=== session-join message missing required tags ===");
            return;
        }

        CoordinationSession session = sessions.get(sessionId);
        log.error("=== Session found: {} ===", session != null);
        if(session == null) {
            log.error("=== Received join for unknown session: {} ===", sessionId);
            return;
        }

        try {
            log.error("=== Parsing event content ===");
            @SuppressWarnings("unchecked")
            Map<String, Object> content = gson.fromJson(event.getContent(), Map.class);

            String name = (String) content.get("name");
            String xpub = (String) content.get("xpub");
            log.error("=== Participant name: {}, xpub: {} ===", name, xpub != null ? xpub.substring(0, 20) + "..." : "null");

            CoordinationParticipant participant = new CoordinationParticipant(participantPubkey, name, xpub);
            log.error("=== Participant object created ===");

            // Add participant if not already in session
            CoordinationParticipant existing = session.getParticipant(participantPubkey);
            log.error("=== Existing participant: {} ===", existing != null);

            if(existing == null) {
                log.error("=== Adding participant to session ===");
                session.addParticipant(participant);

                // Fire event
                log.error("=== Firing CoordinationParticipantJoinedEvent ===");
                EventManager.get().post(new CoordinationParticipantJoinedEvent(sessionId, participant));

                log.error("=== Participant {} joined session {} ===", participantPubkey.substring(0, 8), sessionId);
            } else {
                log.error("=== Participant already in session, skipping ===");
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

            log.error("=== Creating output with pubkey: {} ===", proposedBy);

            // Create output with pubkey (not name) - UI will get name from participant
            CoordinationOutput output = new CoordinationOutput(address, amount, label, proposedBy);

            CoordinationParticipant participant = session.getParticipant(proposedBy);

            // Check for duplicate before adding
            boolean duplicate = session.getOutputs().stream()
                    .anyMatch(existing -> existing.getAddress().equals(output.getAddress()));

            if(!duplicate) {
                session.proposeOutput(output);

                // Add to participant's outputs
                if(participant != null) {
                    participant.addProposedOutput(output);
                }

                // Fire event to update UI
                EventManager.get().post(new CoordinationOutputProposedEvent(session, output));

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

        log.error("=== handleFeeProposalMessage called: sessionId={}, feeRate={} ===", sessionId, feeRateStr);

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
            String content = event.getContent();
            log.error("=== Fee proposal content: {} ===", content);

            @SuppressWarnings("unchecked")
            Map<String, Object> contentMap = gson.fromJson(content, Map.class);

            String proposedBy = (String) contentMap.get("proposed_by");
            Number feeRateNum = (Number) contentMap.get("fee_rate");

            log.error("=== Parsed: proposedBy={}, feeRateNum={} ===", proposedBy, feeRateNum);

            if(proposedBy == null || feeRateNum == null) {
                log.warn("fee-proposal message missing required fields: proposed_by={}, fee_rate={}", proposedBy, feeRateNum);
                return;
            }

            double feeRate = feeRateNum.doubleValue();

            log.error("=== Creating fee proposal with pubkey: {} ===", proposedBy);

            // Create fee proposal with pubkey (not name) so it can be associated with participant
            CoordinationFeeProposal feeProposal = new CoordinationFeeProposal(proposedBy, feeRate);
            session.proposeFee(feeProposal);

            // Fire event to update UI
            EventManager.get().post(new CoordinationFeeProposedEvent(session, feeProposal));

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
     * Handle incoming psbt-signed messages from participants
     */
    private void handlePSBTSignedMessage(NostrEvent event) {
        String sessionId = event.getTagValue("session-id");

        if(sessionId == null) {
            log.warn("psbt-signed message missing session-id tag");
            return;
        }

        CoordinationSession session = sessions.get(sessionId);
        if(session == null) {
            log.debug("Received PSBT signature for unknown session: {}", sessionId);
            return;
        }

        try {
            String content = event.getContent();
            log.info("=== Received PSBT signature for session: {} ===", sessionId);
            log.debug("PSBT content length: {}", content != null ? content.length() : 0);

            @SuppressWarnings("unchecked")
            Map<String, Object> contentMap = gson.fromJson(content, Map.class);

            String signedBy = (String) contentMap.get("signed_by");
            String psbtBase64 = (String) contentMap.get("psbt");

            if(signedBy == null || psbtBase64 == null) {
                log.warn("psbt-signed message missing required fields: signed_by={}, psbt={}",
                    signedBy, psbtBase64 != null ? "present" : "null");
                return;
            }

            log.info("PSBT signed by participant: {}", signedBy);

            // Parse PSBT from base64
            PSBT psbt = PSBT.fromString(psbtBase64);

            // Store PSBT in participant
            CoordinationParticipant participant = session.getParticipant(signedBy);
            if(participant != null) {
                participant.setSignedPSBT(psbt);
                log.info("Stored signed PSBT from participant: {}", participant.getName());

                // Fire event to update UI
                EventManager.get().post(new CoordinationPSBTSignedEvent(session, participant, psbt));

                // Check if all participants have signed
                checkAndCombinePSBTs(session);
            } else {
                log.warn("Participant not found for pubkey: {}", signedBy);
            }

        } catch(Exception e) {
            log.error("Error handling psbt-signed message", e);
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
        log.error("=== publishSessionCreateEvent() called for session: {} ===", session.getSessionId());
        log.error("=== nostrRelayManager: {}, isConnected: {} ===",
                  nostrRelayManager != null ? "available" : "null",
                  nostrRelayManager != null ? nostrRelayManager.isConnected() : false);

        if(nostrRelayManager == null || !nostrRelayManager.isConnected()) {
            log.error("=== Cannot publish session-create event - Nostr not connected ===");
            return;
        }

        try {
            log.error("=== Creating Nostr event with pubkey: {} ===", myNostrPubkey);
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

            log.error("=== About to publish event to Nostr relays ===");
            nostrRelayManager.publishEvent(event);
            log.error("=== Published session-create event for session: {} ===", session.getSessionId());

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
    private void publishOutputProposalEvent(String sessionId, CoordinationOutput output, String participantPubkey) {
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
            content.put("proposed_by", participantPubkey); // Send pubkey for identification
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
            content.put("proposed_by", feeProposal.getProposedBy()); // Already contains pubkey
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
     * Check if all participants have signed and combine PSBTs if ready
     */
    private void checkAndCombinePSBTs(CoordinationSession session) {
        log.info("Checking if all participants have signed for session: {}", session.getSessionId());

        // Check if all participants have signed
        boolean allSigned = true;
        for(CoordinationParticipant participant : session.getParticipants()) {
            if(!participant.hasSigned()) {
                log.info("Participant {} has not signed yet", participant.getName());
                allSigned = false;
            } else {
                log.info("Participant {} has signed", participant.getName());
            }
        }

        if(!allSigned) {
            log.info("Not all participants have signed yet, waiting...");
            return;
        }

        log.info("All participants have signed! Combining PSBTs...");

        try {
            // Collect all signed PSBTs
            List<PSBT> psbtList = new ArrayList<>();
            for(CoordinationParticipant participant : session.getParticipants()) {
                PSBT psbt = participant.getSignedPSBT();
                if(psbt != null) {
                    psbtList.add(psbt);
                    log.info("Added PSBT from participant: {}", participant.getName());
                }
            }

            if(psbtList.isEmpty()) {
                log.error("No PSBTs to combine!");
                return;
            }

            // Combine all PSBTs
            PSBT combinedPSBT = psbtList.get(0);
            for(int i = 1; i < psbtList.size(); i++) {
                log.info("Combining PSBT {} of {}", i + 1, psbtList.size());
                combinedPSBT.combine(psbtList.get(i));
            }

            log.info("PSBTs combined successfully!");
            log.info("Combined PSBT has {} inputs and {} outputs",
                combinedPSBT.getPsbtInputs().size(),
                combinedPSBT.getPsbtOutputs().size());

            // Fire event with combined PSBT
            EventManager.get().post(new CoordinationPSBTCombinedEvent(session, combinedPSBT));

            // TODO: Finalize and extract transaction
            // TODO: Broadcast transaction

        } catch(Exception e) {
            log.error("Error combining PSBTs", e);
        }
    }

    /**
     * Publish signed PSBT to Nostr
     */
    public void publishSignedPSBT(String sessionId, PSBT signedPSBT) {
        CoordinationSession session = sessions.get(sessionId);
        if(session == null) {
            log.error("Session not found: {}", sessionId);
            return;
        }

        if(nostrRelayManager == null || !nostrRelayManager.isConnected()) {
            log.warn("Cannot publish signed PSBT - Nostr not connected");
            return;
        }

        try {
            NostrEvent event = new NostrEvent(myNostrPubkey, NostrEvent.KIND_COORDINATION, "");

            event.addTag("d", "psbt-signed");
            event.addTag("session-id", sessionId);

            // Serialize PSBT to base64
            String psbtBase64 = signedPSBT.toBase64String();

            Map<String, Object> content = new HashMap<>();
            content.put("signed_by", myNostrPubkey);
            content.put("psbt", psbtBase64);
            event.setContent(gson.toJson(content));

            nostrRelayManager.publishEvent(event);
            log.info("Published signed PSBT for session: {}", sessionId);

            // Store our own signed PSBT locally
            CoordinationParticipant myParticipant = session.getParticipant(myNostrPubkey);
            if(myParticipant != null) {
                myParticipant.setSignedPSBT(signedPSBT);
                log.info("Stored our signed PSBT locally");

                // Fire event to update UI
                EventManager.get().post(new CoordinationPSBTSignedEvent(session, myParticipant, signedPSBT));

                // Check if we can combine now
                checkAndCombinePSBTs(session);
            }

        } catch(Exception e) {
            log.error("Failed to publish signed PSBT", e);
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
