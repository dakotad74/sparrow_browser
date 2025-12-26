package com.sparrowwallet.sparrow.event;

/**
 * Event fired when Nostr relay connections are established
 */
public class NostrConnectedEvent {
    private final int connectedRelays;

    public NostrConnectedEvent(int connectedRelays) {
        this.connectedRelays = connectedRelays;
    }

    public int getConnectedRelays() {
        return connectedRelays;
    }
}
