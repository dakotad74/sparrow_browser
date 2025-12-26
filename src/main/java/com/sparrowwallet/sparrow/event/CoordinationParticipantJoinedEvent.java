package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.sparrow.coordination.CoordinationParticipant;

/**
 * Event fired when a participant joins a coordination session
 */
public class CoordinationParticipantJoinedEvent {
    private final String sessionId;
    private final CoordinationParticipant participant;

    public CoordinationParticipantJoinedEvent(String sessionId, CoordinationParticipant participant) {
        this.sessionId = sessionId;
        this.participant = participant;
    }

    public String getSessionId() {
        return sessionId;
    }

    public CoordinationParticipant getParticipant() {
        return participant;
    }
}
