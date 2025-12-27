package com.sparrowwallet.sparrow.p2p.identity;

/**
 * Status of a Nostr identity in its lifecycle.
 */
public enum IdentityStatus {
    /**
     * Identity is active and can be used for trading
     */
    ACTIVE,

    /**
     * Identity has been used and trade is in progress
     */
    IN_USE,

    /**
     * Identity was used and trade is completed (ephemeral only)
     */
    USED,

    /**
     * Identity has been deleted and cannot be used
     */
    DELETED
}
