package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.psbt.PSBT;

/**
 * Event posted when a PSBT has been reconstructed with additional inputs
 */
public class PSBTReconstructedEvent {
    private final PSBT psbt;

    public PSBTReconstructedEvent(PSBT psbt) {
        this.psbt = psbt;
    }

    public PSBT getPsbt() {
        return psbt;
    }
}
