package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.sparrow.coordination.CoordinationFeeProposal;
import com.sparrowwallet.sparrow.coordination.CoordinationSession;

/**
 * Event fired when a fee rate is proposed in a coordination session.
 *
 * This event is triggered when any participant proposes a fee rate
 * for the coordinated transaction.
 *
 * UI components can listen to this event to:
 * - Display all proposed fee rates in real-time
 * - Show which participant proposed each rate
 * - Highlight conflicts (different rates)
 * - Enable fee agreement when all participants have proposed
 */
public class CoordinationFeeProposedEvent {
    private final CoordinationSession session;
    private final CoordinationFeeProposal feeProposal;

    public CoordinationFeeProposedEvent(CoordinationSession session, CoordinationFeeProposal feeProposal) {
        this.session = session;
        this.feeProposal = feeProposal;
    }

    public CoordinationSession getSession() {
        return session;
    }

    public CoordinationFeeProposal getFeeProposal() {
        return feeProposal;
    }
}
