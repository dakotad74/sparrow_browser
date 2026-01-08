package com.sparrowwallet.sparrow.p2p.ui;

import com.sparrowwallet.sparrow.p2p.chat.ChatMessage;
import com.sparrowwallet.sparrow.p2p.chat.ChatService;
import com.sparrowwallet.sparrow.p2p.identity.NostrIdentity;
import com.sparrowwallet.sparrow.p2p.identity.NostrIdentityManager;
import com.sparrowwallet.sparrow.p2p.trade.TradeOffer;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;

public class ChatPanel extends VBox {
    private static final Logger log = LoggerFactory.getLogger(ChatPanel.class);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final String peerHex;
    private final String peerDisplayName;
    private final String conversationId;
    private final VBox messagesContainer;
    private final ScrollPane scrollPane;
    private TextField inputField;
    private Button sendButton;
    private Label headerLabel;
    private final Label statusLabel;

    private final ChatService chatService;
    private final NostrIdentityManager identityManager;
    private Consumer<Void> onCloseHandler;

    public ChatPanel(String peerHex, String peerDisplayName, String conversationId) {
        this.peerHex = peerHex;
        this.peerDisplayName = peerDisplayName;
        this.conversationId = conversationId != null ? conversationId : peerHex;
        this.chatService = ChatService.getInstance();
        this.identityManager = NostrIdentityManager.getInstance();

        setSpacing(0);
        getStyleClass().add("chat-panel");
        setMinWidth(300);
        setPrefWidth(350);

        HBox header = createHeader();
        messagesContainer = new VBox(8);
        messagesContainer.setPadding(new Insets(10));
        messagesContainer.setFillWidth(true);

        scrollPane = new ScrollPane(messagesContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.getStyleClass().add("chat-scroll");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        HBox inputBox = createInputBox();

        statusLabel = new Label("Encrypted with NIP-04");
        statusLabel.getStyleClass().add("chat-status");
        statusLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #666; -fx-padding: 4 10;");

        getChildren().addAll(header, scrollPane, inputBox, statusLabel);

        chatService.addMessageHandler(this::handleIncomingMessage);
        loadChatHistory();
    }

    public ChatPanel(TradeOffer offer) {
        this(offer.getCreatorHex(), offer.getCreatorDisplayName(), offer.getId());
    }

    private HBox createHeader() {
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(10, 15, 10, 15));
        header.getStyleClass().add("chat-header");
        header.setStyle("-fx-background-color: derive(-fx-base, -5%); -fx-border-color: derive(-fx-base, -15%); -fx-border-width: 0 0 1 0;");

        Circle avatar = new Circle(16);
        avatar.setFill(Color.rgb(100, 100, 100));

        VBox nameBox = new VBox(2);
        headerLabel = new Label(peerDisplayName);
        headerLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        Label npubLabel = new Label(shortenNpub(peerHex));
        npubLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #888;");

        nameBox.getChildren().addAll(headerLabel, npubLabel);
        HBox.setHgrow(nameBox, Priority.ALWAYS);

        Button closeButton = new Button("×");
        closeButton.getStyleClass().add("chat-close-button");
        closeButton.setStyle("-fx-font-size: 18px; -fx-background-color: transparent; -fx-text-fill: #888;");
        closeButton.setOnAction(e -> {
            if (onCloseHandler != null) {
                onCloseHandler.accept(null);
            }
        });

        header.getChildren().addAll(avatar, nameBox, closeButton);
        return header;
    }

    private HBox createInputBox() {
        HBox inputBox = new HBox(8);
        inputBox.setAlignment(Pos.CENTER);
        inputBox.setPadding(new Insets(10, 15, 10, 15));
        inputBox.getStyleClass().add("chat-input-box");
        inputBox.setStyle("-fx-background-color: derive(-fx-base, -3%); -fx-border-color: derive(-fx-base, -15%); -fx-border-width: 1 0 0 0;");

        inputField = new TextField();
        inputField.setPromptText("Type a message...");
        inputField.getStyleClass().add("chat-input");
        HBox.setHgrow(inputField, Priority.ALWAYS);

        inputField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER && !event.isShiftDown()) {
                sendMessage();
                event.consume();
            }
        });

        sendButton = new Button("Send");
        sendButton.getStyleClass().add("chat-send-button");
        sendButton.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-font-weight: bold;");
        sendButton.setOnAction(e -> sendMessage());

        inputBox.getChildren().addAll(inputField, sendButton);
        return inputBox;
    }

    private void sendMessage() {
        String text = inputField.getText().trim();
        if (text.isEmpty()) {
            return;
        }

        NostrIdentity identity = identityManager.getActiveIdentity();
        if (identity == null) {
            showError("No active identity");
            return;
        }

        try {
            inputField.setDisable(true);
            sendButton.setDisable(true);

            chatService.sendMessage(peerHex, text, conversationId);

            ChatMessage sentMessage = new ChatMessage(
                identity.getHex(),
                identity.getDisplayName(),
                peerHex,
                peerDisplayName,
                text,
                java.time.LocalDateTime.now(),
                true
            );
            addMessageToUI(sentMessage);

            inputField.clear();
            scrollToBottom();

        } catch (Exception e) {
            log.error("Failed to send message", e);
            showError("Failed to send: " + e.getMessage());
        } finally {
            inputField.setDisable(false);
            sendButton.setDisable(false);
            inputField.requestFocus();
        }
    }

    private void handleIncomingMessage(ChatMessage message) {
        if (message.getSenderHex().equals(peerHex) || message.getRecipientHex().equals(peerHex)) {
            Platform.runLater(() -> {
                addMessageToUI(message);
                scrollToBottom();
            });
        }
    }

    private void addMessageToUI(ChatMessage message) {
        NostrIdentity myIdentity = identityManager.getActiveIdentity();
        boolean isMine = myIdentity != null && message.getSenderHex().equals(myIdentity.getHex());

        HBox messageRow = new HBox();
        messageRow.setFillHeight(false);

        VBox bubble = new VBox(4);
        bubble.setMaxWidth(250);
        bubble.setPadding(new Insets(8, 12, 8, 12));
        bubble.getStyleClass().add(isMine ? "chat-bubble-sent" : "chat-bubble-received");

        if (isMine) {
            bubble.setStyle("-fx-background-color: #2196F3; -fx-background-radius: 12 12 4 12;");
            messageRow.setAlignment(Pos.CENTER_RIGHT);
        } else {
            bubble.setStyle("-fx-background-color: derive(-fx-base, -10%); -fx-background-radius: 12 12 12 4;");
            messageRow.setAlignment(Pos.CENTER_LEFT);
        }

        Label textLabel = new Label(message.getContent());
        textLabel.setWrapText(true);
        textLabel.setStyle(isMine ? "-fx-text-fill: white;" : "-fx-text-fill: -fx-text-base-color;");

        Label timeLabel = new Label(message.getTimestamp().format(TIME_FORMAT));
        timeLabel.setStyle("-fx-font-size: 9px; -fx-text-fill: " + (isMine ? "rgba(255,255,255,0.7);" : "#888;"));

        bubble.getChildren().addAll(textLabel, timeLabel);
        messageRow.getChildren().add(bubble);
        messagesContainer.getChildren().add(messageRow);
    }

    private void loadChatHistory() {
        try {
            List<ChatMessage> history = chatService.getConversation(conversationId);
            for (ChatMessage msg : history) {
                addMessageToUI(msg);
            }
            scrollToBottom();
        } catch (Exception e) {
            log.warn("Could not load chat history", e);
        }
    }

    private void scrollToBottom() {
        Platform.runLater(() -> {
            scrollPane.layout();
            scrollPane.setVvalue(1.0);
        });
    }

    private void showError(String message) {
        statusLabel.setText("Error: " + message);
        statusLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #F44336; -fx-padding: 4 10;");
    }

    private String shortenNpub(String hex) {
        if (hex == null || hex.length() < 16) return hex;
        return hex.substring(0, 8) + "..." + hex.substring(hex.length() - 8);
    }

    public void setOnClose(Consumer<Void> handler) {
        this.onCloseHandler = handler;
    }

    public String getPeerHex() {
        return peerHex;
    }

    public String getConversationId() {
        return conversationId;
    }
}
