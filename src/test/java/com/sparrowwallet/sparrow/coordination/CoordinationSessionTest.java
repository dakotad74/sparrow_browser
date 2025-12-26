package com.sparrowwallet.sparrow.coordination;

import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.address.Address;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CoordinationSession and related models
 */
public class CoordinationSessionTest {

    @Test
    public void testSessionCreation() {
        CoordinationSession session = new CoordinationSession(
            "test-session-1",
            "test-wallet",
            Network.TESTNET,
            2
        );

        assertNotNull(session);
        assertEquals("test-session-1", session.getSessionId());
        assertEquals(Network.TESTNET, session.getNetwork());
        assertEquals(SessionState.CREATED, session.getState());
        assertEquals(2, session.getExpectedParticipants());
        assertFalse(session.allParticipantsJoined());
    }

    @Test
    public void testParticipantJoining() {
        CoordinationSession session = new CoordinationSession(
            "test-session-2",
            "test-wallet",
            Network.TESTNET,
            2
        );

        CoordinationParticipant participant1 = new CoordinationParticipant("pubkey1", "Alice", "xpub1");
        CoordinationParticipant participant2 = new CoordinationParticipant("pubkey2", "Bob", "xpub2");

        session.addParticipant(participant1);
        assertEquals(1, session.getParticipants().size());
        assertFalse(session.allParticipantsJoined());

        session.addParticipant(participant2);
        assertEquals(2, session.getParticipants().size());
        assertTrue(session.allParticipantsJoined());
    }

    @Test
    public void testOutputProposal() throws Exception {
        CoordinationSession session = new CoordinationSession(
            "test-session-3",
            "test-wallet",
            Network.TESTNET,
            2
        );

        // Create a valid testnet address (bech32)
        Address address = Address.fromString("tb1q9pvjqz5u5sdgpatg3wn0ce438u5cyv85lly0pc");
        CoordinationOutput output = new CoordinationOutput(address, 100000, "Test payment", "pubkey1");

        session.proposeOutput(output);

        assertEquals(1, session.getOutputs().size());
        assertEquals(100000, session.getTotalOutputAmount());
    }

    @Test
    public void testFeeProposal() {
        CoordinationSession session = new CoordinationSession(
            "test-session-4",
            "test-wallet",
            Network.TESTNET,
            2
        );

        CoordinationParticipant participant1 = new CoordinationParticipant("pubkey1");
        CoordinationParticipant participant2 = new CoordinationParticipant("pubkey2");
        session.addParticipant(participant1);
        session.addParticipant(participant2);

        CoordinationFeeProposal fee1 = new CoordinationFeeProposal("pubkey1", 10.0);
        CoordinationFeeProposal fee2 = new CoordinationFeeProposal("pubkey2", 15.0);

        session.proposeFee(fee1);
        session.proposeFee(fee2);

        assertTrue(session.allParticipantsProposedFees());
        assertEquals(15.0, session.getHighestProposedFeeRate().orElse(0.0));
    }

    @Test
    public void testSessionFinalization() throws Exception {
        CoordinationSession session = new CoordinationSession(
            "test-session-5",
            "test-wallet",
            Network.TESTNET,
            2
        );

        // Add participants
        session.addParticipant(new CoordinationParticipant("pubkey1"));
        session.addParticipant(new CoordinationParticipant("pubkey2"));

        // Add output
        Address address = Address.fromString("tb1q9pvjqz5u5sdgpatg3wn0ce438u5cyv85lly0pc");
        session.proposeOutput(new CoordinationOutput(address, 100000, "pubkey1"));

        // Propose fees
        session.proposeFee(new CoordinationFeeProposal("pubkey1", 10.0));
        session.proposeFee(new CoordinationFeeProposal("pubkey2", 12.0));

        // Agree on fee
        session.agreeFee(12.0);

        // Should be ready to finalize
        assertTrue(session.isReadyToFinalize());

        // Finalize
        session.finalizeSession();
        assertEquals(SessionState.FINALIZED, session.getState());
    }

    @Test
    public void testDuplicateParticipant() {
        CoordinationSession session = new CoordinationSession(
            "test-session-6",
            "test-wallet",
            Network.TESTNET,
            2
        );

        CoordinationParticipant participant = new CoordinationParticipant("pubkey1");
        session.addParticipant(participant);

        // Try to add same participant again
        assertThrows(IllegalArgumentException.class, () -> {
            session.addParticipant(new CoordinationParticipant("pubkey1"));
        });
    }

    @Test
    public void testInvalidOutput() {
        CoordinationSession session = new CoordinationSession(
            "test-session-7",
            "test-wallet",
            Network.TESTNET,
            2
        );

        // Negative amount should throw
        assertThrows(IllegalArgumentException.class, () -> {
            Address address = Address.fromString("tb1q9pvjqz5u5sdgpatg3wn0ce438u5cyv85lly0pc");
            new CoordinationOutput(address, -100, "pubkey1");
        });
    }
}
