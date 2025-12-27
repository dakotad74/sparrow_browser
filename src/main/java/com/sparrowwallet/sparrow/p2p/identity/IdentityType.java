package com.sparrowwallet.sparrow.p2p.identity;

/**
 * Type of Nostr identity for P2P trading.
 *
 * EPHEMERAL: Single-use identity for maximum privacy
 * - Auto-generated for each trade
 * - Deleted after trade completion
 * - No reputation tracking
 * - Recommended for privacy-focused users
 *
 * PERSISTENT: Long-term identity for building reputation
 * - Reused across multiple trades
 * - Builds reputation score over time
 * - Can receive reviews and ratings
 * - Recommended for regular traders
 */
public enum IdentityType {
    /**
     * Ephemeral identity - single-use, auto-delete, maximum privacy
     */
    EPHEMERAL,

    /**
     * Persistent identity - long-term, build reputation over time
     */
    PERSISTENT
}
