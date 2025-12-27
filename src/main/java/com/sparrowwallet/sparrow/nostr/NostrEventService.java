package com.sparrowwallet.sparrow.nostr;

import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.NostrConnectedEvent;
import com.sparrowwallet.sparrow.event.NostrDisconnectedEvent;
import com.sparrowwallet.sparrow.event.NostrMessageReceivedEvent;
import com.sparrowwallet.sparrow.io.Config;
import javafx.concurrent.ScheduledService;
import javafx.concurrent.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Background service managing Nostr relay connections and event processing.
 * Extends JavaFX ScheduledService for integration with Sparrow's async architecture.
 */
public class NostrEventService extends ScheduledService<Boolean> {
    private static final Logger log = LoggerFactory.getLogger(NostrEventService.class);

    private NostrRelayManager relayManager;
    private boolean initialized = false;
    private boolean subscribed = false;

    public NostrEventService() {
        setOnSucceeded(event -> {
            if(relayManager != null && relayManager.isConnected()) {
                EventManager.get().post(new NostrConnectedEvent(relayManager.getConnectedRelayCount()));
            }
        });

        setOnFailed(event -> {
            Throwable exception = getException();
            log.error("Nostr service failed", exception);
            EventManager.get().post(new NostrDisconnectedEvent(exception.getMessage()));
        });
    }

    @Override
    protected Task<Boolean> createTask() {
        return new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                if(!initialized) {
                    initialize();
                }

                // Subscribe to coordination events once connected
                if(initialized && !subscribed && relayManager != null) {
                    int connectedCount = relayManager.getConnectedRelayCount();

                    if(connectedCount > 0) {
                        log.warn("Relays connected ({}), subscribing to coordination events...", connectedCount);
                        subscribeToCoordinationEvents();
                        subscribed = true;
                    }
                }

                // Periodic health check - only reconnect if we had a connection before
                if(initialized && relayManager != null && subscribed) {
                    int connectedCount = relayManager.getConnectedRelayCount();
                    if(connectedCount == 0) {
                        log.warn("All relays disconnected, attempting to reconnect...");
                        relayManager.connect();
                        subscribed = false; // Reset subscription flag to resubscribe after reconnect
                    }
                }

                return true; // Always return success to allow periodic re-execution
            }
        };
    }

    /**
     * Initialize Nostr relay connections
     */
    private void initialize() {
        try {
            Config config = Config.get();

            if(!config.isNostrEnabled()) {
                log.info("Nostr coordination is disabled in configuration");
                return;
            }

            // TEMPORARY FIX: Override config relays with working ones
            // TODO: Remove this override once config persistence is fixed
            List<String> relayUrls = List.of(
                "wss://relay.damus.io",
                "wss://nos.lol",
                "wss://relay.snort.social"
            );

            log.warn("Using hardcoded relay list (temporary fix for testing)");
            log.info("Initializing Nostr service with {} relays", relayUrls.size());

            relayManager = new NostrRelayManager(relayUrls);

            // TODO Phase 2: Configure Tor proxy if available
            // TorService torService = AppServices.get().getTorService();
            // if(torService != null && torService.isRunning()) {
            //     Proxy proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("127.0.0.1", torService.getProxyPort()));
            //     relayManager.setProxy(proxy);
            //     log.info("Tor is running, Nostr traffic will be routed through Tor");
            // }

            // Connect to relays
            relayManager.connect();

            initialized = true;

            log.info("Nostr service initialized successfully");
            log.info("Will subscribe to coordination events once connected");

        } catch(Exception e) {
            log.error("Failed to initialize Nostr service", e);
            throw new RuntimeException("Nostr initialization failed", e);
        }
    }

    /**
     * Get the relay manager instance
     */
    public NostrRelayManager getRelayManager() {
        return relayManager;
    }

    /**
     * Subscribe to coordination events (kind: 38383)
     */
    public void subscribeToCoordinationEvents() {
        if(relayManager == null || !relayManager.isConnected()) {
            log.warn("Cannot subscribe: relay manager not initialized or connected");
            return;
        }

        // Create filter for coordination events (kind: 38383)
        Map<String, Object> filter = new HashMap<>();
        filter.put("kinds", List.of(NostrEvent.KIND_COORDINATION));

        // Subscribe since now (don't fetch old events)
        filter.put("since", System.currentTimeMillis() / 1000);

        // Generate unique subscription ID
        String subscriptionId = "coordination-" + System.currentTimeMillis();

        log.info("Subscribing to coordination events (kind: {})", NostrEvent.KIND_COORDINATION);
        relayManager.subscribe(subscriptionId, filter);
    }

    /**
     * Shutdown the service and disconnect from all relays
     */
    public void shutdown() {
        log.info("Shutting down Nostr service");

        if(relayManager != null) {
            relayManager.disconnect();
        }

        cancel();
    }
}
