package com.sparrowwallet.sparrow.p2p.chat;

import com.sparrowwallet.sparrow.p2p.identity.NostrIdentity;
import com.sparrowwallet.sparrow.p2p.identity.NostrIdentityManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Controller for encrypted chat dialog.
 * Displays conversation with a peer using NIP-04 encryption.
 */
public class ChatDialogController implements Initializable {
    private static final Logger log = LoggerFactory.getLogger(ChatDialogController.class);

    @FXML
    private Label peerNameLabel;

    @FXML
    private Label peerNpubLabel;

    @FXML
    private Label connectionStatusLabel;

    @FXML
    private ListView<ChatMessage> messagesListView;

    @FXML
    private TextArea messageInputArea;

    @FXML
    private Button sendButton;

    @FXML
    private Button closeButton;

    private Stage dialogStage;
    private String peerHex;
    private String peerName;
    private String peerNpub;
    private String offerId;  // Track which offer this chat is about
    private String conversationId;  // Unique ID: offerId or peerHex if no offer

    private ChatService chatService;
    private NostrIdentityManager identityManager;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        chatService = ChatService.getInstance();
        identityManager = NostrIdentityManager.getInstance();

        // Setup message list with custom cell factory
        messagesListView.setCellFactory(lv -> new MessageListCell());

        // Setup button handlers
        sendButton.setOnAction(e -> handleSendMessage());
        closeButton.setOnAction(e -> closeDialog());

        // Enable send on Enter (Shift+Enter for new line)
        messageInputArea.setOnKeyPressed(event -> {
            if (event.getCode().toString().equals("ENTER") && !event.isShiftDown()) {
                event.consume();
                handleSendMessage();
            }
        });

        // Disable send button when input is empty
        messageInputArea.textProperty().addListener((obs, oldVal, newVal) -> {
            sendButton.setDisable(newVal == null || newVal.trim().isEmpty());
        });

        // Register message handler to update UI
        chatService.addMessageHandler(this::handleIncomingMessage);
    }

    /**
     * Set the dialog stage
     */
    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;

        // Update title when stage is set
        if (dialogStage != null && peerName != null) {
            dialogStage.setTitle("Chat with " + peerName);
        }
    }

    /**
     * Set the peer information and load conversation
     */
    public void setPeer(String peerHex, String peerName, String peerNpub) {
        setPeer(peerHex, peerName, peerNpub, null);
    }

    /**
     * Set the peer information with offer context
     */
    public void setPeer(String peerHex, String peerName, String peerNpub, String offerId) {
        this.peerHex = peerHex;
        this.peerName = peerName;
        this.peerNpub = peerNpub;
        this.offerId = offerId;

        // Create unique conversation ID based on offer or just peer
        this.conversationId = (offerId != null) ? offerId : peerHex;

        // Update UI
        peerNameLabel.setText(peerName);
        peerNpubLabel.setText(peerNpub);
        connectionStatusLabel.setText("🔒 Encrypted (NIP-04)");
        connectionStatusLabel.setStyle("-fx-text-fill: #2ecc71; -fx-font-weight: bold;");

        // Update dialog title
        if (dialogStage != null) {
            dialogStage.setTitle("Chat with " + peerName + (offerId != null ? " (Offer)" : ""));
        }

        // Subscribe to conversation
        try {
            chatService.subscribeToConversation(peerHex);
            log.info("Subscribed to chat with {} for conversation {}", peerHex.substring(0, 8), conversationId.substring(0, 8));
        } catch (Exception e) {
            log.error("Failed to subscribe to conversation", e);
            showError("Failed to establish encrypted connection: " + e.getMessage());
        }

        // Load existing messages
        loadMessages();
    }

    /**
     * Load messages from conversation history
     */
    private void loadMessages() {
        List<ChatMessage> messages = chatService.getConversation(conversationId);
        messagesListView.getItems().setAll(messages);

        // Scroll to bottom
        if (!messages.isEmpty()) {
            messagesListView.scrollTo(messages.size() - 1);
        }
    }

    /**
     * Handle send message button
     */
    private void handleSendMessage() {
        String messageText = messageInputArea.getText().trim();

        if (messageText.isEmpty()) {
            return;
        }

        try {
            // Send encrypted message via ChatService with conversation context
            chatService.sendMessage(peerHex, messageText, conversationId);

            // Clear input
            messageInputArea.clear();

            // Reload messages (sendMessage adds to conversation)
            loadMessages();

            log.info("Sent message to {} for conversation {}", peerHex.substring(0, 8), conversationId.substring(0, 8));

        } catch (Exception e) {
            log.error("Failed to send message", e);
            showError("Failed to send message: " + e.getMessage());
        }
    }

    /**
     * Handle incoming message from ChatService
     */
    private void handleIncomingMessage(ChatMessage message) {
        // Only update if this is a message from our current peer
        if (message.getSenderHex().equals(peerHex)) {
            Platform.runLater(() -> {
                loadMessages();

                // Mark as read
                message.markAsRead();
            });
        }
    }

    /**
     * Show error alert
     */
    private void showError(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("Chat Error");
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    /**
     * Close the dialog
     */
    private void closeDialog() {
        // Unregister message handler
        chatService.removeMessageHandler(this::handleIncomingMessage);

        if (dialogStage != null) {
            dialogStage.close();
        }
    }

    /**
     * Custom ListCell for rendering chat messages
     */
    private class MessageListCell extends ListCell<ChatMessage> {
        @Override
        protected void updateItem(ChatMessage message, boolean empty) {
            super.updateItem(message, empty);

            if (empty || message == null) {
                setGraphic(null);
                setText(null);
                return;
            }

            // Get active identity to determine if message is outgoing
            NostrIdentity activeIdentity = identityManager.getActiveIdentity();
            boolean isOutgoing = activeIdentity != null &&
                                 message.getSenderHex().equals(activeIdentity.getHex());

            // Create message bubble
            VBox bubble = new VBox(5);
            bubble.setStyle(
                "-fx-padding: 10;" +
                "-fx-background-radius: 10;" +
                "-fx-background-color: " + (isOutgoing ? "#dcf8c6" : "#ffffff") + ";" +
                "-fx-border-color: " + (isOutgoing ? "#34b7f1" : "#cccccc") + ";" +
                "-fx-border-width: 1;" +
                "-fx-border-radius: 10;"
            );

            // Message content
            Label contentLabel = new Label(message.getContent());
            contentLabel.setWrapText(true);
            contentLabel.setMaxWidth(400);
            contentLabel.setStyle("-fx-font-size: 13px;");

            // Timestamp and sender
            DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
            String timeText = message.getTimestamp().format(timeFormatter);

            Label metaLabel = new Label(
                (isOutgoing ? "You" : message.getSenderName()) + " • " + timeText
            );
            metaLabel.setStyle(
                "-fx-font-size: 11px;" +
                "-fx-text-fill: #666666;"
            );

            bubble.getChildren().addAll(contentLabel, metaLabel);

            // Container for alignment
            HBox container = new HBox();
            container.setPrefWidth(messagesListView.getWidth() - 20);

            if (isOutgoing) {
                // Align right for outgoing messages
                HBox.setHgrow(bubble, Priority.NEVER);
                container.getChildren().addAll(createSpacer(), bubble);
            } else {
                // Align left for incoming messages
                HBox.setHgrow(bubble, Priority.NEVER);
                container.getChildren().addAll(bubble, createSpacer());
            }

            setGraphic(container);
        }

        private HBox createSpacer() {
            HBox spacer = new HBox();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            return spacer;
        }
    }
}
