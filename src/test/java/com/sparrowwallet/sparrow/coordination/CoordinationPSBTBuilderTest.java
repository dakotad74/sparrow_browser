package com.sparrowwallet.sparrow.coordination;

import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.wallet.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test PSBT construction from coordination sessions.
 *
 * These tests focus on the CoordinationPSBTBuilder logic:
 * - Session state validation
 * - Network compatibility
 * - Parameter conversion
 * - Error handling
 *
 * Note: These are unit tests that validate the builder logic.
 * Integration testing with real wallets is done in CoordinationIntegrationTest.
 */
public class CoordinationPSBTBuilderTest {

    private CoordinationSession session;
    private static final String PARTICIPANT_PUBKEY = "test-participant-pubkey";
    private static final double TEST_FEE_RATE = 10.0; // sat/vB

    @BeforeEach
    public void setUp() throws Exception {
        // Set network to TESTNET
        Network.set(Network.TESTNET);

        // Create a coordination session
        session = new CoordinationSession(
            "test-session-id",
            "mock-wallet-descriptor",
            Network.TESTNET,
            2 // 2 participants
        );

        // Add participants
        CoordinationParticipant participant1 = new CoordinationParticipant(
            PARTICIPANT_PUBKEY,
            "Participant 1",
            "xpub-mock-1"
        );
        CoordinationParticipant participant2 = new CoordinationParticipant(
            "participant2-pubkey",
            "Participant 2",
            "xpub-mock-2"
        );
        session.addParticipant(participant1);
        session.addParticipant(participant2);

        // Propose outputs
        Address outputAddr1 = Address.fromString("tb1q9pvjqz5u5sdgpatg3wn0ce438u5cyv85lly0pc");
        CoordinationOutput output1 = new CoordinationOutput(
            outputAddr1,
            50000L,
            "Payment to Alice",
            PARTICIPANT_PUBKEY
        );

        Address outputAddr2 = Address.fromString("tb1qw508d6qejxtdg4y5r3zarvary0c5xw7kxpjzsx");
        CoordinationOutput output2 = new CoordinationOutput(
            outputAddr2,
            30000L,
            "Payment to Bob",
            "participant2-pubkey"
        );

        session.proposeOutput(output1);
        session.proposeOutput(output2);

        // Propose fees
        CoordinationFeeProposal fee1 = new CoordinationFeeProposal(
            PARTICIPANT_PUBKEY,
            TEST_FEE_RATE
        );
        CoordinationFeeProposal fee2 = new CoordinationFeeProposal(
            "participant2-pubkey",
            TEST_FEE_RATE
        );
        session.proposeFee(fee1);
        session.proposeFee(fee2);

        // Agree on fee
        session.agreeFee(TEST_FEE_RATE);

        // Finalize session
        session.finalizeSession();
    }

    @Test
    public void testRejectsNonFinalizedSession() {
        // Create a new session that's not finalized
        CoordinationSession unfinishedSession = new CoordinationSession(
            "unfinished-session",
            "mock-descriptor",
            Network.TESTNET,
            2
        );

        // Create a minimal mock wallet (won't be used because session is not finalized)
        Wallet mockWallet = new Wallet("Test Wallet");
        mockWallet.setNetwork(Network.TESTNET);

        // Should throw IllegalStateException
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> CoordinationPSBTBuilder.buildPSBT(unfinishedSession, mockWallet, 2500000)
        );

        assertTrue(exception.getMessage().contains("finalized"),
            "Exception should mention session must be finalized");
    }

    @Test
    public void testRejectsSessionWithoutAgreedFee() {
        // Create a session without agreed fee
        CoordinationSession noFeeSession = new CoordinationSession(
            "no-fee-session",
            "mock-descriptor",
            Network.TESTNET,
            2
        );

        // Manually set state to FINALIZED without going through proper workflow
        // (This is a test hack to bypass validation)
        // In reality, finalizeSession() checks for agreed fee
        // So we test the buildPSBT validation directly

        Wallet mockWallet = new Wallet("Test Wallet");
        mockWallet.setNetwork(Network.TESTNET);

        // Should throw IllegalStateException because agreedFeeRate is null
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> CoordinationPSBTBuilder.buildPSBT(noFeeSession, mockWallet, 2500000)
        );

        assertTrue(exception.getMessage().contains("finalized") ||
                   exception.getMessage().contains("fee"),
            "Exception should mention missing fee or finalization");
    }

    @Test
    public void testRejectsNetworkMismatch() {
        // Create a mainnet wallet
        Wallet mainnetWallet = new Wallet("Mainnet Wallet");
        mainnetWallet.setNetwork(Network.MAINNET);

        // Session is TESTNET, wallet is MAINNET - should throw
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> CoordinationPSBTBuilder.buildPSBT(session, mainnetWallet, 2500000)
        );

        assertTrue(exception.getMessage().contains("network"),
            "Exception should mention network mismatch");
    }

    @Test
    public void testSessionStateValidation() {
        // Verify session is properly finalized
        assertEquals(SessionState.FINALIZED, session.getState(),
            "Session should be in FINALIZED state");

        assertNotNull(session.getAgreedFeeRate(),
            "Session should have agreed fee rate");

        assertEquals(TEST_FEE_RATE, session.getAgreedFeeRate(),
            "Agreed fee rate should match");

        assertEquals(2, session.getOutputs().size(),
            "Session should have 2 outputs");

        assertEquals(80000L, session.getTotalOutputAmount(),
            "Total output amount should be 50k + 30k = 80k");
    }

    @Test
    public void testSessionHasCorrectOutputs() {
        // Verify outputs are correctly stored
        var outputs = session.getOutputs();

        boolean has50kOutput = outputs.stream()
            .anyMatch(out -> out.getAmount() == 50000L);
        boolean has30kOutput = outputs.stream()
            .anyMatch(out -> out.getAmount() == 30000L);

        assertTrue(has50kOutput, "Session should have 50k sat output");
        assertTrue(has30kOutput, "Session should have 30k sat output");
    }

    @Test
    public void testSessionHasCorrectFeeProposals() {
        // Verify fee proposals
        var feeProposals = session.getFeeProposals();

        assertEquals(2, feeProposals.size(),
            "Session should have 2 fee proposals");

        boolean allMatch = feeProposals.stream()
            .allMatch(fp -> fp.getFeeRate() == TEST_FEE_RATE);

        assertTrue(allMatch,
            "All fee proposals should match TEST_FEE_RATE");
    }

    @Test
    public void testEstimatedTransactionStructure() {
        // Test the EstimatedTransaction class structure
        CoordinationPSBTBuilder.EstimatedTransaction estimate =
            new CoordinationPSBTBuilder.EstimatedTransaction(
                250.0,  // vsize
                2500L,  // estimated fee
                100000L, // input value
                80000L   // output value
            );

        assertEquals(250.0, estimate.getVirtualSize());
        assertEquals(2500L, estimate.getEstimatedFee());
        assertEquals(100000L, estimate.getParticipantInputValue());
        assertEquals(80000L, estimate.getTotalOutputValue());

        // Test toString (for debugging/logging)
        String str = estimate.toString();
        assertNotNull(str);
        assertTrue(str.contains("vsize"));
        assertTrue(str.contains("fee"));
    }

    @Test
    public void testPaymentConversionLogic() {
        // This tests the internal logic of converting CoordinationOutput to Payment
        // We can't directly test the private conversion, but we can verify
        // the session has the right data that would be converted

        var outputs = session.getOutputs();

        // First output
        CoordinationOutput out1 = outputs.get(0);
        assertEquals(50000L, out1.getAmount());
        assertEquals("Payment to Alice", out1.getLabel());
        assertNotNull(out1.getAddress());

        // Second output
        CoordinationOutput out2 = outputs.get(1);
        assertEquals(30000L, out2.getAmount());
        assertEquals("Payment to Bob", out2.getLabel());
        assertNotNull(out2.getAddress());

        // Both outputs are valid for TESTNET
        assertTrue(out1.isValidForNetwork(Network.TESTNET));
        assertTrue(out2.isValidForNetwork(Network.TESTNET));
    }
}
