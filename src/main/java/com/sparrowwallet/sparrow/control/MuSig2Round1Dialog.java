package com.sparrowwallet.sparrow.control;

import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.SecureString;
import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.crypto.musig2.MuSig2;
import com.sparrowwallet.drongo.crypto.musig2.MuSig2.CompleteNonce;
import com.sparrowwallet.drongo.crypto.musig2.MuSig2Core;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.psbt.PSBTInput;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.protocol.SigHash;
import com.sparrowwallet.drongo.KeyDerivation;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;
import com.sparrowwallet.drongo.ExtendedKey;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.MuSig2Round1Event;
import com.sparrowwallet.sparrow.event.PSBTSignedEvent;
import com.sparrowwallet.sparrow.io.Storage;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Dialog for MuSig2 Round 1: Nonce generation and exchange.
 *
 * This dialog handles the first round of MuSig2 signing where:
 * 1. Each signer generates their public nonce
 * 2. Nonces are exchanged between all signers
 * 3. All nonces are combined for the next round
 */
public class MuSig2Round1Dialog extends Dialog<MuSig2Round1Dialog.MuSig2Round1Data> {
    private static final Logger log = LoggerFactory.getLogger(MuSig2Round1Dialog.class);

    private final Wallet wallet;
    private final PSBT psbt;
    private final Map<Integer, CompleteNonce> myCompleteNonces;
    private final Map<Integer, byte[]> messages;
    private final Map<Integer, List<ECKey>> inputPublicKeys;

    private TextArea myNoncesArea;
    private TextArea otherNoncesArea;
    private Label statusLabel;
    private Button continueButton;

    public MuSig2Round1Dialog(Wallet wallet, PSBT psbt) {
        log.error("=== MuSig2Round1Dialog CONSTRUCTOR called for wallet: " + wallet.getName() + " ===");
        this.wallet = wallet;
        this.psbt = psbt;
        this.myCompleteNonces = new HashMap<>();
        this.messages = new HashMap<>();
        this.inputPublicKeys = new HashMap<>();

        EventManager.get().register(this);
        setResultConverter(dialogButton -> dialogButton.getButtonData().isCancelButton() ? null : getRound1Data());
        initDialog();
    }

    private void initDialog() {
        setTitle("MuSig2 Round 1 - Nonce Exchange");
        getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL);
        getDialogPane().setPrefWidth(600);

        VBox content = new VBox(10);
        content.setPadding(new Insets(15));

        // Instructions
        Label instructionsLabel = new Label(
            "MuSig2 requires two rounds of signing. In Round 1, each signer generates a public nonce.\n\n" +
            "1. Click 'Generate My Nonces' to create your nonces\n" +
            "2. Share your nonces with other signers (via QR code, text, or file)\n" +
            "3. Enter nonces from other signers (one per line: input_index:nonce_hex)\n" +
            "   Nonce format: 132 hex characters (66 bytes = pubKey1 + pubKey2)\n" +
            "4. Click 'Continue to Round 2' when all nonces are collected"
        );
        instructionsLabel.setWrapText(true);
        content.getChildren().add(instructionsLabel);

        // Generate button
        Button generateButton = new Button("Generate My Nonces");
        generateButton.setMaxWidth(Double.MAX_VALUE);
        generateButton.setOnAction(e -> {
            log.error("=== Generate My Nonces BUTTON CLICKED ===");
            generateMyNonces();
        });
        content.getChildren().add(generateButton);

        // My nonces display
        VBox myNoncesBox = new VBox(5);
        myNoncesBox.getChildren().add(new Label("My Public Nonces (R value - share with other signers):"));
        myNoncesArea = new TextArea();
        myNoncesArea.setEditable(false);
        myNoncesArea.setPrefRowCount(4);
        myNoncesBox.getChildren().add(myNoncesArea);
        content.getChildren().add(myNoncesBox);

        // Other nonces input
        VBox otherNoncesBox = new VBox(5);
        otherNoncesBox.getChildren().add(new Label("Enter nonces from other signers (one per line: input_index,R_hex):"));
        otherNoncesArea = new TextArea();
        otherNoncesArea.setPrefRowCount(4);
        otherNoncesArea.setPromptText("Example:\n0:02abcd1234567890abcdef...\n1:04fedcba0987654321fedcba...");
        otherNoncesBox.getChildren().add(otherNoncesArea);
        content.getChildren().add(otherNoncesBox);

        // Status
        statusLabel = new Label("Click 'Generate My Nonces' to begin");
        statusLabel.setStyle("-fx-text-fill: #666;");
        content.getChildren().add(statusLabel);

        // Continue button
        continueButton = new Button("Continue to Round 2");
        continueButton.setMaxWidth(Double.MAX_VALUE);
        continueButton.setDisable(true);
        continueButton.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        continueButton.setOnAction(e -> {
            MuSig2Round1Data data = getRound1Data();
            if(data != null) {
                setResult(data);
                close();
            }
        });
        content.getChildren().add(continueButton);

        getDialogPane().setContent(content);
    }

    private void generateMyNonces() {
        log.error("=== generateMyNonces called, wallet: " + wallet.getName() + ", encrypted: " + wallet.isEncrypted() + " ===");
        try {
            // Check if wallet is encrypted
            if(wallet.isEncrypted()) {
                // Wallet is encrypted - need to decrypt first
                log.info("Wallet is encrypted, prompting for password...");
                statusLabel.setText("Wallet is encrypted. Please enter password to decrypt.");
                statusLabel.setStyle("-fx-text-fill: #f39c12;");

                // Prompt for password and decrypt wallet
                decryptWalletAndGenerateNonces();
                return;
            }

            // Wallet is not encrypted, proceed directly
            generateNoncesWithWallet(wallet);

        } catch(Exception e) {
            log.error("Error generating MuSig2 nonces", e);
            statusLabel.setText("Error: " + e.getMessage());
            statusLabel.setStyle("-fx-text-fill: #c0392b;");
        }
    }

    private void decryptWalletAndGenerateNonces() {
        try {
            Storage storage = AppServices.get().getOpenWallets().get(wallet);

            // Create password dialog
            WalletPasswordDialog dlg = new WalletPasswordDialog(wallet.getMasterName(), WalletPasswordDialog.PasswordRequirement.LOAD);
            dlg.initOwner(myNoncesArea.getScene().getWindow());
            Optional<SecureString> password = dlg.showAndWait();

            if(password.isEmpty()) {
                statusLabel.setText("Password entry cancelled.");
                statusLabel.setStyle("-fx-text-fill: #c0392b;");
                return;
            }

            SecureString passwordEntry = password.get();
            if(passwordEntry == null || passwordEntry.isEmpty()) {
                statusLabel.setText("Password is required.");
                statusLabel.setStyle("-fx-text-fill: #c0392b;");
                return;
            }

            // Decrypt wallet using background service
            Storage.DecryptWalletService decryptWalletService =
                new Storage.DecryptWalletService(
                    wallet.copy(),
                    passwordEntry
                );

            decryptWalletService.setOnSucceeded(workerStateEvent -> {
                try {
                    Wallet decryptedWallet = decryptWalletService.getValue();
                    log.info("Wallet decrypted successfully");

                    // Generate nonces with decrypted wallet
                    generateNoncesWithWallet(decryptedWallet);

                    // IMPORTANT: Clear private keys from decrypted wallet after use
                    decryptedWallet.clearPrivate();
                    log.info("Cleared private keys from decrypted wallet");

                } catch(Exception e) {
                    log.error("Error generating nonces with decrypted wallet", e);
                    Platform.runLater(() -> {
                        statusLabel.setText("Error: " + e.getMessage());
                        statusLabel.setStyle("-fx-text-fill: #c0392b;");
                    });
                }
            });

            decryptWalletService.setOnFailed(workerStateEvent -> {
                log.error("Failed to decrypt wallet", decryptWalletService.getException());
                Platform.runLater(() -> {
                    statusLabel.setText("Decryption failed: " + decryptWalletService.getException().getMessage());
                    statusLabel.setStyle("-fx-text-fill: #c0392b;");
                });
            });

            // Start decryption in background
            EventManager.get().post(
                new com.sparrowwallet.sparrow.event.StorageEvent(
                    storage.getWalletId(wallet),
                    com.sparrowwallet.sparrow.event.TimedEvent.Action.START,
                    "Decrypting wallet..."
                )
            );
            decryptWalletService.start();

        } catch(Exception e) {
            log.error("Error in decryptWalletAndGenerateNonces", e);
            Platform.runLater(() -> {
                statusLabel.setText("Error: " + e.getMessage());
                statusLabel.setStyle("-fx-text-fill: #c0392b;");
            });
        }
    }

    private void generateNoncesWithWallet(Wallet signingWallet) {
        log.error("=== generateNoncesWithWallet called for wallet: " + signingWallet.getName() + " ===");

        // Get signing keystores (with fallback for MuSig2) - ONCE for the whole method
        Collection<Keystore> signingKeystores = signingWallet.getSigningKeystores(psbt);
        if(signingKeystores.isEmpty() && signingWallet.getPolicyType() == PolicyType.MUSIG2) {
            log.error("getSigningKeystores empty, falling back to keystores with private keys");
            for(Keystore k : signingWallet.getKeystores()) {
                if(k.hasPrivateKey()) {
                    signingKeystores.add(k);
                }
            }
        }

        if(signingKeystores.isEmpty()) {
            statusLabel.setText("Error: No signing keystores found");
            statusLabel.setStyle("-fx-text-fill: #c0392b;");
            return;
        }

        try {
            // Generate nonces for each input
            StringBuilder nonceDisplay = new StringBuilder();
            int inputCount = 0;

            Map<PSBTInput, WalletNode> signingNodes = signingWallet.getSigningNodes(psbt);
            log.error("Signing nodes found: " + signingNodes.size());
            if(signingNodes.isEmpty()) {
                log.error("No signing nodes found for PSBT!");
                statusLabel.setText("Error: No signing nodes found - check wallet has funds");
                statusLabel.setStyle("-fx-text-fill: #c0392b;");
                return;
            }

            for(Map.Entry<PSBTInput, WalletNode> inputEntry : signingNodes.entrySet()) {
                PSBTInput input = inputEntry.getKey();
                WalletNode walletNode = inputEntry.getValue();

                // Get all public keys for MuSig2 - these are the DERIVED keys for this input
                // IMPORTANT: Use ALL keystores from the wallet, not just those with private keys
                // This is because MuSig2 needs public keys of ALL signers, not just the current signer
                List<ECKey> publicKeys = new ArrayList<>();

                // Derive public keys for this specific input (walletNode) from ALL wallet keystores
                for(Keystore keystore : signingWallet.getKeystores()) {
                    try {
                        ECKey derivedKey = null;
                        if(keystore.hasPrivateKey()) {
                            // For keystores with private key, derive normally
                            derivedKey = keystore.getKey(walletNode);
                        } else {
                            // For watch-only keystores, use getPubKey which derives from extended public key
                            derivedKey = keystore.getPubKey(walletNode);
                        }

                        if(derivedKey != null) {
                            // Get the public key of the derived key
                            ECKey pubKey = ECKey.fromPublicOnly(derivedKey.getPubKey());
                            publicKeys.add(pubKey);
                            log.error("Added derived public key for keystore: " + keystore.getLabel());
                        }
                    } catch(Exception e) {
                        log.error("Could not derive public key for keystore " + keystore.getLabel() + ": " + e.getMessage());
                    }
                }

                if(publicKeys.isEmpty()) {
                    log.error("No public keys found for input " + inputCount);
                    inputCount++;
                    continue;
                }

                // CRITICAL: Sort public keys before MuSig2 operations
                // BIP-327 requires keys to be sorted lexicographically to ensure
                // all signers use the same key order regardless of wallet configuration
                MuSig2Core.sortPublicKeys(publicKeys);
                log.info("Public keys sorted for MuSig2: " + publicKeys.size() + " keys");

                // Get sighash for this input
                // For Taproot MuSig2, we need the taproot signature hash
                try {
                    // Use reflection to access private getHashForSignature method
                    // This is needed because PSBTInput.getHashForSignature() is private
                    java.lang.reflect.Method method = PSBTInput.class.getDeclaredMethod(
                        "getHashForSignature",
                        com.sparrowwallet.drongo.protocol.Script.class,
                        SigHash.class
                    );
                    method.setAccessible(true);

                    com.sparrowwallet.drongo.protocol.Script signingScript = input.getSigningScript();
                    SigHash sigHashType =
                        input.getSigHash() != null ? input.getSigHash() : SigHash.DEFAULT;

                    Sha256Hash message = (Sha256Hash)method.invoke(input, signingScript, sigHashType);
                    byte[] sigHash = message.getBytes();

                    // Get private key for this specific wallet node
                    log.error("Wallet keystores: " + signingWallet.getKeystores().size());
                    for(Keystore k : signingWallet.getKeystores()) {
                        log.error("  Keystore: " + k.getLabel() + " xpub: " + k.getExtendedPublicKey());
                    }
                    log.error("PSBT xpubs: " + psbt.getExtendedPublicKeys().size());
                    for(Map.Entry<ExtendedKey, KeyDerivation> e : psbt.getExtendedPublicKeys().entrySet()) {
                        log.error("  PSBT xpub: " + e.getKey() + " der: " + e.getValue());
                    }
                    log.error("PSBT inputs: " + psbt.getPsbtInputs().size());
                    for(PSBTInput inp : psbt.getPsbtInputs()) {
                        log.error("  Input derived keys: " + inp.getDerivedPublicKeys().size());
                        for(Map.Entry<ECKey, KeyDerivation> e : inp.getDerivedPublicKeys().entrySet()) {
                            log.error("    Derived: " + e.getKey() + " der: " + e.getValue());
                        }
                    }

                    // Get the first signing keystore's private key for this wallet node
                    // Use the signingKeystores obtained at the beginning of the method
                    ECKey privKey = null;
                    for(Keystore signingKeystore : signingKeystores) {
                        try {
                            privKey = signingKeystore.getKey(walletNode);
                            if(privKey != null && privKey.hasPrivKey()) {
                                log.info("Obtained private key from keystore: " + signingKeystore.getLabel());
                                break;
                            }
                        } catch(Exception e) {
                            log.debug("Could not get key from keystore " + signingKeystore.getLabel() + ": " + e.getMessage());
                        }
                    }

                    if(privKey == null || !privKey.hasPrivKey()) {
                        log.error("Could not obtain private key for wallet node");
                        statusLabel.setText("Error: Could not obtain private key for signing");
                        statusLabel.setStyle("-fx-text-fill: #c0392b;");
                        return;
                    }

                    // Generate Round 1 nonce using MuSig2 API
                    CompleteNonce completeNonce = MuSig2.generateRound1Nonce(privKey, publicKeys, message);

                    myCompleteNonces.put(inputCount, completeNonce);
                    messages.put(inputCount, sigHash);
                    inputPublicKeys.put(inputCount, publicKeys);

                    // Display nonce as combined pubKey1 + pubKey2 (66 bytes total = 33 + 33 compressed points)
                    // This allows proper reconstruction in Round 2
                    byte[] pubKey1 = completeNonce.getPublicNonce().getPublicKey1();
                    byte[] pubKey2 = completeNonce.getPublicNonce().getPublicKey2();
                    byte[] combined = new byte[66];
                    System.arraycopy(pubKey1, 0, combined, 0, 33);
                    System.arraycopy(pubKey2, 0, combined, 33, 33);
                    String nonceHex = bytesToHex(combined);
                    nonceDisplay.append(inputCount).append(":").append(nonceHex).append("\n");

                    inputCount++;
                } catch(Exception e) {
                    log.error("Error generating nonce for input", e);
                    continue;
                }
            }

            myNoncesArea.setText(nonceDisplay.toString());
            statusLabel.setText("Nonces generated! Share your R values with other signers, then enter their R values below.");
            statusLabel.setStyle("-fx-text-fill: #009670;");

            // Enable continue button when other nonces are entered
            otherNoncesArea.textProperty().addListener((obs, oldVal, newVal) -> {
                continueButton.setDisable(!hasRequiredNonces());
            });

        } catch(Exception e) {
            log.error("Error in generateNoncesWithWallet", e);
            statusLabel.setText("Error: " + e.getMessage());
            statusLabel.setStyle("-fx-text-fill: #c0392b;");
        }
    }

    private boolean hasRequiredNonces() {
        // Check if user has entered nonces from other signers
        String text = otherNoncesArea.getText().trim();
        log.error("hasRequiredNonces called, text length: " + text.length() + ", empty: " + text.isEmpty());
        if(text.isEmpty()) {
            return false;
        }

        // Validate format: input_index:nonce_hex (132 hex chars = 66 bytes = pubKey1 + pubKey2, both compressed)
        String[] lines = text.split("\n");
        log.error("Lines count: " + lines.length);
        for(String line : lines) {
            String trimmed = line.trim();
            log.error("Checking line: '" + trimmed + "', length: " + trimmed.length());
            boolean matches = trimmed.matches("^\\d+:[0-9a-fA-F]{132}$");
            log.error("Matches regex: " + matches);
            if(!matches) {
                statusLabel.setText("Invalid nonce format. Use: input_index:nonce_hex (132 hex chars)");
                statusLabel.setStyle("-fx-text-fill: #c0392b;");
                return false;
            }
        }

        return true;
    }

    private MuSig2Round1Data getRound1Data() {
        try {
            // Changed to support multiple nonces per input (one from each signer)
            Map<Integer, List<MuSig2.MuSig2Nonce>> allNonces = new HashMap<>();

            // Add my nonces first
            for(Map.Entry<Integer, CompleteNonce> entry : myCompleteNonces.entrySet()) {
                int inputIndex = entry.getKey();
                List<MuSig2.MuSig2Nonce> nonceList = new ArrayList<>();
                nonceList.add(entry.getValue().getPublicNonce());
                allNonces.put(inputIndex, nonceList);
            }

            // Parse other nonces from text area and ADD to the list (don't replace)
            String text = otherNoncesArea.getText().trim();
            String[] lines = text.split("\n");
            for(String line : lines) {
                String[] parts = line.trim().split(":");
                if(parts.length == 2) {
                    int inputIndex = Integer.parseInt(parts[0]);
                    byte[] combined = hexToBytes(parts[1]); // 66 bytes = pubKey1 (33) + pubKey2 (33)
                    if(combined.length == 66) {
                        // Split into pubKey1 and pubKey2 (33 bytes each, compressed points)
                        byte[] pubKey1 = new byte[33];
                        byte[] pubKey2 = new byte[33];
                        System.arraycopy(combined, 0, pubKey1, 0, 33);
                        System.arraycopy(combined, 33, pubKey2, 0, 33);
                        // Create MuSig2Nonce with both components
                        MuSig2.MuSig2Nonce nonce = new MuSig2.MuSig2Nonce(pubKey1, pubKey2);
                        // Add to existing list instead of replacing
                        allNonces.computeIfAbsent(inputIndex, k -> new ArrayList<>()).add(nonce);
                    }
                }
            }

            return new MuSig2Round1Data(allNonces, messages, inputPublicKeys, myCompleteNonces);

        } catch(Exception e) {
            log.error("Error parsing other nonces", e);
            statusLabel.setText("Error parsing nonces: " + e.getMessage());
            statusLabel.setStyle("-fx-text-fill: #c0392b;");
            return null;
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for(byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for(int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    @Subscribe
    public void psbtSigned(PSBTSignedEvent event) {
        if(psbt == event.getPsbt()) {
            // PSBT was signed through other means, close this dialog
            Platform.runLater(() -> close());
        }
    }

    /**
     * Data class containing the results of Round 1
     */
    public static class MuSig2Round1Data {
        // Changed to support multiple nonces per input (one from each signer)
        private final Map<Integer, List<MuSig2.MuSig2Nonce>> allPublicNonces;
        private final Map<Integer, byte[]> messages;
        private final Map<Integer, List<ECKey>> inputPublicKeys;
        private final Map<Integer, CompleteNonce> myCompleteNonces;

        public MuSig2Round1Data(Map<Integer, List<MuSig2.MuSig2Nonce>> allPublicNonces,
                                  Map<Integer, byte[]> messages,
                                  Map<Integer, List<ECKey>> inputPublicKeys,
                                  Map<Integer, CompleteNonce> myCompleteNonces) {
            this.allPublicNonces = allPublicNonces != null ? allPublicNonces : new HashMap<>();
            this.messages = messages;
            this.inputPublicKeys = inputPublicKeys;
            this.myCompleteNonces = myCompleteNonces != null ? myCompleteNonces : new HashMap<>();
        }

        public Map<Integer, List<MuSig2.MuSig2Nonce>> getAllPublicNonces() {
            return allPublicNonces;
        }

        public Map<Integer, byte[]> getMessages() {
            return messages;
        }

        public Map<Integer, List<ECKey>> getInputPublicKeys() {
            return inputPublicKeys;
        }

        public Map<Integer, CompleteNonce> getMyCompleteNonces() {
            return myCompleteNonces;
        }
    }
}
