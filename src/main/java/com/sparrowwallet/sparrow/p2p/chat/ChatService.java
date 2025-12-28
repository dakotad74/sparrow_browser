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

    // Conversations organized by peer hex pubkey
    private final Map<String, List<ChatMessage>> conversations;

    // Active subscriptions by peer hex
    private final Map<String, String> activeSubscriptions;

    // Message handlers
    private final List<Consumer<ChatMessage>> messageHandlers;

    private final NostrIdentityManager identityManager;
    private final NostrP2PService p2pService;

    private ChatService() {
        this.conversations = new ConcurrentHashMap<>();
        this.activeSubscriptions = new ConcurrentHashMap<>();
        this.messageHandlers = new ArrayList<>();
        this.identityManager = NostrIdentityManager.getInstance();
        this.p2pService = NostrP2PService.getInstance();
    }

    public static synchronized ChatService getInstance() {
        if (instance == null) {
            instance = new ChatService();
        }
        return instance;
    }

    /**
     * Send an encrypted message to a peer
     */
    public void sendMessage(String recipientHex, String plaintext) {
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
            log.error("=== CHAT: senderPrivkey created successfully ===");

            // Encrypt message with NIP-04
            log.error("=== CHAT: Attempting to encrypt message for recipient: {} ===", recipientHex.substring(0, 8));
            String encryptedContent = NostrCrypto.encrypt(plaintext, recipientHex, senderPrivkey);
            log.error("=== CHAT: Message encrypted successfully ===");

            // Create Nostr event (kind 4 = encrypted DM)
            NostrEvent event = new NostrEvent(identity.getHex(), 4, encryptedContent);
            event.addTag("p", recipientHex); // Tag recipient

            // Set private key for signing
            relayManager.setPrivateKey(senderPrivkey);

            // Publish event
            relayManager.publishEvent(event);

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

            addMessageToConversation(recipientHex, message);

            log.info("Sent encrypted message to {}", recipientHex.substring(0, 8));

        } catch (Exception e) {
            log.error("Failed to send message", e);
            throw new RuntimeException("Failed to send message: " + e.getMessage(), e);
        }
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
        relayManager.setMessageHandler(this::handleIncomingEvent);

        relayManager.subscribe(subscriptionId, filter);
        activeSubscriptions.put(peerHex, subscriptionId);

        log.info("Subscribed to conversation with {}", peerHex.substring(0, 8));
    }

    /**
     * Handle incoming Nostr event
     */
    private void handleIncomingEvent(NostrEvent event) {
        if (event.getKind() != 4) {
            return; // Not an encrypted DM
        }

        try {
            NostrIdentity identity = identityManager.getActiveIdentity();
            if (identity == null) {
                return;
            }

            // Get sender's pubkey
            String senderHex = event.getPubkey();

            // Get recipient from tags
            String recipientHex = event.getTagValue("p");
            if (recipientHex == null) {
                log.warn("Encrypted DM without recipient tag");
                return;
            }

            // Only process if we're the recipient
            if (!recipientHex.equals(identity.getHex())) {
                return;
            }

            // Decrypt message
            String nsecHex = identity.getNsec();
            ECKey recipientPrivkey = ECKey.fromPrivate(Utils.hexToBytes(nsecHex));

            String plaintext = NostrCrypto.decrypt(event.getContent(), senderHex, recipientPrivkey);

            // Create chat message
            ChatMessage message = new ChatMessage(
                senderHex,
                "Anon", // TODO: Fetch name from profile
                identity.getHex(),
                plaintext,
                LocalDateTime.ofEpochSecond(event.getCreatedAt(), 0, ZoneOffset.UTC),
                event.getId(),
                false // incoming
            );

            // Add to conversation
            addMessageToConversation(senderHex, message);

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
     * Add message to conversation
     */
    private void addMessageToConversation(String peerHex, ChatMessage message) {
        conversations.computeIfAbsent(peerHex, k -> new ArrayList<>()).add(message);
    }

    /**
     * Get conversation with a peer
     */
    public List<ChatMessage> getConversation(String peerHex) {
        return conversations.getOrDefault(peerHex, new ArrayList<>());
    }

    /**
     * Get all conversations
     */
    public Map<String, List<ChatMessage>> getAllConversations() {
        return new HashMap<>(conversations);
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
        conversations.remove(peerHex);
        log.info("Cleared conversation with {}", peerHex.substring(0, 8));
    }

    /**
     * Get relay manager for direct access
     */
    public NostrRelayManager getRelayManager() {
        return p2pService.getRelayManager();
    }
}
