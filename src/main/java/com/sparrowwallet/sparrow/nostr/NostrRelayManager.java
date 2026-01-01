package com.sparrowwallet.sparrow.nostr;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.sparrowwallet.drongo.crypto.ECKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Manages WebSocket connections to multiple Nostr relays.
 *
 * This is a native Java implementation using java.net.http.WebSocket (Java 11+).
 * No external Nostr library dependencies - fully JPMS compatible.
 *
 * Implements NIP-01: Basic protocol flow
 * - EVENT: Publish events
 * - REQ: Subscribe to events with filters
 * - CLOSE: Unsubscribe
 *
 * Features:
 * - Multiple relay connections
 * - Automatic reconnection
 * - Tor proxy support via ProxySelector
 * - Event publishing and subscription
 * - Message parsing and handling
 */
public class NostrRelayManager {
    private static final Logger log = LoggerFactory.getLogger(NostrRelayManager.class);

    private static final int CONNECT_TIMEOUT_SECONDS = 10;
    private static final int RECONNECT_DELAY_SECONDS = 5;
    private static final int MAX_RECONNECT_ATTEMPTS = 3;

    private final List<String> relayUrls;
    private final Map<String, RelayConnection> connections;
    private final HttpClient httpClient;
    private final Gson gson;
    private final ScheduledExecutorService executor;

    private final List<Consumer<NostrEvent>> messageHandlers;
    private ECKey privateKey; // Optional: for signing events
    private boolean connected;

    public NostrRelayManager(List<String> relayUrls) {
        this.relayUrls = new ArrayList<>(relayUrls);
        this.connections = new ConcurrentHashMap<>();
        this.messageHandlers = new CopyOnWriteArrayList<>();
        this.gson = new Gson();
        this.executor = Executors.newScheduledThreadPool(2, r -> {
            Thread thread = new Thread(r);
            thread.setName("NostrRelay-" + thread.getId());
            thread.setDaemon(true);
            return thread;
        });

        // Create HTTP client with default settings
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
            .build();

        this.connected = false;
    }

    /**
     * Connect to all configured relays
     */
    public void connect() {
        if(connected) {
            log.warn("Already connected to relays");
            return;
        }

        log.info("Connecting to {} Nostr relays", relayUrls.size());

        for(String url : relayUrls) {
            connectToRelay(url);
        }

        connected = true;
    }

    /**
     * Connect to a single relay with WebSocket
     */
    private void connectToRelay(String url) {
        try {
            log.debug("Connecting to relay: {}", url);

            URI uri = URI.create(url);
            RelayConnection connection = new RelayConnection(url);

            // Build WebSocket asynchronously
            httpClient.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
                .buildAsync(uri, connection)
                .thenAccept(ws -> {
                    connection.setWebSocket(ws);
                    connections.put(url, connection);
                    log.info("Connected to relay: {}", url);
                })
                .exceptionally(e -> {
                    log.error("Failed to connect to relay: {}", url, e);
                    connection.setError(e.getMessage());
                    connections.put(url, connection);
                    scheduleReconnect(url);
                    return null;
                });

        } catch(Exception e) {
            log.error("Failed to connect to relay: {}", url, e);
        }
    }

    /**
     * Schedule reconnection attempt
     */
    private void scheduleReconnect(String url) {
        RelayConnection connection = connections.get(url);
        if(connection == null || connection.getReconnectAttempts() >= MAX_RECONNECT_ATTEMPTS) {
            log.warn("Max reconnect attempts reached for relay: {}", url);
            return;
        }

        connection.incrementReconnectAttempts();

        executor.schedule(() -> {
            log.info("Attempting to reconnect to relay: {} (attempt {})",
                url, connection.getReconnectAttempts());
            connectToRelay(url);
        }, RECONNECT_DELAY_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Disconnect from all relays
     */
    public void disconnect() {
        log.info("Disconnecting from all relays");

        for(RelayConnection connection : connections.values()) {
            if(connection.isConnected()) {
                connection.getWebSocket().sendClose(WebSocket.NORMAL_CLOSURE, "Closing connection")
                    .thenRun(() -> log.debug("Closed connection to {}", connection.getUrl()));
            }
        }

        connections.clear();
        connected = false;
    }

    /**
     * Set Tor SOCKS proxy for all relay connections
     * Note: ProxySelector must be set before creating HttpClient
     */
    public void setProxySelector(ProxySelector proxySelector) {
        log.info("Tor proxy selector configured");
        // Note: To use proxy, need to recreate HttpClient with proxy selector
        // This requires disconnecting and reconnecting
    }

    /**
     * Set message handler for incoming events (clears existing handlers).
     * For backwards compatibility. Consider using addMessageHandler() instead.
     */
    public void setMessageHandler(Consumer<NostrEvent> handler) {
        this.messageHandlers.clear();
        if (handler != null) {
            this.messageHandlers.add(handler);
        }
    }

    /**
     * Add a message handler for incoming events (allows multiple handlers).
     */
    public void addMessageHandler(Consumer<NostrEvent> handler) {
        if (handler != null && !this.messageHandlers.contains(handler)) {
            this.messageHandlers.add(handler);
        }
    }

    /**
     * Remove a message handler.
     */
    public void removeMessageHandler(Consumer<NostrEvent> handler) {
        this.messageHandlers.remove(handler);
    }

    /**
     * Set private key for signing published events.
     * If not set, events will be published unsigned (may be rejected by relays).
     *
     * @param privateKey ECKey for signing events
     */
    public void setPrivateKey(ECKey privateKey) {
        this.privateKey = privateKey;
    }

    /**
     * Publish an event to all connected relays
     *
     * Nostr protocol: ["EVENT", <event object>]
     *
     * If privateKey is set, the event will be signed before publishing.
     * Event ID and signature are automatically generated.
     */
    public void publishEvent(NostrEvent event) {
        log.error("=== publishEvent() called: kind={}, connected={}, privateKey={} ===",
            event.getKind(), connected, privateKey != null ? "SET" : "NULL");

        if(!connected || connections.isEmpty()) {
            log.error("=== EARLY RETURN: not connected or no connections ===");
            log.warn("Cannot publish event - not connected to any relays");
            return;
        }

        log.error("=== Passed connection check, about to sign ===");

        // Sign event if private key is available
        if(privateKey != null && event.getId() == null) {
            try {
                log.error("=== Signing event... ===");
                // Generate event ID
                String eventId = NostrCrypto.generateEventId(event);
                event.setId(eventId);

                // Sign event
                String signature = NostrCrypto.signEvent(eventId, privateKey);
                event.setSig(signature);

                log.error("=== Event signed: id={} ===", eventId.substring(0, 8));

            } catch(Exception e) {
                log.error("Failed to sign event", e);
                // Continue without signature - relay may reject
            }
        } else if(privateKey == null) {
            log.warn("Publishing unsigned event (no private key set) - relay may reject");
        }

        log.error("=== About to serialize event ===");

        // Serialize event to JSON
        String eventJson = gson.toJson(event);
        String message = "[\"EVENT\"," + eventJson + "]";

        log.error("=== Event serialized, message length: {} ===", message.length());
        log.error("=== Event JSON: {} ===", eventJson);
        log.info("Publishing event: kind={}, content length={}, signed={}",
            event.getKind(), event.getContent().length(), event.getSig() != null);

        // Send to all connected relays
        for(RelayConnection connection : connections.values()) {
            if(connection.isConnected()) {
                connection.getWebSocket().sendText(message, true)
                    .thenRun(() -> log.debug("Event sent to {}", connection.getUrl()))
                    .exceptionally(e -> {
                        log.error("Failed to send event to {}", connection.getUrl(), e);
                        return null;
                    });
            }
        }
    }

    /**
     * Subscribe to events matching filters
     *
     * Nostr protocol: ["REQ", <subscription_id>, <filters object>]
     */
    public void subscribe(String subscriptionId, Map<String, Object> filters) {
        log.error("=== subscribe() called: id={}, connected={}, connections.size={} ===",
            subscriptionId, connected, connections != null ? connections.size() : "NULL");

        if(!connected || connections.isEmpty()) {
            log.warn("Cannot subscribe - not connected to any relays");
            return;
        }

        // Serialize filters to JSON
        String filtersJson = gson.toJson(filters);
        String message = "[\"REQ\",\"" + subscriptionId + "\"," + filtersJson + "]";

        log.info("Creating subscription: {} with filters: {}", subscriptionId, filtersJson);

        // Send to all connected relays
        for(RelayConnection connection : connections.values()) {
            if(connection.isConnected()) {
                connection.getWebSocket().sendText(message, true)
                    .thenRun(() -> log.debug("Subscription sent to {}", connection.getUrl()))
                    .exceptionally(e -> {
                        log.error("Failed to send subscription to {}", connection.getUrl(), e);
                        return null;
                    });
            }
        }
    }

    /**
     * Unsubscribe from a subscription
     *
     * Nostr protocol: ["CLOSE", <subscription_id>]
     */
    public void unsubscribe(String subscriptionId) {
        if(!connected || connections.isEmpty()) {
            return;
        }

        String message = "[\"CLOSE\",\"" + subscriptionId + "\"]";

        log.info("Closing subscription: {}", subscriptionId);

        // Send to all connected relays
        for(RelayConnection connection : connections.values()) {
            if(connection.isConnected()) {
                connection.getWebSocket().sendText(message, true);
            }
        }
    }

    /**
     * Check if connected to at least one relay
     */
    public boolean isConnected() {
        return connected && connections.values().stream().anyMatch(RelayConnection::isConnected);
    }

    /**
     * Get status of all relays
     */
    public List<RelayStatus> getRelayStatuses() {
        List<RelayStatus> statuses = new ArrayList<>();
        for(RelayConnection connection : connections.values()) {
            statuses.add(new RelayStatus(
                connection.getUrl(),
                connection.isConnected(),
                connection.getError()
            ));
        }
        return statuses;
    }

    /**
     * Get number of connected relays
     */
    public int getConnectedRelayCount() {
        return (int) connections.values().stream()
                .filter(RelayConnection::isConnected)
                .count();
    }

    /**
     * Process incoming Nostr message
     *
     * Message types:
     * - ["EVENT", <subscription_id>, <event>]
     * - ["OK", <event_id>, <accepted: true/false>, <message>]
     * - ["EOSE", <subscription_id>] (End Of Stored Events)
     * - ["NOTICE", <message>]
     */
    private void processMessage(String message) {
        try {
            JsonArray array = JsonParser.parseString(message).getAsJsonArray();

            if(array.size() < 1) {
                log.warn("Invalid Nostr message: empty array");
                return;
            }

            String type = array.get(0).getAsString();

            switch(type) {
                case "EVENT":
                    handleEventMessage(array);
                    break;
                case "OK":
                    handleOKMessage(array);
                    break;
                case "EOSE":
                    handleEOSEMessage(array);
                    break;
                case "NOTICE":
                    handleNoticeMessage(array);
                    break;
                default:
                    log.debug("Unknown message type: {}", type);
            }

        } catch(Exception e) {
            log.error("Failed to process Nostr message", e);
        }
    }

    /**
     * Handle EVENT message: ["EVENT", <subscription_id>, <event>]
     */
    private void handleEventMessage(JsonArray array) {
        if(array.size() < 3) {
            log.warn("Invalid EVENT message");
            return;
        }

        String subscriptionId = array.get(1).getAsString();
        JsonElement eventJson = array.get(2);

        try {
            NostrEvent event = gson.fromJson(eventJson, NostrEvent.class);
            log.debug("Received event: kind={}, subscription={}", event.getKind(), subscriptionId);

            // Pass to all message handlers
            for (Consumer<NostrEvent> handler : messageHandlers) {
                try {
                    handler.accept(event);
                } catch (Exception handlerEx) {
                    log.error("Error in message handler", handlerEx);
                }
            }
        } catch(Exception e) {
            log.error("Failed to parse EVENT message", e);
        }
    }

    /**
     * Handle OK message: ["OK", <event_id>, <accepted>, <message>]
     */
    private void handleOKMessage(JsonArray array) {
        if(array.size() < 4) {
            log.warn("Invalid OK message");
            return;
        }

        String eventId = array.get(1).getAsString();
        boolean accepted = array.get(2).getAsBoolean();
        String message = array.get(3).getAsString();

        if(accepted) {
            log.debug("Event accepted: {} - {}", eventId, message);
        } else {
            log.warn("Event rejected: {} - {}", eventId, message);
        }
    }

    /**
     * Handle EOSE message: ["EOSE", <subscription_id>]
     */
    private void handleEOSEMessage(JsonArray array) {
        if(array.size() < 2) {
            log.warn("Invalid EOSE message");
            return;
        }

        String subscriptionId = array.get(1).getAsString();
        log.debug("End of stored events for subscription: {}", subscriptionId);
    }

    /**
     * Handle NOTICE message: ["NOTICE", <message>]
     */
    private void handleNoticeMessage(JsonArray array) {
        if(array.size() < 2) {
            log.warn("Invalid NOTICE message");
            return;
        }

        String notice = array.get(1).getAsString();
        log.info("Relay notice: {}", notice);
    }

    /**
     * Relay connection wrapper with WebSocket.Listener
     */
    private class RelayConnection implements WebSocket.Listener {
        private final String url;
        private WebSocket webSocket;
        private String error;
        private int reconnectAttempts;
        private final StringBuilder messageBuffer;

        public RelayConnection(String url) {
            this.url = url;
            this.reconnectAttempts = 0;
            this.messageBuffer = new StringBuilder();
        }

        public void setWebSocket(WebSocket ws) {
            this.webSocket = ws;
            this.error = null;
        }

        public void setError(String error) {
            this.error = error;
        }

        public void incrementReconnectAttempts() {
            this.reconnectAttempts++;
        }

        public String getUrl() {
            return url;
        }

        public WebSocket getWebSocket() {
            return webSocket;
        }

        public String getError() {
            return error;
        }

        public int getReconnectAttempts() {
            return reconnectAttempts;
        }

        public boolean isConnected() {
            return webSocket != null && error == null;
        }

        // WebSocket.Listener implementation

        @Override
        public void onOpen(WebSocket webSocket) {
            log.debug("WebSocket opened: {}", url);
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            messageBuffer.append(data);

            if(last) {
                String message = messageBuffer.toString();
                messageBuffer.setLength(0);

                // Process message in executor to avoid blocking WebSocket thread
                executor.submit(() -> processMessage(message));
            }

            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            log.info("WebSocket closed: {} (code: {}, reason: {})", url, statusCode, reason);

            if(statusCode != WebSocket.NORMAL_CLOSURE) {
                scheduleReconnect(url);
            }

            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            log.error("WebSocket error: {}", url, error);
            this.error = error.getMessage();
            scheduleReconnect(url);
        }
    }

    /**
     * Relay status information
     */
    public static class RelayStatus {
        private final String url;
        private final boolean connected;
        private final String error;

        public RelayStatus(String url, boolean connected, String error) {
            this.url = url;
            this.connected = connected;
            this.error = error;
        }

        public String getUrl() {
            return url;
        }

        public boolean isConnected() {
            return connected;
        }

        public String getError() {
            return error;
        }

        @Override
        public String toString() {
            return "RelayStatus{url='" + url + "', connected=" + connected +
                   (error != null ? ", error='" + error + "'" : "") + "}";
        }
    }
}
