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
     * @param recipientPubkey Recipient's public key (32-byte Nostr hex format - x-coordinate only)
     * @param senderPrivkey Sender's private key
     * @return Base64 encoded: ciphertext?iv=ivBase64
     */
    public static String encrypt(String plaintext, String recipientPubkey, ECKey senderPrivkey) {
        try {
            // Convert Nostr pubkey (32 bytes x-coordinate) to compressed secp256k1 format (33 bytes)
            byte[] recipientPubkeyCompressed = nostrPubkeyToCompressed(recipientPubkey);
            byte[] senderPrivkeyBytes = senderPrivkey.getPrivKeyBytes();

            // ECDH: multiply recipient's public key by our private key
            byte[] sharedSecret = NativeSecp256k1.createECDHSecret(senderPrivkeyBytes, recipientPubkeyCompressed);

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
     * @param encryptedContent Base64 encoded ciphertext?iv=ivBase64
     * @param senderPubkey Sender's public key (32-byte Nostr hex format - x-coordinate only)
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

            // Convert Nostr pubkey (32 bytes x-coordinate) to compressed secp256k1 format (33 bytes)
            byte[] senderPubkeyCompressed = nostrPubkeyToCompressed(senderPubkey);
            byte[] recipientPrivkeyBytes = recipientPrivkey.getPrivKeyBytes();

            // ECDH: multiply sender's public key by our private key
            byte[] sharedSecret = NativeSecp256k1.createECDHSecret(recipientPrivkeyBytes, senderPubkeyCompressed);

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

    /**
     * Generate a new Nostr private key (hex format).
     *
     * @return 32-byte private key as hex string
     */
    public static String generateNostrPrivateKeyHex() {
        ECKey key = new ECKey();
        byte[] privKeyBytes = key.getPrivKeyBytes();
        return bytesToHex(privKeyBytes);
    }

    /**
     * Derive Nostr public key in npub format from hex private key.
     *
     * @param privateKeyHex 32-byte private key as hex string
     * @return Public key in npub (bech32) format
     */
    public static String deriveNostrPublicKeyNpub(String privateKeyHex) {
        try {
            ECKey key = ECKey.fromPrivate(hexToBytes(privateKeyHex));
            byte[] pubKeyBytes = key.getPubKeyXCoord();

            // Convert to npub using bech32 encoding
            // npub = bech32("npub", pubkey)
            return Bech32.encode("npub", convertBits(pubKeyBytes, 8, 5, true));

        } catch (Exception e) {
            log.error("Failed to derive npub from private key", e);
            throw new RuntimeException("Failed to derive npub: " + e.getMessage(), e);
        }
    }

    /**
     * Derive Nostr public key in hex format from hex private key.
     *
     * @param privateKeyHex 32-byte private key as hex string
     * @return Public key as hex string
     */
    public static String deriveNostrPublicKeyHex(String privateKeyHex) {
        try {
            ECKey key = ECKey.fromPrivate(hexToBytes(privateKeyHex));
            byte[] pubKeyBytes = key.getPubKeyXCoord();
            return bytesToHex(pubKeyBytes);

        } catch (Exception e) {
            log.error("Failed to derive hex pubkey from private key", e);
            throw new RuntimeException("Failed to derive pubkey: " + e.getMessage(), e);
        }
    }

    /**
     * Simple bech32 encoder for npub format.
     * Based on BIP173 specification.
     */
    private static class Bech32 {
        private static final String CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l";

        public static String encode(String hrp, byte[] data) {
            byte[] checksum = createChecksum(hrp, data);
            byte[] combined = new byte[data.length + checksum.length];
            System.arraycopy(data, 0, combined, 0, data.length);
            System.arraycopy(checksum, 0, combined, data.length, checksum.length);

            StringBuilder sb = new StringBuilder(hrp + "1");
            for (byte b : combined) {
                sb.append(CHARSET.charAt(b));
            }
            return sb.toString();
        }

        private static byte[] createChecksum(String hrp, byte[] data) {
            byte[] values = new byte[hrp.length() + 1 + data.length + 6];
            int i = 0;
            for (char c : hrp.toCharArray()) {
                values[i++] = (byte) (c >> 5);
            }
            values[i++] = 0;
            for (char c : hrp.toCharArray()) {
                values[i++] = (byte) (c & 31);
            }
            for (byte b : data) {
                values[i++] = b;
            }

            int polymod = polymodStep(values) ^ 1;
            byte[] checksum = new byte[6];
            for (int j = 0; j < 6; j++) {
                checksum[j] = (byte) ((polymod >> (5 * (5 - j))) & 31);
            }
            return checksum;
        }

        private static int polymodStep(byte[] values) {
            int[] GEN = {0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3};
            int chk = 1;
            for (byte v : values) {
                int b = chk >> 25;
                chk = ((chk & 0x1ffffff) << 5) ^ v;
                for (int i = 0; i < 5; i++) {
                    if (((b >> i) & 1) == 1) {
                        chk ^= GEN[i];
                    }
                }
            }
            return chk;
        }
    }

    /**
     * Convert Nostr public key (32-byte x-coordinate) to compressed secp256k1 format (33 bytes).
     *
     * Nostr uses only the x-coordinate of the public key (32 bytes).
     * For ECDH, we need the full compressed public key (33 bytes with 02/03 prefix).
     *
     * Since we only have the x-coordinate, we need to reconstruct the point.
     * We try prefix 02 (even y) first, and if that doesn't work, try 03 (odd y).
     *
     * @param nostrPubkeyHex 32-byte public key (x-coordinate only) as hex
     * @return 33-byte compressed public key
     */
    private static byte[] nostrPubkeyToCompressed(String nostrPubkeyHex) {
        try {
            byte[] xCoord = hexToBytes(nostrPubkeyHex);

            if (xCoord.length != 32) {
                throw new IllegalArgumentException("Nostr pubkey must be 32 bytes, got: " + xCoord.length);
            }

            // Try even y-coordinate first (prefix 02)
            byte[] compressedEven = new byte[33];
            compressedEven[0] = 0x02;
            System.arraycopy(xCoord, 0, compressedEven, 1, 32);

            try {
                // Attempt to parse as valid secp256k1 point
                ECKey.fromPublicOnly(compressedEven);
                return compressedEven;
            } catch (Exception e) {
                // Even y didn't work, try odd y (prefix 03)
                byte[] compressedOdd = new byte[33];
                compressedOdd[0] = 0x03;
                System.arraycopy(xCoord, 0, compressedOdd, 1, 32);

                try {
                    // Verify this is valid
                    ECKey.fromPublicOnly(compressedOdd);
                    return compressedOdd;
                } catch (Exception e2) {
                    throw new IllegalArgumentException("Invalid Nostr public key - cannot reconstruct point", e2);
                }
            }

        } catch (Exception e) {
            log.error("Failed to convert Nostr pubkey to compressed format", e);
            throw new RuntimeException("Failed to convert Nostr pubkey", e);
        }
    }

    /**
     * Convert bits between different bases (for bech32 encoding).
     */
    private static byte[] convertBits(byte[] data, int fromBits, int toBits, boolean pad) {
        int acc = 0;
        int bits = 0;
        int maxv = (1 << toBits) - 1;
        java.util.ArrayList<Byte> ret = new java.util.ArrayList<>();

        for (byte b : data) {
            int value = b & 0xff;
            acc = (acc << fromBits) | value;
            bits += fromBits;
            while (bits >= toBits) {
                bits -= toBits;
                ret.add((byte) ((acc >> bits) & maxv));
            }
        }

        if (pad && bits > 0) {
            ret.add((byte) ((acc << (toBits - bits)) & maxv));
        }

        byte[] result = new byte[ret.size()];
        for (int i = 0; i < ret.size(); i++) {
            result[i] = ret.get(i);
        }
        return result;
    }
}
