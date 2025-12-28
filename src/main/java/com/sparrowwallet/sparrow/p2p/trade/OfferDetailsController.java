package com.sparrowwallet.sparrow.p2p.trade;

import com.sparrowwallet.sparrow.p2p.identity.NostrIdentity;
import com.sparrowwallet.sparrow.p2p.identity.NostrIdentityManager;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;

/**
 * Controller for viewing trade offer details.
 * Displays complete information about a marketplace offer.
 */
public class OfferDetailsController implements Initializable {
    private static final Logger log = LoggerFactory.getLogger(OfferDetailsController.class);

    // Offer info
    @FXML
    private Label offerTypeLabel;

    @FXML
    private Label amountLabel;

    @FXML
    private Label priceLabel;

    @FXML
    private Label effectivePriceLabel;

    @FXML
    private Label minTradeLabel;

    @FXML
    private Label maxTradeLabel;

    // Payment and location
    @FXML
    private Label paymentMethodLabel;

    @FXML
    private Label locationLabel;

    @FXML
    private TextArea locationDetailArea;

    // Terms
    @FXML
    private TextArea descriptionArea;

    @FXML
    private TextArea termsArea;

    @FXML
    private Label escrowTimeLabel;

    // Creator info
    @FXML
    private Label creatorNameLabel;

    @FXML
    private Label creatorNpubLabel;

    @FXML
    private Label creatorReputationLabel;

    // Metadata
    @FXML
    private Label publishedLabel;

    @FXML
    private Label statusLabel;

    @FXML
    private Label viewsLabel;

    // Buttons
    @FXML
    private Button contactButton;

    @FXML
    private Button closeButton;

    private Stage dialogStage;
    private TradeOffer offer;
    private NostrIdentityManager identityManager;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        identityManager = NostrIdentityManager.getInstance();

        // Setup button handlers
        contactButton.setOnAction(e -> handleContactSeller());
        closeButton.setOnAction(e -> closeDialog());
    }

    /**
     * Set the dialog stage
     */
    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    /**
     * Set the offer to display
     */
    public void setOffer(TradeOffer offer) {
        this.offer = offer;
        displayOffer();
    }

    /**
     * Display offer details
     */
    private void displayOffer() {
        if (offer == null) {
            return;
        }

        // Offer type and amount
        offerTypeLabel.setText(offer.getType().getDisplayName());
        offerTypeLabel.setStyle(offer.getType() == TradeOfferType.BUY ?
            "-fx-text-fill: #2ecc71; -fx-font-weight: bold; -fx-font-size: 18px;" :
            "-fx-text-fill: #e74c3c; -fx-font-weight: bold; -fx-font-size: 18px;");

        amountLabel.setText(String.format("%.8f BTC (%,d sats)",
            offer.getAmountBtc(), offer.getAmountSats()));

        // Price
        String priceText = offer.getCurrency() + " " + String.format("%,.2f", offer.getPrice());
        if (offer.isUseMarketPrice()) {
            priceText += " (market price)";
        }
        priceLabel.setText(priceText);

        // Premium/discount
        double premium = offer.getPremiumPercent();
        String premiumText;
        if (premium > 0) {
            premiumText = "+" + String.format("%.1f", premium) + "% premium";
            effectivePriceLabel.setStyle("-fx-text-fill: #e74c3c;");
        } else if (premium < 0) {
            premiumText = String.format("%.1f", premium) + "% discount";
            effectivePriceLabel.setStyle("-fx-text-fill: #2ecc71;");
        } else {
            premiumText = "No premium/discount";
            effectivePriceLabel.setStyle("-fx-text-fill: #7f8c8d;");
        }
        effectivePriceLabel.setText(premiumText);

        // Trading limits
        minTradeLabel.setText(String.format("%.8f BTC (%,d sats)",
            offer.getMinTradeSats() / 100_000_000.0, offer.getMinTradeSats()));
        maxTradeLabel.setText(String.format("%.8f BTC (%,d sats)",
            offer.getMaxTradeSats() / 100_000_000.0, offer.getMaxTradeSats()));

        // Payment and location
        paymentMethodLabel.setText(offer.getPaymentMethod().getDisplayName());

        if (offer.getLocation() != null && !offer.getLocation().isEmpty()) {
            locationLabel.setText(offer.getLocation());
        } else {
            locationLabel.setText("Not specified");
        }

        if (offer.getLocationDetail() != null && !offer.getLocationDetail().isEmpty()) {
            locationDetailArea.setText(offer.getLocationDetail());
            locationDetailArea.setVisible(true);
            locationDetailArea.setManaged(true);
        } else {
            locationDetailArea.setVisible(false);
            locationDetailArea.setManaged(false);
        }

        // Terms
        if (offer.getDescription() != null && !offer.getDescription().isEmpty()) {
            descriptionArea.setText(offer.getDescription());
        } else {
            descriptionArea.setText("No description provided");
        }

        if (offer.getTerms() != null && !offer.getTerms().isEmpty()) {
            termsArea.setText(offer.getTerms());
            termsArea.setVisible(true);
            termsArea.setManaged(true);
        } else {
            termsArea.setVisible(false);
            termsArea.setManaged(false);
        }

        escrowTimeLabel.setText(offer.getEscrowTimeHours() + " hours");

        // Creator info
        creatorNameLabel.setText(offer.getCreatorDisplayName());
        creatorNpubLabel.setText(offer.getCreatorNpub());

        // Reputation (if available)
        // TODO: Fetch reputation from Nostr events
        creatorReputationLabel.setText("No reputation data available");

        // Metadata
        if (offer.getPublishedAt() != null) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            publishedLabel.setText(offer.getPublishedAt().format(formatter));
        } else {
            publishedLabel.setText("Unknown");
        }

        statusLabel.setText(offer.getStatus().getDisplayName());
        viewsLabel.setText(offer.getViewCount() + " views • " +
                          offer.getContactCount() + " contacts");

        // Check if this is our own offer
        NostrIdentity activeIdentity = identityManager.getActiveIdentity();
        if (activeIdentity != null &&
            activeIdentity.getHex().equals(offer.getCreatorHex())) {
            contactButton.setDisable(true);
            contactButton.setText("Your Offer");
        }
    }

    /**
     * Handle contact seller button
     */
    private void handleContactSeller() {
        log.info("Contact seller: {}", offer.getCreatorNpub());

        // Increment contact count
        TradeOfferManager.getInstance().incrementContactCount(offer.getId());

        // Open chat dialog
        try {
            openChatDialog();
        } catch (Exception e) {
            log.error("Failed to open chat", e);
            Alert error = new Alert(Alert.AlertType.ERROR);
            error.setTitle("Error");
            error.setHeaderText("Failed to Open Chat");
            error.setContentText(e.getMessage());
            error.showAndWait();
        }
    }

    /**
     * Open chat dialog with seller
     */
    private void openChatDialog() {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                getClass().getResource("/com/sparrowwallet/sparrow/p2p/chat/chat-dialog.fxml")
            );

            javafx.scene.Parent root = loader.load();

            // Get controller and set peer info
            com.sparrowwallet.sparrow.p2p.chat.ChatDialogController controller = loader.getController();

            // Create new stage for chat
            javafx.stage.Stage chatStage = new javafx.stage.Stage();
            chatStage.initModality(javafx.stage.Modality.NONE);
            chatStage.initOwner(dialogStage);

            controller.setDialogStage(chatStage);
            controller.setPeer(
                offer.getCreatorHex(),
                offer.getCreatorDisplayName(),
                offer.getCreatorNpub()
            );

            javafx.scene.Scene scene = new javafx.scene.Scene(root);
            chatStage.setScene(scene);
            chatStage.show();

            log.info("Opened chat with {}", offer.getCreatorHex().substring(0, 8));

        } catch (Exception e) {
            log.error("Failed to load chat dialog", e);
            throw new RuntimeException("Failed to open chat dialog: " + e.getMessage(), e);
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
}
