package com.sparrowwallet.sparrow.coordination;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a participant in a coordination session
 */
public class CoordinationParticipant {
    private final String pubkey;
    private String name;
    private String xpub;
    private ParticipantStatus status;
    private final List<CoordinationOutput> proposedOutputs;
    private CoordinationFeeProposal feeProposal;
    private final LocalDateTime joinedAt;

    public CoordinationParticipant(String pubkey) {
        this.pubkey = pubkey;
        this.status = ParticipantStatus.JOINED;
        this.proposedOutputs = new ArrayList<>();
        this.joinedAt = LocalDateTime.now();
    }

    public CoordinationParticipant(String pubkey, String name, String xpub) {
        this(pubkey);
        this.name = name;
        this.xpub = xpub;
    }

    public String getPubkey() {
        return pubkey;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getXpub() {
        return xpub;
    }

    public void setXpub(String xpub) {
        this.xpub = xpub;
    }

    public ParticipantStatus getStatus() {
        return status;
    }

    public void setStatus(ParticipantStatus status) {
        this.status = status;
    }

    public List<CoordinationOutput> getProposedOutputs() {
        return new ArrayList<>(proposedOutputs);
    }

    public void addProposedOutput(CoordinationOutput output) {
        this.proposedOutputs.add(output);
    }

    public CoordinationFeeProposal getFeeProposal() {
        return feeProposal;
    }

    public void setFeeProposal(CoordinationFeeProposal feeProposal) {
        this.feeProposal = feeProposal;
    }

    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }

    public boolean hasProposedFee() {
        return feeProposal != null;
    }

    public boolean isReady() {
        return status == ParticipantStatus.READY;
    }

    @Override
    public String toString() {
        return "CoordinationParticipant{" +
                "pubkey='" + pubkey + '\'' +
                ", name='" + name + '\'' +
                ", status=" + status +
                ", proposedOutputs=" + proposedOutputs.size() +
                ", hasProposedFee=" + hasProposedFee() +
                '}';
    }

    /**
     * Status of a participant in the coordination session
     */
    public enum ParticipantStatus {
        /**
         * Participant has joined the session
         */
        JOINED,

        /**
         * Participant is actively proposing outputs/fees
         */
        ACTIVE,

        /**
         * Participant has finished proposing and is ready to finalize
         */
        READY,

        /**
         * Participant has left the session
         */
        LEFT
    }
}
