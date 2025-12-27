package com.sparrowwallet.sparrow.p2p.trade;

/**
 * Type of trade offer
 */
public enum TradeOfferType {
    /**
     * Buying BTC (offering fiat)
     */
    BUY("Buy BTC"),

    /**
     * Selling BTC (requesting fiat)
     */
    SELL("Sell BTC");

    private final String displayName;

    TradeOfferType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
