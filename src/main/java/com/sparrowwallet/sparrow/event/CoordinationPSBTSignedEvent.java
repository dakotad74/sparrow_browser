package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.sparrow.coordination.CoordinationParticipant;
import com.sparrowwallet.sparrow.coordination.CoordinationSession;

/**
 * Event fired when a participant signs their PSBT in a coordination session.
 *
 * This event is triggered when any participant signs and publishes their
 * PSBT for the coordinated transaction.
 *
 * UI components can listen to this event to:
 * - Display which participants have signed
 * - Show signing progress
 * - Enable transaction finalization when all participants have signed
 */
public class CoordinationPSBTSignedEvent {
    private final CoordinationSession session;
    private final CoordinationParticipant participant;
    private final PSBT psbt;

    public CoordinationPSBTSignedEvent(CoordinationSession session, CoordinationParticipant participant, PSBT psbt) {
        this.session = session;
        this.participant = participant;
        this.psbt = psbt;
    }

    public CoordinationSession getSession() {
        return session;
    }

    public CoordinationParticipant getParticipant() {
        return participant;
    }

    public PSBT getPsbt() {
        return psbt;
    }
}
