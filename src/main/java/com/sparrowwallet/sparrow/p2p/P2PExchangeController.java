package com.sparrowwallet.sparrow.p2p;

import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.p2p.identity.NostrIdentity;
import com.sparrowwallet.sparrow.p2p.identity.NostrIdentityManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
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
    private ListView<String> offersListView;

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

    // Status bar
    @FXML
    private Label statusLabel;

    private NostrIdentityManager identityManager;
    private NostrIdentity currentIdentity;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        log.info("Initializing P2P Exchange controller");

        identityManager = NostrIdentityManager.getInstance();

        // Initialize identity
        initializeIdentity();

        // Initialize filters
        initializeFilters();

        // Initialize marketplace
        initializeMarketplace();

        // Initialize activity section
        initializeActivity();

        updateStatusBar();
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

        // TODO: Load offers from Nostr
        offersListView.getItems().add("No offers available. Create one to get started!");

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
        // TODO: Get actual relay connection status
        statusLabel.setText("Connected to 3 Nostr relays  •  0 offers available");
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

        // TODO: Filter offers based on selected criteria
        refreshOffers();
    }

    /**
     * Refresh offers from Nostr
     */
    private void refreshOffers() {
        log.info("Refreshing offers from Nostr...");
        // TODO: Implement Nostr offer fetching
        Platform.runLater(() -> {
            statusLabel.setText("Refreshing offers...");
            // Simulate refresh
            new Thread(() -> {
                try {
                    Thread.sleep(1000);
                    Platform.runLater(() -> {
                        statusLabel.setText("Connected to 3 Nostr relays  •  0 offers available");
                    });
                } catch (InterruptedException e) {
                    log.error("Refresh interrupted", e);
                }
            }).start();
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
        // TODO: Open CreateOfferDialog
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Create Offer");
        alert.setHeaderText("Create Trade Offer");
        alert.setContentText("Create offer UI coming soon!");
        alert.showAndWait();
    }

    /**
     * Cleanup when controller is destroyed
     */
    public void shutdown() {
        log.info("Shutting down P2P Exchange controller");
        // Cleanup resources
    }
}
