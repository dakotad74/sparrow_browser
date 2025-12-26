package com.sparrowwallet.sparrow.coordination;

import java.time.LocalDateTime;

/**
 * Represents a fee rate proposal from a participant
 */
public class CoordinationFeeProposal {
    private final String proposedBy;
    private final double feeRate; // sat/vB
    private final LocalDateTime proposedAt;

    public CoordinationFeeProposal(String proposedBy, double feeRate) {
        if(proposedBy == null || proposedBy.trim().isEmpty()) {
            throw new IllegalArgumentException("ProposedBy cannot be null or empty");
        }
        if(feeRate <= 0) {
            throw new IllegalArgumentException("Fee rate must be positive");
        }

        this.proposedBy = proposedBy;
        this.feeRate = feeRate;
        this.proposedAt = LocalDateTime.now();
    }

    public String getProposedBy() {
        return proposedBy;
    }

    public double getFeeRate() {
        return feeRate;
    }

    public LocalDateTime getProposedAt() {
        return proposedAt;
    }

    @Override
    public String toString() {
        return "CoordinationFeeProposal{" +
                "proposedBy='" + proposedBy + '\'' +
                ", feeRate=" + feeRate +
                " sat/vB, proposedAt=" + proposedAt +
                '}';
    }
}
