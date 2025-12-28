package com.sparrowwallet.sparrow.p2p;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.sparrow.nostr.NostrEvent;
import com.sparrowwallet.sparrow.nostr.NostrEventService;
import com.sparrowwallet.sparrow.nostr.NostrRelayManager;
import com.sparrowwallet.sparrow.p2p.identity.NostrIdentity;
import com.sparrowwallet.sparrow.p2p.identity.NostrIdentityManager;
import com.sparrowwallet.sparrow.p2p.trade.PaymentMethod;
import com.sparrowwallet.sparrow.p2p.trade.TradeOffer;
import com.sparrowwallet.sparrow.p2p.trade.TradeOfferManager;
import com.sparrowwallet.sparrow.p2p.trade.TradeOfferType;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Nostr service for P2P Exchange.
 * Handles publishing and subscribing to trade offers via Nostr.
 */
public class NostrP2PService {
    private static final Logger log = LoggerFactory.getLogger(NostrP2PService.class);

    private static NostrP2PService instance;

    private final NostrEventService eventService;
    private final TradeOfferManager offerManager;
    private final NostrIdentityManager identityManager;
    private final Gson gson;
    private final Set<String> processedEventIds; // Track processed Nostr event IDs to avoid duplicates

    private boolean subscribed = false;
    private String subscriptionId;

    private NostrP2PService() {
        this.eventService = new NostrEventService();
        this.offerManager = TradeOfferManager.getInstance();
        this.identityManager = NostrIdentityManager.getInstance();
        this.gson = new Gson();
        this.processedEventIds = new java.util.concurrent.ConcurrentHashMap<String, Boolean>().newKeySet();
    }

    public static synchronized NostrP2PService getInstance() {
        if (instance == null) {
            instance = new NostrP2PService();
        }
        return instance;
    }

    /**
     * Start the P2P service
     */
    public void start() {
        log.error("=== STARTING NOSTR P2P SERVICE ===");

        // Start event service
        eventService.start();

        log.error("=== NOSTR P2P SERVICE STARTED ===");
    }

    /**
     * Subscribe to trade offers
     */
    public void subscribeToOffers() {
        NostrRelayManager relayManager = eventService.getRelayManager();
        if (relayManager == null || !relayManager.isConnected()) {
            log.warn("Cannot subscribe to offers: not connected to relays");
            return;
        }

        if (subscribed) {
            log.debug("Already subscribed to offers");
            return;
        }

        // Configure message handler (after relays are connected)
        // This chains P2P handler with coordination handler
        relayManager.setMessageHandler(event -> {
            log.error("=== RECEIVED EVENT: kind={}, pubkey={} ===", event.getKind(), event.getPubkey().substring(0, 8));

            // Process P2P events (kind 38400)
            handleIncomingEvent(event);

            // Also process coordination events (kind 38383)
            if (event.getKind() == 38383) {
                // Let coordination system handle it
                // This is a workaround - coordination handler is lost
            }
        });
        log.error("=== MESSAGE HANDLER SET (CHAINED) ===");

        // Create filter for trade offers (kind: 38400)
        Map<String, Object> filter = new HashMap<>();
        filter.put("kinds", List.of(NostrEvent.KIND_P2P_TRADE_OFFER));

        // Get offers from last 24 hours (not just future ones)
        long oneDayAgo = (System.currentTimeMillis() / 1000) - (24 * 60 * 60);
        filter.put("since", oneDayAgo);

        // Limit to 100 most recent offers
        filter.put("limit", 100);

        // Generate unique subscription ID
        subscriptionId = "p2p-offers-" + System.currentTimeMillis();

        log.error("=== SUBSCRIBING TO P2P TRADE OFFERS (kind: {}) ===", NostrEvent.KIND_P2P_TRADE_OFFER);
        relayManager.subscribe(subscriptionId, filter);

        subscribed = true;
        log.error("=== SUBSCRIBED TO OFFERS ===");
    }

    /**
     * Publish a trade offer to Nostr
     */
    public void publishOffer(TradeOffer offer) {
        NostrRelayManager relayManager = eventService.getRelayManager();
        if (relayManager == null || !relayManager.isConnected()) {
            log.warn("Cannot publish offer: not connected to relays");
            return;
        }

        // Get active identity
        NostrIdentity identity = identityManager.getActiveIdentity();
        if (identity == null) {
            log.error("Cannot publish offer: no active identity");
            return;
        }

        try {
            // Create Nostr event for the offer
            NostrEvent event = createOfferEvent(offer, identity);

            // Set private key for signing (convert nsec hex to ECKey)
            String nsecHex = identity.getNsec();
            if (nsecHex == null || nsecHex.isEmpty()) {
                log.error("Cannot publish offer: identity has no private key");
                return;
            }

            ECKey privateKey = ECKey.fromPrivate(Utils.hexToBytes(nsecHex));
            relayManager.setPrivateKey(privateKey);

            // Publish event
            relayManager.publishEvent(event);

            // Store event ID in offer
            if (event.getId() != null) {
                offer.setNostrEventId(event.getId());
            }

            log.error("=== PUBLISHED OFFER TO NOSTR: {} ===", offer.getId());

        } catch (Exception e) {
            log.error("Failed to publish offer to Nostr", e);
        }
    }

    /**
     * Create Nostr event from trade offer
     */
    private NostrEvent createOfferEvent(TradeOffer offer, NostrIdentity identity) {
        // Create event content (JSON with offer details)
        JsonObject content = new JsonObject();
        content.addProperty("offer_id", offer.getId());
        content.addProperty("type", offer.getType().name());
        content.addProperty("amount_sats", offer.getAmountSats());
        content.addProperty("currency", offer.getCurrency());
        content.addProperty("price", offer.getPrice());
        content.addProperty("use_market_price", offer.isUseMarketPrice());
        content.addProperty("premium_percent", offer.getPremiumPercent());
        content.addProperty("payment_method", offer.getPaymentMethod().name());

        if (offer.getLocation() != null) {
            content.addProperty("location", offer.getLocation());
        }

        if (offer.getDescription() != null) {
            content.addProperty("description", offer.getDescription());
        }

        if (offer.getTerms() != null) {
            content.addProperty("terms", offer.getTerms());
        }

        content.addProperty("min_trade_sats", offer.getMinTradeSats());
        content.addProperty("max_trade_sats", offer.getMaxTradeSats());
        content.addProperty("escrow_time_hours", offer.getEscrowTimeHours());

        // Create Nostr event
        NostrEvent event = new NostrEvent(
            identity.getHex(),
            NostrEvent.KIND_P2P_TRADE_OFFER,
            gson.toJson(content)
        );

        // Add tags for filtering
        event.addTag("t", "p2p-trade"); // General P2P trade tag
        event.addTag("type", offer.getType().name().toLowerCase()); // buy or sell
        event.addTag("currency", offer.getCurrency()); // USD, EUR, etc.
        event.addTag("payment", offer.getPaymentMethod().name()); // Payment method

        if (offer.getLocation() != null && !offer.getLocation().isEmpty()) {
            event.addTag("location", offer.getLocation());
        }

        return event;
    }

    /**
     * Handle incoming Nostr event
     */
    private void handleIncomingEvent(NostrEvent event) {
        log.error("=== handleIncomingEvent called: kind={} ===", event.getKind());

        if (event.getKind() != NostrEvent.KIND_P2P_TRADE_OFFER) {
            log.error("=== Not a trade offer, kind={} (expected {}) ===", event.getKind(), NostrEvent.KIND_P2P_TRADE_OFFER);
            return; // Not a trade offer
        }

        // Check if we already processed this event (avoid duplicates from multiple relays)
        String eventId = event.getId();
        if (processedEventIds.contains(eventId)) {
            log.debug("Already processed event {}, ignoring duplicate from another relay", eventId.substring(0, 8));
            return;
        }

        try {
            log.error("=== Processing trade offer event: {} ===", eventId);

            // Mark event as processed
            processedEventIds.add(eventId);

            // Parse offer from event content
            TradeOffer offer = parseOfferFromEvent(event);

            if (offer != null) {
                // Add to marketplace (on JavaFX thread)
                Platform.runLater(() -> {
                    offerManager.addMarketplaceOffer(offer);
                    log.error("=== ADDED OFFER TO MARKETPLACE: {} ===", offer.getDisplaySummary());
                });
            }

        } catch (Exception e) {
            log.error("Failed to parse offer from Nostr event", e);
        }
    }

    /**
     * Parse trade offer from Nostr event
     */
    private TradeOffer parseOfferFromEvent(NostrEvent event) {
        try {
            JsonObject content = gson.fromJson(event.getContent(), JsonObject.class);

            // Extract offer data
            String offerId = content.get("offer_id").getAsString();
            TradeOfferType type = TradeOfferType.valueOf(content.get("type").getAsString());
            long amountSats = content.get("amount_sats").getAsLong();
            String currency = content.get("currency").getAsString();
            double price = content.get("price").getAsDouble();
            boolean useMarketPrice = content.get("use_market_price").getAsBoolean();
            double premiumPercent = content.get("premium_percent").getAsDouble();
            PaymentMethod paymentMethod = PaymentMethod.valueOf(content.get("payment_method").getAsString());

            String location = content.has("location") ? content.get("location").getAsString() : null;
            String locationDetail = null; // Not included in public event for privacy
            String description = content.has("description") ? content.get("description").getAsString() : null;
            String terms = content.has("terms") ? content.get("terms").getAsString() : null;

            long minTradeSats = content.get("min_trade_sats").getAsLong();
            long maxTradeSats = content.get("max_trade_sats").getAsLong();
            int escrowTimeHours = content.get("escrow_time_hours").getAsInt();

            // Get creator info from event
            String creatorHex = event.getPubkey();
            String creatorNpub = convertHexToNpub(creatorHex);
            String creatorName = "Anon"; // Default name, could fetch from profile events

            // Create offer object
            TradeOffer offer = new TradeOffer(
                creatorHex,
                creatorName,
                type,
                amountSats,
                currency,
                price,
                useMarketPrice,
                premiumPercent,
                minTradeSats,
                maxTradeSats,
                paymentMethod,
                location,
                locationDetail,
                description,
                terms,
                escrowTimeHours
            );

            // Set Nostr event ID
            offer.setNostrEventId(event.getId());

            // Set published time from event
            offer.setPublishedAt(java.time.LocalDateTime.ofEpochSecond(
                event.getCreatedAt(),
                0,
                java.time.ZoneOffset.UTC
            ));

            // Set status to ACTIVE (offers from Nostr are already published)
            offer.setStatus(com.sparrowwallet.sparrow.p2p.trade.TradeOfferStatus.ACTIVE);

            return offer;

        } catch (Exception e) {
            log.error("Failed to parse offer content", e);
            return null;
        }
    }

    /**
     * Convert hex pubkey to npub (Bech32 encoding)
     * TODO: Implement proper Bech32 encoding
     */
    private String convertHexToNpub(String hex) {
        // Placeholder - would need proper Bech32 encoding
        return "npub1" + hex.substring(0, Math.min(16, hex.length()));
    }

    /**
     * Check if connected to relays
     */
    public boolean isConnected() {
        NostrRelayManager relayManager = eventService.getRelayManager();
        return relayManager != null && relayManager.isConnected();
    }

    /**
     * Get number of connected relays
     */
    public int getConnectedRelayCount() {
        NostrRelayManager relayManager = eventService.getRelayManager();
        if (relayManager == null) {
            return 0;
        }
        return relayManager.getConnectedRelayCount();
    }

    /**
     * Get relay manager for direct access
     */
    public NostrRelayManager getRelayManager() {
        return eventService.getRelayManager();
    }

    /**
     * Stop the service
     */
    public void stop() {
        log.info("Stopping Nostr P2P Service");

        if (subscribed && subscriptionId != null) {
            NostrRelayManager relayManager = eventService.getRelayManager();
            if (relayManager != null) {
                relayManager.unsubscribe(subscriptionId);
            }
            subscribed = false;
        }

        eventService.shutdown();
        log.info("Nostr P2P Service stopped");
    }
}
