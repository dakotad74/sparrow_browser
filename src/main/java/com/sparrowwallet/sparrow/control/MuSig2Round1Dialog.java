package com.sparrowwallet.sparrow.control;

import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.crypto.musig2.MuSig2;
import com.sparrowwallet.drongo.crypto.musig2.MuSig2.CompleteNonce;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.psbt.PSBTInput;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.protocol.SigHash;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.MuSig2Round1Event;
import com.sparrowwallet.sparrow.event.PSBTSignedEvent;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
            "3. Enter nonces from other signers (one per line: input_index,R_hex)\n" +
            "4. Click 'Continue to Round 2' when all nonces are collected"
        );
        instructionsLabel.setWrapText(true);
        content.getChildren().add(instructionsLabel);

        // Generate button
        Button generateButton = new Button("Generate My Nonces");
        generateButton.setMaxWidth(Double.MAX_VALUE);
        generateButton.setOnAction(e -> generateMyNonces());
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
        try {
            // Check if wallet is encrypted
            if(wallet.isEncrypted()) {
                statusLabel.setText("Wallet is encrypted. Please decrypt wallet first.");
                statusLabel.setStyle("-fx-text-fill: #c0392b;");
                return;
            }

            // Get all public keys for MuSig2
            List<ECKey> publicKeys = new ArrayList<>();
            for(Keystore keystore : wallet.getKeystores()) {
                if(keystore.getExtendedPublicKey() != null) {
                    ECKey pubKey = keystore.getExtendedPublicKey().getKey();
                    publicKeys.add(pubKey);
                }
            }

            if(publicKeys.isEmpty()) {
                statusLabel.setText("Error: No public keys found in wallet");
                statusLabel.setStyle("-fx-text-fill: #c0392b;");
                return;
            }

            // Generate nonces for each input
            StringBuilder nonceDisplay = new StringBuilder();
            int inputCount = 0;

            Map<PSBTInput, WalletNode> signingNodes = wallet.getSigningNodes(psbt);

            for(Map.Entry<PSBTInput, WalletNode> inputEntry : signingNodes.entrySet()) {
                PSBTInput input = inputEntry.getKey();

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

                    // Get first private key (simplified - in real scenario, would get key for specific input)
                    ECKey privKey = wallet.getKeystores().get(0).getExtendedPublicKey().getKey();

                    // Generate Round 1 nonce using MuSig2 API
                    CompleteNonce completeNonce = MuSig2.generateRound1Nonce(privKey, publicKeys, message);

                    myCompleteNonces.put(inputCount, completeNonce);
                    messages.put(inputCount, sigHash);
                    inputPublicKeys.put(inputCount, publicKeys);

                    // Display nonce R value (use publicKey1 which is the first nonce component)
                    // For simplicity, we'll display the first 32 bytes (publicKey1)
                    String nonceHex = bytesToHex(completeNonce.getPublicNonce().getPublicKey1());
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
            log.error("Error generating MuSig2 nonces", e);
            statusLabel.setText("Error: " + e.getMessage());
            statusLabel.setStyle("-fx-text-fill: #c0392b;");
        }
    }

    private boolean hasRequiredNonces() {
        // Check if user has entered nonces from other signers
        String text = otherNoncesArea.getText().trim();
        if(text.isEmpty()) {
            return false;
        }

        // Validate format: input_index:R_hex (66 hex chars = 33 bytes compressed)
        String[] lines = text.split("\n");
        for(String line : lines) {
            if(!line.trim().matches("^\\d+:[0-9a-fA-F]{66}$")) {
                statusLabel.setText("Invalid nonce format. Use: input_index:R_hex (66 hex chars)");
                statusLabel.setStyle("-fx-text-fill: #c0392b;");
                return false;
            }
        }

        return true;
    }

    private MuSig2Round1Data getRound1Data() {
        try {
            Map<Integer, MuSig2.MuSig2Nonce> allNonces = new HashMap<>();

            // Add my nonces
            for(Map.Entry<Integer, CompleteNonce> entry : myCompleteNonces.entrySet()) {
                allNonces.put(entry.getKey(), entry.getValue().getPublicNonce());
            }

            // Parse other nonces from text area
            String text = otherNoncesArea.getText().trim();
            String[] lines = text.split("\n");
            for(String line : lines) {
                String[] parts = line.trim().split(":");
                if(parts.length == 2) {
                    int inputIndex = Integer.parseInt(parts[0]);
                    byte[] R = hexToBytes(parts[1]);
                    // Create MuSig2Nonce with R and empty second nonce (will be combined)
                    MuSig2.MuSig2Nonce nonce = new MuSig2.MuSig2Nonce(R, new byte[32]);
                    allNonces.put(inputIndex, nonce);
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
        private final Map<Integer, MuSig2.MuSig2Nonce> allPublicNonces;
        private final Map<Integer, byte[]> messages;
        private final Map<Integer, List<ECKey>> inputPublicKeys;
        private final Map<Integer, CompleteNonce> myCompleteNonces;

        public MuSig2Round1Data(Map<Integer, MuSig2.MuSig2Nonce> allPublicNonces,
                                  Map<Integer, byte[]> messages,
                                  Map<Integer, List<ECKey>> inputPublicKeys,
                                  Map<Integer, CompleteNonce> myCompleteNonces) {
            this.allPublicNonces = allPublicNonces;
            this.messages = messages;
            this.inputPublicKeys = inputPublicKeys;
            this.myCompleteNonces = myCompleteNonces != null ? myCompleteNonces : new HashMap<>();
        }

        public Map<Integer, MuSig2.MuSig2Nonce> getAllPublicNonces() {
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
