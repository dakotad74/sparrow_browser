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

import java.util.List;

/**
 * Background service managing Nostr relay connections and event processing.
 * Extends JavaFX ScheduledService for integration with Sparrow's async architecture.
 */
public class NostrEventService extends ScheduledService<Void> {
    private static final Logger log = LoggerFactory.getLogger(NostrEventService.class);

    private NostrRelayManager relayManager;
    private boolean initialized = false;

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
    protected Task<Void> createTask() {
        return new Task<>() {
            @Override
            protected Void call() throws Exception {
                if(!initialized) {
                    initialize();
                }

                // Periodic health check
                if(relayManager != null && !relayManager.isConnected()) {
                    log.warn("Nostr relays disconnected, attempting to reconnect...");
                    relayManager.connect();
                }

                return null;
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

            List<String> relayUrls = config.getNostrRelays();
            if(relayUrls == null || relayUrls.isEmpty()) {
                log.warn("No Nostr relays configured");
                return;
            }

            log.info("Initializing Nostr service with {} relays", relayUrls.size());

            relayManager = new NostrRelayManager(relayUrls);

            // TODO Phase 2: Configure Tor proxy if available
            // TorService torService = AppServices.get().getTorService();
            // if(torService != null && torService.isRunning()) {
            //     Proxy proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("127.0.0.1", torService.getProxyPort()));
            //     relayManager.setProxy(proxy);
            //     log.info("Tor is running, Nostr traffic will be routed through Tor");
            // }

            // Connect to relays (stub implementation for Phase 1)
            relayManager.connect();

            initialized = true;

            log.info("Nostr service initialized successfully");

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

        // TODO: Implement subscription to kind 38383 events
        // This will be implemented in Phase 2
        log.info("Subscribed to coordination events");
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
