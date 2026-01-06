package com.sparrowwallet.sparrow.control;

import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.SecureString;
import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.crypto.musig2.MuSig2;
import com.sparrowwallet.drongo.crypto.musig2.MuSig2Core;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.psbt.PSBTInput;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.MuSig2Round2Event;
import com.sparrowwallet.sparrow.event.PSBTSignedEvent;
import com.sparrowwallet.sparrow.io.Storage;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Dialog for MuSig2 Round 2: Partial signature creation.
 *
 * This dialog handles the second round of MuSig2 signing where:
 * 1. All public nonces from Round 1 are combined
 * 2. Each signer creates their partial signature
 * 3. Partial signatures are exchanged (or combined if all are present)
 */
public class MuSig2Round2Dialog extends Dialog<PSBT> {
    private static final Logger log = LoggerFactory.getLogger(MuSig2Round2Dialog.class);

    private final Wallet wallet;
    private final PSBT psbt;
    private final MuSig2Round1Dialog.MuSig2Round1Data round1Data;

    private ProgressBar progress;
    private Label statusLabel;
    private Button signButton;

    public MuSig2Round2Dialog(Wallet wallet, PSBT psbt, MuSig2Round1Dialog.MuSig2Round1Data round1Data) {
        this.wallet = wallet;
        this.psbt = psbt;
        this.round1Data = round1Data;

        EventManager.get().register(this);
        setResultConverter(dialogButton -> dialogButton.getButtonData().isCancelButton() ? null : psbt);
        initDialog();
    }

    private void initDialog() {
        setTitle("MuSig2 Round 2 - Create Partial Signature");
        getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL);
        getDialogPane().setPrefWidth(500);

        VBox content = new VBox(15);
        content.setPadding(new Insets(20));

        // Instructions
        Label instructionsLabel = new Label(
            "In Round 2, you will create your partial signature for the transaction.\n\n" +
            "This requires access to your private key. If your wallet is encrypted,\n" +
            "you will be prompted for your password.\n\n" +
            "Click 'Create Partial Signature' to begin."
        );
        instructionsLabel.setWrapText(true);
        content.getChildren().add(instructionsLabel);

        // Progress bar
        progress = new ProgressBar();
        progress.setProgress(0);
        progress.setVisible(false);
        content.getChildren().add(progress);

        // Status label
        statusLabel = new Label("Ready to create partial signature");
        statusLabel.setWrapText(true);
        content.getChildren().add(statusLabel);

        // Sign button
        signButton = new Button("Create Partial Signature");
        signButton.setMaxWidth(Double.MAX_VALUE);
        signButton.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        signButton.setOnAction(e -> createPartialSignature());
        content.getChildren().add(signButton);

        getDialogPane().setContent(content);
    }

    private void createPartialSignature() {
        signButton.setDisable(true);
        progress.setVisible(true);
        progress.setProgress(0.1);

        // Check if wallet is encrypted
        if(wallet.isEncrypted()) {
            statusLabel.setText("Wallet is encrypted. Password required...");
            progress.setProgress(0.2);

            // Prompt for password and decrypt wallet
            decryptWalletAndCreatePartialSignature();
            return;
        }

        // Wallet is not encrypted, proceed directly
        createPartialSignatureWithWallet(wallet);
    }

    private void decryptWalletAndCreatePartialSignature() {
        try {
            Storage storage = AppServices.get().getOpenWallets().get(wallet);

            // Create password dialog
            WalletPasswordDialog dlg = new WalletPasswordDialog(wallet.getMasterName(), WalletPasswordDialog.PasswordRequirement.LOAD);
            dlg.initOwner(signButton.getScene().getWindow());
            Optional<SecureString> password = dlg.showAndWait();

            if(password.isEmpty()) {
                statusLabel.setText("Password entry cancelled.");
                statusLabel.setStyle("-fx-text-fill: #c0392b;");
                progress.setProgress(0);
                signButton.setDisable(false);
                return;
            }

            SecureString passwordEntry = password.get();
            if(passwordEntry == null || passwordEntry.isEmpty()) {
                statusLabel.setText("Password is required.");
                statusLabel.setStyle("-fx-text-fill: #c0392b;");
                progress.setProgress(0);
                signButton.setDisable(false);
                return;
            }

            // Decrypt wallet using background service
            Storage.DecryptWalletService decryptWalletService =
                new Storage.DecryptWalletService(
                    wallet.copy(),
                    passwordEntry
                );

            decryptWalletService.setOnSucceeded(workerStateEvent -> {
                Wallet decryptedWallet = decryptWalletService.getValue();
                log.info("Wallet decrypted successfully, creating partial signature");

                try {
                    // Create partial signature with decrypted wallet
                    createPartialSignatureWithWallet(decryptedWallet);

                    // IMPORTANT: Clear private keys from decrypted wallet after use
                    decryptedWallet.clearPrivate();
                    log.info("Cleared private keys from decrypted wallet");

                } catch(Exception e) {
                    log.error("Error creating partial signature with decrypted wallet", e);
                    Platform.runLater(() -> {
                        statusLabel.setText("Error: " + e.getMessage());
                        statusLabel.setStyle("-fx-text-fill: #c0392b;");
                        progress.setProgress(0);
                        signButton.setDisable(false);
                    });
                }
            });

            decryptWalletService.setOnFailed(workerStateEvent -> {
                log.error("Failed to decrypt wallet", decryptWalletService.getException());
                Platform.runLater(() -> {
                    statusLabel.setText("Decryption failed: " + decryptWalletService.getException().getMessage());
                    statusLabel.setStyle("-fx-text-fill: #c0392b;");
                    progress.setProgress(0);
                    signButton.setDisable(false);
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
            log.error("Error in decryptWalletAndCreatePartialSignature", e);
            Platform.runLater(() -> {
                statusLabel.setText("Error: " + e.getMessage());
                statusLabel.setStyle("-fx-text-fill: #c0392b;");
                progress.setProgress(0);
                signButton.setDisable(false);
            });
        }
    }

    private void createPartialSignatureWithWallet(Wallet signingWallet) {
        // Create partial signature in background thread
        new Thread(() -> {
            try {
                Platform.runLater(() -> {
                    statusLabel.setText("Creating partial signature...");
                    progress.setProgress(0.3);
                });

                // Get signing nodes from wallet
                Map<PSBTInput, WalletNode> signingNodes = wallet.getSigningNodes(psbt);

                int[] inputCount = {0};  // Use array to allow modification in lambda
                int totalInputs = signingNodes.size();

                // Process each input
                for(Map.Entry<PSBTInput, WalletNode> inputEntry : signingNodes.entrySet()) {
                    PSBTInput psbtInput = inputEntry.getKey();
                    WalletNode walletNode = inputEntry.getValue();

                    final int currentInput = inputCount[0];  // Final copy for lambda

                    Platform.runLater(() -> {
                        statusLabel.setText("Creating partial signature for input " + (currentInput + 1) + "...");
                        progress.setProgress(0.3 + (0.5 * currentInput / totalInputs));
                    });

                    // Check if we have Round 1 data for this input
                    if(!round1Data.getMyCompleteNonces().containsKey(inputCount[0])) {
                        log.error("No Round 1 data found for input {}", inputCount[0]);
                        inputCount[0]++;
                        continue;
                    }

                    // Get secret nonce from Round 1
                    MuSig2.CompleteNonce completeNonce = round1Data.getMyCompleteNonces().get(inputCount[0]);
                    MuSig2.SecretNonce secretNonce = completeNonce.getSecretNonce();

                    // Get public keys for this input
                    List<ECKey> publicKeys = round1Data.getInputPublicKeys().get(inputCount[0]);
                    if(publicKeys == null || publicKeys.isEmpty()) {
                        log.error("No public keys found for input {}", inputCount[0]);
                        inputCount[0]++;
                        continue;
                    }

                    // CRITICAL: Sort public keys before MuSig2 operations
                    // BIP-327 requires keys to be sorted lexicographically to ensure
                    // all signers use the same key order regardless of wallet configuration
                    MuSig2Core.sortPublicKeys(publicKeys);
                    log.error("=== Sorted {} public keys for MuSig2 ===", publicKeys.size());

                    // Get all public nonces from Round 1 data (includes nonces from ALL signers)
                    List<MuSig2.MuSig2Nonce> publicNonces = round1Data.getAllPublicNonces().get(inputCount[0]);
                    if(publicNonces == null || publicNonces.isEmpty()) {
                        log.error("No public nonces found for input {}", inputCount[0]);
                        inputCount[0]++;
                        continue;
                    }

                    // CRITICAL: Sort nonces to ensure all signers use the same nonce order
                    // The nonce coefficient b depends on the order of nonces in the list
                    java.util.Collections.sort(publicNonces, (n1, n2) -> {
                        // Compare by publicKey1 first, then publicKey2
                        byte[] pk1_1 = n1.getPublicKey1();
                        byte[] pk2_1 = n2.getPublicKey1();
                        for(int i = 0; i < Math.min(pk1_1.length, pk2_1.length); i++) {
                            int cmp = Integer.compare(pk1_1[i] & 0xFF, pk2_1[i] & 0xFF);
                            if(cmp != 0) return cmp;
                        }
                        int lenCmp = Integer.compare(pk1_1.length, pk2_1.length);
                        if(lenCmp != 0) return lenCmp;

                        // If publicKey1 are equal, compare publicKey2
                        byte[] pk1_2 = n1.getPublicKey2();
                        byte[] pk2_2 = n2.getPublicKey2();
                        for(int i = 0; i < Math.min(pk1_2.length, pk2_2.length); i++) {
                            int cmp = Integer.compare(pk1_2[i] & 0xFF, pk2_2[i] & 0xFF);
                            if(cmp != 0) return cmp;
                        }
                        return Integer.compare(pk1_2.length, pk2_2.length);
                    });
                    log.error("=== Sorted {} nonces for MuSig2 Round 2 ===", publicNonces.size());

                    // Get message (sighash) - use wrap() to create Sha256Hash from bytes
                    byte[] messageBytes = round1Data.getMessages().get(inputCount[0]);
                    Sha256Hash message = Sha256Hash.wrap(messageBytes);

                    // Get private key from signing wallet for this input
                    ECKey privKey = null;
                    Collection<Keystore> signingKeystores = signingWallet.getSigningKeystores(psbt);

                    // For MuSig2, if getSigningKeystores returns empty, fall back to keystores with private keys
                    if(signingKeystores.isEmpty() && signingWallet.getPolicyType() == PolicyType.MUSIG2) {
                        log.error("getSigningKeystores empty in Round2, falling back to keystores with private keys");
                        for(Keystore k : signingWallet.getKeystores()) {
                            if(k.hasPrivateKey()) {
                                signingKeystores.add(k);
                            }
                        }
                    }

                    if(signingKeystores.isEmpty()) {
                        log.error("No signing keystores found for PSBT");
                        inputCount[0]++;
                        continue;
                    }

                    // Try to get private key from each signing keystore
                    for(Keystore signingKeystore : signingKeystores) {
                        try {
                            ECKey key = signingKeystore.getKey(walletNode);
                            if(key != null && key.hasPrivKey()) {
                                privKey = key;
                                log.info("Obtained private key from keystore: " + signingKeystore.getLabel());
                                break;
                            }
                        } catch(Exception e) {
                            log.debug("Could not get key from keystore " + signingKeystore.getLabel() + ": " + e.getMessage());
                        }
                    }

                    if(privKey == null) {
                        log.error("No private key found for input {}", inputCount[0]);
                        Platform.runLater(() -> {
                            statusLabel.setText("Error: No private key available for signing.");
                            statusLabel.setStyle("-fx-text-fill: #c0392b;");
                            progress.setProgress(0);
                            signButton.setDisable(false);
                        });
                        return;
                    }

                    // For Taproot, get the output key X (tweaked) from the UTXO script
                    // This is needed for the signature to verify correctly against the Taproot output
                    ECKey taprootOutputKey = null;
                    log.error("=== Checking for Taproot output key: psbtInput.getUtxo()={}, psbtInput.getWitnessUtxo()={} ===",
                        psbtInput.getUtxo() != null, psbtInput.getWitnessUtxo() != null);

                    if(psbtInput.getWitnessUtxo() != null && psbtInput.getWitnessUtxo().getScript() != null) {
                        try {
                            com.sparrowwallet.drongo.protocol.Script script = psbtInput.getWitnessUtxo().getScript();
                            log.error("=== UTXO script: {} ===", script);
                            if(com.sparrowwallet.drongo.protocol.ScriptType.P2TR.isScriptType(script)) {
                                // For Taproot, we need x-only pubkey (32 bytes without prefix)
                                byte[] chunkData = script.getChunks().get(1).data;
                                byte[] xonlyPubkey = new byte[32];
                                int len = Math.min(32, chunkData.length);
                                System.arraycopy(chunkData, 0, xonlyPubkey, 0, len);
                                log.error("=== Taproot x-only pubkey ({} bytes from {} chunk bytes): {} ===",
                                    xonlyPubkey.length, chunkData.length, com.sparrowwallet.drongo.Utils.bytesToHex(xonlyPubkey));
                                // Create ECKey from x-only pubkey (32 bytes)
                                taprootOutputKey = ECKey.fromPublicOnly(xonlyPubkey);
                                log.error("=== Created Taproot output key X ===");
                            } else {
                                log.error("=== Script is not P2TR type ===");
                            }
                        } catch(Exception e) {
                            log.error("Could not get Taproot output key", e);
                        }
                    } else if(psbtInput.getUtxo() != null && psbtInput.getUtxo().getScript() != null) {
                        try {
                            com.sparrowwallet.drongo.protocol.Script script = psbtInput.getUtxo().getScript();
                            log.error("=== Non-witness UTXO script: {} ===", script);
                            if(com.sparrowwallet.drongo.protocol.ScriptType.P2TR.isScriptType(script)) {
                                byte[] chunkData = script.getChunks().get(1).data;
                                byte[] xonlyPubkey = new byte[32];
                                int len = Math.min(32, chunkData.length);
                                System.arraycopy(chunkData, 0, xonlyPubkey, 0, len);
                                log.error("=== Taproot x-only pubkey ({} bytes from {} chunk bytes): {} ===",
                                    xonlyPubkey.length, chunkData.length, com.sparrowwallet.drongo.Utils.bytesToHex(xonlyPubkey));
                                taprootOutputKey = ECKey.fromPublicOnly(xonlyPubkey);
                                log.error("=== Created Taproot output key X ===");
                            }
                        } catch(Exception e) {
                            log.error("Could not get Taproot output key", e);
                        }
                    }

                    // Create partial signature using MuSig2 API
                    log.error("=== About to call signRound2BIP327 ===");

                    // CRITICAL FIX for BIP-327 Taproot MuSig2:
                    // For Taproot key-path spending, the challenge MUST use the output key X, not the internal key Q
                    // This is required by BIP-327 Section 2.4 and BIP-341 Section 5
                    // The tweak adjustment approach previously used is not compliant with MuSig2
                    if(taprootOutputKey != null) {
                        log.error("=== ================================================== ===");
                        log.error("=== MuSig2 Round 2: TAPROOT SIGNING DETAILS ===");
                        log.error("=== ================================================== ===");
                        log.error("=== Taproot output key X: {} ===", com.sparrowwallet.drongo.Utils.bytesToHex(taprootOutputKey.getPubKeyXCoord()));
                        log.error("=== Message (sighash): {} ===", com.sparrowwallet.drongo.Utils.bytesToHex(message.getBytes()));
                        log.error("=== Public keys count: {} ===", publicKeys.size());
                        for(int i = 0; i < publicKeys.size(); i++) {
                            log.error("===   Public key {}: {} ===", i, com.sparrowwallet.drongo.Utils.bytesToHex(publicKeys.get(i).getPubKeyXCoord()));
                        }
                        log.error("=== Using Taproot output key X for MuSig2 challenge (BIP-327 compliant) ===");
                    } else {
                        log.error("=== Using internal key Q for MuSig2 (non-Taproot or X not available) ===");
                    }
                    MuSig2.PartialSignature partialSig = MuSig2.signRound2BIP327(
                        privKey,
                        secretNonce,
                        publicKeys,
                        publicNonces,
                        message,
                        taprootOutputKey  // Pass X for Taproot so challenge uses X, not Q
                    );
                    log.error("=== signRound2BIP327 returned successfully ===");
                    if(taprootOutputKey != null) {
                        log.error("=== Partial signature R (x-only): {} ===", com.sparrowwallet.drongo.Utils.bytesToHex(partialSig.getR()));
                        log.error("=== Partial signature s: {} ===", com.sparrowwallet.drongo.Utils.bytesToHex(partialSig.getS()));
                        log.error("=== ================================================== ===");
                    }

                    // Add partial signature to PSBT
                    // Use the public key corresponding to the private key
                    ECKey myPublicKey = ECKey.fromPublicOnly(privKey.getPubKey());
                    log.error("=== About to add MuSig2 partial signature for myPublicKey: {} ===", com.sparrowwallet.drongo.Utils.bytesToHex(myPublicKey.getPubKey()));
                    log.error("=== MuSig2 aggregated public keys ({} total): ===", publicKeys.size());
                    for(int i = 0; i < publicKeys.size(); i++) {
                        log.error("===   [{}] {} ===", i, com.sparrowwallet.drongo.Utils.bytesToHex(publicKeys.get(i).getPubKey()));
                    }
                    psbtInput.addMuSig2PartialSig(myPublicKey, partialSig);

                    // Store the MuSig2 public keys for later use when combining signatures
                    // This is needed to correctly compute the aggregated key Q for Taproot tweaking
                    psbtInput.setMuSig2PublicKeys(publicKeys);
                    log.error("=== Set MuSig2 public keys ({} keys) for signature combination ===", publicKeys.size());

                    log.error("=== Created MuSig2 partial signature for input {} ===", inputCount[0]);
                    inputCount[0]++;
                }

                Platform.runLater(() -> {
                    statusLabel.setText("Partial signature created successfully!");
                    statusLabel.setStyle("-fx-text-fill: #009670;");
                    progress.setProgress(1.0);

                    // Post event with updated PSBT
                    EventManager.get().post(new MuSig2Round2Event(psbt, psbt));

                    // Close dialog after a short delay
                    new Thread(() -> {
                        try {
                            Thread.sleep(1500);
                            Platform.runLater(() -> {
                                setResult(psbt);
                                close();
                            });
                        } catch(InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }).start();
                });

            } catch(Exception e) {
                log.error("Error creating partial signature", e);
                Platform.runLater(() -> {
                    statusLabel.setText("Error: " + e.getMessage());
                    statusLabel.setStyle("-fx-text-fill: #c0392b;");
                    progress.setProgress(0);
                    signButton.setDisable(false);
                });
            }
        }).start();
    }

    @Subscribe
    public void psbtSigned(PSBTSignedEvent event) {
        if(psbt == event.getPsbt()) {
            // PSBT was signed through other means, close this dialog
            Platform.runLater(() -> close());
        }
    }
}
