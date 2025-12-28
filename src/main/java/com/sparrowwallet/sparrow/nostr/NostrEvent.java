package com.sparrowwallet.sparrow.nostr;

import com.google.gson.annotations.SerializedName;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a Nostr event for coordination messages.
 *
 * Implements NIP-01: Basic protocol event structure
 *
 * Nostr event structure (JSON):
 * {
 *   "id": <32-byte hex string>,
 *   "pubkey": <32-byte hex string>,
 *   "created_at": <unix timestamp>,
 *   "kind": <integer>,
 *   "tags": [["tag_name", "value1", "value2"], ...],
 *   "content": <string>,
 *   "sig": <64-byte hex string>
 * }
 *
 * This class uses Gson's @SerializedName to map Java camelCase to JSON snake_case.
 */
public class NostrEvent {
    private String id;

    private String pubkey;

    @SerializedName("created_at")
    private long createdAt;

    private int kind;

    private List<List<String>> tags;

    private String content;

    private String sig;

    /**
     * Event kinds for coordination
     */
    public static final int KIND_COORDINATION = 38383; // Custom kind for Bitcoin coordination
    public static final int KIND_P2P_TRADE_OFFER = 38400; // P2P trade offers (buy/sell BTC)

    public NostrEvent() {
        this.tags = new ArrayList<>();
        this.createdAt = Instant.now().getEpochSecond();
    }

    public NostrEvent(String pubkey, int kind, String content) {
        this();
        this.pubkey = pubkey;
        this.kind = kind;
        this.content = content != null ? content : "";
    }

    /**
     * Add a tag to the event
     */
    public void addTag(String... tagValues) {
        List<String> tag = new ArrayList<>();
        for(String value : tagValues) {
            if(value != null) {
                tag.add(value);
            }
        }
        if(!tag.isEmpty()) {
            tags.add(tag);
        }
    }

    /**
     * Get tag value by tag name
     */
    public String getTagValue(String tagName) {
        for(List<String> tag : tags) {
            if(!tag.isEmpty() && tag.get(0).equals(tagName)) {
                return tag.size() > 1 ? tag.get(1) : null;
            }
        }
        return null;
    }

    /**
     * Get all values for a tag name
     */
    public List<String> getTagValues(String tagName) {
        List<String> values = new ArrayList<>();
        for(List<String> tag : tags) {
            if(!tag.isEmpty() && tag.get(0).equals(tagName)) {
                if(tag.size() > 1) {
                    values.add(tag.get(1));
                }
            }
        }
        return values;
    }

    // Getters and Setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPubkey() {
        return pubkey;
    }

    public void setPubkey(String pubkey) {
        this.pubkey = pubkey;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public int getKind() {
        return kind;
    }

    public void setKind(int kind) {
        this.kind = kind;
    }

    public List<List<String>> getTags() {
        return tags;
    }

    public void setTags(List<List<String>> tags) {
        this.tags = tags != null ? tags : new ArrayList<>();
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getSig() {
        return sig;
    }

    public void setSig(String sig) {
        this.sig = sig;
    }

    @Override
    public String toString() {
        return "NostrEvent{" +
                "id='" + (id != null ? id.substring(0, Math.min(8, id.length())) + "..." : "null") + '\'' +
                ", pubkey='" + (pubkey != null ? pubkey.substring(0, Math.min(8, pubkey.length())) + "..." : "null") + '\'' +
                ", createdAt=" + createdAt +
                ", kind=" + kind +
                ", tags=" + tags.size() +
                ", content=" + (content != null ? content.length() + " chars" : "null") +
                '}';
    }
}
