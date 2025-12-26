package com.sparrowwallet.sparrow.nostr;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Basic tests for NostrEventService stub implementation
 */
public class NostrEventServiceTest {

    @Test
    public void testServiceCanBeInstantiated() {
        NostrEventService service = new NostrEventService();
        assertNotNull(service, "NostrEventService should be instantiable");
    }

    @Test
    public void testRelayManagerCanBeInstantiated() {
        java.util.List<String> relays = java.util.List.of(
            "wss://relay.damus.io",
            "wss://nostr.wine"
        );
        NostrRelayManager manager = new NostrRelayManager(relays);
        assertNotNull(manager, "NostrRelayManager should be instantiable");
    }

    @Test
    public void testRelayManagerConnection() throws InterruptedException {
        java.util.List<String> relays = java.util.List.of("wss://relay.damus.io");
        NostrRelayManager manager = new NostrRelayManager(relays);

        manager.connect();

        // Wait for stub connection to complete (runs in background thread)
        Thread.sleep(100);

        assertTrue(manager.isConnected(), "Manager should report as connected (stub)");
        assertEquals(1, manager.getConnectedRelayCount(), "Should have 1 connected relay (stub)");

        manager.disconnect();
        assertFalse(manager.isConnected(), "Manager should report as disconnected");
    }
}
