package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.sparrow.coordination.CoordinationSession;

/**
 * Event fired when a PSBT is successfully created from a coordination session.
 *
 * This event contains:
 * - The coordination session that generated the PSBT
 * - The created PSBT ready for signing/combining
 *
 * UI components can listen to this event to:
 * - Display the PSBT in a viewer
 * - Offer to save the PSBT to disk
 * - Show combine/sign options
 * - Navigate to PSBT tab
 */
public class CoordinationPSBTCreatedEvent {
    private final CoordinationSession session;
    private final PSBT psbt;

    public CoordinationPSBTCreatedEvent(CoordinationSession session, PSBT psbt) {
        this.session = session;
        this.psbt = psbt;
    }

    public CoordinationSession getSession() {
        return session;
    }

    public PSBT getPsbt() {
        return psbt;
    }
}
