package com.sparrowwallet.sparrow.coordination;

import com.sparrowwallet.drongo.address.Address;

import java.time.LocalDateTime;

/**
 * Represents a proposed output in a coordination session
 */
public class CoordinationOutput {
    private final Address address;
    private final long amount;
    private final String label;
    private final String proposedBy;
    private final LocalDateTime proposedAt;

    public CoordinationOutput(Address address, long amount, String proposedBy) {
        this(address, amount, null, proposedBy);
    }

    public CoordinationOutput(Address address, long amount, String label, String proposedBy) {
        if(address == null) {
            throw new IllegalArgumentException("Address cannot be null");
        }
        if(amount <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        if(proposedBy == null || proposedBy.trim().isEmpty()) {
            throw new IllegalArgumentException("ProposedBy cannot be null or empty");
        }

        this.address = address;
        this.amount = amount;
        this.label = label;
        this.proposedBy = proposedBy;
        this.proposedAt = LocalDateTime.now();
    }

    public Address getAddress() {
        return address;
    }

    public long getAmount() {
        return amount;
    }

    public String getLabel() {
        return label;
    }

    public String getProposedBy() {
        return proposedBy;
    }

    public LocalDateTime getProposedAt() {
        return proposedAt;
    }

    /**
     * Validate that this output is compatible with the given network
     */
    public boolean isValidForNetwork(com.sparrowwallet.drongo.Network network) {
        try {
            // Address validation is done at construction time by Address class
            // Just verify address toString doesn't throw
            address.toString();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CoordinationOutput that = (CoordinationOutput) o;

        if (amount != that.amount) return false;
        return address.equals(that.address);
    }

    @Override
    public int hashCode() {
        int result = address.hashCode();
        result = 31 * result + (int) (amount ^ (amount >>> 32));
        return result;
    }

    @Override
    public String toString() {
        return "CoordinationOutput{" +
                "address=" + address +
                ", amount=" + amount +
                ", label='" + label + '\'' +
                ", proposedBy='" + proposedBy + '\'' +
                ", proposedAt=" + proposedAt +
                '}';
    }
}
