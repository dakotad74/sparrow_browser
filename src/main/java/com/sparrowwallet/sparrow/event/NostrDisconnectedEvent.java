package com.sparrowwallet.sparrow.event;

/**
 * Event fired when Nostr relay connections are lost
 */
public class NostrDisconnectedEvent {
    private final String reason;

    public NostrDisconnectedEvent(String reason) {
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }
}
