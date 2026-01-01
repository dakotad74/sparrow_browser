package com.sparrowwallet.sparrow.p2p.trade;

import com.google.gson.*;
import com.sparrowwallet.sparrow.io.Storage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Singleton manager for P2P trade offers.
 * Manages local and remote offers, filtering, and Nostr integration.
 */
public class TradeOfferManager {
    private static final Logger log = LoggerFactory.getLogger(TradeOfferManager.class);

    private static TradeOfferManager instance;
    private static final String MY_OFFERS_FILENAME = "my-trade-offers.json";

    // Thread-safe storage
    private final Map<String, TradeOffer> myOffers;           // Offers I created (persistent)
    private final Map<String, TradeOffer> marketplaceOffers;  // Offers from others (not persisted)
    private final List<Runnable> offerUpdateListeners;        // Listeners for UI updates

    /**
     * Private constructor for singleton
     */
    private TradeOfferManager() {
        this.myOffers = new ConcurrentHashMap<>();
        this.marketplaceOffers = new ConcurrentHashMap<>();
        this.offerUpdateListeners = new ArrayList<>();
        loadMyOffersFromDisk();
        log.info("TradeOfferManager initialized");
    }

    /**
     * Get singleton instance
     */
    public static synchronized TradeOfferManager getInstance() {
        if (instance == null) {
            instance = new TradeOfferManager();
        }
        return instance;
    }

    /**
     * Add a new offer created by me
     */
    public void addMyOffer(TradeOffer offer) {
        if (offer == null) {
            throw new IllegalArgumentException("Offer cannot be null");
        }

        myOffers.put(offer.getId(), offer);
        saveMyOffersToDisk();
        log.info("Added my offer: {} - {}", offer.getId(), offer.getShortDescription());
    }

    /**
     * Add an offer from the marketplace (from Nostr)
     */
    public void addMarketplaceOffer(TradeOffer offer) {
        if (offer == null) {
            throw new IllegalArgumentException("Offer cannot be null");
        }

        // Check if this is our own offer that we actually created (exists in myOffers)
        com.sparrowwallet.sparrow.p2p.identity.NostrIdentityManager identityManager =
            com.sparrowwallet.sparrow.p2p.identity.NostrIdentityManager.getInstance();
        com.sparrowwallet.sparrow.p2p.identity.NostrIdentity activeIdentity = identityManager.getActiveIdentity();

        if (activeIdentity != null && offer.getCreatorHex().equals(activeIdentity.getHex())) {
            // This is from our pubkey, but only skip if it's in our local myOffers
            // (i.e., we created it in this session, not from a previous/deleted identity)
            log.error("=== Offer from own pubkey: {}, myOffers.size={}, contains={}",
                     offer.getId().substring(0, 8), myOffers.size(), myOffers.containsKey(offer.getId()));

            // Debug: print all keys in myOffers
            log.error("=== Keys in myOffers: {}", myOffers.keySet().stream()
                     .map(id -> id.substring(0, 8))
                     .collect(java.util.stream.Collectors.joining(", ")));

            // Debug: print offer's Nostr event ID
            log.error("=== Offer Nostr event ID: {}", offer.getNostrEventId() != null ?
                     offer.getNostrEventId().substring(0, 8) : "null");

            if (myOffers.containsKey(offer.getId())) {
                log.error("=== Skipping own offer from marketplace (already in myOffers): {}", offer.getId().substring(0, 8));
                return;
            }
            // If it's from our pubkey but NOT in myOffers, it's from a previous session
            // or deleted identity - treat it as a marketplace offer
            log.error("=== Offer from our pubkey but NOT in myOffers (old identity?), adding to marketplace: {}", offer.getId().substring(0, 8));
        }

        // Check if offer already exists (avoid duplicates from multiple relays)
        if (marketplaceOffers.containsKey(offer.getId())) {
            log.debug("Offer already exists, ignoring duplicate: {}", offer.getId());
            return; // Don't notify listeners for duplicates
        }

        marketplaceOffers.put(offer.getId(), offer);
        log.debug("Added marketplace offer: {} from {}", offer.getId(), offer.getCreatorDisplayName());

        // Notify listeners only for new offers
        notifyOfferUpdate();
    }

    /**
     * Register a listener for offer updates
     */
    public void addOfferUpdateListener(Runnable listener) {
        offerUpdateListeners.add(listener);
    }

    /**
     * Notify all listeners that offers were updated
     */
    private void notifyOfferUpdate() {
        for (Runnable listener : offerUpdateListeners) {
            try {
                listener.run();
            } catch (Exception e) {
                log.error("Error notifying offer update listener", e);
            }
        }
    }

    /**
     * Get all my offers for the active identity
     */
    public List<TradeOffer> getMyOffers() {
        com.sparrowwallet.sparrow.p2p.identity.NostrIdentityManager identityManager =
            com.sparrowwallet.sparrow.p2p.identity.NostrIdentityManager.getInstance();
        com.sparrowwallet.sparrow.p2p.identity.NostrIdentity activeIdentity = identityManager.getActiveIdentity();

        if (activeIdentity == null) {
            return new ArrayList<>();
        }

        String activeIdentityHex = activeIdentity.getHex();

        // Filter offers to only show those created by the active identity
        return myOffers.values().stream()
            .filter(offer -> activeIdentityHex.equals(offer.getCreatorHex()))
            .collect(Collectors.toList());
    }

    /**
     * Get all active marketplace offers (excluding my own)
     */
    public List<TradeOffer> getMarketplaceOffers() {
        com.sparrowwallet.sparrow.p2p.identity.NostrIdentityManager identityManager =
            com.sparrowwallet.sparrow.p2p.identity.NostrIdentityManager.getInstance();
        com.sparrowwallet.sparrow.p2p.identity.NostrIdentity activeIdentity = identityManager.getActiveIdentity();

        String activeIdentityHex = activeIdentity != null ? activeIdentity.getHex() : null;

        return marketplaceOffers.values().stream()
            .filter(TradeOffer::isActive)
            .filter(offer -> !offer.isExpired())
            .filter(offer -> activeIdentityHex == null || !activeIdentityHex.equals(offer.getCreatorHex())) // Exclude my offers
            .sorted(Comparator.comparing(TradeOffer::getPublishedAt).reversed())
            .collect(Collectors.toList());
    }

    /**
     * Get all offers (mine + marketplace)
     */
    public List<TradeOffer> getAllOffers() {
        List<TradeOffer> all = new ArrayList<>();
        all.addAll(getMyOffers());
        all.addAll(getMarketplaceOffers());
        return all;
    }

    /**
     * Get offer by ID
     */
    public TradeOffer getOfferById(String offerId) {
        TradeOffer offer = myOffers.get(offerId);
        if (offer == null) {
            offer = marketplaceOffers.get(offerId);
        }
        return offer;
    }

    /**
     * Get offer by Nostr event ID
     */
    public TradeOffer getOfferByNostrEventId(String nostrEventId) {
        // Search in my offers
        for (TradeOffer offer : myOffers.values()) {
            if (nostrEventId.equals(offer.getNostrEventId())) {
                return offer;
            }
        }

        // Search in marketplace offers
        for (TradeOffer offer : marketplaceOffers.values()) {
            if (nostrEventId.equals(offer.getNostrEventId())) {
                return offer;
            }
        }

        return null;
    }

    /**
     * Remove offer by local ID (used for local deletion without Nostr event)
     */
    public void removeMyOffer(String offerId) {
        TradeOffer removed = myOffers.remove(offerId);
        if (removed != null) {
            log.info("Removed local offer: {}", offerId);
            saveMyOffersToDisk();
            notifyOfferUpdate();
        }
    }

    /**
     * Remove offer by Nostr event ID (used for handling deletion events)
     */
    public void removeOfferByNostrEventId(String nostrEventId) {
        // Remove from my offers
        myOffers.values().removeIf(offer -> nostrEventId.equals(offer.getNostrEventId()));

        // Remove from marketplace offers
        marketplaceOffers.values().removeIf(offer -> nostrEventId.equals(offer.getNostrEventId()));

        // Notify listeners
        notifyOfferUpdate();
    }

    /**
     * Filter offers by type
     */
    public List<TradeOffer> filterByType(List<TradeOffer> offers, TradeOfferType type) {
        if (type == null) {
            return offers;
        }
        return offers.stream()
            .filter(offer -> offer.getType() == type)
            .collect(Collectors.toList());
    }

    /**
     * Filter offers by currency
     */
    public List<TradeOffer> filterByCurrency(List<TradeOffer> offers, String currency) {
        if (currency == null || currency.isEmpty() || "All".equals(currency)) {
            return offers;
        }
        return offers.stream()
            .filter(offer -> currency.equals(offer.getCurrency()))
            .collect(Collectors.toList());
    }

    /**
     * Filter offers by payment method
     */
    public List<TradeOffer> filterByPaymentMethod(List<TradeOffer> offers, PaymentMethod paymentMethod) {
        if (paymentMethod == null) {
            return offers;
        }
        return offers.stream()
            .filter(offer -> offer.getPaymentMethod() == paymentMethod)
            .collect(Collectors.toList());
    }

    /**
     * Filter offers by location
     */
    public List<TradeOffer> filterByLocation(List<TradeOffer> offers, String location) {
        if (location == null || location.isEmpty() || "Any".equals(location)) {
            return offers;
        }
        return offers.stream()
            .filter(offer -> offer.getLocation() != null &&
                           offer.getLocation().toLowerCase().contains(location.toLowerCase()))
            .collect(Collectors.toList());
    }

    /**
     * Filter offers by amount range
     */
    public List<TradeOffer> filterByAmountRange(List<TradeOffer> offers, long minSats, long maxSats) {
        return offers.stream()
            .filter(offer -> {
                long amount = offer.getAmountSats();
                return amount >= minSats && amount <= maxSats;
            })
            .collect(Collectors.toList());
    }

    /**
     * Apply all filters
     */
    public List<TradeOffer> applyFilters(TradeOfferType type, String currency,
                                        PaymentMethod paymentMethod, String location,
                                        String amountRange) {
        List<TradeOffer> filtered = getMarketplaceOffers();

        // Filter by type
        if (type != null) {
            filtered = filterByType(filtered, type);
        }

        // Filter by currency
        if (currency != null && !currency.isEmpty() && !"All".equals(currency)) {
            filtered = filterByCurrency(filtered, currency);
        }

        // Filter by payment method
        if (paymentMethod != null) {
            filtered = filterByPaymentMethod(filtered, paymentMethod);
        }

        // Filter by location
        if (location != null && !location.isEmpty() && !"Any".equals(location)) {
            filtered = filterByLocation(filtered, location);
        }

        // Filter by amount range
        if (amountRange != null && !amountRange.isEmpty() && !"Any".equals(amountRange)) {
            filtered = filterByAmountRangeString(filtered, amountRange);
        }

        return filtered;
    }

    /**
     * Filter by amount range string (e.g., "< 0.01 BTC", "0.01-0.1 BTC", "> 0.1 BTC")
     */
    private List<TradeOffer> filterByAmountRangeString(List<TradeOffer> offers, String range) {
        if (range.startsWith("< ")) {
            // Less than 0.01 BTC
            return filterByAmountRange(offers, 0, 1_000_000); // 0.01 BTC in sats
        } else if (range.contains("-")) {
            // Between 0.01 and 0.1 BTC
            return filterByAmountRange(offers, 1_000_000, 10_000_000);
        } else if (range.startsWith("> ")) {
            // Greater than 0.1 BTC
            return filterByAmountRange(offers, 10_000_000, Long.MAX_VALUE);
        }
        return offers;
    }

    /**
     * Publish offer to Nostr
     */
    public void publishToNostr(TradeOffer offer) {
        if (offer == null) {
            throw new IllegalArgumentException("Offer cannot be null");
        }

        // Mark as published
        offer.publish();

        // Add to my offers
        addMyOffer(offer);

        // Publish to Nostr via P2P service
        try {
            com.sparrowwallet.sparrow.p2p.NostrP2PService p2pService =
                com.sparrowwallet.sparrow.p2p.NostrP2PService.getInstance();
            p2pService.publishOffer(offer);
            log.info("Published offer to Nostr: {}", offer.getId());
        } catch (Exception e) {
            log.error("Failed to publish offer to Nostr", e);
        }
    }

    /**
     * Cancel an offer
     */
    public void cancelOffer(String offerId) {
        TradeOffer offer = myOffers.get(offerId);
        if (offer != null) {
            // Publish cancellation event to Nostr (this will also remove from local storage)
            com.sparrowwallet.sparrow.p2p.NostrP2PService nostrService =
                com.sparrowwallet.sparrow.p2p.NostrP2PService.getInstance();
            if (nostrService != null) {
                nostrService.cancelOffer(offer);
            }

            // Also remove from marketplace if present (can happen if we loaded our own offer from Nostr)
            marketplaceOffers.remove(offerId);

            log.info("Cancelled offer: {}", offerId);
        }
    }

    /**
     * Update offer statistics
     */
    public void incrementViewCount(String offerId) {
        TradeOffer offer = getOfferById(offerId);
        if (offer != null) {
            offer.incrementViewCount();
        }
    }

    /**
     * Increment contact count
     */
    public void incrementContactCount(String offerId) {
        TradeOffer offer = getOfferById(offerId);
        if (offer != null) {
            offer.incrementContactCount();
        }
    }

    /**
     * Get statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("myOffersCount", myOffers.size());
        stats.put("marketplaceOffersCount", marketplaceOffers.size());
        stats.put("activeOffersCount", getMarketplaceOffers().size());
        return stats;
    }

    /**
     * Get count of my active offers for the active identity
     */
    public int getMyActiveOffersCount() {
        return (int) getMyOffers().stream()
            .filter(TradeOffer::isActive)
            .count();
    }

    /**
     * Get count of all my offers for the active identity
     */
    public int getMyOffersCount() {
        return getMyOffers().size();
    }

    /**
     * Clear all offers (for testing)
     */
    public void clearAll() {
        myOffers.clear();
        marketplaceOffers.clear();
        saveMyOffersToDisk();
        log.info("Cleared all offers");
    }

    /**
     * Save my offers to disk
     */
    private void saveMyOffersToDisk() {
        try {
            File sparrowDir = Storage.getSparrowDir();
            File offersFile = new File(sparrowDir, MY_OFFERS_FILENAME);

            if(!offersFile.exists()) {
                Storage.createOwnerOnlyFile(offersFile);
            }

            // Create JSON array to save
            JsonArray offersArray = new JsonArray();
            for(TradeOffer offer : myOffers.values()) {
                JsonObject offerJson = new JsonObject();

                // Save all offer fields
                offerJson.addProperty("id", offer.getId());
                offerJson.addProperty("nostrEventId", offer.getNostrEventId());
                offerJson.addProperty("creatorHex", offer.getCreatorHex());
                offerJson.addProperty("creatorNpub", offer.getCreatorNpub());
                offerJson.addProperty("creatorDisplayName", offer.getCreatorDisplayName());
                offerJson.addProperty("type", offer.getType().name());
                offerJson.addProperty("amountSats", offer.getAmountSats());
                offerJson.addProperty("currency", offer.getCurrency());
                offerJson.addProperty("price", offer.getPrice());
                offerJson.addProperty("useMarketPrice", offer.isUseMarketPrice());
                offerJson.addProperty("premiumPercent", offer.getPremiumPercent());
                offerJson.addProperty("minTradeSats", offer.getMinTradeSats());
                offerJson.addProperty("maxTradeSats", offer.getMaxTradeSats());
                offerJson.addProperty("paymentMethod", offer.getPaymentMethod().name());
                offerJson.addProperty("location", offer.getLocation());
                offerJson.addProperty("locationDetail", offer.getLocationDetail());
                offerJson.addProperty("description", offer.getDescription());
                offerJson.addProperty("terms", offer.getTerms());
                offerJson.addProperty("escrowTimeHours", offer.getEscrowTimeHours());
                offerJson.addProperty("status", offer.getStatus().name());
                offerJson.addProperty("createdAt", offer.getCreatedAt().toString());

                if(offer.getExpiresAt() != null) {
                    offerJson.addProperty("expiresAt", offer.getExpiresAt().toString());
                }
                if(offer.getPublishedAt() != null) {
                    offerJson.addProperty("publishedAt", offer.getPublishedAt().toString());
                }
                if(offer.getCompletedAt() != null) {
                    offerJson.addProperty("completedAt", offer.getCompletedAt().toString());
                }

                offersArray.add(offerJson);
            }

            // Write to file
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            try (Writer writer = new FileWriter(offersFile)) {
                gson.toJson(offersArray, writer);
                writer.flush();
            }

            log.debug("Saved {} offers to disk", myOffers.size());

        } catch (IOException e) {
            log.error("Failed to save offers to disk", e);
        }
    }

    /**
     * Load my offers from disk
     */
    private void loadMyOffersFromDisk() {
        try {
            File sparrowDir = Storage.getSparrowDir();
            File offersFile = new File(sparrowDir, MY_OFFERS_FILENAME);

            if(!offersFile.exists()) {
                log.debug("No offers file found, starting fresh");
                return;
            }

            // Read file
            try (Reader reader = new FileReader(offersFile)) {
                Gson gson = new Gson();
                JsonArray offersArray = gson.fromJson(reader, JsonArray.class);

                if(offersArray == null) {
                    log.warn("Empty offers file");
                    return;
                }

                // Load offers
                for(JsonElement element : offersArray) {
                    JsonObject offerJson = element.getAsJsonObject();

                    try {
                        String id = offerJson.get("id").getAsString();
                        String nostrEventId = offerJson.has("nostrEventId") ? offerJson.get("nostrEventId").getAsString() : null;
                        String creatorHex = offerJson.get("creatorHex").getAsString();
                        String creatorDisplayName = offerJson.get("creatorDisplayName").getAsString();
                        TradeOfferType type = TradeOfferType.valueOf(offerJson.get("type").getAsString());
                        long amountSats = offerJson.get("amountSats").getAsLong();
                        String currency = offerJson.get("currency").getAsString();
                        double price = offerJson.get("price").getAsDouble();
                        boolean useMarketPrice = offerJson.get("useMarketPrice").getAsBoolean();
                        double premiumPercent = offerJson.get("premiumPercent").getAsDouble();
                        long minTradeSats = offerJson.get("minTradeSats").getAsLong();
                        long maxTradeSats = offerJson.get("maxTradeSats").getAsLong();
                        PaymentMethod paymentMethod = PaymentMethod.valueOf(offerJson.get("paymentMethod").getAsString());
                        String location = offerJson.has("location") ? offerJson.get("location").getAsString() : null;
                        String locationDetail = offerJson.has("locationDetail") ? offerJson.get("locationDetail").getAsString() : null;
                        String description = offerJson.has("description") ? offerJson.get("description").getAsString() : null;
                        String terms = offerJson.has("terms") ? offerJson.get("terms").getAsString() : null;
                        int escrowTimeHours = offerJson.get("escrowTimeHours").getAsInt();

                        // Reconstruct offer
                        TradeOffer offer = new TradeOffer(
                            creatorHex, creatorDisplayName, type,
                            amountSats, currency, price, useMarketPrice, premiumPercent,
                            minTradeSats, maxTradeSats, paymentMethod,
                            location, locationDetail, description, terms,
                            escrowTimeHours
                        );

                        // Restore state
                        offer.setId(id);  // Restore original ID from JSON (constructor generates new UUID)
                        if(nostrEventId != null) {
                            offer.setNostrEventId(nostrEventId);
                        }
                        offer.setStatus(TradeOfferStatus.valueOf(offerJson.get("status").getAsString()));

                        if(offerJson.has("expiresAt")) {
                            offer.setExpiresAt(LocalDateTime.parse(offerJson.get("expiresAt").getAsString()));
                        }
                        if(offerJson.has("publishedAt")) {
                            offer.setPublishedAt(LocalDateTime.parse(offerJson.get("publishedAt").getAsString()));
                        }
                        if(offerJson.has("completedAt")) {
                            offer.setCompletedAt(LocalDateTime.parse(offerJson.get("completedAt").getAsString()));
                        }

                        myOffers.put(id, offer);

                    } catch (Exception e) {
                        log.error("Failed to load offer from JSON", e);
                    }
                }

                log.info("Loaded {} offers from disk", myOffers.size());

            }

        } catch (Exception e) {
            log.error("Failed to load offers from disk", e);
        }
    }
}
