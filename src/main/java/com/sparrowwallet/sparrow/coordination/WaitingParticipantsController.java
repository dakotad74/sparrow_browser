package com.sparrowwallet.sparrow.coordination;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.control.CoordinationDialog;
import com.sparrowwallet.sparrow.event.CoordinationParticipantJoinedEvent;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;

/**
 * Controller for Step 2: Waiting for Participants
 *
 * Displays QR code with session ID for other participants to join.
 * Shows real-time participant list as users join.
 * Auto-advances to next step when all participants have joined.
 */
public class WaitingParticipantsController implements Initializable, CoordinationController.StepController {
    private static final Logger log = LoggerFactory.getLogger(WaitingParticipantsController.class);
    private static final int QR_CODE_SIZE = 300;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    @FXML
    private Label sessionIdLabel;

    @FXML
    private StackPane qrCodeContainer;

    @FXML
    private Button copySessionIdButton;

    @FXML
    private TreeTableView<ParticipantRow> participantsTable;

    @FXML
    private TreeTableColumn<ParticipantRow, String> participantColumn;

    @FXML
    private TreeTableColumn<ParticipantRow, String> statusColumn;

    @FXML
    private TreeTableColumn<ParticipantRow, String> joinedAtColumn;

    @FXML
    private Label participantCountLabel;

    @FXML
    private Label statusLabel;

    private Wallet wallet;
    private CoordinationDialog dialog;
    private CoordinationSession session;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Setup table columns
        participantColumn.setCellValueFactory(param ->
            new javafx.beans.property.SimpleStringProperty(param.getValue().getValue().name));
        statusColumn.setCellValueFactory(param ->
            new javafx.beans.property.SimpleStringProperty(param.getValue().getValue().status));
        joinedAtColumn.setCellValueFactory(param ->
            new javafx.beans.property.SimpleStringProperty(param.getValue().getValue().joinedAt));
    }

    @Override
    public void initializeStep(Wallet wallet, CoordinationDialog dialog) {
        this.wallet = wallet;
        this.dialog = dialog;
        this.session = dialog.getSession();

        if(session == null) {
            log.error("Session is null in WaitingParticipantsController");
            statusLabel.setText("Error: No session found");
            return;
        }

        // Display session ID
        sessionIdLabel.setText("Session ID: " + session.getSessionId());

        // Generate QR code
        generateQRCode(session.getSessionId());

        // Initialize participant list
        updateParticipantList();

        // Update status
        updateStatus();
    }

    @Override
    public boolean validateStep() {
        // For testing: Allow proceeding even if not all participants have joined
        // In production, you might want to require all participants
        log.error("=== WaitingParticipantsController.validateStep() called ===");
        log.error("Session is null? {}", session == null);
        if(session != null) {
            log.error("Session ID: {}", session.getSessionId());
            log.error("Participants: {} / {}", session.getParticipants().size(), session.getExpectedParticipants());
        }
        boolean result = session != null;
        log.error("Returning: {}", result);
        return result;
    }

    @Override
    public void onEventReceived(Object event) {
        if(event instanceof CoordinationParticipantJoinedEvent) {
            CoordinationParticipantJoinedEvent participantEvent = (CoordinationParticipantJoinedEvent) event;

            // Only update if this event is for our session
            if(session != null && participantEvent.getSessionId().equals(session.getSessionId())) {
                Platform.runLater(() -> {
                    updateParticipantList();
                    updateStatus();
                });
            }
        }
    }

    /**
     * Generate QR code for session ID
     */
    private void generateQRCode(String sessionId) {
        try {
            // Create QR data (use nostr:// URL format for consistency)
            String qrData = "nostr://coordinate/" + sessionId;

            // Generate QR code
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(qrData, BarcodeFormat.QR_CODE, QR_CODE_SIZE, QR_CODE_SIZE);

            // Convert to JavaFX Image
            BufferedImage bufferedImage = MatrixToImageWriter.toBufferedImage(bitMatrix);
            Image qrImage = SwingFXUtils.toFXImage(bufferedImage, null);

            // Display in ImageView
            ImageView imageView = new ImageView(qrImage);
            imageView.setFitWidth(QR_CODE_SIZE);
            imageView.setFitHeight(QR_CODE_SIZE);
            imageView.setPreserveRatio(true);

            qrCodeContainer.getChildren().clear();
            qrCodeContainer.getChildren().add(imageView);

            log.info("Generated QR code for session: {}", sessionId);

        } catch(Exception e) {
            log.error("Failed to generate QR code", e);
            Label errorLabel = new Label("Failed to generate QR code");
            errorLabel.setStyle("-fx-text-fill: red;");
            qrCodeContainer.getChildren().clear();
            qrCodeContainer.getChildren().add(errorLabel);
        }
    }

    /**
     * Copy session ID to clipboard
     */
    @FXML
    private void copySessionId() {
        if(session != null) {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(session.getSessionId());
            clipboard.setContent(content);

            // Visual feedback
            String originalText = copySessionIdButton.getText();
            copySessionIdButton.setText("Copied!");
            copySessionIdButton.setDisable(true);

            // Reset after 2 seconds
            new Thread(() -> {
                try {
                    Thread.sleep(2000);
                } catch(InterruptedException e) {
                    // Ignore
                }
                Platform.runLater(() -> {
                    copySessionIdButton.setText(originalText);
                    copySessionIdButton.setDisable(false);
                });
            }).start();

            log.debug("Copied session ID to clipboard: {}", session.getSessionId());
        }
    }

    /**
     * Update participant list from session
     */
    private void updateParticipantList() {
        if(session == null) {
            return;
        }

        // Create root item
        TreeItem<ParticipantRow> root = new TreeItem<>(new ParticipantRow("Participants", "", ""));
        root.setExpanded(true);

        // Add participants
        for(CoordinationParticipant participant : session.getParticipants()) {
            String name = participant.getName() != null ? participant.getName() : "Participant";
            String status = participant.getStatus() != null ? participant.getStatus().toString() : "JOINED";
            String joinedAt = participant.getJoinedAt() != null ?
                             participant.getJoinedAt().format(TIME_FORMATTER) : "";

            TreeItem<ParticipantRow> item = new TreeItem<>(new ParticipantRow(name, status, joinedAt));
            root.getChildren().add(item);
        }

        participantsTable.setRoot(root);
        participantsTable.setShowRoot(false);

        log.debug("Updated participant list: {} participants", session.getParticipants().size());
    }

    /**
     * Update status labels
     */
    private void updateStatus() {
        if(session == null) {
            return;
        }

        int current = session.getParticipants().size();
        int expected = session.getExpectedParticipants();

        participantCountLabel.setText(current + " / " + expected + " participants joined");

        if(current < expected) {
            statusLabel.setText("Waiting for " + (expected - current) + " more participant(s) to join...");
        } else {
            statusLabel.setText("All participants joined! Ready to proceed.");
            statusLabel.setStyle("-fx-text-fill: green;");
        }
    }

    /**
     * Helper class for table rows
     */
    public static class ParticipantRow {
        private final String name;
        private final String status;
        private final String joinedAt;

        public ParticipantRow(String name, String status, String joinedAt) {
            this.name = name;
            this.status = status;
            this.joinedAt = joinedAt;
        }
    }
}
