package com.sparrowwallet.sparrow.p2p.trade;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a P2P trade offer published on Nostr.
 *
 * Trade offers are published as Nostr kind 38400 events and include:
 * - What is being offered (BUY or SELL BTC)
 * - Amount of BTC
 * - Price and fiat currency
 * - Payment methods accepted
 * - Location preferences
 * - Trading limits (min/max amounts)
 */
public class TradeOffer {

    // Unique identifier (local)
    private final String id;

    // Nostr event ID (when published)
    private String nostrEventId;

    // Creator's identity
    private final String creatorHex;         // Public key in hex format
    private final String creatorNpub;
    private final String creatorDisplayName;

    // Offer type
    private final TradeOfferType type;

    // Amount and pricing
    private final long amountSats;          // Amount in satoshis
    private final String currency;           // USD, EUR, GBP, etc.
    private final double price;              // Price per BTC in fiat
    private final boolean useMarketPrice;    // If true, use current market price
    private final double premiumPercent;     // Premium/discount from market price

    // Trading limits
    private final long minTradeSats;         // Minimum trade amount
    private final long maxTradeSats;         // Maximum trade amount

    // Payment and location
    private final PaymentMethod paymentMethod;
    private final String location;           // City or region
    private final String locationDetail;     // Additional location info

    // Terms and conditions
    private final String description;        // Offer description
    private final String terms;              // Trading terms
    private final int escrowTimeHours;       // Max time for escrow (hours)

    // Status and lifecycle
    private TradeOfferStatus status;
    private final LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private LocalDateTime publishedAt;
    private LocalDateTime completedAt;

    // Statistics
    private int viewCount;
    private int contactCount;

    /**
     * Create a new trade offer
     */
    public TradeOffer(String creatorHex, String creatorDisplayName, TradeOfferType type,
                     long amountSats, String currency, double price, boolean useMarketPrice,
                     double premiumPercent, long minTradeSats, long maxTradeSats,
                     PaymentMethod paymentMethod, String location, String locationDetail,
                     String description, String terms, int escrowTimeHours) {
        this.id = UUID.randomUUID().toString();
        this.creatorHex = creatorHex;
        this.creatorNpub = "npub1" + creatorHex.substring(0, Math.min(16, creatorHex.length())); // Simplified npub
        this.creatorDisplayName = creatorDisplayName;
        this.type = type;
        this.amountSats = amountSats;
        this.currency = currency;
        this.price = price;
        this.useMarketPrice = useMarketPrice;
        this.premiumPercent = premiumPercent;
        this.minTradeSats = minTradeSats;
        this.maxTradeSats = maxTradeSats;
        this.paymentMethod = paymentMethod;
        this.location = location;
        this.locationDetail = locationDetail;
        this.description = description;
        this.terms = terms;
        this.escrowTimeHours = escrowTimeHours;
        this.status = TradeOfferStatus.DRAFT;
        this.createdAt = LocalDateTime.now();
        this.viewCount = 0;
        this.contactCount = 0;
    }

    // Getters
    public String getId() { return id; }
    public String getNostrEventId() { return nostrEventId; }
    public String getCreatorHex() { return creatorHex; }
    public String getCreatorNpub() { return creatorNpub; }
    public String getCreatorDisplayName() { return creatorDisplayName; }
    public TradeOfferType getType() { return type; }
    public long getAmountSats() { return amountSats; }
    public String getCurrency() { return currency; }
    public double getPrice() { return price; }
    public boolean isUseMarketPrice() { return useMarketPrice; }
    public double getPremiumPercent() { return premiumPercent; }
    public long getMinTradeSats() { return minTradeSats; }
    public long getMaxTradeSats() { return maxTradeSats; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public String getLocation() { return location; }
    public String getLocationDetail() { return locationDetail; }
    public String getDescription() { return description; }
    public String getTerms() { return terms; }
    public int getEscrowTimeHours() { return escrowTimeHours; }
    public TradeOfferStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public int getViewCount() { return viewCount; }
    public int getContactCount() { return contactCount; }

    // Setters for mutable fields
    public void setNostrEventId(String nostrEventId) { this.nostrEventId = nostrEventId; }
    public void setStatus(TradeOfferStatus status) { this.status = status; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public void incrementViewCount() { this.viewCount++; }
    public void incrementContactCount() { this.contactCount++; }

    /**
     * Get Bitcoin amount in BTC (formatted)
     */
    public double getAmountBtc() {
        return amountSats / 100_000_000.0;
    }

    /**
     * Get minimum trade in BTC
     */
    public double getMinTradeBtc() {
        return minTradeSats / 100_000_000.0;
    }

    /**
     * Get maximum trade in BTC
     */
    public double getMaxTradeBtc() {
        return maxTradeSats / 100_000_000.0;
    }

    /**
     * Get total fiat value
     */
    public double getTotalFiatValue() {
        return getAmountBtc() * price;
    }

    /**
     * Get effective price (considering premium)
     */
    public double getEffectivePrice() {
        if (useMarketPrice) {
            // In real implementation, fetch market price and apply premium
            return price * (1 + premiumPercent / 100.0);
        }
        return price;
    }

    /**
     * Check if offer is active
     */
    public boolean isActive() {
        return status == TradeOfferStatus.ACTIVE;
    }

    /**
     * Check if offer is expired
     */
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * Get short description for display
     */
    public String getShortDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append(type == TradeOfferType.BUY ? "Buying" : "Selling");
        sb.append(" ");
        sb.append(String.format("%.8f", getAmountBtc()));
        sb.append(" BTC for ");
        sb.append(currency);
        sb.append(" ");
        sb.append(String.format("%.2f", price));
        return sb.toString();
    }

    /**
     * Get display summary with payment method and location
     */
    public String getDisplaySummary() {
        StringBuilder sb = new StringBuilder();
        sb.append(getShortDescription());
        sb.append(" • ");
        sb.append(paymentMethod.getDisplayName());
        if (location != null && !location.isEmpty()) {
            sb.append(" • ");
            sb.append(location);
        }
        return sb.toString();
    }

    /**
     * Publish this offer to Nostr
     */
    public void publish() {
        if (status == TradeOfferStatus.DRAFT) {
            status = TradeOfferStatus.ACTIVE;
            publishedAt = LocalDateTime.now();
            // Set default expiration (7 days)
            if (expiresAt == null) {
                expiresAt = LocalDateTime.now().plusDays(7);
            }
        }
    }

    /**
     * Pause this offer (hide from marketplace)
     */
    public void pause() {
        if (status == TradeOfferStatus.ACTIVE) {
            status = TradeOfferStatus.PAUSED;
        }
    }

    /**
     * Cancel this offer
     */
    public void cancel() {
        status = TradeOfferStatus.CANCELLED;
        completedAt = LocalDateTime.now();
    }

    /**
     * Complete this offer
     */
    public void complete() {
        status = TradeOfferStatus.COMPLETED;
        completedAt = LocalDateTime.now();
    }

    @Override
    public String toString() {
        return "TradeOffer{" +
               "id='" + id + '\'' +
               ", type=" + type +
               ", amount=" + getAmountBtc() + " BTC" +
               ", price=" + price + " " + currency +
               ", payment=" + paymentMethod +
               ", location='" + location + '\'' +
               ", status=" + status +
               '}';
    }
}
