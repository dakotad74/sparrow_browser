# P2P Exchange UI Redesign - Implementation Summary

## Overview

This document summarizes the implementation of the P2P Exchange UI redesign for Sparrow Wallet. The new design follows a Master-Detail layout (Wallapop-style) with integrated chat, status bar showing Tor/Nostr/Relays, and NIP-99 compatible offers.

## Files Created

### UI Components (`src/main/java/com/sparrowwallet/sparrow/p2p/ui/`)

| File | Description |
|------|-------------|
| `P2PStatusBar.java` | Top status bar showing Tor, Nostr, and 3 relay connection status |
| `OfferCard.java` | Grid card component for displaying offers (Wallapop-style) |
| `OfferDetailPanel.java` | Right panel with full offer details and "Contact" button |
| `ChatPanel.java` | Integrated chat panel with NIP-04 encrypted messages |
| `P2PMarketplacePane.java` | Main marketplace layout with Master-Detail SplitPane |
| `P2PExchangePane.java` | Wrapper component that initializes services |

### Nostr Integration (`src/main/java/com/sparrowwallet/sparrow/p2p/nostr/`)

| File | Description |
|------|-------------|
| `NostrOffer.java` | NIP-99 (kind 30402) offer format converter |

### Resources

| File | Description |
|------|-------------|
| `p2p-marketplace.fxml` | FXML for new marketplace layout |
| `p2p.css` (updated) | New styles for offer cards, chat bubbles, status bar |

### Documentation

| File | Description |
|------|-------------|
| `NOSTR_MARKETPLACE_SPEC.md` | Technical spec for NIPs used (NIP-99, NIP-04, NIP-09) |

## Files Modified

| File | Changes |
|------|---------|
| `NostrEvent.java` | Added constants for NIP-99 (KIND_CLASSIFIED_LISTING=30402) |
| `NostrRelayManager.java` | Added `getRelayUrls()` and `isRelayConnected()` methods |
| `NostrP2PService.java` | Updated to handle both kind 30402 and 38400 offers |
| `ChatMessage.java` | Added constructor without nostrEventId parameter |
| `p2p.css` | Added ~150 lines of new styles |

## Architecture

```
P2PMarketplacePane (BorderPane)
├── top: P2PStatusBar
│   ├── Tor indicator (green/red)
│   ├── Nostr indicator
│   └── 3 Relay indicators with names
├── center: SplitPane (60/40)
│   ├── left: VBox
│   │   ├── Header (title + filters)
│   │   └── ScrollPane → FlowPane (OfferCards grid)
│   └── right: VBox
│       ├── OfferDetailPanel
│       └── ChatPanel (when contacting)
└── bottom: Identity bar + navigation buttons
```

## NIP Compliance

### NIP-99 (Classified Listings)
- Offers published as kind 30402 events
- Tags: `d`, `title`, `summary`, `price`, `amt`, `type`, `payment`, `location`, `status`
- Content: Markdown formatted description
- Backward compatible with legacy kind 38400

### NIP-04 (Encrypted DMs)
- Already implemented in ChatService
- Messages sent as kind 4 events
- ECDH shared secret + AES-256-CBC encryption

### NIP-09 (Event Deletion)
- Offers deleted with kind 5 events
- Uses `a` tag for addressable events (kind 30402)
- Uses `e` tag for legacy events

## Features Implemented

1. **Status Bar**
   - Tor connection indicator (connected/disconnected)
   - Nostr network status
   - 3 relay indicators with names and tooltips
   - Connection count (e.g., "2/3")

2. **Offer Grid**
   - FlowPane with responsive OfferCard components
   - Card shows: type badge, price, amount, payment method, seller
   - Hover and selection effects
   - Click to view details

3. **Detail Panel**
   - Full offer information
   - Seller profile section
   - "Contact Seller/Buyer" button
   - Delete button (for own offers)

4. **Integrated Chat**
   - Opens below detail panel (no popup)
   - Message bubbles with timestamps
   - NIP-04 encryption status shown
   - Close button to dismiss

5. **Filters**
   - Type (Buy/Sell/All)
   - Currency (USD/EUR/etc)
   - Payment method
   - Text search

6. **Tor Integration**
   - Status updates every 5 seconds
   - Checks `AppServices.isTorRunning()` and `Config.isUseProxy()`

## Usage

The new UI can be integrated by:

1. **As standalone pane:**
```java
P2PMarketplacePane marketplace = new P2PMarketplacePane();
// Add to your scene
```

2. **Via P2PExchangePane (recommended):**
```java
P2PExchangePane exchange = new P2PExchangePane();
// Automatically initializes Nostr services
```

3. **Via FXML:**
```xml
<P2PMarketplacePane fx:id="marketplacePane"/>
```

## Styling

New CSS classes added to `p2p.css`:

- `.p2p-status-bar` - Status bar container
- `.offer-card` - Grid card styling
- `.offer-card:hover` - Hover effect
- `.offer-detail-panel` - Right panel
- `.chat-panel` - Chat container
- `.chat-bubble-sent` - Blue bubble (sent messages)
- `.chat-bubble-received` - Gray bubble (received)
- `.contact-button` - Primary action button

## Next Steps

1. **Integration with main app**: Replace or toggle between old/new P2P Exchange view
2. **Image support**: Add image upload to offers (NIP-94)
3. **NIP-17 migration**: Replace NIP-04 with NIP-17 for better privacy
4. **Tor proxy for WebSocket**: Configure NostrRelayManager to use Tor SOCKS5

---
Document created: 2026-01-08
Version: 1.0
