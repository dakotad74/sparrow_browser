package com.sparrowwallet.sparrow.control;

import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.crypto.musig2.MuSig2;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.psbt.PSBTInput;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.MuSig2Round2Event;
import com.sparrowwallet.sparrow.event.PSBTSignedEvent;
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
import java.util.List;
import java.util.Map;

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

            // For now, show error - encrypted wallet support would require password dialog
            Platform.runLater(() -> {
                statusLabel.setText("Error: Encrypted wallets not yet supported in MuSig2 dialog.\n" +
                    "Please decrypt wallet first using the main signing flow.");
                statusLabel.setStyle("-fx-text-fill: #c0392b;");
                progress.setProgress(0);
                signButton.setDisable(false);
            });
            return;
        }

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

                    // Get all public nonces (from all signers)
                    List<MuSig2.MuSig2Nonce> publicNonces = new ArrayList<>();
                    publicNonces.add(completeNonce.getPublicNonce());

                    // Get message (sighash) - use wrap() to create Sha256Hash from bytes
                    byte[] messageBytes = round1Data.getMessages().get(inputCount[0]);
                    Sha256Hash message = Sha256Hash.wrap(messageBytes);

                    // Get private key from wallet for this input
                    ECKey privKey = null;
                    for(Keystore keystore : wallet.getKeystores()) {
                        if(keystore.hasPrivateKey()) {
                            ECKey key = keystore.getKey(walletNode);
                            if(key != null && key.hasPrivKey()) {
                                privKey = key;
                                break;
                            }
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

                    // Create partial signature using MuSig2 API
                    MuSig2.PartialSignature partialSig = MuSig2.signRound2BIP327(
                        privKey,
                        secretNonce,
                        publicKeys,
                        publicNonces,
                        message
                    );

                    // Add partial signature to PSBT
                    // Use the public key corresponding to the private key
                    ECKey myPublicKey = ECKey.fromPublicOnly(privKey.getPubKey());
                    psbtInput.addMuSig2PartialSig(myPublicKey, partialSig);

                    log.info("Created MuSig2 partial signature for input {}", inputCount[0]);
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
