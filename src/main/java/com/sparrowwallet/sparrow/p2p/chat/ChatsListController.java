package com.sparrowwallet.sparrow.p2p.chat;

import com.sparrowwallet.sparrow.p2p.identity.NostrIdentity;
import com.sparrowwallet.sparrow.p2p.identity.NostrIdentityManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.*;

/**
 * Controller for managing active chat conversations.
 * Shows list of peers with whom the user has exchanged messages.
 */
public class ChatsListController implements Initializable {
    private static final Logger log = LoggerFactory.getLogger(ChatsListController.class);

    @FXML
    private ListView<ConversationSummary> conversationsListView;

    @FXML
    private Button refreshButton;

    @FXML
    private Button closeButton;

    private Stage dialogStage;
    private ChatService chatService;
    private NostrIdentityManager identityManager;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        chatService = ChatService.getInstance();
        identityManager = NostrIdentityManager.getInstance();

        // Setup list with custom cell factory
        conversationsListView.setCellFactory(lv -> new ConversationListCell());

        // Double-click to open chat
        conversationsListView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                ConversationSummary selected = conversationsListView.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    openChatDialog(selected);
                }
            }
        });

        // Setup button handlers
        refreshButton.setOnAction(e -> loadConversations());
        closeButton.setOnAction(e -> closeDialog());

        // Load conversations
        loadConversations();
    }

    /**
     * Set the dialog stage
     */
    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    /**
     * Load conversations from ChatService
     */
    private void loadConversations() {
        Map<String, List<ChatMessage>> allConversations = chatService.getAllConversations();

        List<ConversationSummary> summaries = new ArrayList<>();

        for (Map.Entry<String, List<ChatMessage>> entry : allConversations.entrySet()) {
            String peerHex = entry.getKey();
            List<ChatMessage> messages = entry.getValue();

            if (!messages.isEmpty()) {
                // Get last message
                ChatMessage lastMessage = messages.get(messages.size() - 1);

                // Count unread messages
                long unreadCount = messages.stream()
                    .filter(msg -> !msg.isRead() && !msg.isOutgoing())
                    .count();

                // Get peer name (from last message)
                String peerName = lastMessage.getSenderName();
                if (lastMessage.isOutgoing()) {
                    // If last message is outgoing, get name from earlier messages
                    peerName = messages.stream()
                        .filter(msg -> !msg.isOutgoing())
                        .findFirst()
                        .map(ChatMessage::getSenderName)
                        .orElse("Unknown");
                }

                ConversationSummary summary = new ConversationSummary(
                    peerHex,
                    peerName,
                    lastMessage.getContent(),
                    lastMessage.getTimestamp(),
                    unreadCount,
                    messages.size()
                );

                summaries.add(summary);
            }
        }

        // Sort by last message timestamp (most recent first)
        summaries.sort((a, b) -> b.lastMessageTime.compareTo(a.lastMessageTime));

        conversationsListView.getItems().setAll(summaries);

        log.info("Loaded {} conversations", summaries.size());
    }

    /**
     * Open chat dialog with selected peer
     */
    private void openChatDialog(ConversationSummary summary) {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/com/sparrowwallet/sparrow/p2p/chat/chat-dialog.fxml")
            );

            Parent root = loader.load();

            // Get controller and set peer info
            ChatDialogController controller = loader.getController();

            // Create new stage for chat
            Stage chatStage = new Stage();
            chatStage.initModality(Modality.NONE);
            chatStage.initOwner(dialogStage);

            // Get the real peer hex from the conversation (not the conversationId)
            String conversationId = summary.peerHex;  // summary.peerHex is actually conversationId
            String realPeerHex = chatService.getPeerHexForConversation(conversationId);

            if (realPeerHex == null) {
                log.error("Could not determine peer hex for conversation {}", conversationId.substring(0, 8));
                return;
            }

            controller.setDialogStage(chatStage);
            controller.setPeer(
                realPeerHex,
                summary.peerName,
                "npub1" + realPeerHex.substring(0, 16), // TODO: proper npub encoding
                conversationId.equals(realPeerHex) ? null : conversationId  // Pass offerId if different from peerHex
            );

            Scene scene = new Scene(root);
            chatStage.setScene(scene);
            chatStage.show();

            // Refresh list when chat closes
            chatStage.setOnHidden(e -> loadConversations());

            log.info("Opened chat with {}", summary.peerHex.substring(0, 8));

        } catch (Exception e) {
            log.error("Failed to open chat dialog", e);
            Alert error = new Alert(Alert.AlertType.ERROR);
            error.setTitle("Error");
            error.setHeaderText("Failed to Open Chat");
            error.setContentText(e.getMessage());
            error.showAndWait();
        }
    }

    /**
     * Close the dialog
     */
    private void closeDialog() {
        if (dialogStage != null) {
            dialogStage.close();
        }
    }

    /**
     * Summary of a conversation for list display
     */
    private static class ConversationSummary {
        final String peerHex;
        final String peerName;
        final String lastMessageContent;
        final java.time.LocalDateTime lastMessageTime;
        final long unreadCount;
        final int totalMessages;

        ConversationSummary(String peerHex, String peerName, String lastMessageContent,
                          java.time.LocalDateTime lastMessageTime, long unreadCount, int totalMessages) {
            this.peerHex = peerHex;
            this.peerName = peerName;
            this.lastMessageContent = lastMessageContent;
            this.lastMessageTime = lastMessageTime;
            this.unreadCount = unreadCount;
            this.totalMessages = totalMessages;
        }
    }

    /**
     * Custom ListCell for rendering conversation summaries
     */
    private static class ConversationListCell extends ListCell<ConversationSummary> {
        @Override
        protected void updateItem(ConversationSummary summary, boolean empty) {
            super.updateItem(summary, empty);

            if (empty || summary == null) {
                setGraphic(null);
                setText(null);
                return;
            }

            // Format timestamp
            java.time.format.DateTimeFormatter formatter =
                java.time.format.DateTimeFormatter.ofPattern("MMM dd, HH:mm");
            String timeStr = summary.lastMessageTime.format(formatter);

            // Truncate last message if too long
            String preview = summary.lastMessageContent;
            if (preview.length() > 60) {
                preview = preview.substring(0, 57) + "...";
            }

            // Build display text
            String line1 = summary.peerName +
                          (summary.unreadCount > 0 ? " (" + summary.unreadCount + " new)" : "");
            String line2 = preview + " • " + timeStr;

            // Style
            String style = summary.unreadCount > 0 ?
                "-fx-font-weight: bold;" : "-fx-font-weight: normal;";

            Label nameLabel = new Label(line1);
            nameLabel.setStyle(style + "-fx-font-size: 14px;");

            Label previewLabel = new Label(line2);
            previewLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #666666;");

            javafx.scene.layout.VBox container = new javafx.scene.layout.VBox(3);
            container.getChildren().addAll(nameLabel, previewLabel);

            setGraphic(container);
        }
    }
}
