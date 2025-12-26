package com.sparrowwallet.sparrow.coordination;

import com.sparrowwallet.drongo.Network;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple tests for CoordinationSession that don't require Address objects
 */
public class CoordinationSessionSimpleTest {

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
    public void testFeeProposal() {
        CoordinationSession session = new CoordinationSession(
            "test-session-3",
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
    public void testParticipantStatus() {
        CoordinationParticipant participant = new CoordinationParticipant("pubkey1", "Alice", "xpub1");

        assertEquals(CoordinationParticipant.ParticipantStatus.JOINED, participant.getStatus());
        assertFalse(participant.isReady());

        participant.setStatus(CoordinationParticipant.ParticipantStatus.READY);
        assertTrue(participant.isReady());
    }

    @Test
    public void testDuplicateParticipant() {
        CoordinationSession session = new CoordinationSession(
            "test-session-4",
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
    public void testInvalidFeeRate() {
        assertThrows(IllegalArgumentException.class, () -> {
            new CoordinationFeeProposal("pubkey1", -5.0);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            new CoordinationFeeProposal("pubkey1", 0);
        });
    }

    @Test
    public void testSessionExpiration() {
        CoordinationSession session = new CoordinationSession(
            "test-session-5",
            "test-wallet",
            Network.TESTNET,
            2
        );

        assertFalse(session.isExpired());
        assertNotNull(session.getCreatedAt());
        assertNotNull(session.getExpiresAt());
    }

    @Test
    public void testFeeProposalReplacement() {
        CoordinationSession session = new CoordinationSession(
            "test-session-6",
            "test-wallet",
            Network.TESTNET,
            2
        );

        session.addParticipant(new CoordinationParticipant("pubkey1"));

        // First proposal
        session.proposeFee(new CoordinationFeeProposal("pubkey1", 10.0));
        assertEquals(1, session.getFeeProposals().size());

        // Second proposal from same participant should replace first
        session.proposeFee(new CoordinationFeeProposal("pubkey1", 15.0));
        assertEquals(1, session.getFeeProposals().size());
        assertEquals(15.0, session.getHighestProposedFeeRate().orElse(0.0));
    }
}
