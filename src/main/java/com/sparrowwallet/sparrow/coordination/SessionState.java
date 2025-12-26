package com.sparrowwallet.sparrow.coordination;

/**
 * Represents the current state of a coordination session
 */
public enum SessionState {
    /**
     * Session has been created but participants haven't joined yet
     */
    CREATED,

    /**
     * Participants are joining the session
     */
    JOINING,

    /**
     * Participants are proposing outputs
     */
    PROPOSING,

    /**
     * Participants are agreeing on fee rate
     */
    AGREEING,

    /**
     * Session is finalized and ready for PSBT creation
     */
    FINALIZED,

    /**
     * PSBT has been created and distributed
     */
    COMPLETED,

    /**
     * Session has expired due to inactivity or timeout
     */
    EXPIRED
}
