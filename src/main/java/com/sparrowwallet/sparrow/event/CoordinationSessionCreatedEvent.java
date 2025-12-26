package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.sparrow.coordination.CoordinationSession;

/**
 * Event fired when a new coordination session is created
 */
public class CoordinationSessionCreatedEvent {
    private final CoordinationSession session;

    public CoordinationSessionCreatedEvent(CoordinationSession session) {
        this.session = session;
    }

    public CoordinationSession getSession() {
        return session;
    }
}
