package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.sparrow.coordination.CoordinationOutput;
import com.sparrowwallet.sparrow.coordination.CoordinationSession;

/**
 * Event fired when an output is proposed in a coordination session.
 *
 * This event is triggered when any participant (local or remote) proposes
 * a new output to be included in the coordinated transaction.
 *
 * UI components can listen to this event to:
 * - Update the output list in real-time
 * - Show which participant proposed the output
 * - Recalculate total output amount
 * - Validate no conflicts exist
 */
public class CoordinationOutputProposedEvent {
    private final CoordinationSession session;
    private final CoordinationOutput output;

    public CoordinationOutputProposedEvent(CoordinationSession session, CoordinationOutput output) {
        this.session = session;
        this.output = output;
    }

    public CoordinationSession getSession() {
        return session;
    }

    public CoordinationOutput getOutput() {
        return output;
    }
}
