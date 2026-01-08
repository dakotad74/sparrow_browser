package com.sparrowwallet.sparrow.p2p.chat;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a chat message between two traders.
 * Messages are encrypted using NIP-04 and sent as Nostr kind 4 events.
 */
public class ChatMessage {
    private final String id;
    private final String senderHex;
    private final String senderName;
    private final String recipientHex;
    private final String content;              // Decrypted content
    private final LocalDateTime timestamp;
    private final String nostrEventId;         // Nostr event ID
    private final boolean outgoing;            // True if sent by current user
    private boolean read;                      // Read status

    /**
     * Constructor for received message
     */
    public ChatMessage(String senderHex, String senderName, String recipientHex,
                      String content, LocalDateTime timestamp, String nostrEventId,
                      boolean outgoing) {
        this.id = UUID.randomUUID().toString();
        this.senderHex = senderHex;
        this.senderName = senderName;
        this.recipientHex = recipientHex;
        this.content = content;
        this.timestamp = timestamp;
        this.nostrEventId = nostrEventId;
        this.outgoing = outgoing;
        this.read = outgoing;
    }

    /**
     * Constructor for local message (without nostrEventId)
     */
    public ChatMessage(String senderHex, String senderName, String recipientHex,
                      String recipientName, String content, LocalDateTime timestamp,
                      boolean outgoing) {
        this.id = UUID.randomUUID().toString();
        this.senderHex = senderHex;
        this.senderName = senderName;
        this.recipientHex = recipientHex;
        this.content = content;
        this.timestamp = timestamp;
        this.nostrEventId = null;
        this.outgoing = outgoing;
        this.read = outgoing;
    }

    // Getters
    public String getId() { return id; }
    public String getSenderHex() { return senderHex; }
    public String getSenderName() { return senderName; }
    public String getRecipientHex() { return recipientHex; }
    public String getContent() { return content; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public String getNostrEventId() { return nostrEventId; }
    public boolean isOutgoing() { return outgoing; }
    public boolean isRead() { return read; }

    public void markAsRead() {
        this.read = true;
    }

    /**
     * Get formatted timestamp for display
     */
    public String getFormattedTime() {
        java.time.format.DateTimeFormatter formatter =
            java.time.format.DateTimeFormatter.ofPattern("HH:mm");
        return timestamp.format(formatter);
    }

    /**
     * Get formatted date for display
     */
    public String getFormattedDate() {
        java.time.format.DateTimeFormatter formatter =
            java.time.format.DateTimeFormatter.ofPattern("MMM dd");
        return timestamp.format(formatter);
    }

    @Override
    public String toString() {
        return "ChatMessage{" +
            "sender='" + senderName + '\'' +
            ", timestamp=" + timestamp +
            ", outgoing=" + outgoing +
            ", content='" + (content.length() > 50 ? content.substring(0, 50) + "..." : content) + '\'' +
            '}';
    }
}
