package com.sparrowwallet.sparrow.nostr;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.sparrowwallet.drongo.crypto.ECDSASignature;
import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.crypto.SchnorrSignature;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.protocol.SignatureDecodeException;
import org.bitcoin.NativeSecp256k1;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Cryptographic operations for Nostr protocol.
 *
 * Implements:
 * - Event ID generation (SHA-256 of canonical serialization)
 * - Event signing with secp256k1 (Schnorr signatures)
 * - NIP-04: Encrypted Direct Messages (AES-256-CBC)
 */
public class NostrCrypto {
    private static final Logger log = LoggerFactory.getLogger(NostrCrypto.class);
    private static final Gson gson = new Gson();

    /**
     * Generate event ID from canonical JSON serialization.
     *
     * NIP-01 specifies event ID as SHA-256 hash of serialized event data:
     * [
     *   0,
     *   <pubkey as hex string>,
     *   <created_at as number>,
     *   <kind as number>,
     *   <tags as array of arrays>,
     *   <content as string>
     * ]
     *
     * @param event Event to generate ID for
     * @return 32-byte hex string event ID
     */
    public static String generateEventId(NostrEvent event) {
        try {
            // Build canonical array per NIP-01
            JsonArray canonical = new JsonArray();
            canonical.add(0); // Version
            canonical.add(event.getPubkey());
            canonical.add(event.getCreatedAt());
            canonical.add(event.getKind());
            canonical.add(gson.toJsonTree(event.getTags()));
            canonical.add(event.getContent());

            // Serialize to compact JSON (no whitespace)
            String serialized = gson.toJson(canonical);

            // SHA-256 hash
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(serialized.getBytes(StandardCharsets.UTF_8));

            // Convert to hex
            return bytesToHex(hash);

        } catch(Exception e) {
            log.error("Failed to generate event ID", e);
            throw new RuntimeException("Failed to generate event ID", e);
        }
    }

    /**
     * Sign an event with private key using Schnorr signature (BIP-340).
     *
     * Nostr uses Schnorr signatures over secp256k1 as specified in BIP-340.
     * Signatures are 64 bytes (32-byte r value + 32-byte s value).
     *
     * @param eventId Event ID to sign (32-byte hex string)
     * @param privateKey Private key for signing
     * @return Schnorr signature as 64-byte hex string
     */
    public static String signEvent(String eventId, ECKey privateKey) {
        try {
            // Convert event ID from hex to Sha256Hash
            byte[] eventIdBytes = hexToBytes(eventId);
            Sha256Hash hash = Sha256Hash.wrap(eventIdBytes);

            // Sign with Schnorr (Nostr standard, BIP-340)
            SchnorrSignature signature = privateKey.signSchnorr(hash);

            // Encode signature to 64 bytes (r || s)
            byte[] signatureBytes = signature.encode();

            return bytesToHex(signatureBytes);

        } catch(Exception e) {
            log.error("Failed to sign event", e);
            throw new RuntimeException("Failed to sign event", e);
        }
    }

    /**
     * Verify event signature.
     *
     * @param event Event with id, pubkey, and sig fields populated
     * @return true if signature is valid
     */
    public static boolean verifySignature(NostrEvent event) {
        try {
            if(event.getId() == null || event.getPubkey() == null || event.getSig() == null) {
                return false;
            }

            // Regenerate event ID to verify integrity
            String computedId = generateEventId(event);
            if(!computedId.equals(event.getId())) {
                log.warn("Event ID mismatch: expected {}, got {}", computedId, event.getId());
                return false;
            }

            // Get public key from hex
            byte[] pubkeyBytes = hexToBytes(event.getPubkey());

            // Get signature from hex
            byte[] signatureBytes = hexToBytes(event.getSig());
            ECDSASignature signature = ECDSASignature.decodeFromDER(signatureBytes);

            // Verify signature
            byte[] eventIdBytes = hexToBytes(event.getId());
            return signature.verify(eventIdBytes, pubkeyBytes);

        } catch(SignatureDecodeException e) {
            log.error("Failed to decode signature", e);
            return false;
        } catch(Exception e) {
            log.error("Failed to verify signature", e);
            return false;
        }
    }

    /**
     * Encrypt content using NIP-04 encryption.
     *
     * NIP-04 uses AES-256-CBC with:
     * - Shared secret from ECDH(sender_privkey, recipient_pubkey)
     * - Random 16-byte IV
     * - Base64 encoding of result
     *
     * @param plaintext Content to encrypt
     * @param recipientPubkey Recipient's public key (33-byte compressed hex)
     * @param senderPrivkey Sender's private key
     * @return Base64 encoded: ciphertext?iv
     */
    public static String encrypt(String plaintext, String recipientPubkey, ECKey senderPrivkey) {
        try {
            // Derive shared secret via ECDH using libsecp256k1
            byte[] recipientPubkeyBytes = hexToBytes(recipientPubkey);
            byte[] senderPrivkeyBytes = senderPrivkey.getPrivKeyBytes();

            // ECDH: multiply recipient's public key by our private key
            byte[] sharedSecret = NativeSecp256k1.createECDHSecret(senderPrivkeyBytes, recipientPubkeyBytes);

            // Use first 32 bytes as AES-256 key
            byte[] aesKey = MessageDigest.getInstance("SHA-256").digest(sharedSecret);

            // Generate random IV
            byte[] iv = new byte[16];
            new SecureRandom().nextBytes(iv);

            // Encrypt with AES-256-CBC
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new IvParameterSpec(iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // Encode result: base64(ciphertext)?base64(iv)
            String ciphertextB64 = Base64.getEncoder().encodeToString(ciphertext);
            String ivB64 = Base64.getEncoder().encodeToString(iv);

            return ciphertextB64 + "?iv=" + ivB64;

        } catch(Exception e) {
            log.error("Failed to encrypt content", e);
            throw new RuntimeException("Failed to encrypt content", e);
        }
    }

    /**
     * Decrypt content using NIP-04 encryption.
     *
     * @param encryptedContent Base64 encoded ciphertext?iv
     * @param senderPubkey Sender's public key (33-byte compressed hex)
     * @param recipientPrivkey Recipient's private key
     * @return Decrypted plaintext
     */
    public static String decrypt(String encryptedContent, String senderPubkey, ECKey recipientPrivkey) {
        try {
            // Parse ciphertext?iv format
            String[] parts = encryptedContent.split("\\?iv=");
            if(parts.length != 2) {
                throw new IllegalArgumentException("Invalid encrypted content format");
            }

            byte[] ciphertext = Base64.getDecoder().decode(parts[0]);
            byte[] iv = Base64.getDecoder().decode(parts[1]);

            // Derive shared secret via ECDH using libsecp256k1
            byte[] senderPubkeyBytes = hexToBytes(senderPubkey);
            byte[] recipientPrivkeyBytes = recipientPrivkey.getPrivKeyBytes();

            // ECDH: multiply sender's public key by our private key
            byte[] sharedSecret = NativeSecp256k1.createECDHSecret(recipientPrivkeyBytes, senderPubkeyBytes);

            // Use first 32 bytes as AES-256 key
            byte[] aesKey = MessageDigest.getInstance("SHA-256").digest(sharedSecret);

            // Decrypt with AES-256-CBC
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new IvParameterSpec(iv));
            byte[] plaintext = cipher.doFinal(ciphertext);

            return new String(plaintext, StandardCharsets.UTF_8);

        } catch(Exception e) {
            log.error("Failed to decrypt content", e);
            throw new RuntimeException("Failed to decrypt content", e);
        }
    }

    /**
     * Convert hex string to bytes.
     */
    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for(int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                                + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    /**
     * Convert bytes to hex string.
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for(byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
