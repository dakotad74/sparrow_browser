package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.sparrow.coordination.SessionState;

/**
 * Event fired when a coordination session changes state
 */
public class CoordinationSessionStateChangedEvent {
    private final String sessionId;
    private final SessionState oldState;
    private final SessionState newState;

    public CoordinationSessionStateChangedEvent(String sessionId, SessionState oldState, SessionState newState) {
        this.sessionId = sessionId;
        this.oldState = oldState;
        this.newState = newState;
    }

    public String getSessionId() {
        return sessionId;
    }

    public SessionState getOldState() {
        return oldState;
    }

    public SessionState getNewState() {
        return newState;
    }
}
