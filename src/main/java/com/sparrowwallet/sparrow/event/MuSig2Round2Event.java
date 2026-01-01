package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.psbt.PSBT;

/**
 * Event posted when MuSig2 Round 2 (partial signature creation) is complete.
 * Contains the PSBT with the partial signature added.
 */
public class MuSig2Round2Event extends PSBTEvent {
    private final PSBT partialSignedPsbt;

    public MuSig2Round2Event(PSBT psbt, PSBT partialSignedPsbt) {
        super(psbt);
        this.partialSignedPsbt = partialSignedPsbt;
    }

    /**
     * Get the PSBT with the partial signature added
     * @return PSBT with partial signature
     */
    public PSBT getPartialSignedPsbt() {
        return partialSignedPsbt;
    }
}
