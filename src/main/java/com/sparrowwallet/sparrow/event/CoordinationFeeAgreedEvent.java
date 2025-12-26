package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.sparrow.coordination.CoordinationSession;

/**
 * Event fired when a fee rate has been agreed upon by all participants.
 *
 * This event indicates that:
 * - All participants have proposed fee rates
 * - A consensus fee rate has been selected (typically the highest)
 * - The session is one step closer to finalization
 *
 * UI components can listen to this event to:
 * - Show the agreed fee rate prominently
 * - Disable fee proposal UI
 * - Enable finalization button
 * - Display confirmation message
 */
public class CoordinationFeeAgreedEvent {
    private final CoordinationSession session;
    private final double agreedFeeRate;

    public CoordinationFeeAgreedEvent(CoordinationSession session, double agreedFeeRate) {
        this.session = session;
        this.agreedFeeRate = agreedFeeRate;
    }

    public CoordinationSession getSession() {
        return session;
    }

    public double getAgreedFeeRate() {
        return agreedFeeRate;
    }
}
