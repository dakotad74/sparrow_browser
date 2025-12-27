package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.sparrow.coordination.CoordinationSession;

/**
 * Event fired when all participant PSBTs have been combined in a coordination session.
 *
 * This event is triggered when all participants have signed their PSBTs
 * and the PSBTs have been successfully combined into a single PSBT.
 *
 * UI components can listen to this event to:
 * - Display the combined PSBT
 * - Enable transaction finalization
 * - Show that the coordination is complete and ready for broadcast
 */
public class CoordinationPSBTCombinedEvent {
    private final CoordinationSession session;
    private final PSBT combinedPSBT;

    public CoordinationPSBTCombinedEvent(CoordinationSession session, PSBT combinedPSBT) {
        this.session = session;
        this.combinedPSBT = combinedPSBT;
    }

    public CoordinationSession getSession() {
        return session;
    }

    public PSBT getCombinedPSBT() {
        return combinedPSBT;
    }
}
