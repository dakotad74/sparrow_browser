package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.crypto.musig2.MuSig2;

import java.util.Map;

/**
 * Event posted when MuSig2 Round 1 (nonce generation and exchange) is complete.
 * Contains the public nonces that need to be shared with other signers.
 */
public class MuSig2Round1Event extends PSBTEvent {
    private final Map<Integer, MuSig2.MuSig2Nonce> publicNonces;
    private final byte[] message;

    public MuSig2Round1Event(PSBT psbt, Map<Integer, MuSig2.MuSig2Nonce> publicNonces, byte[] message) {
        super(psbt);
        this.publicNonces = publicNonces;
        this.message = message;
    }

    /**
     * Get the public nonces for each input index
     * @return Map of input index to MuSig2 nonce
     */
    public Map<Integer, MuSig2.MuSig2Nonce> getPublicNonces() {
        return publicNonces;
    }

    /**
     * Get the message to be signed (transaction hash)
     * @return message as byte array
     */
    public byte[] getMessage() {
        return message;
    }
}
