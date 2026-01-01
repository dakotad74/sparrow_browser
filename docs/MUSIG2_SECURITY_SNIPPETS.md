# 💻 Código de Referencia - Correcciones de Seguridad MuSig2

**Fecha:** 2025-12-31
**Propósito:** Snippets de código listos para copiar/pegar

---

## 🔧 SNIPPET 1: SecureRandom por Operación

### Archivo: `MuSig2.java`

```java
// ============================================================================
// ANTES (PROBLEMA)
// ============================================================================
public class MuSig2 {
    private static final Logger log = LoggerFactory.getLogger(MuSig2.class);
    private static final SecureRandom random = new SecureRandom(); // ❌ COMPARTIDO

    public static CompleteNonce generateRound1Nonce(
            ECKey signer,
            List<ECKey> publicKeys,
            Sha256Hash message) {

        byte[] auxRand = new byte[32];
        random.nextBytes(auxRand); // ❌ Usa instancia compartida
        // ...
    }
}

// ============================================================================
// DESPUÉS (CORRECTO)
// ============================================================================
public class MuSig2 {
    private static final Logger log = LoggerFactory.getLogger(MuSig2.class);
    // ✅ REMOVER: private static final SecureRandom random = new SecureRandom();

    public static CompleteNonce generateRound1Nonce(
            ECKey signer,
            List<ECKey> publicKeys,
            Sha256Hash message) {

        // ✅ CORRECCIÓN: Crear nueva instancia por operación
        SecureRandom random = new SecureRandom();

        byte[] auxRand = new byte[32];
        random.nextBytes(auxRand);

        // Resto del código sin cambios...
        BigInteger[] nonces = MuSig2Core.generateDeterministicNonces(
            signer, publicKeys, message, auxRand
        );

        BigInteger k1 = nonces[0];
        BigInteger k2 = nonces[1];

        // Generar nonces públicos
        ECKey pubNonce1 = ECKey.fromPrivate(Utils.bigIntegerToBytes(k1, 32));
        ECKey pubNonce2 = ECKey.fromPrivate(Utils.bigIntegerToBytes(k2, 32));

        MuSig2Nonce publicNonce = new MuSig2Nonce(
            pubNonce1.getPubKey(),
            pubNonce2.getPubKey()
        );

        SecretNonce secretNonce = new SecretNonce(
            Utils.bigIntegerToBytes(k1, 32),
            Utils.bigIntegerToBytes(k2, 32),
            signer.getPubKey()
        );

        return new CompleteNonce(publicNonce, secretNonce);
    }
}
```

---

## 🔧 SNIPPET 2: Validación de Puntos

### Archivo: `MuSig2Core.java`

```java
// ============================================================================
// NUEVOS MÉTODOS DE VALIDACIÓN
// ============================================================================

/**
 * Validate that an ECPoint is valid on secp256k1 curve
 *
 * @param point Point to validate
 * @return true if point is valid, false otherwise
 */
private static boolean isValidPoint(org.bouncycastle.math.ec.ECPoint point) {
    if (point == null) {
        return false;
    }

    // Check if point is infinity (not valid for public keys)
    if (point.isInfinity()) {
        return false;
    }

    // Check if point is on curve
    if (!point.isValid()) {
        return false;
    }

    // Additional validation: check coordinates are in field
    try {
        BigInteger x = point.getAffineXCoord().toBigInteger();
        BigInteger y = point.getAffineYCoord().toBigInteger();

        // Verify coordinates are valid for secp256k1
        // x and y must be in [0, p-1] where p is field characteristic
        ECKey.CURVE.getCurve().validatePoint(x, y);

        return true;

    } catch (IllegalArgumentException e) {
        log.warn("Invalid point coordinates: {}", e.getMessage());
        return false;
    }
}

/**
 * Validate ECKey's public key
 *
 * @param key Key to validate
 * @return true if key is valid, false otherwise
 */
private static boolean isValidPublicKey(ECKey key) {
    if (key == null) {
        log.error("Public key is null");
        return false;
    }

    try {
        org.bouncycastle.math.ec.ECPoint point = key.getPubKeyPoint();
        return isValidPoint(point);
    } catch (Exception e) {
        log.error("Error validating public key: {}", e.getMessage());
        return false;
    }
}

// ============================================================================
// MODIFICACIÓN DE aggregatePublicKeys()
// ============================================================================

public static KeyAggContext aggregatePublicKeys(List<ECKey> publicKeys) {
    if (publicKeys == null) {
        throw new IllegalArgumentException("publicKeys cannot be null");
    }

    if (publicKeys.isEmpty()) {
        throw new IllegalArgumentException("Cannot aggregate empty list of public keys");
    }

    log.info("MuSig2: Aggregating {} public keys using BIP-327", publicKeys.size());

    try {
        // ✅ CORRECCIÓN: Validar todas las claves antes de procesar
        for (int i = 0; i < publicKeys.size(); i++) {
            ECKey key = publicKeys.get(i);

            if (!isValidPublicKey(key)) {
                throw new IllegalArgumentException(
                    "Invalid public key at index " + i + ": " +
                    (key != null ? Utils.bytesToHex(key.getPubKey()) : "null")
                );
            }
        }

        // BIP-327: Keys must be pre-sorted by caller
        // Reference implementation does NOT sort keys internally
        ECKey pk2 = getSecondKey(publicKeys);

        // BIP-327: Compute aggregated point Q = a_1*P_1 + a_2*P_2 + ... + a_u*P_u
        org.bouncycastle.math.ec.ECPoint aggregatedPoint = null;

        for (int i = 0; i < publicKeys.size(); i++) {
            ECKey signerKey = publicKeys.get(i);
            org.bouncycastle.math.ec.ECPoint P_i = signerKey.getPubKeyPoint();

            // ✅ Validación adicional (redundante pero segura)
            if (!isValidPoint(P_i)) {
                throw new IllegalArgumentException(
                    "Invalid point for key at index " + i
                );
            }

            // Compute coefficient a_i using BIP-327 algorithm
            BigInteger a_i;
            if (signerKey.equals(pk2)) {
                // MuSig2* optimization: second distinct key gets coefficient 1
                a_i = BigInteger.ONE;
            } else {
                // Compute coefficient using tagged hash
                byte[] L = hashKeys(publicKeys);
                byte[] pkBytes = signerKey.getPubKey();
                byte[] hashInput = new byte[L.length + pkBytes.length];
                System.arraycopy(L, 0, hashInput, 0, L.length);
                System.arraycopy(pkBytes, 0, hashInput, L.length, pkBytes.length);
                byte[] hashBytes = Utils.taggedHash(KEYAGG_COEFF_TAG, hashInput);
                a_i = new BigInteger(1, hashBytes).mod(ECKey.CURVE.getN());
            }

            // Multiply point by coefficient: a_i * P_i
            org.bouncycastle.math.ec.ECPoint adjustedPoint = P_i.multiply(a_i);

            // Add to aggregate
            if (aggregatedPoint == null) {
                aggregatedPoint = adjustedPoint;
            } else {
                aggregatedPoint = aggregatedPoint.add(adjustedPoint);
            }
        }

        // Fail if aggregated point is infinity (should not happen with valid keys)
        if (aggregatedPoint.isInfinity()) {
            throw new IllegalArgumentException(
                "Aggregated public key is infinity (invalid key combination)"
            );
        }

        // Initialize tweak accumulators
        BigInteger gacc = BigInteger.ONE;
        BigInteger tacc = BigInteger.ZERO;

        log.info("MuSig2: Successfully aggregated {} public keys", publicKeys.size());

        return new KeyAggContext(aggregatedPoint, gacc, tacc);

    } catch (IllegalArgumentException e) {
        log.error("Key aggregation failed: {}", e.getMessage());
        throw e;
    } catch (Exception e) {
        log.error("Unexpected error in key aggregation", e);
        throw new RuntimeException("Key aggregation failed", e);
    }
}
```

---

## 🔧 SNIPPET 3: Constant-Time Comparison

### Archivo: `MuSig2Crypto.java` (NUEVO)

```java
package com.sparrowwallet.drongo.crypto.musig2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.Arrays;

/**
 * Cryptographic utilities for MuSig2 with constant-time operations
 *
 * This class provides utility methods for cryptographic operations that must
 * be resistant to timing attacks.
 */
public class MuSig2Crypto {
    private static final Logger log = LoggerFactory.getLogger(MuSig2Crypto.class);

    /**
     * Constant-time byte array comparison
     *
     * This method compares two byte arrays in constant time,
     * preventing timing attacks that could leak information about
     * the comparison result through execution time differences.
     *
     * Algorithm: XOR all bytes and check if result is zero
     *
     * @param a First byte array
     * @param b Second byte array
     * @return true if arrays are equal, false otherwise
     */
    public static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a == null || b == null) {
            return a == b;
        }

        // Length check is NOT constant-time, but that's acceptable here
        // because differing lengths would be obvious anyway
        if (a.length != b.length) {
            return false;
        }

        // Constant-time comparison: XOR all bytes
        int result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }

        return result == 0;
    }

    /**
     * Constant-time BigInteger comparison
     *
     * Converts BigIntegers to byte arrays and performs constant-time
     * comparison on the byte representations.
     *
     * @param a First BigInteger
     * @param b Second BigInteger
     * @return true if equal, false otherwise
     */
    public static boolean constantTimeEquals(BigInteger a, BigInteger b) {
        if (a == null || b == null) {
            return a == b;
        }

        byte[] aBytes = a.toByteArray();
        byte[] bBytes = b.toByteArray();

        // Pad to same length if necessary
        int maxLength = Math.max(aBytes.length, bBytes.length);

        if (aBytes.length < maxLength) {
            byte[] padded = new byte[maxLength];
            System.arraycopy(aBytes, 0, padded, maxLength - aBytes.length, aBytes.length);
            aBytes = padded;
        }

        if (bBytes.length < maxLength) {
            byte[] padded = new byte[maxLength];
            System.arraycopy(bBytes, 0, padded, maxLength - bBytes.length, bBytes.length);
            bBytes = padded;
        }

        return constantTimeEquals(aBytes, bBytes);
    }

    /**
     * Compare byte arrays in constant-time with early exit on length mismatch
     *
     * This variant allows early exit on length mismatch for cases where
     * length differences are not sensitive.
     *
     * @param a First byte array
     * @param b Second byte array
     * @param allowLengthCheck If true, allow early exit on length mismatch
     * @return true if equal, false otherwise
     */
    public static boolean constantTimeEquals(byte[] a, byte[] b, boolean allowLengthCheck) {
        if (allowLengthCheck && (a == null || b == null || a.length != b.length)) {
            return false;
        }

        return constantTimeEquals(a, b);
    }

    /**
     * Securely wipe byte array contents
     *
     * This method overwrites the byte array with zeros to help
     * prevent sensitive data from remaining in memory.
     *
     * Note: This is a best-effort approach in Java and cannot guarantee
     * that the data is completely removed from memory due to JVM garbage
     * collection and potential copies.
     *
     * @param data Byte array to wipe
     */
    public static void secureWipe(byte[] data) {
        if (data != null) {
            // Overwrite with zeros multiple times
            for (int i = 0; i < 3; i++) {
                Arrays.fill(data, (byte) 0);
            }

            // Suggest garbage collection
            System.gc();
        }
    }

    /**
     * Securely wipe multiple byte arrays
     *
     * @param arrays Variable number of byte arrays to wipe
     */
    public static void secureWipeAll(byte[]... arrays) {
        if (arrays != null) {
            for (byte[] array : arrays) {
                secureWipe(array);
            }
        }
    }
}
```

### USO en `MuSig2.java`:

```java
import com.sparrowwallet.drongo.crypto.musig2.MuSig2Crypto;

// En métodos de verificación y comparación sensible:

public static boolean verify(
        SchnorrSignature sig,
        ECKey aggregatedKey,
        Sha256Hash message) {

    try {
        // ... cálculos de verificación ...

        // ✅ USO: Comparación constant-time para valores sensibles
        byte[] computedR = /* R calculado */;
        byte[] signatureR = sig.getR();

        if (!MuSig2Crypto.constantTimeEquals(computedR, signatureR)) {
            log.debug("Signature verification failed: R mismatch");
            return false;
        }

        BigInteger computedS = /* s calculado */;
        BigInteger signatureS = new BigInteger(1, sig.getS());

        if (!MuSig2Crypto.constantTimeEquals(computedS, signatureS)) {
            log.debug("Signature verification failed: s mismatch");
            return false;
        }

        return true;

    } catch (Exception e) {
        log.error("Error during signature verification", e);
        throw new RuntimeException("Signature verification failed", e);
    }
}
```

---

## 🔧 SNIPPET 4: Excepciones Personalizadas

### Archivo: `MuSig2Exception.java` (NUEVO)

```java
package com.sparrowwallet.drongo.crypto.musig2;

/**
 * MuSig2-specific exception
 *
 * This exception is thrown for errors specific to MuSig2 operations,
 * providing detailed error information with error codes for programmatic
 * handling.
 */
public class MuSig2Exception extends RuntimeException {

    private final String errorCode;
    private final String context;

    /**
     * Error codes for MuSig2 operations
     */
    public static class ErrorCodes {
        public static final String INVALID_PUBLIC_KEY = "INVALID_PUBLIC_KEY";
        public static final String INVALID_POINT = "INVALID_POINT";
        public static final String INVALID_X_COORDINATE = "INVALID_X_COORDINATE";
        public static final String INVALID_NONCE = "INVALID_NONCE";
        public static final String INVALID_SIGNATURE = "INVALID_SIGNATURE";
        public static final String KEY_AGGREGATION_FAILED = "KEY_AGGREGATION_FAILED";
        public static final String NONCE_GENERATION_FAILED = "NONCE_GENERATION_FAILED";
        public static final String SIGNING_FAILED = "SIGNING_FAILED";
        public static final String VERIFICATION_FAILED = "VERIFICATION_FAILED";
        public static final String INVALID_MESSAGE = "INVALID_MESSAGE";
    }

    /**
     * Construct a new MuSig2 exception
     *
     * @param message Error message
     * @param errorCode Error code from ErrorCodes class
     */
    public MuSig2Exception(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
        this.context = null;
    }

    /**
     * Construct a new MuSig2 exception with cause
     *
     * @param message Error message
     * @param errorCode Error code from ErrorCodes class
     * @param cause Underlying cause
     */
    public MuSig2Exception(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.context = null;
    }

    /**
     * Construct a new MuSig2 exception with context
     *
     * @param message Error message
     * @param errorCode Error code from ErrorCodes class
     * @param context Additional context information
     */
    public MuSig2Exception(String message, String errorCode, String context) {
        super(message + (context != null ? " [" + context + "]" : ""));
        this.errorCode = errorCode;
        this.context = context;
    }

    /**
     * Get the error code
     *
     * @return Error code string
     */
    public String getErrorCode() {
        return errorCode;
    }

    /**
     * Get additional context
     *
     * @return Context string or null if not set
     */
    public String getContext() {
        return context;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("MuSig2Exception[").append(errorCode).append("]: ");
        sb.append(getMessage());

        if (context != null) {
            sb.append(" (context: ").append(context).append(")");
        }

        return sb.toString();
    }
}
```

### USO en el código:

```java
import com.sparrowwallet.drongo.crypto.musig2.MuSig2Exception;
import com.sparrowwallet.drongo.crypto.musig2.MuSig2Exception.ErrorCodes;

// En lugar de:
throw new IllegalArgumentException("Invalid public key");

// Usar:
throw new MuSig2Exception(
    "Public key is not valid on secp256k1 curve",
    MuSig2Exception.ErrorCodes.INVALID_PUBLIC_KEY,
    "key index: " + i
);

// En catch blocks:
try {
    // ... operación criptográfica ...
} catch (Exception e) {
    log.error("Error during key aggregation", e);
    throw new MuSig2Exception(
        "Failed to aggregate public keys",
        MuSig2Exception.ErrorCodes.KEY_AGGREGATION_FAILED,
        e
    );
}
```

---

## 🔧 SNIPPET 5: Tests de Concurrency

### Archivo: `MuSig2ConcurrencyTest.java` (NUEVO)

```java
package com.sparrowwallet.drongo.crypto.musig2;

import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Concurrency tests for MuSig2
 *
 * These tests verify that MuSig2 operations are thread-safe
 * and can handle concurrent operations correctly.
 */
public class MuSig2ConcurrencyTest {

    @Test
    @DisplayName("Concurrent nonce generation should be thread-safe")
    public void testConcurrentNonceGeneration() throws Exception {
        int numThreads = 20;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    ECKey signer = new ECKey();
                    List<ECKey> pubKeys = Arrays.asList(
                        ECKey.fromPublicOnly(signer.getPubKey())
                    );
                    Sha256Hash message = Sha256Hash.twiceOf(
                        ("Thread " + threadId).getBytes()
                    );

                    MuSig2.CompleteNonce nonce = MuSig2.generateRound1Nonce(
                        signer, pubKeys, message
                    );

                    assertNotNull(nonce, "Nonce should not be null");
                    assertNotNull(nonce.getPublicNonce(), "Public nonce should not be null");
                    assertNotNull(nonce.getSecretNonce(), "Secret nonce should not be null");

                    successCount.incrementAndGet();

                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(30, TimeUnit.SECONDS);
        long duration = System.currentTimeMillis() - startTime;

        executor.shutdown();

        assertTrue(completed, "All threads should complete within timeout");
        assertEquals(numThreads, successCount.get(), "All threads should succeed");
        assertEquals(0, failureCount.get(), "No threads should fail");

        System.out.println("Completed " + numThreads + " concurrent nonce generations in " +
                          duration + "ms");
    }

    @Test
    @DisplayName("Concurrent key aggregation should be thread-safe")
    public void testConcurrentKeyAggregation() throws Exception {
        int numThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    List<ECKey> keys = new ArrayList<>();
                    for (int j = 0; j < 3; j++) {
                        keys.add(new ECKey());
                    }

                    MuSig2Core.KeyAggContext ctx = MuSig2Core.aggregatePublicKeys(keys);

                    assertNotNull(ctx, "KeyAggContext should not be null");
                    assertNotNull(ctx.getQ(), "Aggregated key should not be null");

                    successCount.incrementAndGet();

                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "All threads should complete");
        assertEquals(numThreads, successCount.get(), "All aggregations should succeed");
    }

    @Test
    @DisplayName("Concurrent signing operations should be thread-safe")
    public void testConcurrentSigning() throws Exception {
        int numThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    ECKey signer1 = new ECKey();
                    ECKey signer2 = new ECKey();

                    Sha256Hash message = Sha256Hash.twiceOf(
                        ("Thread " + threadId).getBytes()
                    );

                    SchnorrSignature sig = MuSig2.sign2of2(signer1, signer2, message);

                    assertNotNull(sig, "Signature should not be null");
                    assertNotNull(sig.getData(), "Signature data should not be null");

                    successCount.incrementAndGet();

                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "All threads should complete");
        assertEquals(numThreads, successCount.get(), "All signing operations should succeed");
    }
}
```

---

## 📋 INSTRUCCIONES DE USO

### Paso 1: Backup
```bash
cd /home/r2d2/Desarrollo/SparrowDev/sparrow
git checkout -b feature/musig2-security-fixes
git commit -am "Backup before security fixes"
```

### Paso 2: Aplicar snippets en orden
1. ✅ Snippet 1: SecureRandom
2. ✅ Snippet 2: Validación de puntos
3. ✅ Snippet 3: Constant-time
4. ✅ Snippet 4: Excepciones
5. ✅ Snippet 5: Tests

### Paso 3: Test
```bash
./gradlew clean compileJava
./gradlew drongo:test --tests "*MuSig2*"
```

### Paso 4: Verificar
```bash
# Todos los tests deben pasar
./gradlew drongo:test

# Ver 43 tests + nuevos tests de concurrencia
```

---

**Snippets creados:** 2025-12-31
**Listos para:** Copiar/Pegar
**Estado:** ✅ Completados

*Fin de snippets*
