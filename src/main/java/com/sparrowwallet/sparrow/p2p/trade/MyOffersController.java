package com.sparrowwallet.sparrow.p2p.trade;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * Controller for managing user's own trade offers.
 * Allows viewing, pausing, cancelling, and monitoring offer statistics.
 */
public class MyOffersController implements Initializable {
    private static final Logger log = LoggerFactory.getLogger(MyOffersController.class);

    @FXML
    private ListView<TradeOffer> myOffersListView;

    @FXML
    private Label selectedOfferTypeLabel;

    @FXML
    private Label selectedOfferAmountLabel;

    @FXML
    private Label selectedOfferPriceLabel;

    @FXML
    private Label selectedOfferStatusLabel;

    @FXML
    private Label selectedOfferPublishedLabel;

    @FXML
    private Label selectedOfferStatsLabel;

    @FXML
    private TextArea selectedOfferDescriptionArea;

    @FXML
    private Button pauseResumeButton;

    @FXML
    private Button cancelButton;

    @FXML
    private Button viewDetailsButton;

    @FXML
    private Button refreshButton;

    @FXML
    private Button closeButton;

    private Stage dialogStage;
    private TradeOfferManager offerManager;
    private TradeOffer selectedOffer;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        offerManager = TradeOfferManager.getInstance();

        // Setup list view
        myOffersListView.setCellFactory(lv -> new MyOfferListCell());
        myOffersListView.getSelectionModel().selectedItemProperty().addListener(
            (obs, oldVal, newVal) -> onOfferSelected(newVal)
        );

        // Setup buttons
        pauseResumeButton.setOnAction(e -> handlePauseResume());
        cancelButton.setOnAction(e -> handleCancel());
        viewDetailsButton.setOnAction(e -> handleViewDetails());
        refreshButton.setOnAction(e -> loadMyOffers());
        closeButton.setOnAction(e -> closeDialog());

        // Disable action buttons initially
        updateButtonStates(null);

        // Load offers
        loadMyOffers();
    }

    /**
     * Set the dialog stage
     */
    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    /**
     * Load user's offers
     */
    private void loadMyOffers() {
        List<TradeOffer> myOffers = offerManager.getMyOffers();
        myOffersListView.getItems().clear();
        myOffersListView.getItems().addAll(myOffers);

        log.info("Loaded {} my offers", myOffers.size());
    }

    /**
     * Handle offer selection
     */
    private void onOfferSelected(TradeOffer offer) {
        selectedOffer = offer;
        updateButtonStates(offer);
        displaySelectedOffer(offer);
    }

    /**
     * Display selected offer details
     */
    private void displaySelectedOffer(TradeOffer offer) {
        if (offer == null) {
            selectedOfferTypeLabel.setText("--");
            selectedOfferAmountLabel.setText("--");
            selectedOfferPriceLabel.setText("--");
            selectedOfferStatusLabel.setText("--");
            selectedOfferPublishedLabel.setText("--");
            selectedOfferStatsLabel.setText("--");
            selectedOfferDescriptionArea.setText("");
            return;
        }

        // Type
        selectedOfferTypeLabel.setText(offer.getType().getDisplayName());
        selectedOfferTypeLabel.setStyle(offer.getType() == TradeOfferType.BUY ?
            "-fx-text-fill: #2ecc71; -fx-font-weight: bold;" :
            "-fx-text-fill: #e74c3c; -fx-font-weight: bold;");

        // Amount
        selectedOfferAmountLabel.setText(String.format("%.8f BTC", offer.getAmountBtc()));

        // Price
        String priceText = offer.getCurrency() + " " + String.format("%,.2f", offer.getPrice());
        if (offer.getPremiumPercent() != 0) {
            priceText += " (" + (offer.getPremiumPercent() > 0 ? "+" : "") +
                        String.format("%.1f", offer.getPremiumPercent()) + "%)";
        }
        selectedOfferPriceLabel.setText(priceText);

        // Status
        selectedOfferStatusLabel.setText(offer.getStatus().getDisplayName());
        String statusStyle = switch (offer.getStatus()) {
            case ACTIVE -> "-fx-text-fill: #2ecc71;";
            case PAUSED -> "-fx-text-fill: #f39c12;";
            case CANCELLED, EXPIRED -> "-fx-text-fill: #95a5a6;";
            case COMPLETED -> "-fx-text-fill: #3498db;";
            default -> "-fx-text-fill: #7f8c8d;";
        };
        selectedOfferStatusLabel.setStyle(statusStyle);

        // Published date
        if (offer.getPublishedAt() != null) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            selectedOfferPublishedLabel.setText(offer.getPublishedAt().format(formatter));
        } else {
            selectedOfferPublishedLabel.setText("Not published");
        }

        // Statistics
        selectedOfferStatsLabel.setText(
            offer.getViewCount() + " views  •  " +
            offer.getContactCount() + " contacts"
        );

        // Description
        if (offer.getDescription() != null && !offer.getDescription().isEmpty()) {
            selectedOfferDescriptionArea.setText(offer.getDescription());
        } else {
            selectedOfferDescriptionArea.setText("No description");
        }
    }

    /**
     * Update button states based on selected offer
     */
    private void updateButtonStates(TradeOffer offer) {
        boolean hasSelection = offer != null;
        boolean canPause = hasSelection && offer.getStatus().canCancel();
        boolean canCancel = hasSelection && offer.getStatus().canCancel();

        pauseResumeButton.setDisable(!canPause);
        cancelButton.setDisable(!canCancel);
        viewDetailsButton.setDisable(!hasSelection);

        // Update pause/resume button text
        if (hasSelection && offer.getStatus() == TradeOfferStatus.PAUSED) {
            pauseResumeButton.setText("Resume Offer");
        } else {
            pauseResumeButton.setText("Pause Offer");
        }
    }

    /**
     * Handle pause/resume offer
     */
    private void handlePauseResume() {
        if (selectedOffer == null) {
            return;
        }

        try {
            if (selectedOffer.getStatus() == TradeOfferStatus.PAUSED) {
                // Resume offer
                selectedOffer.publish(); // Sets status back to ACTIVE
                log.info("Resumed offer: {}", selectedOffer.getId());

                Alert success = new Alert(Alert.AlertType.INFORMATION);
                success.setTitle("Offer Resumed");
                success.setHeaderText("Offer Resumed Successfully");
                success.setContentText("Your offer is now active and visible in the marketplace.");
                success.showAndWait();

            } else {
                // Pause offer
                selectedOffer.pause();
                log.info("Paused offer: {}", selectedOffer.getId());

                Alert success = new Alert(Alert.AlertType.INFORMATION);
                success.setTitle("Offer Paused");
                success.setHeaderText("Offer Paused Successfully");
                success.setContentText("Your offer is now hidden from the marketplace.\n\nYou can resume it at any time.");
                success.showAndWait();
            }

            // Refresh display
            loadMyOffers();
            myOffersListView.getSelectionModel().select(selectedOffer);

        } catch (Exception e) {
            log.error("Failed to pause/resume offer", e);
            Alert error = new Alert(Alert.AlertType.ERROR);
            error.setTitle("Error");
            error.setHeaderText("Failed to Update Offer");
            error.setContentText(e.getMessage());
            error.showAndWait();
        }
    }

    /**
     * Handle cancel offer
     */
    private void handleCancel() {
        if (selectedOffer == null) {
            return;
        }

        // Confirm cancellation
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Cancel Offer");
        confirm.setHeaderText("Cancel Trade Offer");
        confirm.setContentText("Are you sure you want to cancel this offer?\n\n" +
            selectedOffer.getDisplaySummary() + "\n\n" +
            "This action cannot be undone.");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                // Cancel offer
                offerManager.cancelOffer(selectedOffer.getId());
                log.info("Cancelled offer: {}", selectedOffer.getId());

                Alert success = new Alert(Alert.AlertType.INFORMATION);
                success.setTitle("Offer Cancelled");
                success.setHeaderText("Offer Cancelled Successfully");
                success.setContentText("Your offer has been cancelled and removed from the marketplace.");
                success.showAndWait();

                // Refresh display
                loadMyOffers();

            } catch (Exception e) {
                log.error("Failed to cancel offer", e);
                Alert error = new Alert(Alert.AlertType.ERROR);
                error.setTitle("Error");
                error.setHeaderText("Failed to Cancel Offer");
                error.setContentText(e.getMessage());
                error.showAndWait();
            }
        }
    }

    /**
     * Handle view details
     */
    private void handleViewDetails() {
        if (selectedOffer == null) {
            return;
        }

        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                getClass().getResource("/com/sparrowwallet/sparrow/p2p/trade/offer-details-dialog.fxml")
            );
            javafx.stage.Stage stage = new javafx.stage.Stage();
            stage.setTitle("Offer Details - Sparrow");
            stage.setScene(new javafx.scene.Scene(loader.load()));
            stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            stage.setMinWidth(700);
            stage.setMinHeight(750);

            OfferDetailsController controller = loader.getController();
            controller.setDialogStage(stage);
            controller.setOffer(selectedOffer);

            stage.showAndWait();

        } catch (Exception e) {
            log.error("Failed to open Offer Details dialog", e);
            Alert error = new Alert(Alert.AlertType.ERROR);
            error.setTitle("Error");
            error.setHeaderText("Failed to View Offer");
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
     * Custom ListCell for my offers
     */
    private static class MyOfferListCell extends ListCell<TradeOffer> {
        @Override
        protected void updateItem(TradeOffer offer, boolean empty) {
            super.updateItem(offer, empty);

            if (empty || offer == null) {
                setText(null);
                setGraphic(null);
            } else {
                VBox content = new VBox(5);

                // Main line: Type, Amount, Price
                HBox mainLine = new HBox(10);
                mainLine.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

                Label typeLabel = new Label(offer.getType().getDisplayName().toUpperCase());
                typeLabel.setStyle("-fx-font-weight: bold;");

                Label amountLabel = new Label(String.format("%.8f BTC", offer.getAmountBtc()));

                Label priceLabel = new Label("@ " + offer.getCurrency() + " " +
                    String.format("%.2f", offer.getPrice()));

                mainLine.getChildren().addAll(typeLabel, amountLabel, priceLabel);

                // Details line: Status, Payment, Stats
                HBox detailsLine = new HBox(10);
                detailsLine.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

                Label statusLabel = new Label("• " + offer.getStatus().getDisplayName());
                String statusColor = switch (offer.getStatus()) {
                    case ACTIVE -> "#2ecc71";
                    case PAUSED -> "#f39c12";
                    case CANCELLED, EXPIRED -> "#95a5a6";
                    case COMPLETED -> "#3498db";
                    default -> "#7f8c8d";
                };
                statusLabel.setStyle("-fx-text-fill: " + statusColor + ";");

                Label paymentLabel = new Label("• " + offer.getPaymentMethod().getDisplayName());
                paymentLabel.setStyle("-fx-text-fill: #666;");

                Label statsLabel = new Label("• " + offer.getViewCount() + " views, " +
                    offer.getContactCount() + " contacts");
                statsLabel.setStyle("-fx-text-fill: #666;");

                detailsLine.getChildren().addAll(statusLabel, paymentLabel, statsLabel);

                content.getChildren().addAll(mainLine, detailsLine);
                setGraphic(content);
                setText(null);
            }
        }
    }
}
