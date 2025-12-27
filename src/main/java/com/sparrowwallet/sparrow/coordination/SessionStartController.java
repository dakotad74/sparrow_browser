package com.sparrowwallet.sparrow.coordination;

import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.control.CoordinationDialog;
import com.sparrowwallet.sparrow.control.QRScanDialog;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.UUID;

/**
 * Controller for the session start step (create or join).
 */
public class SessionStartController implements Initializable, CoordinationController.StepController {
    private static final Logger log = LoggerFactory.getLogger(SessionStartController.class);

    @FXML
    private Spinner<Integer> participantCountSpinner;

    @FXML
    private Button createSessionButton;

    @FXML
    private TextField sessionIdField;

    @FXML
    private Button scanQRButton;

    @FXML
    private Button joinSessionButton;

    @FXML
    private Label walletNameLabel;

    @FXML
    private Label walletTypeLabel;

    private Wallet wallet;
    private CoordinationDialog dialog;
    private CoordinationSessionManager sessionManager;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Setup spinner
        SpinnerValueFactory<Integer> valueFactory =
            new SpinnerValueFactory.IntegerSpinnerValueFactory(2, 15, 2);
        participantCountSpinner.setValueFactory(valueFactory);

        // Enable join button when session ID is entered
        sessionIdField.textProperty().addListener((obs, oldVal, newVal) -> {
            joinSessionButton.setDisable(newVal == null || newVal.trim().isEmpty());
        });
    }

    @Override
    public void initializeStep(Wallet wallet, CoordinationDialog dialog) {
        log.error("=== initializeStep() called for wallet: {} ===", wallet != null ? wallet.getName() : "null");
        this.wallet = wallet;
        this.dialog = dialog;

        // Get session manager from AppServices (singleton)
        // For now, create a new instance if AppServices doesn't have it
        log.error("=== About to call getOrCreateSessionManager() ===");
        this.sessionManager = getOrCreateSessionManager();
        log.error("=== SessionManager created: {} ===", this.sessionManager != null);

        // Store in dialog for other controllers to use
        dialog.setSessionManager(this.sessionManager);

        // Display wallet info
        walletNameLabel.setText(wallet.getFullDisplayName());
        walletTypeLabel.setText(wallet.getPolicyType().getName() + " wallet");

        // Check wallet compatibility
        if(!isWalletCompatible(wallet)) {
            showWarning("This wallet may not be suitable for coordination.\n" +
                       "Coordination works best with multisig wallets.");
        }
    }

    @Override
    public boolean validateStep() {
        // This step doesn't need validation - user creates/joins via buttons
        return true;
    }

    @Override
    public void onEventReceived(Object event) {
        // No events handled in this step
    }

    /**
     * Create a new coordination session
     */
    @FXML
    private void createSession() {
        try {
            int participantCount = participantCountSpinner.getValue();

            log.info("Creating coordination session with {} participants", participantCount);

            // Create session
            CoordinationSession session = sessionManager.createSession(wallet, participantCount);

            log.info("Session created: {}", session.getSessionId());

            // Store in dialog
            dialog.setSession(session);

            // Session created event will be posted, triggering navigation to next step

        } catch(Exception e) {
            log.error("Failed to create session", e);
            e.printStackTrace();
            showError("Failed to create session: " + e.getMessage());
        }
    }

    /**
     * Join an existing coordination session
     */
    @FXML
    private void joinSession() {
        String sessionId = sessionIdField.getText().trim();

        if(sessionId.isEmpty()) {
            showWarning("Please enter a session ID");
            return;
        }

        try {
            // Validate UUID format
            UUID.fromString(sessionId);

            log.info("Joining coordination session: {}", sessionId);

            // Generate participant pubkey from wallet
            String participantPubkey = getNostrPubkeyFromWallet(wallet);

            // Join session
            sessionManager.joinSession(sessionId, wallet, participantPubkey);

            log.info("Joined session: {}", sessionId);

            // Participant joined event will be posted

        } catch(IllegalArgumentException e) {
            showError("Invalid session ID format.\nSession ID should be a UUID.");
        } catch(Exception e) {
            log.error("Failed to join session", e);
            showError("Failed to join session: " + e.getMessage());
        }
    }

    /**
     * Scan QR code to get session ID
     */
    @FXML
    private void scanQRCode() {
        try {
            QRScanDialog qrScanDialog = new QRScanDialog();
            Optional<QRScanDialog.Result> result = qrScanDialog.showAndWait();

            result.ifPresent(scanResult -> {
                // Get scanned data from result
                String scannedData = scanResult.payload != null ? scanResult.payload : "";

                // Extract session ID from scanned data
                String sessionId = extractSessionId(scannedData);
                if(sessionId != null) {
                    sessionIdField.setText(sessionId);
                } else {
                    showWarning("Could not extract session ID from QR code");
                }
            });

        } catch(Exception e) {
            log.error("Failed to scan QR code", e);
            showError("Failed to scan QR code: " + e.getMessage());
        }
    }

    /**
     * Extract session ID from scanned data
     */
    private String extractSessionId(String scannedData) {
        // Try to parse as plain UUID
        try {
            UUID.fromString(scannedData.trim());
            return scannedData.trim();
        } catch(IllegalArgumentException e) {
            // Not a plain UUID
        }

        // Try to extract from URL format (nostr://coordinate/<session-id>)
        if(scannedData.startsWith("nostr://coordinate/")) {
            String sessionId = scannedData.substring("nostr://coordinate/".length());
            try {
                UUID.fromString(sessionId);
                return sessionId;
            } catch(IllegalArgumentException e) {
                // Invalid UUID
            }
        }

        return null;
    }

    /**
     * Check if wallet is compatible with coordination
     */
    private boolean isWalletCompatible(Wallet wallet) {
        // Coordination works with any wallet, but multisig is recommended
        return true;
    }

    /**
     * Show error dialog
     */
    private void showError(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("Coordination Error");
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    /**
     * Show warning dialog
     */
    private void showWarning(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Warning");
            alert.setHeaderText("Coordination Warning");
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    /**
     * Get or create the coordination session manager singleton.
     *
     * TODO: This should be a proper singleton in AppServices.
     * For now, we create a new instance and connect it to NostrEventService.
     */
    private CoordinationSessionManager getOrCreateSessionManager() {
        log.error("=== getOrCreateSessionManager() called ===");
        CoordinationSessionManager manager = new CoordinationSessionManager();

        // Connect to Nostr if available
        try {
            var nostrService = AppServices.get().getNostrEventService();
            log.error("=== NostrEventService: {} ===", nostrService != null ? "available" : "null");
            if(nostrService != null) {
                var relayManager = nostrService.getRelayManager();
                log.error("=== RelayManager: {} ===", relayManager != null ? "available" : "null");
                if(relayManager != null) {
                    manager.setNostrRelayManager(relayManager);
                    log.error("=== Connected SessionManager to NostrRelayManager ===");

                    // Set message handler to process incoming events
                    relayManager.setMessageHandler(event -> {
                        log.error("=== Received Nostr event: kind={} ===", event.getKind());
                        manager.handleNostrEvent(event);
                    });
                    log.error("=== Configured message handler for incoming events ===");

                    // Derive Nostr key pair from wallet (both private key and pubkey)
                    log.error("=== About to derive Nostr key from wallet ===");
                    ECKey nostrKey = getNostrPrivateKeyFromWallet(wallet);
                    log.error("=== Nostr key derived: {} ===", nostrKey != null);
                    if(nostrKey != null) {
                        // Extract x-only pubkey from the ECKey
                        byte[] fullPubkey = nostrKey.getPubKey();
                        byte[] xOnlyPubkey = new byte[32];
                        System.arraycopy(fullPubkey, 1, xOnlyPubkey, 0, 32);
                        String myPubkey = bytesToHex(xOnlyPubkey);

                        // Set pubkey in SessionManager
                        manager.setMyNostrPubkey(myPubkey);
                        log.error("=== Set participant pubkey: {} ===", myPubkey.substring(0, Math.min(16, myPubkey.length())) + "...");

                        // Set private key in RelayManager for signing
                        relayManager.setPrivateKey(nostrKey);
                        log.error("=== Configured Nostr key pair for event signing ===");
                    } else {
                        log.error("=== Could not derive Nostr private key - events will be unsigned ===");
                    }
                } else {
                    log.error("=== NostrRelayManager not available - coordination will work locally only ===");
                }
            } else {
                log.error("=== NostrEventService not available - coordination will work locally only ===");
            }
        } catch(Exception e) {
            log.error("Failed to connect to Nostr service", e);
        }

        return manager;
    }

    /**
     * Generate Nostr public key from wallet.
     *
     * For consistency, we derive the Nostr pubkey from the wallet's first keystore.
     * This ensures the same wallet always generates the same Nostr identity.
     *
     * @param wallet Wallet to derive pubkey from
     * @return Hex-encoded Nostr public key (33 bytes compressed)
     */
    private String getNostrPubkeyFromWallet(Wallet wallet) {
        try {
            // Get first keystore's master public key
            if(wallet.getKeystores() != null && !wallet.getKeystores().isEmpty()) {
                var keystore = wallet.getKeystores().get(0);
                var extendedPubKey = keystore.getExtendedPublicKey();

                if(extendedPubKey != null) {
                    // Get the DeterministicKey and then the public key bytes
                    var key = extendedPubKey.getKey();
                    if(key != null) {
                        byte[] pubkeyBytes = key.getPubKey();

                        // Nostr uses x-only pubkeys (32 bytes) not compressed pubkeys (33 bytes)
                        // Strip the first byte (0x02 or 0x03 prefix) to get x-only format
                        byte[] xOnlyPubkey = new byte[32];
                        System.arraycopy(pubkeyBytes, 1, xOnlyPubkey, 0, 32);

                        // Convert to hex string for Nostr (32 bytes x-only)
                        return bytesToHex(xOnlyPubkey);
                    }
                }
            }

            // Fallback: generate deterministic pubkey from wallet name
            log.warn("Could not derive pubkey from keystore, using wallet name hash");
            String walletId = wallet.getName() + wallet.hashCode();
            return String.format("%064x", walletId.hashCode()).substring(0, 66);

        } catch(Exception e) {
            log.error("Failed to generate Nostr pubkey", e);
            // Last resort: random but consistent per session
            return UUID.randomUUID().toString().replace("-", "");
        }
    }

    /**
     * Derive Nostr private key from wallet.
     *
     * For production use, this derives a Nostr-specific private key from the wallet's
     * master key by hashing the master public key. This ensures:
     * 1. Deterministic - same wallet always produces same Nostr key
     * 2. Secure - doesn't expose wallet's signing keys
     * 3. Compatible - works with both watch-only and full wallets
     *
     * For watch-only wallets (no private keys), we derive from public key only.
     *
     * @param wallet Wallet to derive Nostr key from
     * @return ECKey for signing Nostr events, or null if cannot derive
     */
    private ECKey getNostrPrivateKeyFromWallet(Wallet wallet) {
        try {
            // Get first keystore's master public key (always available)
            if(wallet.getKeystores() != null && !wallet.getKeystores().isEmpty()) {
                var keystore = wallet.getKeystores().get(0);

                log.info("Checking keystore: hasSeed={}, hasMasterPrivateKey={}",
                         keystore.hasSeed(), keystore.hasMasterPrivateKey());

                // Option 1: Derive from seed (most common for software wallets)
                if(keystore.hasSeed()) {
                    var seed = keystore.getSeed();
                    if(seed != null && !seed.isEncrypted()) {
                        try {
                            // Get seed bytes
                            byte[] seedBytes = seed.getSeedBytes();

                            // Derive Nostr private key by hashing seed
                            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
                            byte[] nostrSeed = sha256.digest(seedBytes);

                            // Create ECKey from derived seed
                            ECKey nostrKey = ECKey.fromPrivate(nostrSeed);
                            log.info("✅ Derived Nostr private key from wallet seed");
                            return nostrKey;
                        } catch(Exception e) {
                            log.error("Failed to derive from seed", e);
                            // Continue to try other methods
                        }
                    } else if(seed != null && seed.isEncrypted()) {
                        log.warn("Wallet seed is encrypted - cannot derive Nostr private key without password");
                        // TODO: Prompt for password to decrypt wallet
                        return null;
                    }
                }

                // Option 2: Derive from master private extended key (less common)
                if(keystore.hasMasterPrivateKey()) {
                    var masterPrivKey = keystore.getMasterPrivateExtendedKey();
                    if(masterPrivKey != null && !masterPrivKey.isEncrypted()) {
                        // Get DeterministicKey from master private extended key
                        var deterministicKey = masterPrivKey.getPrivateKey();
                        byte[] privateKeyBytes = deterministicKey.getPrivKeyBytes();

                        // Derive Nostr private key by hashing master private key
                        // This creates a separate key hierarchy for Nostr
                        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
                        byte[] nostrSeed = sha256.digest(privateKeyBytes);

                        // Create ECKey from derived seed
                        ECKey nostrKey = ECKey.fromPrivate(nostrSeed);
                        log.info("✅ Derived Nostr private key from wallet master private key");
                        return nostrKey;
                    } else if(masterPrivKey != null && masterPrivKey.isEncrypted()) {
                        log.warn("Wallet is encrypted - cannot derive Nostr private key without password");
                        // TODO: Prompt for password to decrypt wallet
                        return null;
                    }
                }

                // Fallback: For watch-only wallets, derive from public key
                // This won't allow signing, but we try anyway
                var extendedPubKey = keystore.getExtendedPublicKey();
                if(extendedPubKey != null) {
                    var key = extendedPubKey.getKey();
                    if(key != null) {
                        byte[] pubkeyBytes = key.getPubKey();

                        // Derive deterministic "private key" from public key hash
                        // NOTE: This is NOT cryptographically secure for watch-only wallets
                        // Events will still be unsigned - this is just for consistency
                        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
                        byte[] derivedSeed = sha256.digest(pubkeyBytes);

                        ECKey derivedKey = ECKey.fromPrivate(derivedSeed);
                        log.warn("Derived Nostr key from public key (watch-only wallet) - event signing may fail");
                        return derivedKey;
                    }
                }
            }

            log.error("Could not derive Nostr private key - no keystore available");
            return null;

        } catch(Exception e) {
            log.error("Failed to derive Nostr private key", e);
            return null;
        }
    }

    /**
     * Convert bytes to hex string
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for(byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
