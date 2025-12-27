package com.sparrowwallet.sparrow.p2p.identity;

import com.sparrowwallet.drongo.SecureString;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents a Nostr identity for P2P trading.
 *
 * Supports two types of identities:
 * - EPHEMERAL: Single-use, auto-delete, maximum privacy
 * - PERSISTENT: Long-term, build reputation, multiple trades
 *
 * Each identity has:
 * - Nostr keypair (nsec/npub)
 * - Display name
 * - Type and status
 * - Optional reputation data (persistent only)
 * - Lifecycle tracking
 */
public class NostrIdentity {
    private final String id;                    // UUID local identifier
    private String nsec;                        // Private key (hex format, will be encrypted in storage)
    private String npub;                        // Public key (npub format)
    private String hex;                         // Public key (hex format)
    private String displayName;                 // User-visible name
    private final IdentityType type;            // EPHEMERAL or PERSISTENT

    // Reputation (only for PERSISTENT identities)
    private Integer completedTrades;
    private Double averageRating;
    private List<String> reviewIds;             // References to review events on Nostr

    // Lifecycle
    private final LocalDateTime createdAt;
    private LocalDateTime expiresAt;            // Only for EPHEMERAL
    private LocalDateTime lastUsedAt;
    private IdentityStatus status;

    // Privacy settings
    private boolean autoDelete;                 // Auto-delete after use (EPHEMERAL)
    private String linkedTradeId;               // Associated trade (EPHEMERAL)

    /**
     * Create a new Nostr identity
     */
    public NostrIdentity(String nsec, String npub, String hex, String displayName, IdentityType type) {
        this.id = UUID.randomUUID().toString();
        this.nsec = nsec;
        this.npub = npub;
        this.hex = hex;
        this.displayName = displayName;
        this.type = type;
        this.createdAt = LocalDateTime.now();
        this.status = IdentityStatus.ACTIVE;
        this.autoDelete = (type == IdentityType.EPHEMERAL);
        this.reviewIds = new ArrayList<>();

        // Initialize reputation for persistent identities
        if (type == IdentityType.PERSISTENT) {
            this.completedTrades = 0;
            this.averageRating = 0.0;
        }
    }

    // Getters and setters

    public String getId() {
        return id;
    }

    public String getNsec() {
        return nsec;
    }

    public void setNsec(String nsec) {
        this.nsec = nsec;
    }

    public String getNpub() {
        return npub;
    }

    public String getHex() {
        return hex;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public IdentityType getType() {
        return type;
    }

    public Integer getCompletedTrades() {
        return completedTrades;
    }

    public void setCompletedTrades(Integer completedTrades) {
        this.completedTrades = completedTrades;
    }

    public Double getAverageRating() {
        return averageRating;
    }

    public void setAverageRating(Double averageRating) {
        this.averageRating = averageRating;
    }

    public List<String> getReviewIds() {
        return new ArrayList<>(reviewIds);
    }

    public void addReview(String reviewId) {
        this.reviewIds.add(reviewId);
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public LocalDateTime getLastUsedAt() {
        return lastUsedAt;
    }

    public void setLastUsedAt(LocalDateTime lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }

    public IdentityStatus getStatus() {
        return status;
    }

    public void setStatus(IdentityStatus status) {
        this.status = status;
    }

    public boolean isAutoDelete() {
        return autoDelete;
    }

    public void setAutoDelete(boolean autoDelete) {
        this.autoDelete = autoDelete;
    }

    public String getLinkedTradeId() {
        return linkedTradeId;
    }

    public void setLinkedTradeId(String linkedTradeId) {
        this.linkedTradeId = linkedTradeId;
    }

    // Utility methods

    /**
     * Check if this identity is ephemeral
     */
    public boolean isEphemeral() {
        return type == IdentityType.EPHEMERAL;
    }

    /**
     * Check if this identity is persistent
     */
    public boolean isPersistent() {
        return type == IdentityType.PERSISTENT;
    }

    /**
     * Check if this identity is currently active
     */
    public boolean isActive() {
        return status == IdentityStatus.ACTIVE;
    }

    /**
     * Check if this identity is deleted
     */
    public boolean isDeleted() {
        return status == IdentityStatus.DELETED;
    }

    /**
     * Check if this identity has expired (ephemeral only)
     */
    public boolean isExpired() {
        if (expiresAt == null) {
            return false;
        }
        return LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * Mark this identity as used (updates status and last used time)
     */
    public void markAsUsed() {
        this.lastUsedAt = LocalDateTime.now();
        if (isEphemeral()) {
            this.status = IdentityStatus.USED;
        }
    }

    /**
     * Mark this identity as deleted
     */
    public void delete() {
        this.status = IdentityStatus.DELETED;
    }

    /**
     * Increment completed trades counter (persistent only)
     */
    public void incrementCompletedTrades() {
        if (isPersistent() && completedTrades != null) {
            completedTrades++;
        }
    }

    /**
     * Update average rating (persistent only)
     */
    public void updateRating(double newRating) {
        if (isPersistent() && averageRating != null && completedTrades != null) {
            // Weighted average
            double totalRating = averageRating * (completedTrades - 1);
            averageRating = (totalRating + newRating) / completedTrades;
        }
    }

    /**
     * Get a short display version of the npub (first 8 chars + ... + last 4 chars)
     */
    public String getShortNpub() {
        if (npub == null || npub.length() < 16) {
            return npub;
        }
        return npub.substring(0, 12) + "..." + npub.substring(npub.length() - 4);
    }

    /**
     * Get reputation stars (for display)
     */
    public String getReputationStars() {
        if (!isPersistent() || averageRating == null) {
            return "";
        }

        int fullStars = (int) Math.floor(averageRating);
        StringBuilder stars = new StringBuilder();
        for (int i = 0; i < fullStars && i < 5; i++) {
            stars.append("⭐");
        }
        return stars.toString();
    }

    @Override
    public String toString() {
        return "NostrIdentity{" +
                "id='" + id + '\'' +
                ", displayName='" + displayName + '\'' +
                ", npub='" + getShortNpub() + '\'' +
                ", type=" + type +
                ", status=" + status +
                ", completedTrades=" + completedTrades +
                ", rating=" + averageRating +
                '}';
    }
}
