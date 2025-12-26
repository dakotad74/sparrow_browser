package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.sparrow.nostr.NostrEvent;

/**
 * Event fired when a Nostr message/event is received from relays.
 * Used to distribute incoming coordination messages to interested components.
 */
public class NostrMessageReceivedEvent {
    private final NostrEvent nostrEvent;
    private final String relayUrl;

    public NostrMessageReceivedEvent(NostrEvent nostrEvent) {
        this(nostrEvent, null);
    }

    public NostrMessageReceivedEvent(NostrEvent nostrEvent, String relayUrl) {
        this.nostrEvent = nostrEvent;
        this.relayUrl = relayUrl;
    }

    public NostrEvent getNostrEvent() {
        return nostrEvent;
    }

    public String getRelayUrl() {
        return relayUrl;
    }

    @Override
    public String toString() {
        return "NostrMessageReceivedEvent{" +
                "event=" + nostrEvent +
                ", relay='" + relayUrl + '\'' +
                '}';
    }
}
