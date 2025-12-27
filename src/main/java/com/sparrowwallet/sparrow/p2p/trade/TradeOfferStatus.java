package com.sparrowwallet.sparrow.p2p.trade;

/**
 * Status of a trade offer
 */
public enum TradeOfferStatus {
    /**
     * Offer is being created but not yet published
     */
    DRAFT("Draft"),

    /**
     * Offer is active and visible in marketplace
     */
    ACTIVE("Active"),

    /**
     * Offer is paused (not visible but can be reactivated)
     */
    PAUSED("Paused"),

    /**
     * Offer has been cancelled by creator
     */
    CANCELLED("Cancelled"),

    /**
     * Offer has been completed (trade executed)
     */
    COMPLETED("Completed"),

    /**
     * Offer has expired (time limit reached)
     */
    EXPIRED("Expired");

    private final String displayName;

    TradeOfferStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Check if offer can be published
     */
    public boolean canPublish() {
        return this == DRAFT || this == PAUSED;
    }

    /**
     * Check if offer can be edited
     */
    public boolean canEdit() {
        return this == DRAFT;
    }

    /**
     * Check if offer can be cancelled
     */
    public boolean canCancel() {
        return this == ACTIVE || this == PAUSED;
    }

    /**
     * Check if offer is visible in marketplace
     */
    public boolean isVisibleInMarketplace() {
        return this == ACTIVE;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
