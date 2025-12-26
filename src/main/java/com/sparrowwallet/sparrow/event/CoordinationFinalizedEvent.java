package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.sparrow.coordination.CoordinationSession;

/**
 * Event fired when a coordination session is finalized and ready for PSBT creation.
 *
 * This event indicates that:
 * - All participants have joined
 * - All outputs have been proposed
 * - Fee rate has been agreed upon
 * - Session is locked and ready for PSBT construction
 *
 * UI components can listen to this event to enable "Create PSBT" buttons
 * or automatically trigger PSBT creation.
 */
public class CoordinationFinalizedEvent {
    private final CoordinationSession session;

    public CoordinationFinalizedEvent(CoordinationSession session) {
        this.session = session;
    }

    public CoordinationSession getSession() {
        return session;
    }
}
