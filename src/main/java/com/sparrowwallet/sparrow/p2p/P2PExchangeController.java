package com.sparrowwallet.sparrow.p2p;

import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.p2p.NostrP2PService;
import com.sparrowwallet.sparrow.p2p.identity.NostrIdentity;
import com.sparrowwallet.sparrow.p2p.identity.NostrIdentityManager;
import com.sparrowwallet.sparrow.p2p.trade.TradeOffer;
import com.sparrowwallet.sparrow.p2p.trade.TradeOfferManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Main controller for the P2P Exchange tab.
 *
 * This is the primary interface for P2P Bitcoin trading via Nostr.
 * Features:
 * - Nostr identity management (ephemeral/persistent)
 * - Trade offer marketplace
 * - Active trades management
 * - Reputation system
 */
public class P2PExchangeController implements Initializable {
    private static final Logger log = LoggerFactory.getLogger(P2PExchangeController.class);

    // Identity section
    @FXML
    private Label identityNameLabel;

    @FXML
    private Label identityNpubLabel;

    @FXML
    private Label identityTypeLabel;

    @FXML
    private Label identityReputationLabel;

    @FXML
    private Label identityCreatedLabel;

    @FXML
    private Button changeIdentityButton;

    @FXML
    private Button createIdentityButton;

    // Marketplace section
    @FXML
    private Button createOfferButton;

    @FXML
    private Button refreshButton;

    @FXML
    private ComboBox<String> typeFilterCombo;

    @FXML
    private ComboBox<String> currencyFilterCombo;

    @FXML
    private ComboBox<String> locationFilterCombo;

    @FXML
    private ComboBox<String> paymentFilterCombo;

    @FXML
    private ComboBox<String> amountFilterCombo;

    @FXML
    private ListView<TradeOffer> offersListView;

    // My Activity section
    @FXML
    private Label activeTradesLabel;

    @FXML
    private Label completedTradesLabel;

    @FXML
    private Label openOffersLabel;

    @FXML
    private VBox myActivityBox;

    @FXML
    private ListView<String> activityListView;

    @FXML
    private Button createOfferActivityButton;

    @FXML
    private Button manageOffersButton;

    @FXML
    private Button viewChatsButton;

    // Status bar
    @FXML
    private Label statusLabel;

    private NostrIdentityManager identityManager;
    private TradeOfferManager offerManager;
    private NostrP2PService nostrP2PService;
    private NostrIdentity currentIdentity;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        log.error("=== P2P EXCHANGE CONTROLLER INITIALIZING ===");

        identityManager = NostrIdentityManager.getInstance();
        offerManager = TradeOfferManager.getInstance();
        nostrP2PService = NostrP2PService.getInstance();

        // Initialize identity
        initializeIdentity();

        // Initialize filters
        initializeFilters();

        // Initialize marketplace
        initializeMarketplace();

        // Initialize activity section
        initializeActivity();

        // Start Nostr P2P service
        initializeNostrService();

        updateStatusBar();
    }

    /**
     * Initialize Nostr P2P service
     */
    private void initializeNostrService() {
        log.error("=== STARTING NOSTR P2P SERVICE ===");

        // Start service in background
        new Thread(() -> {
            try {
                // IMPORTANT: Service.start() must be called from the FX Application Thread
                Platform.runLater(() -> {
                    try {
                        nostrP2PService.start();
                    } catch (Exception e) {
                        log.error("Failed to start Nostr P2P Service from FX thread", e);
                        Platform.runLater(() -> {
                            statusLabel.setText("Failed to connect to Nostr relays");
                        });
                    }
                });

                // Wait for service to actually start
                Thread.sleep(2000);

                // Subscribe to offers
                log.error("=== SUBSCRIBING TO OFFERS ===");
                nostrP2PService.subscribeToOffers();

                // Subscribe to incoming chat messages
                log.error("=== SUBSCRIBING TO CHAT MESSAGES ===");
                com.sparrowwallet.sparrow.p2p.chat.ChatService chatService = com.sparrowwallet.sparrow.p2p.chat.ChatService.getInstance();
                chatService.subscribeToAllMessages();
                log.error("=== CHAT SUBSCRIPTION COMPLETED ===");

                // Register listener for identity changes to re-subscribe automatically
                com.sparrowwallet.sparrow.p2p.identity.NostrIdentityManager identityManager =
                    com.sparrowwallet.sparrow.p2p.identity.NostrIdentityManager.getInstance();
                identityManager.addIdentityChangeListener((newIdentity, oldIdentity) -> {
                    Platform.runLater(() -> {
                        chatService.updateSubscriptionForIdentity(newIdentity, oldIdentity);
                        // Refresh marketplace to update visible offers
                        refreshOffers();
                    });
                });
                log.info("Registered identity change listener for chat re-subscription and offer refresh");

                // Update status on JavaFX thread
                Platform.runLater(this::updateStatusBar);

                log.info("Nostr P2P service started successfully");

            } catch (Exception e) {
                log.error("Failed to complete Nostr P2P service initialization", e);
                Platform.runLater(() -> {
                    statusLabel.setText("Failed to connect to Nostr relays");
                });
            }
        }, "NostrP2PInit").start();
    }

    /**
     * Initialize identity section
     */
    private void initializeIdentity() {
        // Get or create active identity
        currentIdentity = identityManager.getOrCreateActiveIdentity();

        updateIdentityDisplay();

        // Setup button handlers
        changeIdentityButton.setOnAction(e -> openIdentityManager());
        createIdentityButton.setOnAction(e -> createNewIdentity());
    }

    /**
     * Update identity display
     */
    private void updateIdentityDisplay() {
        if (currentIdentity == null) {
            identityNameLabel.setText("No identity");
            identityNpubLabel.setText("");
            identityTypeLabel.setText("");
            identityReputationLabel.setText("");
            identityCreatedLabel.setText("");
            return;
        }

        identityNameLabel.setText(currentIdentity.getDisplayName());
        identityNpubLabel.setText(currentIdentity.getShortNpub());

        String typeText = currentIdentity.getType().name();
        if (currentIdentity.isEphemeral()) {
            typeText += " (Single-use)";
        } else {
            typeText += " (Long-term)";
        }
        identityTypeLabel.setText("Type: " + typeText);

        // Reputation (persistent only)
        if (currentIdentity.isPersistent()) {
            String reputation = currentIdentity.getReputationStars();
            if (reputation.isEmpty()) {
                reputation = "N/A (New identity)";
            } else {
                reputation += " (" + currentIdentity.getCompletedTrades() + " trades, " +
                             String.format("%.1f", currentIdentity.getAverageRating()) + " rating)";
            }
            identityReputationLabel.setText("Reputation: " + reputation);
        } else {
            identityReputationLabel.setText("Reputation: N/A (Ephemeral)");
        }

        String created = "Created: " +
            (currentIdentity.getCreatedAt() != null ?
                formatTimeAgo(currentIdentity.getCreatedAt()) : "Unknown");

        if (currentIdentity.getExpiresAt() != null) {
            created += " • Expires: " +
                (currentIdentity.isEphemeral() ? "After trade completion" : "Never");
        }
        identityCreatedLabel.setText(created);

        log.info("Updated identity display: {}", currentIdentity.getDisplayName());
    }

    /**
     * Format time ago (simple version)
     */
    private String formatTimeAgo(java.time.LocalDateTime dateTime) {
        java.time.Duration duration = java.time.Duration.between(dateTime, java.time.LocalDateTime.now());
        long minutes = duration.toMinutes();

        if (minutes < 1) return "Just now";
        if (minutes < 60) return minutes + " min ago";

        long hours = duration.toHours();
        if (hours < 24) return hours + " hour" + (hours > 1 ? "s" : "") + " ago";

        long days = duration.toDays();
        return days + " day" + (days > 1 ? "s" : "") + " ago";
    }

    /**
     * Initialize marketplace filters
     */
    private void initializeFilters() {
        // Type filter
        typeFilterCombo.getItems().addAll("All", "Buy BTC", "Sell BTC");
        typeFilterCombo.setValue("All");

        // Currency filter
        currencyFilterCombo.getItems().addAll("All", "USD", "EUR", "GBP", "JPY", "CNY");
        currencyFilterCombo.setValue("All");

        // Location filter
        locationFilterCombo.getItems().addAll("Any", "Madrid", "Barcelona", "Valencia", "Seville");
        locationFilterCombo.setValue("Any");

        // Payment filter
        paymentFilterCombo.getItems().addAll("All", "Cash in person", "Bank transfer", "Other");
        paymentFilterCombo.setValue("All");

        // Amount filter
        amountFilterCombo.getItems().addAll("Any", "< 0.01 BTC", "0.01 - 0.1 BTC", "> 0.1 BTC");
        amountFilterCombo.setValue("Any");

        // Add change listeners
        typeFilterCombo.setOnAction(e -> applyFilters());
        currencyFilterCombo.setOnAction(e -> applyFilters());
        locationFilterCombo.setOnAction(e -> applyFilters());
        paymentFilterCombo.setOnAction(e -> applyFilters());
        amountFilterCombo.setOnAction(e -> applyFilters());
    }

    /**
     * Initialize marketplace
     */
    private void initializeMarketplace() {
        createOfferButton.setOnAction(e -> createOffer());
        refreshButton.setOnAction(e -> refreshOffers());

        // Setup custom cell factory for offers
        offersListView.setCellFactory(lv -> new OfferListCell());

        // Setup click handler to view offer details
        offersListView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) { // Double-click
                TradeOffer selectedOffer = offersListView.getSelectionModel().getSelectedItem();
                if (selectedOffer != null) {
                    viewOfferDetails(selectedOffer);
                }
            }
        });

        // Register listener for automatic UI updates when offers arrive
        offerManager.addOfferUpdateListener(this::refreshOffers);

        // Load initial offers
        refreshOffers();

        log.info("Marketplace initialized");
    }

    /**
     * Initialize activity section
     */
    private void initializeActivity() {
        activeTradesLabel.setText("Active Trades: 0");
        completedTradesLabel.setText("Completed Trades: 0");
        openOffersLabel.setText("Open Offers: 0");

        createOfferActivityButton.setOnAction(e -> createOffer());
        manageOffersButton.setOnAction(e -> manageOffers());
        viewChatsButton.setOnAction(e -> viewChats());

        // Add initial activity
        activityListView.getItems().add("• Identity created (" + formatTimeAgo(currentIdentity.getCreatedAt()) + ")");
        activityListView.getItems().add("• Connected to Nostr relays");
        activityListView.getItems().add("• Marketplace loaded");

        log.info("Activity section initialized");
    }

    /**
     * Update status bar
     */
    private void updateStatusBar() {
        // Get relay connection status
        int connectedRelays = nostrP2PService.getConnectedRelayCount();
        int offerCount = offerManager.getAllOffers().size();

        String status;
        if (connectedRelays == 0) {
            status = "Connecting to Nostr relays...";
        } else {
            status = "Connected to " + connectedRelays + " Nostr relay" +
                    (connectedRelays == 1 ? "" : "s") + "  •  " +
                    offerCount + " offer" + (offerCount == 1 ? "" : "s") + " available";
        }

        statusLabel.setText(status);
    }

    /**
     * Apply marketplace filters
     */
    private void applyFilters() {
        log.info("Applying filters: type={}, currency={}, location={}, payment={}, amount={}",
            typeFilterCombo.getValue(),
            currencyFilterCombo.getValue(),
            locationFilterCombo.getValue(),
            paymentFilterCombo.getValue(),
            amountFilterCombo.getValue());

        Platform.runLater(() -> {
            // Get filter values
            String typeFilter = typeFilterCombo.getValue();
            String currencyFilter = currencyFilterCombo.getValue();
            String locationFilter = locationFilterCombo.getValue();
            String paymentFilter = paymentFilterCombo.getValue();
            String amountFilter = amountFilterCombo.getValue();

            // Convert type filter to enum
            com.sparrowwallet.sparrow.p2p.trade.TradeOfferType typeEnum = null;
            if ("Buy BTC".equals(typeFilter)) {
                typeEnum = com.sparrowwallet.sparrow.p2p.trade.TradeOfferType.BUY;
            } else if ("Sell BTC".equals(typeFilter)) {
                typeEnum = com.sparrowwallet.sparrow.p2p.trade.TradeOfferType.SELL;
            }

            // Convert payment filter to enum
            com.sparrowwallet.sparrow.p2p.trade.PaymentMethod paymentEnum = null;
            if (!"All".equals(paymentFilter)) {
                try {
                    // Match display name to enum
                    for (com.sparrowwallet.sparrow.p2p.trade.PaymentMethod pm : com.sparrowwallet.sparrow.p2p.trade.PaymentMethod.values()) {
                        if (pm.getDisplayName().equals(paymentFilter)) {
                            paymentEnum = pm;
                            break;
                        }
                    }
                } catch (Exception e) {
                    log.warn("Could not parse payment method filter: {}", paymentFilter);
                }
            }

            // Apply filters through manager
            List<TradeOffer> filteredOffers = offerManager.applyFilters(
                typeEnum,
                "All".equals(currencyFilter) ? null : currencyFilter,
                paymentEnum,
                "Any".equals(locationFilter) ? null : locationFilter,
                "Any".equals(amountFilter) ? null : amountFilter
            );

            // Update ListView
            offersListView.getItems().clear();
            offersListView.getItems().addAll(filteredOffers);

            // Update status
            int offerCount = filteredOffers.size();
            statusLabel.setText("Connected to 3 Nostr relays  •  " + offerCount + " offer" +
                              (offerCount == 1 ? "" : "s") + " available");

            log.info("Applied filters, showing {} offers", offerCount);
        });
    }

    /**
     * Refresh offers from Nostr
     */
    private void refreshOffers() {
        log.error("=== REFRESH OFFERS CALLED ===");

        Platform.runLater(() -> {
            // Get all offers from manager
            List<TradeOffer> offers = offerManager.getAllOffers();

            log.error("=== Got {} offers from manager ===", offers.size());

            // Update ListView
            offersListView.getItems().clear();
            offersListView.getItems().addAll(offers);

            log.error("=== ListView now has {} items ===", offersListView.getItems().size());

            // Update status
            int offerCount = offers.size();
            statusLabel.setText("Connected to 3 Nostr relays  •  " + offerCount + " offer" + (offerCount == 1 ? "" : "s") + " available");

            // Update activity stats
            updateActivityStats();

            log.error("=== REFRESH COMPLETE: {} offers ===", offerCount);
        });
    }

    /**
     * Open identity manager dialog
     */
    private void openIdentityManager() {
        log.info("Opening identity manager...");
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                getClass().getResource("/com/sparrowwallet/sparrow/p2p/identity/identity-manager.fxml")
            );
            javafx.stage.Stage stage = new javafx.stage.Stage();
            stage.setTitle("Identity Manager - Sparrow");
            stage.setScene(new javafx.scene.Scene(loader.load()));
            stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            stage.setMinWidth(900);
            stage.setMinHeight(700);

            com.sparrowwallet.sparrow.p2p.identity.IdentityManagerController controller = loader.getController();
            controller.setDialogStage(stage);

            stage.showAndWait();

            // Refresh identity display after dialog closes
            currentIdentity = identityManager.getActiveIdentity();
            updateIdentityDisplay();
        } catch (Exception e) {
            log.error("Failed to open Identity Manager", e);
            Alert error = new Alert(Alert.AlertType.ERROR);
            error.setTitle("Error");
            error.setHeaderText("Failed to Open Identity Manager");
            error.setContentText(e.getMessage());
            error.showAndWait();
        }
    }

    /**
     * Create new identity
     */
    private void createNewIdentity() {
        log.info("Creating new identity...");

        // Simple dialog for now
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Create Identity");
        dialog.setHeaderText("Create New Nostr Identity");
        dialog.setContentText("Choose identity type:\nLeave empty for ephemeral (privacy)\nEnter name for persistent (reputation)");

        dialog.showAndWait().ifPresent(name -> {
            try {
                NostrIdentity newIdentity;
                if (name.trim().isEmpty()) {
                    newIdentity = identityManager.createEphemeralIdentity();
                } else {
                    newIdentity = identityManager.createPersistentIdentity(name);
                }

                identityManager.setActiveIdentity(newIdentity);
                currentIdentity = newIdentity;
                updateIdentityDisplay();

                log.info("Created new identity: {}", newIdentity.getDisplayName());

                Alert success = new Alert(Alert.AlertType.INFORMATION);
                success.setTitle("Identity Created");
                success.setHeaderText("New Identity Created Successfully");
                success.setContentText("Name: " + newIdentity.getDisplayName() + "\n" +
                    "Type: " + newIdentity.getType() + "\n" +
                    "npub: " + newIdentity.getNpub());
                success.showAndWait();

            } catch (Exception e) {
                log.error("Failed to create identity", e);
                Alert error = new Alert(Alert.AlertType.ERROR);
                error.setTitle("Error");
                error.setHeaderText("Failed to Create Identity");
                error.setContentText(e.getMessage());
                error.showAndWait();
            }
        });
    }

    /**
     * Create new offer
     */
    private void createOffer() {
        log.info("Creating new offer...");

        // Check if we have an active identity
        if (currentIdentity == null) {
            Alert error = new Alert(Alert.AlertType.WARNING);
            error.setTitle("No Identity");
            error.setHeaderText("Identity Required");
            error.setContentText("You need an active identity to create offers.\n\nPlease create or select an identity first.");
            error.showAndWait();
            return;
        }

        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                getClass().getResource("/com/sparrowwallet/sparrow/p2p/trade/create-offer-dialog.fxml")
            );
            javafx.stage.Stage stage = new javafx.stage.Stage();
            stage.setTitle("Create Trade Offer - Sparrow");
            stage.setScene(new javafx.scene.Scene(loader.load()));
            stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            stage.setMinWidth(700);
            stage.setMinHeight(800);

            com.sparrowwallet.sparrow.p2p.trade.CreateOfferController controller = loader.getController();
            controller.setDialogStage(stage);

            stage.showAndWait();

            // Check if offer was created
            com.sparrowwallet.sparrow.p2p.trade.TradeOffer newOffer = controller.getCreatedOffer();
            if (newOffer != null) {
                log.info("Offer created: {}", newOffer);

                // Publish to Nostr and add to local offers
                offerManager.publishToNostr(newOffer);

                // Refresh marketplace to show new offer
                refreshOffers();

                Alert success = new Alert(Alert.AlertType.INFORMATION);
                success.setTitle("Offer Created");
                success.setHeaderText("Trade Offer Created Successfully");
                success.setContentText(newOffer.getDisplaySummary() + "\n\nOffer has been published to the marketplace.");
                success.showAndWait();
            }
        } catch (Exception e) {
            log.error("Failed to open Create Offer dialog", e);
            Alert error = new Alert(Alert.AlertType.ERROR);
            error.setTitle("Error");
            error.setHeaderText("Failed to Create Offer");
            error.setContentText(e.getMessage());
            error.showAndWait();
        }
    }

    /**
     * Manage my offers
     */
    private void manageOffers() {
        log.info("Opening Manage Offers dialog...");

        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                getClass().getResource("/com/sparrowwallet/sparrow/p2p/trade/my-offers-dialog.fxml")
            );
            javafx.stage.Stage stage = new javafx.stage.Stage();
            stage.setTitle("Manage My Offers - Sparrow");
            stage.setScene(new javafx.scene.Scene(loader.load()));
            stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
            stage.setMinWidth(900);
            stage.setMinHeight(700);

            com.sparrowwallet.sparrow.p2p.trade.MyOffersController controller = loader.getController();
            controller.setDialogStage(stage);

            stage.showAndWait();

            // Refresh stats after dialog closes
            updateActivityStats();

        } catch (Exception e) {
            log.error("Failed to open Manage Offers dialog", e);
            Alert error = new Alert(Alert.AlertType.ERROR);
            error.setTitle("Error");
            error.setHeaderText("Failed to Open Manage Offers");
            error.setContentText(e.getMessage());
            error.showAndWait();
        }
    }

    /**
     * View chats
     */
    private void viewChats() {
        log.info("Opening View Chats dialog...");

        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                getClass().getResource("/com/sparrowwallet/sparrow/p2p/chat/chats-list-dialog.fxml")
            );
            javafx.stage.Stage stage = new javafx.stage.Stage();
            stage.setTitle("My Chats - Sparrow");
            stage.setScene(new javafx.scene.Scene(loader.load()));
            stage.initModality(javafx.stage.Modality.NONE);
            stage.setMinWidth(700);
            stage.setMinHeight(500);

            com.sparrowwallet.sparrow.p2p.chat.ChatsListController controller = loader.getController();
            controller.setDialogStage(stage);

            stage.show();

        } catch (Exception e) {
            log.error("Failed to open View Chats dialog", e);
            Alert error = new Alert(Alert.AlertType.ERROR);
            error.setTitle("Error");
            error.setHeaderText("Failed to Open Chats");
            error.setContentText(e.getMessage());
            error.showAndWait();
        }
    }

    /**
     * View offer details
     */
    private void viewOfferDetails(TradeOffer offer) {
        log.info("Viewing offer details: {}", offer.getId());

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

            com.sparrowwallet.sparrow.p2p.trade.OfferDetailsController controller = loader.getController();
            controller.setDialogStage(stage);
            controller.setOffer(offer);

            // Increment view count
            offerManager.incrementViewCount(offer.getId());

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
     * Update activity statistics
     */
    private void updateActivityStats() {
        int myOffersCount = offerManager.getMyActiveOffersCount();
        openOffersLabel.setText("Open Offers: " + myOffersCount);

        // TODO: Add active trades and completed trades counts when trade system is implemented
        activeTradesLabel.setText("Active Trades: 0");
        completedTradesLabel.setText("Completed Trades: 0");
    }

    /**
     * Cleanup when controller is destroyed
     */
    public void shutdown() {
        log.info("Shutting down P2P Exchange controller");

        // Stop Nostr P2P service
        if (nostrP2PService != null) {
            nostrP2PService.stop();
        }

        log.info("P2P Exchange controller shutdown complete");
    }

    /**
     * Custom ListCell for displaying trade offers
     */
    private static class OfferListCell extends javafx.scene.control.ListCell<TradeOffer> {
        @Override
        protected void updateItem(TradeOffer offer, boolean empty) {
            super.updateItem(offer, empty);

            if (empty || offer == null) {
                setText(null);
                setGraphic(null);
            } else {
                // Create formatted display
                javafx.scene.layout.VBox content = new javafx.scene.layout.VBox(5);

                // Main line: Type, Amount, Price
                javafx.scene.layout.HBox mainLine = new javafx.scene.layout.HBox(10);
                mainLine.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

                javafx.scene.control.Label typeLabel = new javafx.scene.control.Label(
                    offer.getType().getDisplayName().toUpperCase()
                );
                typeLabel.setStyle("-fx-font-weight: bold;");

                javafx.scene.control.Label amountLabel = new javafx.scene.control.Label(
                    String.format("%.8f BTC", offer.getAmountBtc())
                );

                javafx.scene.control.Label priceLabel = new javafx.scene.control.Label(
                    "@ " + offer.getCurrency() + " " + String.format("%.2f", offer.getPrice())
                );

                mainLine.getChildren().addAll(typeLabel, amountLabel, priceLabel);

                // Details line: Payment method, Location
                javafx.scene.layout.HBox detailsLine = new javafx.scene.layout.HBox(10);
                detailsLine.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

                javafx.scene.control.Label paymentLabel = new javafx.scene.control.Label(
                    "• " + offer.getPaymentMethod().getDisplayName()
                );
                paymentLabel.setStyle("-fx-text-fill: #666;");

                if (offer.getLocation() != null && !offer.getLocation().isEmpty()) {
                    javafx.scene.control.Label locationLabel = new javafx.scene.control.Label(
                        "• " + offer.getLocation()
                    );
                    locationLabel.setStyle("-fx-text-fill: #666;");
                    detailsLine.getChildren().addAll(paymentLabel, locationLabel);
                } else {
                    detailsLine.getChildren().add(paymentLabel);
                }

                content.getChildren().addAll(mainLine, detailsLine);
                setGraphic(content);
                setText(null);
            }
        }
    }
}
