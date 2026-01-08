package com.sparrowwallet.sparrow.p2p.nostr;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sparrowwallet.sparrow.nostr.NostrEvent;
import com.sparrowwallet.sparrow.p2p.trade.PaymentMethod;
import com.sparrowwallet.sparrow.p2p.trade.TradeOffer;
import com.sparrowwallet.sparrow.p2p.trade.TradeOfferStatus;
import com.sparrowwallet.sparrow.p2p.trade.TradeOfferType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

public class NostrOffer {
    private static final Logger log = LoggerFactory.getLogger(NostrOffer.class);
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final DecimalFormat BTC_FORMAT = new DecimalFormat("#.########");

    public static NostrEvent toNostrEvent(TradeOffer offer, String pubkeyHex) {
        StringBuilder content = new StringBuilder();
        content.append("## ").append(offer.getType() == TradeOfferType.SELL ? "Selling" : "Buying");
        content.append(" ").append(BTC_FORMAT.format(offer.getAmountBtc())).append(" BTC\n\n");

        content.append("**Amount:** ").append(String.format("%,d", offer.getAmountSats())).append(" sats\n");
        content.append("**Price:** ");
        if (offer.isUseMarketPrice()) {
            double premium = offer.getPremiumPercent();
            if (premium == 0) {
                content.append("Market price\n");
            } else if (premium > 0) {
                content.append("Market +").append(premium).append("%\n");
            } else {
                content.append("Market ").append(premium).append("%\n");
            }
        } else {
            content.append(String.format("%.2f", offer.getPrice())).append(" ").append(offer.getCurrency()).append("\n");
        }

        content.append("**Payment:** ").append(offer.getPaymentMethod().getDisplayName()).append("\n");

        if (offer.getLocation() != null && !offer.getLocation().isEmpty()) {
            content.append("**Location:** ").append(offer.getLocation()).append("\n");
        }

        if (offer.getDescription() != null && !offer.getDescription().isEmpty()) {
            content.append("\n").append(offer.getDescription()).append("\n");
        }

        if (offer.getTerms() != null && !offer.getTerms().isEmpty()) {
            content.append("\n### Terms\n").append(offer.getTerms()).append("\n");
        }

        NostrEvent event = new NostrEvent(pubkeyHex, NostrEvent.KIND_CLASSIFIED_LISTING, content.toString());

        String dTag = offer.getId() != null ? offer.getId() : UUID.randomUUID().toString();
        event.addTag("d", dTag);

        String title = (offer.getType() == TradeOfferType.SELL ? "Selling " : "Buying ") +
            BTC_FORMAT.format(offer.getAmountBtc()) + " BTC";
        if (!offer.isUseMarketPrice()) {
            title += " for " + String.format("%.2f", offer.getPrice()) + " " + offer.getCurrency();
        }
        event.addTag("title", title);

        event.addTag("summary", title + " via " + offer.getPaymentMethod().getDisplayName());

        event.addTag("published_at", String.valueOf(Instant.now().getEpochSecond()));

        if (offer.getLocation() != null && !offer.getLocation().isEmpty()) {
            event.addTag("location", offer.getLocation());
        }

        if (!offer.isUseMarketPrice()) {
            event.addTag("price", String.valueOf(offer.getPrice()), offer.getCurrency());
        }

        event.addTag("amt", String.valueOf(offer.getAmountSats()), "sats");
        event.addTag("type", offer.getType().name().toLowerCase());
        event.addTag("payment", offer.getPaymentMethod().name().toLowerCase());
        event.addTag("status", "active");

        event.addTag("min_trade", String.valueOf(offer.getMinTradeSats()), "sats");
        event.addTag("max_trade", String.valueOf(offer.getMaxTradeSats()), "sats");
        event.addTag("escrow_hours", String.valueOf(offer.getEscrowTimeHours()));

        event.addTag("t", "bitcoin");
        event.addTag("t", "p2p");
        event.addTag("t", offer.getCurrency().toLowerCase());
        event.addTag("t", offer.getPaymentMethod().name().toLowerCase().replace("_", "-"));

        return event;
    }

    public static TradeOffer fromNostrEvent(NostrEvent event) {
        if (event.getKind() != NostrEvent.KIND_CLASSIFIED_LISTING &&
            event.getKind() != NostrEvent.KIND_P2P_TRADE_OFFER) {
            log.warn("Event is not a classified listing or trade offer: kind={}", event.getKind());
            return null;
        }

        try {
            String dTag = event.getTagValue("d");
            String offerId = dTag != null ? dTag : event.getId();

            String typeStr = event.getTagValue("type");
            TradeOfferType type = TradeOfferType.SELL;
            if (typeStr != null) {
                type = typeStr.equalsIgnoreCase("buy") ? TradeOfferType.BUY : TradeOfferType.SELL;
            }

            String amtStr = event.getTagValue("amt");
            long amountSats = 10000000;
            if (amtStr != null) {
                try {
                    amountSats = Long.parseLong(amtStr);
                } catch (NumberFormatException e) {
                    log.warn("Invalid amount in event: {}", amtStr);
                }
            }

            String currency = "USD";
            double price = 0;
            boolean useMarketPrice = true;
            String priceStr = event.getTagValue("price");
            if (priceStr != null) {
                try {
                    price = Double.parseDouble(priceStr);
                    useMarketPrice = false;
                    for (java.util.List<String> tag : event.getTags()) {
                        if (!tag.isEmpty() && "price".equals(tag.get(0)) && tag.size() > 2) {
                            currency = tag.get(2);
                            break;
                        }
                    }
                } catch (NumberFormatException e) {
                    log.warn("Invalid price in event: {}", priceStr);
                }
            }

            String paymentStr = event.getTagValue("payment");
            PaymentMethod paymentMethod = PaymentMethod.BANK_TRANSFER;
            if (paymentStr != null) {
                try {
                    paymentMethod = PaymentMethod.valueOf(paymentStr.toUpperCase());
                } catch (IllegalArgumentException e) {
                    log.debug("Unknown payment method: {}", paymentStr);
                }
            }

            String location = event.getTagValue("location");
            String title = event.getTagValue("title");
            String description = event.getContent();

            String minTradeStr = event.getTagValue("min_trade");
            long minTradeSats = minTradeStr != null ? Long.parseLong(minTradeStr) : 100000;

            String maxTradeStr = event.getTagValue("max_trade");
            long maxTradeSats = maxTradeStr != null ? Long.parseLong(maxTradeStr) : amountSats;

            String escrowStr = event.getTagValue("escrow_hours");
            int escrowHours = escrowStr != null ? Integer.parseInt(escrowStr) : 24;

            String creatorHex = event.getPubkey();
            String creatorDisplayName = "Anonymous";

            TradeOffer offer = new TradeOffer(
                creatorHex,
                creatorDisplayName,
                type,
                amountSats,
                currency,
                price,
                useMarketPrice,
                0,
                minTradeSats,
                maxTradeSats,
                paymentMethod,
                location,
                null,
                description,
                null,
                escrowHours
            );

            offer.setId(offerId);
            offer.setNostrEventId(event.getId());
            offer.setStatus(TradeOfferStatus.ACTIVE);

            String publishedAt = event.getTagValue("published_at");
            if (publishedAt != null) {
                long timestamp = Long.parseLong(publishedAt);
                offer.setPublishedAt(LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(timestamp), ZoneId.systemDefault()));
            } else if (event.getCreatedAt() > 0) {
                offer.setPublishedAt(LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(event.getCreatedAt()), ZoneId.systemDefault()));
            }

            return offer;

        } catch (Exception e) {
            log.error("Failed to parse offer from Nostr event", e);
            return null;
        }
    }

    public static NostrEvent createDeletionEvent(TradeOffer offer, String pubkeyHex) {
        NostrEvent event = new NostrEvent(pubkeyHex, NostrEvent.KIND_DELETION, "Offer closed");

        if (offer.getNostrEventId() != null) {
            event.addTag("e", offer.getNostrEventId());
        }

        String dTag = offer.getId();
        if (dTag != null) {
            event.addTag("a", NostrEvent.KIND_CLASSIFIED_LISTING + ":" + pubkeyHex + ":" + dTag);
        }

        event.addTag("k", String.valueOf(NostrEvent.KIND_CLASSIFIED_LISTING));

        return event;
    }
}
