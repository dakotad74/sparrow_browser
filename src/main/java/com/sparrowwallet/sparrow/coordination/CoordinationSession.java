package com.sparrowwallet.sparrow.coordination;

import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.wallet.Wallet;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Represents a coordination session for multi-party transaction construction.
 * Manages participants, outputs, fee proposals, and session state.
 */
public class CoordinationSession {
    private final String sessionId;
    private final String walletDescriptor;
    private final Network network;
    private SessionState state;
    private final Map<String, CoordinationParticipant> participants;
    private final List<CoordinationOutput> outputs;
    private final List<CoordinationFeeProposal> feeProposals;
    private Double agreedFeeRate;
    private final LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private final int expectedParticipants;

    // Timeout constants
    private static final int INACTIVITY_TIMEOUT_HOURS = 1;
    private static final int MAX_SESSION_HOURS = 24;

    public CoordinationSession(String sessionId, String walletDescriptor, Network network, int expectedParticipants) {
        if(sessionId == null || sessionId.trim().isEmpty()) {
            throw new IllegalArgumentException("SessionId cannot be null or empty");
        }
        if(walletDescriptor == null) {
            throw new IllegalArgumentException("WalletDescriptor cannot be null");
        }
        if(network == null) {
            throw new IllegalArgumentException("Network cannot be null");
        }
        if(expectedParticipants < 2) {
            throw new IllegalArgumentException("Expected participants must be at least 2");
        }

        this.sessionId = sessionId;
        this.walletDescriptor = walletDescriptor;
        this.network = network;
        this.expectedParticipants = expectedParticipants;
        this.state = SessionState.CREATED;
        this.participants = new ConcurrentHashMap<>();
        this.outputs = Collections.synchronizedList(new ArrayList<>());
        this.feeProposals = Collections.synchronizedList(new ArrayList<>());
        this.createdAt = LocalDateTime.now();
        this.expiresAt = createdAt.plusHours(MAX_SESSION_HOURS);
    }

    // Participant management

    /**
     * Add a participant to the session
     */
    public synchronized void addParticipant(CoordinationParticipant participant) {
        if(state == SessionState.FINALIZED || state == SessionState.COMPLETED || state == SessionState.EXPIRED) {
            throw new IllegalStateException("Cannot add participants to a " + state + " session");
        }

        if(participants.containsKey(participant.getPubkey())) {
            throw new IllegalArgumentException("Participant already exists: " + participant.getPubkey());
        }

        participants.put(participant.getPubkey(), participant);
        updateInactivityTimeout();

        // Update state if all participants joined
        if(state == SessionState.CREATED && participants.size() == expectedParticipants) {
            state = SessionState.JOINING;
        }
    }

    /**
     * Get a participant by pubkey
     */
    public CoordinationParticipant getParticipant(String pubkey) {
        return participants.get(pubkey);
    }

    /**
     * Get all participants
     */
    public List<CoordinationParticipant> getParticipants() {
        return new ArrayList<>(participants.values());
    }

    /**
     * Check if all expected participants have joined
     */
    public boolean allParticipantsJoined() {
        return participants.size() >= expectedParticipants;
    }

    // Output management

    /**
     * Propose an output for the transaction
     */
    public synchronized void proposeOutput(CoordinationOutput output) {
        if(state == SessionState.FINALIZED || state == SessionState.COMPLETED || state == SessionState.EXPIRED) {
            throw new IllegalStateException("Cannot propose outputs to a " + state + " session");
        }

        // Validate network compatibility
        if(!output.isValidForNetwork(network)) {
            throw new IllegalArgumentException("Output address is not valid for network: " + network);
        }

        // Check for duplicate addresses
        boolean duplicate = outputs.stream()
                .anyMatch(existing -> existing.getAddress().equals(output.getAddress()));
        if(duplicate) {
            throw new IllegalArgumentException("Output with this address already exists");
        }

        outputs.add(output);
        updateInactivityTimeout();

        // Update state
        if(state == SessionState.CREATED || state == SessionState.JOINING) {
            state = SessionState.PROPOSING;
        }
    }

    /**
     * Get all proposed outputs
     */
    public List<CoordinationOutput> getOutputs() {
        return new ArrayList<>(outputs);
    }

    /**
     * Get total output amount
     */
    public long getTotalOutputAmount() {
        return outputs.stream()
                .mapToLong(CoordinationOutput::getAmount)
                .sum();
    }

    // Fee management

    /**
     * Propose a fee rate
     */
    public synchronized void proposeFee(CoordinationFeeProposal feeProposal) {
        if(state == SessionState.FINALIZED || state == SessionState.COMPLETED || state == SessionState.EXPIRED) {
            throw new IllegalStateException("Cannot propose fees to a " + state + " session");
        }

        // Remove previous proposal from same participant
        feeProposals.removeIf(fp -> fp.getProposedBy().equals(feeProposal.getProposedBy()));

        feeProposals.add(feeProposal);

        // Update participant's fee proposal
        CoordinationParticipant participant = participants.get(feeProposal.getProposedBy());
        if(participant != null) {
            participant.setFeeProposal(feeProposal);
        }

        updateInactivityTimeout();

        // Update state
        if(state == SessionState.PROPOSING) {
            state = SessionState.AGREEING;
        }
    }

    /**
     * Get all fee proposals
     */
    public List<CoordinationFeeProposal> getFeeProposals() {
        return new ArrayList<>(feeProposals);
    }

    /**
     * Set the agreed fee rate (locks it)
     */
    public synchronized void agreeFee(double feeRate) {
        if(feeRate <= 0) {
            throw new IllegalArgumentException("Fee rate must be positive");
        }
        this.agreedFeeRate = feeRate;
        updateInactivityTimeout();
    }

    /**
     * Get the highest proposed fee rate (automatic selection strategy)
     */
    public Optional<Double> getHighestProposedFeeRate() {
        return feeProposals.stream()
                .map(CoordinationFeeProposal::getFeeRate)
                .max(Double::compareTo);
    }

    /**
     * Check if all participants have proposed fees
     */
    public boolean allParticipantsProposedFees() {
        return participants.values().stream()
                .allMatch(CoordinationParticipant::hasProposedFee);
    }

    // Session lifecycle

    /**
     * Finalize the session (lock it for PSBT creation)
     */
    public synchronized void finalizeSession() {
        if(!isReadyToFinalize()) {
            throw new IllegalStateException("Session is not ready to finalize");
        }

        state = SessionState.FINALIZED;
        updateInactivityTimeout();
    }

    /**
     * Check if session is ready to be finalized
     */
    public boolean isReadyToFinalize() {
        return allParticipantsJoined()
                && !outputs.isEmpty()
                && allParticipantsProposedFees()
                && agreedFeeRate != null
                && state != SessionState.FINALIZED
                && state != SessionState.COMPLETED
                && state != SessionState.EXPIRED;
    }

    /**
     * Mark session as completed
     */
    public synchronized void complete() {
        if(state != SessionState.FINALIZED) {
            throw new IllegalStateException("Can only complete a finalized session");
        }
        state = SessionState.COMPLETED;
    }

    /**
     * Check if session has expired
     */
    public boolean isExpired() {
        if(state == SessionState.EXPIRED) {
            return true;
        }

        LocalDateTime now = LocalDateTime.now();
        if(now.isAfter(expiresAt)) {
            state = SessionState.EXPIRED;
            return true;
        }

        return false;
    }

    /**
     * Update the inactivity timeout
     */
    private void updateInactivityTimeout() {
        expiresAt = LocalDateTime.now().plusHours(INACTIVITY_TIMEOUT_HOURS);

        // But not beyond max session time
        LocalDateTime maxExpiry = createdAt.plusHours(MAX_SESSION_HOURS);
        if(expiresAt.isAfter(maxExpiry)) {
            expiresAt = maxExpiry;
        }
    }

    // Getters

    public String getSessionId() {
        return sessionId;
    }

    public String getWalletDescriptor() {
        return walletDescriptor;
    }

    public Network getNetwork() {
        return network;
    }

    public SessionState getState() {
        return state;
    }

    public Double getAgreedFeeRate() {
        return agreedFeeRate;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public int getExpectedParticipants() {
        return expectedParticipants;
    }

    @Override
    public String toString() {
        return "CoordinationSession{" +
                "sessionId='" + sessionId + '\'' +
                ", network=" + network +
                ", state=" + state +
                ", participants=" + participants.size() + "/" + expectedParticipants +
                ", outputs=" + outputs.size() +
                ", agreedFeeRate=" + agreedFeeRate +
                ", createdAt=" + createdAt +
                ", expiresAt=" + expiresAt +
                '}';
    }
}
