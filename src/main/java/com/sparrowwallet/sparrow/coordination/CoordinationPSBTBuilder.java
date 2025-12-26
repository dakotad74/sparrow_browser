package com.sparrowwallet.sparrow.coordination;

import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.wallet.*;

import java.util.*;

/**
 * Builds PSBTs from coordinated sessions.
 *
 * This class bridges the coordination layer with Sparrow's existing transaction
 * creation infrastructure. It converts CoordinationSession outputs into Payment
 * objects and uses Wallet.createWalletTransaction() to create the PSBT.
 *
 * Key features:
 * - Converts coordination outputs to payments
 * - Uses agreed fee rate from session
 * - Enables allowInsufficientInputs for multi-party coordination
 * - Each participant creates a PSBT with their own inputs + change
 */
public class CoordinationPSBTBuilder {

    /**
     * Build a PSBT from a finalized coordination session.
     *
     * This method:
     * 1. Validates the session is ready
     * 2. Converts CoordinationOutputs to Payment objects
     * 3. Creates TransactionParameters with agreed fee rate
     * 4. Calls wallet.createWalletTransaction() with allowInsufficientInputs=true
     * 5. Converts WalletTransaction to PSBT
     *
     * The allowInsufficientInputs flag is critical for multi-party coordination:
     * - Each participant may not have enough UTXOs to fund the entire transaction
     * - PSBTs from multiple participants will be combined later
     * - Each PSBT contains participant's inputs + their change output
     *
     * @param session Finalized coordination session with agreed outputs and fee
     * @param wallet Participant's wallet to use for input selection
     * @param currentBlockHeight Current block height for locktime
     * @return PSBT with this participant's inputs and all coordinated outputs
     * @throws IllegalStateException if session is not finalized
     * @throws IllegalArgumentException if session network doesn't match wallet network
     * @throws InsufficientFundsException if participant has zero UTXOs available
     */
    public static PSBT buildPSBT(CoordinationSession session, Wallet wallet, Integer currentBlockHeight)
            throws InsufficientFundsException {

        // Validate session state
        if(session.getState() != SessionState.FINALIZED) {
            throw new IllegalStateException("Session must be finalized before creating PSBT. Current state: " + session.getState());
        }

        if(session.getAgreedFeeRate() == null) {
            throw new IllegalStateException("Session must have an agreed fee rate");
        }

        if(session.getOutputs().isEmpty()) {
            throw new IllegalStateException("Session must have at least one output");
        }

        // Validate network compatibility
        if(wallet.getNetwork() != session.getNetwork()) {
            throw new IllegalArgumentException("Wallet network (" + wallet.getNetwork() +
                ") does not match session network (" + session.getNetwork() + ")");
        }

        // Convert CoordinationOutputs to Payments
        List<Payment> payments = new ArrayList<>();
        for(CoordinationOutput output : session.getOutputs()) {
            Payment payment = new Payment(
                output.getAddress(),
                output.getLabel(),
                output.getAmount(),
                false  // sendMax = false for coordinated outputs
            );
            payments.add(payment);
        }

        // Create transaction parameters
        // Use existing UtxoSelector or empty list to use default selector
        List<UtxoSelector> utxoSelectors = new ArrayList<>();

        // No UTXO filters
        List<TxoFilter> txoFilters = new ArrayList<>();

        // No OP_RETURN data
        List<byte[]> opReturns = new ArrayList<>();

        // No excluded change nodes
        Set<WalletNode> excludedChangeNodes = new HashSet<>();

        // Get min relay fee rate (use default if not available)
        double minRelayFeeRate = Transaction.DEFAULT_MIN_RELAY_FEE;

        // Create parameters with allowInsufficientInputs = true
        // This is the key feature that enables multi-party coordination:
        // - Each participant may not have enough funds to cover all outputs
        // - The PSBT will be incomplete, which is expected
        // - PSBTs from all participants will be combined later
        TransactionParameters params = new TransactionParameters(
            utxoSelectors,
            txoFilters,
            payments,
            opReturns,
            excludedChangeNodes,
            session.getAgreedFeeRate(),  // Use agreed fee rate from session
            session.getAgreedFeeRate(),  // longTermFeeRate same as feeRate
            minRelayFeeRate,
            null,  // fee = null, calculate from fee rate
            currentBlockHeight,
            false, // groupByAddress
            true,  // includeMempoolOutputs
            true,  // allowRbf
            true   // allowInsufficientInputs - CRITICAL for coordination
        );

        // Create wallet transaction
        // This will select UTXOs from this participant's wallet
        // and add a change output if needed
        WalletTransaction walletTx = wallet.createWalletTransaction(params);

        // Convert to PSBT
        PSBT psbt = walletTx.createPSBT();

        return psbt;
    }

    /**
     * Build a PSBT with manually selected UTXOs.
     *
     * This variant allows the user to manually select which UTXOs to contribute
     * to the coordinated transaction. Useful when participants want precise
     * control over their inputs.
     *
     * @param session Finalized coordination session
     * @param wallet Participant's wallet
     * @param selectedUtxos UTXOs to use as inputs (can be subset of available UTXOs)
     * @param currentBlockHeight Current block height for locktime
     * @return PSBT with selected inputs and coordinated outputs
     * @throws IllegalStateException if session is not finalized
     * @throws InsufficientFundsException should not occur with preset UTXOs
     */
    public static PSBT buildPSBTWithSelectedUtxos(
            CoordinationSession session,
            Wallet wallet,
            Collection<BlockTransactionHashIndex> selectedUtxos,
            Integer currentBlockHeight) throws InsufficientFundsException {

        // Validate session (same as above)
        if(session.getState() != SessionState.FINALIZED) {
            throw new IllegalStateException("Session must be finalized before creating PSBT");
        }

        if(session.getAgreedFeeRate() == null) {
            throw new IllegalStateException("Session must have an agreed fee rate");
        }

        if(selectedUtxos == null || selectedUtxos.isEmpty()) {
            throw new IllegalArgumentException("Selected UTXOs cannot be null or empty");
        }

        // Convert outputs to payments
        List<Payment> payments = new ArrayList<>();
        for(CoordinationOutput output : session.getOutputs()) {
            Payment payment = new Payment(
                output.getAddress(),
                output.getLabel(),
                output.getAmount(),
                false
            );
            payments.add(payment);
        }

        // Validate that selected UTXOs belong to this wallet
        List<BlockTransactionHashIndex> validUtxos = new ArrayList<>();
        for(BlockTransactionHashIndex utxo : selectedUtxos) {
            // Check if this UTXO belongs to the wallet
            WalletNode node = wallet.getWalletUtxos().get(utxo);
            if(node != null) {
                validUtxos.add(utxo);
            }
        }

        if(validUtxos.isEmpty()) {
            throw new IllegalArgumentException("None of the selected UTXOs belong to this wallet");
        }

        List<UtxoSelector> utxoSelectors = List.of(new PresetUtxoSelector(validUtxos));

        // Create transaction parameters with preset UTXOs
        TransactionParameters params = new TransactionParameters(
            utxoSelectors,
            new ArrayList<>(), // txoFilters
            payments,
            new ArrayList<>(), // opReturns
            new HashSet<>(),   // excludedChangeNodes
            session.getAgreedFeeRate(),
            session.getAgreedFeeRate(),
            Transaction.DEFAULT_MIN_RELAY_FEE,
            null,
            currentBlockHeight,
            false, // groupByAddress
            true,  // includeMempoolOutputs
            true,  // allowRbf
            true   // allowInsufficientInputs
        );

        WalletTransaction walletTx = wallet.createWalletTransaction(params);
        return walletTx.createPSBT();
    }

    /**
     * Estimate the size and fee for a coordinated transaction.
     *
     * Useful for showing participants an estimate before creating the PSBT.
     *
     * @param session Coordination session
     * @param wallet Participant's wallet
     * @param currentBlockHeight Current block height
     * @return EstimatedTransaction with size and fee information
     */
    public static EstimatedTransaction estimateTransaction(
            CoordinationSession session,
            Wallet wallet,
            Integer currentBlockHeight) {

        if(session.getAgreedFeeRate() == null) {
            throw new IllegalStateException("Session must have an agreed fee rate");
        }

        try {
            // Create a PSBT to get accurate size estimate
            PSBT psbt = buildPSBT(session, wallet, currentBlockHeight);
            Transaction tx = psbt.getTransaction();

            double vsize = tx.getVirtualSize();
            long estimatedFee = (long)Math.ceil(session.getAgreedFeeRate() * vsize);

            // Calculate participant's contribution
            long participantInputs = psbt.getPsbtInputs().stream()
                .mapToLong(input -> input.getWitnessUtxo() != null ?
                    input.getWitnessUtxo().getValue() : 0)
                .sum();

            long totalOutputs = session.getTotalOutputAmount();

            return new EstimatedTransaction(vsize, estimatedFee, participantInputs, totalOutputs);

        } catch (InsufficientFundsException e) {
            // Participant has no UTXOs - return zero estimates
            return new EstimatedTransaction(0, 0, 0, session.getTotalOutputAmount());
        }
    }

    /**
     * Simple container for transaction estimates
     */
    public static class EstimatedTransaction {
        private final double virtualSize;
        private final long estimatedFee;
        private final long participantInputValue;
        private final long totalOutputValue;

        public EstimatedTransaction(double virtualSize, long estimatedFee,
                                   long participantInputValue, long totalOutputValue) {
            this.virtualSize = virtualSize;
            this.estimatedFee = estimatedFee;
            this.participantInputValue = participantInputValue;
            this.totalOutputValue = totalOutputValue;
        }

        public double getVirtualSize() { return virtualSize; }
        public long getEstimatedFee() { return estimatedFee; }
        public long getParticipantInputValue() { return participantInputValue; }
        public long getTotalOutputValue() { return totalOutputValue; }

        @Override
        public String toString() {
            return String.format("EstimatedTransaction{vsize=%.2f, fee=%d, inputs=%d, outputs=%d}",
                virtualSize, estimatedFee, participantInputValue, totalOutputValue);
        }
    }
}
