package com.sparrowwallet.sparrow.event;

/**
 * Event fired when a Nostr message/event is received from relays
 *
 * NOTE: This is a stub for Phase 1. Will be fully implemented in Phase 2.
 */
public class NostrMessageReceivedEvent {
    private final String eventData;

    public NostrMessageReceivedEvent(String eventData) {
        this.eventData = eventData;
    }

    public String getEventData() {
        return eventData;
    }
}
