package com.sparrowwallet.sparrow.p2p.chat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sparrowwallet.sparrow.io.Storage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Storage manager for chat messages.
 * Persists encrypted chat messages to disk organized by conversation.
 */
public class ChatMessageStorage {
    private static final Logger log = LoggerFactory.getLogger(ChatMessageStorage.class);
    private static final String MESSAGES_FILENAME = "chat-messages.json";

    private static ChatMessageStorage instance;

    // Messages organized by peer hex pubkey -> list of messages
    private final Map<String, List<ChatMessage>> conversations;

    private ChatMessageStorage() {
        this.conversations = new ConcurrentHashMap<>();
        loadMessagesFromDisk();
    }

    public static synchronized ChatMessageStorage getInstance() {
        if (instance == null) {
            instance = new ChatMessageStorage();
        }
        return instance;
    }

    /**
     * Add a message to a conversation and save to disk.
     * Messages are inserted in chronological order by timestamp.
     */
    public void addMessage(String peerHex, ChatMessage message) {
        List<ChatMessage> messages = conversations.computeIfAbsent(peerHex, k -> new ArrayList<>());

        // Check if message already exists (prevent duplicates)
        boolean exists = messages.stream()
            .anyMatch(m -> m.getNostrEventId() != null &&
                          m.getNostrEventId().equals(message.getNostrEventId()));

        if (exists) {
            log.debug("Message {} already exists in conversation, skipping",
                     message.getNostrEventId().substring(0, 8));
            return;
        }

        // Find correct insertion position based on timestamp
        int insertIndex = 0;
        for (int i = 0; i < messages.size(); i++) {
            if (message.getTimestamp().isBefore(messages.get(i).getTimestamp())) {
                insertIndex = i;
                break;
            }
            insertIndex = i + 1;
        }

        messages.add(insertIndex, message);
        saveMessagesToDisk();

        log.debug("Inserted message at index {} of {} total messages", insertIndex, messages.size());
    }

    /**
     * Get all messages for a conversation involving the active identity, sorted by timestamp.
     * Only returns messages where the sender or recipient is the currently active identity.
     */
    public List<ChatMessage> getConversation(String conversationId) {
        // Get active identity
        com.sparrowwallet.sparrow.p2p.identity.NostrIdentityManager identityManager =
            com.sparrowwallet.sparrow.p2p.identity.NostrIdentityManager.getInstance();
        com.sparrowwallet.sparrow.p2p.identity.NostrIdentity activeIdentity = identityManager.getActiveIdentity();

        if (activeIdentity == null) {
            return new ArrayList<>();
        }

        String activeIdentityHex = activeIdentity.getHex();

        // Filter messages to only include those involving the active identity
        List<ChatMessage> allMessages = conversations.getOrDefault(conversationId, new ArrayList<>());
        List<ChatMessage> filteredMessages = new ArrayList<>();

        for (ChatMessage msg : allMessages) {
            // Include message if active identity is sender OR recipient
            if (activeIdentityHex.equals(msg.getSenderHex()) ||
                activeIdentityHex.equals(msg.getRecipientHex())) {
                filteredMessages.add(msg);
            }
        }

        // Sort by timestamp chronologically
        filteredMessages.sort((m1, m2) -> m1.getTimestamp().compareTo(m2.getTimestamp()));
        return filteredMessages;
    }

    /**
     * Get all conversations
     */
    public Map<String, List<ChatMessage>> getAllConversations() {
        return new HashMap<>(conversations);
    }

    /**
     * Get the peer hex pubkey for a conversation.
     * Extracts from messages - returns the OTHER party (not our identity).
     */
    public String getPeerHexForConversation(String conversationId, String myHex) {
        List<ChatMessage> messages = conversations.get(conversationId);
        if (messages == null || messages.isEmpty()) {
            return null;
        }

        // Get first message
        ChatMessage firstMessage = messages.get(0);

        // If we sent it, peer is the recipient. If we received it, peer is the sender.
        if (firstMessage.getSenderHex().equals(myHex)) {
            return firstMessage.getRecipientHex();
        } else {
            return firstMessage.getSenderHex();
        }
    }

    /**
     * Clear conversation with a specific peer
     */
    public void clearConversation(String peerHex) {
        conversations.remove(peerHex);
        saveMessagesToDisk();
        log.info("Cleared conversation with {}", peerHex.substring(0, 8));
    }

    /**
     * Clear all messages (for testing)
     */
    public void clearAll() {
        conversations.clear();
        saveMessagesToDisk();
        log.info("Cleared all chat messages");
    }

    /**
     * Save messages to disk
     */
    private void saveMessagesToDisk() {
        try {
            File sparrowDir = Storage.getSparrowDir();
            File messagesFile = new File(sparrowDir, MESSAGES_FILENAME);

            if (!messagesFile.exists()) {
                Storage.createOwnerOnlyFile(messagesFile);
            }

            // Create JSON structure: { "conversations": { "peerHex": [messages...] } }
            JsonObject root = new JsonObject();
            JsonObject conversationsJson = new JsonObject();

            for (Map.Entry<String, List<ChatMessage>> entry : conversations.entrySet()) {
                String peerHex = entry.getKey();
                List<ChatMessage> messages = entry.getValue();

                JsonArray messagesArray = new JsonArray();
                for (ChatMessage msg : messages) {
                    JsonObject msgJson = new JsonObject();
                    msgJson.addProperty("senderHex", msg.getSenderHex());
                    msgJson.addProperty("senderName", msg.getSenderName());
                    msgJson.addProperty("recipientHex", msg.getRecipientHex());
                    msgJson.addProperty("content", msg.getContent());
                    msgJson.addProperty("timestamp", msg.getTimestamp().toString());
                    msgJson.addProperty("nostrEventId", msg.getNostrEventId());
                    msgJson.addProperty("isRead", msg.isRead());
                    msgJson.addProperty("outgoing", msg.isOutgoing());

                    messagesArray.add(msgJson);
                }

                conversationsJson.add(peerHex, messagesArray);
            }

            root.add("conversations", conversationsJson);

            // Write to file
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            try (Writer writer = new FileWriter(messagesFile)) {
                gson.toJson(root, writer);
                writer.flush();
            }

            log.debug("Saved {} conversations to disk", conversations.size());

        } catch (IOException e) {
            log.error("Failed to save messages to disk", e);
        }
    }

    /**
     * Load messages from disk
     */
    private void loadMessagesFromDisk() {
        try {
            File sparrowDir = Storage.getSparrowDir();
            File messagesFile = new File(sparrowDir, MESSAGES_FILENAME);

            if (!messagesFile.exists()) {
                log.debug("No messages file found, starting fresh");
                return;
            }

            // Read file
            try (Reader reader = new FileReader(messagesFile)) {
                Gson gson = new Gson();
                JsonObject root = gson.fromJson(reader, JsonObject.class);

                if (root == null || !root.has("conversations")) {
                    log.warn("Empty or invalid messages file");
                    return;
                }

                JsonObject conversationsJson = root.getAsJsonObject("conversations");

                // Load each conversation
                for (Map.Entry<String, JsonElement> entry : conversationsJson.entrySet()) {
                    String peerHex = entry.getKey();
                    JsonArray messagesArray = entry.getValue().getAsJsonArray();

                    List<ChatMessage> messages = new ArrayList<>();
                    for (JsonElement element : messagesArray) {
                        JsonObject msgJson = element.getAsJsonObject();

                        try {
                            String senderHex = msgJson.get("senderHex").getAsString();
                            String senderName = msgJson.get("senderName").getAsString();
                            String recipientHex = msgJson.get("recipientHex").getAsString();
                            String content = msgJson.get("content").getAsString();
                            LocalDateTime timestamp = LocalDateTime.parse(msgJson.get("timestamp").getAsString());
                            String nostrEventId = msgJson.has("nostrEventId") ? msgJson.get("nostrEventId").getAsString() : null;
                            boolean outgoing = msgJson.has("outgoing") ? msgJson.get("outgoing").getAsBoolean() : false;

                            ChatMessage message = new ChatMessage(
                                senderHex,
                                senderName,
                                recipientHex,
                                content,
                                timestamp,
                                nostrEventId,
                                outgoing
                            );

                            messages.add(message);

                        } catch (Exception e) {
                            log.error("Failed to parse message in conversation {}", peerHex, e);
                        }
                    }

                    // Sort messages chronologically after loading
                    messages.sort((m1, m2) -> m1.getTimestamp().compareTo(m2.getTimestamp()));

                    conversations.put(peerHex, messages);
                }

                log.info("Loaded {} conversations from disk", conversations.size());

            } catch (Exception e) {
                log.error("Failed to read messages file", e);
            }

        } catch (Exception e) {
            log.error("Failed to load messages from disk", e);
        }
    }
}
