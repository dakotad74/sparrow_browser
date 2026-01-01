package com.sparrowwallet.sparrow.p2p.chat;

import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.sparrow.nostr.NostrCrypto;
import com.sparrowwallet.sparrow.nostr.NostrEvent;
import com.sparrowwallet.sparrow.nostr.NostrRelayManager;
import com.sparrowwallet.sparrow.p2p.NostrP2PService;
import com.sparrowwallet.sparrow.p2p.identity.NostrIdentity;
import com.sparrowwallet.sparrow.p2p.identity.NostrIdentityManager;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Service for managing P2P chat conversations.
 * Handles NIP-04 encrypted direct messages between traders.
 */
public class ChatService {
    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private static ChatService instance;

    // Active subscriptions by peer hex
    private final Map<String, String> activeSubscriptions;

    // Message handlers
    private final List<Consumer<ChatMessage>> messageHandlers;

    // Track processed event IDs to prevent duplicates from multiple relays
    private final Set<String> processedEventIds;

    // Track if global message handler is registered
    private boolean globalHandlerRegistered = false;

    // Track current subscription identity to detect changes
    private String currentSubscriptionIdentityHex = null;

    private final NostrIdentityManager identityManager;
    private final NostrP2PService p2pService;
    private final ChatMessageStorage messageStorage;

    private ChatService() {
        this.activeSubscriptions = new ConcurrentHashMap<>();
        this.messageHandlers = new ArrayList<>();
        this.processedEventIds = ConcurrentHashMap.newKeySet();
        this.identityManager = NostrIdentityManager.getInstance();
        this.p2pService = NostrP2PService.getInstance();
        this.messageStorage = ChatMessageStorage.getInstance();
    }

    public static synchronized ChatService getInstance() {
        if (instance == null) {
            instance = new ChatService();
        }
        return instance;
    }

    /**
     * Send an encrypted message to a peer with conversation context
     */
    public void sendMessage(String recipientHex, String plaintext, String conversationId) {
        sendMessageInternal(recipientHex, plaintext, conversationId);
    }

    /**
     * Send an encrypted message to a peer (legacy - uses peerHex as conversationId)
     */
    public void sendMessage(String recipientHex, String plaintext) {
        sendMessageInternal(recipientHex, plaintext, recipientHex);
    }

    /**
     * Internal method to send an encrypted message
     */
    private void sendMessageInternal(String recipientHex, String plaintext, String conversationId) {
        NostrIdentity identity = identityManager.getActiveIdentity();
        if (identity == null) {
            throw new IllegalStateException("No active identity");
        }

        try {
            // Get relay manager
            NostrRelayManager relayManager = p2pService.getRelayManager();
            if (relayManager == null || !relayManager.isConnected()) {
                throw new IllegalStateException("Not connected to Nostr relays");
            }

            // Get sender's private key
            String nsecHex = identity.getNsec();
            log.error("=== CHAT: nsecHex length: {} ===", nsecHex != null ? nsecHex.length() : "null");

            if (nsecHex == null || nsecHex.isEmpty()) {
                throw new IllegalStateException("Identity has no private key (nsec)");
            }

            ECKey senderPrivkey = ECKey.fromPrivate(Utils.hexToBytes(nsecHex));
            log.error("=== CHAT: nsec={} ===", nsecHex);
            log.error("=== CHAT: senderPrivkey created successfully ===");

            // Get the pubkey from the ECKey (x-only 32 bytes format for Nostr)
            byte[] pubkeyBytes = senderPrivkey.getPubKey();
            log.error("=== CHAT: Sender's ACTUAL compressed pubkey from privkey: {} ===", Utils.bytesToHex(pubkeyBytes));
            // Extract x-coordinate (skip first byte which is 02 or 03 prefix)
            byte[] xOnly = new byte[32];
            System.arraycopy(pubkeyBytes, 1, xOnly, 0, 32);
            String senderPubkeyHex = Utils.bytesToHex(xOnly);

            log.error("=== CHAT: identity.getHex()={} ===", identity.getHex());
            log.error("=== CHAT: derived pubkey  ={} ===", senderPubkeyHex);
            log.error("=== CHAT: pubkeys match: {} ===", identity.getHex().equals(senderPubkeyHex));

            // Encrypt message with NIP-04
            log.error("=== CHAT: Attempting to encrypt message for recipient: {} ===", recipientHex.substring(0, 8));
            String encryptedContent = NostrCrypto.encrypt(plaintext, recipientHex, senderPrivkey);
            log.error("=== CHAT: Message encrypted successfully ===");

            // Create Nostr event (kind 4 = encrypted DM) - use derived pubkey
            NostrEvent event = new NostrEvent(senderPubkeyHex, 4, encryptedContent);
            event.addTag("p", recipientHex); // Tag recipient

            // Add sender's compressed pubkey for correct ECDH
            String senderCompressedHex = Utils.bytesToHex(pubkeyBytes);
            event.addTag("pubkey_compressed", senderCompressedHex);

            // Add conversation context if different from peerHex
            if (!conversationId.equals(recipientHex)) {
                event.addTag("offer_id", conversationId); // Track which offer this is about
            }

            // Set private key for signing
            relayManager.setPrivateKey(senderPrivkey);
            log.error("=== CHAT: About to publish event, kind={}, hasId={} ===", event.getKind(), event.getId() != null);

            // Publish event
            relayManager.publishEvent(event);
            log.error("=== CHAT: publishEvent() returned ===");

            // Add to local conversation
            ChatMessage message = new ChatMessage(
                identity.getHex(),
                identity.getDisplayName(),
                recipientHex,
                plaintext,
                LocalDateTime.now(),
                event.getId(),
                true // outgoing
            );

            addMessageToConversation(conversationId, message);

            log.info("Sent encrypted message to {}", recipientHex.substring(0, 8));

        } catch (Exception e) {
            log.error("Failed to send message", e);
            throw new RuntimeException("Failed to send message: " + e.getMessage(), e);
        }
    }

    /**
     * Subscribe to ALL incoming messages for the active identity.
     * This should be called when P2P Exchange starts.
     */
    public void subscribeToAllMessages() {
        log.error("=== subscribeToAllMessages() called ===");

        NostrIdentity identity = identityManager.getActiveIdentity();
        log.error("=== Active identity: {} ===", identity != null ? identity.getHex().substring(0, 8) : "NULL");
        if (identity == null) {
            log.warn("Cannot subscribe: no active identity");
            return;
        }

        // If already subscribed to this identity, don't re-subscribe
        if (identity.getHex().equals(currentSubscriptionIdentityHex)) {
            log.debug("Already subscribed to messages for identity {}", identity.getHex().substring(0, 8));
            return;
        }

        NostrRelayManager relayManager = p2pService.getRelayManager();
        log.error("=== Relay manager: {}, connected: {} ===",
            relayManager != null ? "EXISTS" : "NULL",
            relayManager != null ? relayManager.isConnected() : false);
        if (relayManager == null || !relayManager.isConnected()) {
            log.warn("Cannot subscribe: not connected to relays");
            return;
        }

        // Subscribe to all kind 4 (encrypted DM) events where we are the recipient
        // Only get messages from last 24 hours to avoid old/corrupted messages
        long since = Instant.now().getEpochSecond() - (24 * 3600);

        Map<String, Object> filter = new HashMap<>();
        filter.put("kinds", List.of(4)); // Encrypted DMs
        filter.put("#p", List.of(identity.getHex())); // Messages to me
        filter.put("since", since); // Only recent messages

        String subscriptionId = "chat-all-" + identity.getHex().substring(0, 8);
        log.error("=== Creating subscription: {} ===", subscriptionId);

        // Register message handler only once
        if (!globalHandlerRegistered) {
            try {
                relayManager.addMessageHandler(this::handleIncomingEvent);
                globalHandlerRegistered = true;
                log.error("=== Message handler added (first time) ===");
            } catch (Exception e) {
                log.error("=== ERROR adding message handler ===", e);
            }
        } else {
            log.error("=== Message handler already registered, skipping ===");
        }

        try {
            log.error("=== About to call subscribe with filter: {} ===", filter);
            relayManager.subscribe(subscriptionId, filter);
            log.error("=== Subscription request sent ===");

            // Track that we're now subscribed to this identity
            currentSubscriptionIdentityHex = identity.getHex();
        } catch (Exception e) {
            log.error("=== ERROR calling subscribe ===", e);
        }

        log.info("Subscribed to all incoming messages for {}", identity.getHex().substring(0, 8));
    }

    /**
     * Update subscription when active identity changes.
     * Unsubscribes from old identity and subscribes to new one.
     */
    public void updateSubscriptionForIdentity(NostrIdentity newIdentity, NostrIdentity oldIdentity) {
        log.info("Updating chat subscription: {} -> {}",
            oldIdentity != null ? oldIdentity.getHex().substring(0, 8) : "null",
            newIdentity != null ? newIdentity.getHex().substring(0, 8) : "null");

        NostrRelayManager relayManager = p2pService.getRelayManager();
        if (relayManager == null || !relayManager.isConnected()) {
            log.warn("Cannot update subscription: not connected to relays");
            return;
        }

        // Unsubscribe from old identity
        if (oldIdentity != null) {
            String oldSubId = "chat-all-" + oldIdentity.getHex().substring(0, 8);
            try {
                relayManager.unsubscribe(oldSubId);
                log.info("Unsubscribed from old identity: {}", oldSubId);
            } catch (Exception e) {
                log.error("Error unsubscribing from old identity", e);
            }
        }

        // Reset tracking so subscribeToAllMessages will create new subscription
        currentSubscriptionIdentityHex = null;

        // Subscribe to new identity
        subscribeToAllMessages();
    }

    /**
     * Subscribe to messages from a specific peer
     */
    public void subscribeToConversation(String peerHex) {
        if (activeSubscriptions.containsKey(peerHex)) {
            log.debug("Already subscribed to conversation with {}", peerHex.substring(0, 8));
            return;
        }

        NostrIdentity identity = identityManager.getActiveIdentity();
        if (identity == null) {
            throw new IllegalStateException("No active identity");
        }

        NostrRelayManager relayManager = p2pService.getRelayManager();
        if (relayManager == null || !relayManager.isConnected()) {
            log.warn("Cannot subscribe: not connected to relays");
            return;
        }

        // Subscribe to kind 4 events where:
        // - pubkey = peerHex (messages from peer)
        // - OR tags contain "p" = myHex (messages to me)
        Map<String, Object> filter = new HashMap<>();
        filter.put("kinds", List.of(4)); // Encrypted DMs
        filter.put("authors", List.of(peerHex)); // From peer
        filter.put("#p", List.of(identity.getHex())); // To me

        String subscriptionId = "chat-" + peerHex.substring(0, 8) + "-" + System.currentTimeMillis();

        // Set message handler to process incoming DMs
        relayManager.addMessageHandler(this::handleIncomingEvent);

        relayManager.subscribe(subscriptionId, filter);
        activeSubscriptions.put(peerHex, subscriptionId);

        log.info("Subscribed to conversation with {}", peerHex.substring(0, 8));
    }

    /**
     * Handle incoming Nostr event
     */
    private void handleIncomingEvent(NostrEvent event) {
        log.error("=== ChatService.handleIncomingEvent: kind={}, id={} ===", event.getKind(), event.getId().substring(0, 8));

        if (event.getKind() != 4) {
            log.error("=== ChatService: Not kind 4, returning ===");
            return; // Not an encrypted DM
        }

        // Check if we already processed this event (duplicate from another relay)
        if (!processedEventIds.add(event.getId())) {
            log.error("=== ChatService: Duplicate event, ignoring ===");
            return;
        }

        // Ignore messages older than 24 hours (likely from old/corrupted identities)
        long eventAge = Instant.now().getEpochSecond() - event.getCreatedAt();
        if (eventAge > 24 * 3600) {
            log.error("=== ChatService: Message too old ({} hours), ignoring ===", eventAge / 3600);
            return;
        }

        log.error("=== ChatService: Processing kind 4 event ===");

        try {
            NostrIdentity identity = identityManager.getActiveIdentity();
            if (identity == null) {
                log.error("=== ChatService: No active identity ===");
                return;
            }

            // Get sender's pubkey
            String senderHex = event.getPubkey();
            log.error("=== ChatService: Sender={} ===", senderHex.substring(0, 8));

            // Get recipient from tags
            String recipientHex = event.getTagValue("p");
            if (recipientHex == null) {
                log.warn("Encrypted DM without recipient tag");
                return;
            }

            log.error("=== ChatService: Recipient={}, MyHex={} ===",
                recipientHex.substring(0, 8), identity.getHex().substring(0, 8));

            // Only process if we're the recipient
            if (!recipientHex.equals(identity.getHex())) {
                log.error("=== ChatService: Not for me, ignoring ===");
                return;
            }

            log.error("=== ChatService: Message is for me, decrypting... ===");

            // Decrypt message
            String nsecHex = identity.getNsec();
            ECKey recipientPrivkey = ECKey.fromPrivate(Utils.hexToBytes(nsecHex));

            // Try to get sender's compressed pubkey from event tag
            String senderCompressedHex = event.getTagValue("pubkey_compressed");

            String plaintext;
            if (senderCompressedHex != null && !senderCompressedHex.isEmpty()) {
                // Use the compressed pubkey from the event tag
                log.error("=== ChatService: Using compressed pubkey from event tag: {} ===", senderCompressedHex.substring(0, 8));
                plaintext = NostrCrypto.decryptWithCompressed(event.getContent(), senderCompressedHex, recipientPrivkey);
            } else {
                // Fallback to old method (reconstruct from x-only)
                log.error("=== ChatService: No compressed pubkey in event, using fallback ===");
                plaintext = NostrCrypto.decrypt(event.getContent(), senderHex, recipientPrivkey);
            }
            log.error("=== ChatService: Decrypted: {} ===", plaintext);

            // Get conversation ID from offer_id tag
            String conversationId = event.getTagValue("offer_id");

            // IGNORE messages without offer_id tag (old messages from previous system)
            if (conversationId == null) {
                log.error("=== ChatService: Message has no offer_id tag, ignoring (old message) ===");
                return;
            }

            log.error("=== ChatService: Conversation ID: {} ===", conversationId.substring(0, 8));

            // Create chat message
            // Convert Nostr event timestamp (UTC epoch seconds) to local timezone
            LocalDateTime timestamp = Instant.ofEpochSecond(event.getCreatedAt())
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();

            ChatMessage message = new ChatMessage(
                senderHex,
                "Anon", // TODO: Fetch name from profile
                identity.getHex(),
                plaintext,
                timestamp,
                event.getId(),
                false // incoming
            );

            // Add to conversation using correct conversationId
            addMessageToConversation(conversationId, message);

            // Notify handlers
            Platform.runLater(() -> {
                for (Consumer<ChatMessage> handler : messageHandlers) {
                    handler.accept(message);
                }
            });

            log.info("Received encrypted message from {}", senderHex.substring(0, 8));

        } catch (Exception e) {
            log.error("Failed to decrypt message", e);
        }
    }

    /**
     * Add message to conversation and persist to disk
     */
    private void addMessageToConversation(String peerHex, ChatMessage message) {
        messageStorage.addMessage(peerHex, message);
    }

    /**
     * Get conversation with a peer (loads from disk)
     */
    public List<ChatMessage> getConversation(String peerHex) {
        return messageStorage.getConversation(peerHex);
    }

    /**
     * Get all conversations (loads from disk)
     */
    public Map<String, List<ChatMessage>> getAllConversations() {
        return messageStorage.getAllConversations();
    }

    /**
     * Get the peer hex pubkey for a conversation
     */
    public String getPeerHexForConversation(String conversationId) {
        NostrIdentity identity = identityManager.getActiveIdentity();
        if (identity == null) {
            return null;
        }
        return messageStorage.getPeerHexForConversation(conversationId, identity.getHex());
    }

    /**
     * Add message handler
     */
    public void addMessageHandler(Consumer<ChatMessage> handler) {
        messageHandlers.add(handler);
    }

    /**
     * Remove message handler
     */
    public void removeMessageHandler(Consumer<ChatMessage> handler) {
        messageHandlers.remove(handler);
    }

    /**
     * Unsubscribe from conversation
     */
    public void unsubscribeFromConversation(String peerHex) {
        String subscriptionId = activeSubscriptions.remove(peerHex);
        if (subscriptionId != null) {
            NostrRelayManager relayManager = p2pService.getRelayManager();
            if (relayManager != null) {
                relayManager.unsubscribe(subscriptionId);
            }
            log.info("Unsubscribed from conversation with {}", peerHex.substring(0, 8));
        }
    }

    /**
     * Clear conversation history
     */
    public void clearConversation(String peerHex) {
        messageStorage.clearConversation(peerHex);
    }

    /**
     * Get relay manager for direct access
     */
    public NostrRelayManager getRelayManager() {
        return p2pService.getRelayManager();
    }
}
