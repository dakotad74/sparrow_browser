package com.sparrowwallet.sparrow.coordination;

import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.*;
import com.sparrowwallet.sparrow.nostr.NostrEvent;
import com.sparrowwallet.sparrow.nostr.NostrRelayManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for full coordination workflow.
 * Simulates two participants coordinating a transaction via Nostr events.
 */
public class CoordinationIntegrationTest {

    private CoordinationSessionManager manager1;
    private CoordinationSessionManager manager2;
    private TestEventCollector eventCollector;

    private static final String PARTICIPANT1_PUBKEY = "participant1-pubkey-123456";
    private static final String PARTICIPANT2_PUBKEY = "participant2-pubkey-789012";

    @BeforeEach
    public void setUp() {
        // Configure global network to TESTNET for address parsing
        Network.set(Network.TESTNET);

        // Create two coordination managers (simulating two users)
        manager1 = new CoordinationSessionManager();
        manager2 = new CoordinationSessionManager();

        // Set participant public keys
        manager1.setMyNostrPubkey(PARTICIPANT1_PUBKEY);
        manager2.setMyNostrPubkey(PARTICIPANT2_PUBKEY);

        // Create a mock relay manager that relays messages between the two managers
        MockNostrRelay mockRelay = new MockNostrRelay(manager1, manager2);
        manager1.setNostrRelayManager(mockRelay);
        manager2.setNostrRelayManager(mockRelay);

        // Event collector to verify events are fired
        eventCollector = new TestEventCollector();
        EventManager.get().register(eventCollector);
    }

    @AfterEach
    public void tearDown() {
        EventManager.get().unregister(eventCollector);
        manager1.shutdown();
        manager2.shutdown();
    }

    @Test
    public void testFullCoordinationWorkflow() throws Exception {
        // Step 1: Participant 1 creates a session
        Wallet wallet1 = createTestWallet("Wallet1");
        CoordinationSession session1 = manager1.createSession(wallet1, 2);
        String sessionId = session1.getSessionId();

        assertNotNull(sessionId);
        assertEquals(SessionState.CREATED, session1.getState());
        assertEquals(2, session1.getExpectedParticipants());

        // Give time for event propagation
        Thread.sleep(150);

        // Step 2: Participant 2 joins the session (creator is already auto-joined)
        Wallet wallet2 = createTestWallet("Wallet2");

        // Note: Participant 1 (creator) is already in the session from createSession()
        manager2.joinSession(sessionId, wallet2, PARTICIPANT2_PUBKEY);

        // Give more time for Nostr event propagation between managers
        Thread.sleep(300);

        // Get the session from manager2 (it's created/updated during joinSession)
        CoordinationSession session2 = manager2.getSession(sessionId);
        assertNotNull(session2, "Participant 2 should have the session");
        assertEquals(session1.getNetwork(), session2.getNetwork());

        // Verify both sessions have both participants
        assertEquals(2, session1.getParticipants().size());
        assertEquals(2, session2.getParticipants().size());
        assertTrue(session1.allParticipantsJoined());
        assertTrue(session2.allParticipantsJoined());

        System.out.println("=== session1 participants: " + session1.getParticipants().size() + ", joined: " + session1.allParticipantsJoined() + " ===");
        System.out.println("=== session2 participants: " + session2.getParticipants().size() + ", joined: " + session2.allParticipantsJoined() + " ===");

        // Step 3: Participant 1 proposes an output
        Address address1 = Address.fromString("tb1q9pvjqz5u5sdgpatg3wn0ce438u5cyv85lly0pc");
        manager1.proposeOutput(sessionId, address1, 50000, "Payment to Alice", PARTICIPANT1_PUBKEY);

        Thread.sleep(100);

        // Verify both sessions have the output
        assertEquals(1, session1.getOutputs().size());
        assertEquals(1, session2.getOutputs().size());
        assertEquals(50000, session1.getTotalOutputAmount());
        assertEquals(50000, session2.getTotalOutputAmount());

        // Step 4: Participant 2 also proposes an output
        Address address2 = Address.fromString("tb1qw508d6qejxtdg4y5r3zarvary0c5xw7kxpjzsx");
        manager2.proposeOutput(sessionId, address2, 30000, "Payment to Bob", PARTICIPANT2_PUBKEY);

        Thread.sleep(100);

        // Verify both sessions have both outputs
        assertEquals(2, session1.getOutputs().size());
        assertEquals(2, session2.getOutputs().size());
        assertEquals(80000, session1.getTotalOutputAmount());
        assertEquals(80000, session2.getTotalOutputAmount());

        // Step 5: Both participants propose fees
        manager1.proposeFee(sessionId, 10.0, PARTICIPANT1_PUBKEY);
        manager2.proposeFee(sessionId, 12.5, PARTICIPANT2_PUBKEY);

        Thread.sleep(100);

        // Verify fee agreement (should be highest: 12.5)
        assertTrue(session1.allParticipantsProposedFees());
        assertTrue(session2.allParticipantsProposedFees());
        assertEquals(12.5, session1.getAgreedFeeRate());
        assertEquals(12.5, session2.getAgreedFeeRate());

        // Step 6: Verify session is ready to finalize
        assertTrue(session1.isReadyToFinalize());
        assertTrue(session2.isReadyToFinalize());

        // Step 7: Finalize session
        manager1.finalizeSession(sessionId);

        Thread.sleep(100);

        // Verify both sessions are finalized
        assertEquals(SessionState.FINALIZED, session1.getState());
        assertEquals(SessionState.FINALIZED, session2.getState());

        // Verify events were fired
        assertTrue(eventCollector.receivedSessionCreated);
        assertTrue(eventCollector.receivedParticipantJoined);
        assertTrue(eventCollector.receivedStateChanged);
    }

    @Test
    public void testDuplicateOutputRejection() throws Exception {
        Wallet wallet1 = createTestWallet("Wallet1");
        Wallet wallet2 = createTestWallet("Wallet2");
        CoordinationSession session = manager1.createSession(wallet1, 2);
        String sessionId = session.getSessionId();

        Thread.sleep(50);

        // Note: Participant 1 (creator) is already in the session from createSession()
        manager2.joinSession(sessionId, wallet2, PARTICIPANT2_PUBKEY);

        Thread.sleep(50);

        Address address = Address.fromString("tb1q9pvjqz5u5sdgpatg3wn0ce438u5cyv85lly0pc");

        // First output should succeed
        manager1.proposeOutput(sessionId, address, 50000, "Payment 1", PARTICIPANT1_PUBKEY);
        Thread.sleep(50);
        assertEquals(1, session.getOutputs().size());

        // Duplicate address should not add another output (even from different participant)
        manager2.proposeOutput(sessionId, address, 60000, "Payment 2", PARTICIPANT2_PUBKEY);
        Thread.sleep(50);
        assertEquals(1, session.getOutputs().size(), "Duplicate address should be rejected");
    }

    @Test
    public void testSessionExpiration() throws Exception {
        Wallet wallet = createTestWallet("Wallet1");
        CoordinationSession session = manager1.createSession(wallet, 2);

        assertFalse(session.isExpired());

        // Note: Full expiration test would require waiting 1+ hours
        // This is a minimal smoke test
    }

    // Helper Methods

    private Wallet createTestWallet(String name) {
        Wallet wallet = new Wallet(name);
        wallet.setNetwork(Network.TESTNET);
        return wallet;
    }

    /**
     * Mock Nostr relay that immediately delivers messages between two managers
     */
    private static class MockNostrRelay extends NostrRelayManager {
        private final CoordinationSessionManager manager1;
        private final CoordinationSessionManager manager2;

        public MockNostrRelay(CoordinationSessionManager manager1, CoordinationSessionManager manager2) {
            super(List.of("mock://relay"));
            this.manager1 = manager1;
            this.manager2 = manager2;
            // Simulate connected state
            connect();
        }

        @Override
        public void publishEvent(NostrEvent event) {
            // Simulate relay by delivering to both managers via EventManager
            // (they will filter out their own messages)

            NostrMessageReceivedEvent receivedEvent = new NostrMessageReceivedEvent(event, "mock://relay");

            // Post to event bus so all subscribers receive it
            EventManager.get().post(receivedEvent);
        }

        @Override
        public boolean isConnected() {
            return true;
        }
    }

    /**
     * Collects coordination events for verification
     */
    private static class TestEventCollector {
        boolean receivedSessionCreated = false;
        boolean receivedParticipantJoined = false;
        boolean receivedStateChanged = false;

        @Subscribe
        public void onSessionCreated(CoordinationSessionCreatedEvent event) {
            receivedSessionCreated = true;
        }

        @Subscribe
        public void onParticipantJoined(CoordinationParticipantJoinedEvent event) {
            receivedParticipantJoined = true;
        }

        @Subscribe
        public void onStateChanged(CoordinationSessionStateChangedEvent event) {
            receivedStateChanged = true;
        }
    }
}
