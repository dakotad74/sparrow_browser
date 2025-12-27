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
import java.util.ResourceBundle;

/**
 * Controller for Create Offer dialog
 */
public class CreateOfferController implements Initializable {
    private static final Logger log = LoggerFactory.getLogger(CreateOfferController.class);

    // Dialog stage
    private Stage dialogStage;
    private TradeOffer createdOffer;
    private boolean confirmed = false;

    // Identity info
    @FXML private Label identityNameLabel;
    @FXML private Label identityNpubLabel;

    // Offer type
    @FXML private ToggleGroup offerTypeGroup;
    @FXML private RadioButton buyTypeRadio;
    @FXML private RadioButton sellTypeRadio;

    // Amount and pricing
    @FXML private TextField amountField;
    @FXML private ComboBox<String> amountUnitCombo;
    @FXML private ComboBox<String> currencyCombo;
    @FXML private TextField priceField;
    @FXML private CheckBox useMarketPriceCheckbox;
    @FXML private TextField premiumField;
    @FXML private Label effectivePriceLabel;

    // Trading limits
    @FXML private TextField minTradeField;
    @FXML private TextField maxTradeField;
    @FXML private ComboBox<String> limitUnitCombo;

    // Payment and location
    @FXML private ComboBox<PaymentMethod> paymentMethodCombo;
    @FXML private TextField locationField;
    @FXML private TextArea locationDetailArea;

    // Terms
    @FXML private TextArea descriptionArea;
    @FXML private TextArea termsArea;
    @FXML private Spinner<Integer> escrowTimeSpinner;

    // Buttons
    @FXML private Button createButton;
    @FXML private Button cancelButton;

    private NostrIdentityManager identityManager;
    private NostrIdentity currentIdentity;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        log.info("Initializing Create Offer dialog");

        identityManager = NostrIdentityManager.getInstance();
        currentIdentity = identityManager.getActiveIdentity();

        if (currentIdentity == null) {
            showError("No active identity. Please create or select an identity first.");
            return;
        }

        setupIdentitySection();
        setupOfferTypeSection();
        setupAmountSection();
        setupPaymentSection();
        setupTermsSection();
        setupButtons();
    }

    /**
     * Setup identity display
     */
    private void setupIdentitySection() {
        identityNameLabel.setText(currentIdentity.getDisplayName());
        identityNpubLabel.setText(currentIdentity.getShortNpub());
    }

    /**
     * Setup offer type section
     */
    private void setupOfferTypeSection() {
        offerTypeGroup = new ToggleGroup();
        buyTypeRadio.setToggleGroup(offerTypeGroup);
        sellTypeRadio.setToggleGroup(offerTypeGroup);
        buyTypeRadio.setSelected(true);
    }

    /**
     * Setup amount and pricing section
     */
    private void setupAmountSection() {
        // Amount unit combo
        amountUnitCombo.getItems().addAll("BTC", "sats");
        amountUnitCombo.setValue("BTC");

        // Currency combo
        currencyCombo.getItems().addAll("USD", "EUR", "GBP", "JPY", "CNY", "AUD", "CAD", "CHF");
        currencyCombo.setValue("USD");

        // Market price checkbox
        useMarketPriceCheckbox.setSelected(false);
        useMarketPriceCheckbox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            priceField.setDisable(newVal);
            premiumField.setDisable(!newVal);
            updateEffectivePrice();
        });

        // Premium field (disabled by default)
        premiumField.setDisable(true);
        premiumField.setText("0.0");

        // Price change listeners
        priceField.textProperty().addListener((obs, oldVal, newVal) -> updateEffectivePrice());
        premiumField.textProperty().addListener((obs, oldVal, newVal) -> updateEffectivePrice());
        currencyCombo.valueProperty().addListener((obs, oldVal, newVal) -> updateEffectivePrice());

        // Limit unit combo
        limitUnitCombo.getItems().addAll("BTC", "sats");
        limitUnitCombo.setValue("BTC");
    }

    /**
     * Setup payment and location section
     */
    private void setupPaymentSection() {
        // Payment method combo
        paymentMethodCombo.getItems().addAll(PaymentMethod.values());
        paymentMethodCombo.setValue(PaymentMethod.BANK_TRANSFER);

        // Custom cell factory to show payment method description
        paymentMethodCombo.setCellFactory(lv -> new ListCell<PaymentMethod>() {
            @Override
            protected void updateItem(PaymentMethod item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.getDisplayName());
                }
            }
        });
        paymentMethodCombo.setButtonCell(new ListCell<PaymentMethod>() {
            @Override
            protected void updateItem(PaymentMethod item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.getDisplayName());
                }
            }
        });
    }

    /**
     * Setup terms section
     */
    private void setupTermsSection() {
        // Escrow time spinner (1-72 hours)
        SpinnerValueFactory<Integer> valueFactory =
            new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 72, 24, 1);
        escrowTimeSpinner.setValueFactory(valueFactory);

        // Default descriptions based on offer type
        offerTypeGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == buyTypeRadio && descriptionArea.getText().isEmpty()) {
                descriptionArea.setText("Looking to buy BTC. Quick and reliable payment.");
            } else if (newVal == sellTypeRadio && descriptionArea.getText().isEmpty()) {
                descriptionArea.setText("Selling BTC. Fast release after payment confirmation.");
            }
        });

        // Set initial description
        descriptionArea.setText("Looking to buy BTC. Quick and reliable payment.");
    }

    /**
     * Setup buttons
     */
    private void setupButtons() {
        createButton.setOnAction(e -> handleCreateOffer());
        cancelButton.setOnAction(e -> handleCancel());
    }

    /**
     * Update effective price display
     */
    private void updateEffectivePrice() {
        try {
            if (useMarketPriceCheckbox.isSelected()) {
                // In real implementation, fetch current market price
                double marketPrice = 45000.0; // Placeholder
                double premium = Double.parseDouble(premiumField.getText());
                double effectivePrice = marketPrice * (1 + premium / 100.0);
                effectivePriceLabel.setText(String.format("Effective: %.2f %s",
                    effectivePrice, currencyCombo.getValue()));
            } else {
                double price = Double.parseDouble(priceField.getText());
                effectivePriceLabel.setText(String.format("Fixed: %.2f %s",
                    price, currencyCombo.getValue()));
            }
        } catch (NumberFormatException e) {
            effectivePriceLabel.setText("--");
        }
    }

    /**
     * Handle create offer button
     */
    private void handleCreateOffer() {
        try {
            // Validate inputs
            if (!validateInputs()) {
                return;
            }

            // Get offer type
            TradeOfferType type = buyTypeRadio.isSelected() ?
                TradeOfferType.BUY : TradeOfferType.SELL;

            // Parse amount
            double amount = Double.parseDouble(amountField.getText());
            long amountSats = "BTC".equals(amountUnitCombo.getValue()) ?
                (long)(amount * 100_000_000) : (long)amount;

            // Parse price
            double price;
            boolean useMarketPrice = useMarketPriceCheckbox.isSelected();
            double premium = 0.0;

            if (useMarketPrice) {
                // In real implementation, fetch market price
                price = 45000.0; // Placeholder
                premium = Double.parseDouble(premiumField.getText());
            } else {
                price = Double.parseDouble(priceField.getText());
            }

            // Parse trading limits
            double minTrade = minTradeField.getText().isEmpty() ? 0 :
                Double.parseDouble(minTradeField.getText());
            double maxTrade = maxTradeField.getText().isEmpty() ? amount :
                Double.parseDouble(maxTradeField.getText());

            long minTradeSats = "BTC".equals(limitUnitCombo.getValue()) ?
                (long)(minTrade * 100_000_000) : (long)minTrade;
            long maxTradeSats = "BTC".equals(limitUnitCombo.getValue()) ?
                (long)(maxTrade * 100_000_000) : (long)maxTrade;

            // Get other fields
            String currency = currencyCombo.getValue();
            PaymentMethod paymentMethod = paymentMethodCombo.getValue();
            String location = locationField.getText().trim();
            String locationDetail = locationDetailArea.getText().trim();
            String description = descriptionArea.getText().trim();
            String terms = termsArea.getText().trim();
            int escrowTime = escrowTimeSpinner.getValue();

            // Create offer
            createdOffer = new TradeOffer(
                currentIdentity.getHex(),
                currentIdentity.getDisplayName(),
                type,
                amountSats,
                currency,
                price,
                useMarketPrice,
                premium,
                minTradeSats,
                maxTradeSats,
                paymentMethod,
                location,
                locationDetail,
                description,
                terms,
                escrowTime
            );

            confirmed = true;
            log.info("Created trade offer: {}", createdOffer);
            closeDialog();

        } catch (NumberFormatException e) {
            showError("Invalid number format. Please check your inputs.");
        } catch (Exception e) {
            log.error("Failed to create offer", e);
            showError("Failed to create offer: " + e.getMessage());
        }
    }

    /**
     * Validate all inputs
     */
    private boolean validateInputs() {
        // Check amount
        if (amountField.getText().trim().isEmpty()) {
            showError("Please enter an amount");
            return false;
        }

        try {
            double amount = Double.parseDouble(amountField.getText());
            if (amount <= 0) {
                showError("Amount must be greater than 0");
                return false;
            }
        } catch (NumberFormatException e) {
            showError("Invalid amount format");
            return false;
        }

        // Check price
        if (!useMarketPriceCheckbox.isSelected()) {
            if (priceField.getText().trim().isEmpty()) {
                showError("Please enter a price or use market price");
                return false;
            }
            try {
                double price = Double.parseDouble(priceField.getText());
                if (price <= 0) {
                    showError("Price must be greater than 0");
                    return false;
                }
            } catch (NumberFormatException e) {
                showError("Invalid price format");
                return false;
            }
        }

        // Check payment method
        if (paymentMethodCombo.getValue() == null) {
            showError("Please select a payment method");
            return false;
        }

        // Check description
        if (descriptionArea.getText().trim().isEmpty()) {
            showError("Please provide a description");
            return false;
        }

        return true;
    }

    /**
     * Handle cancel button
     */
    private void handleCancel() {
        confirmed = false;
        closeDialog();
    }

    /**
     * Close dialog
     */
    private void closeDialog() {
        if (dialogStage != null) {
            dialogStage.close();
        }
    }

    /**
     * Set dialog stage
     */
    public void setDialogStage(Stage stage) {
        this.dialogStage = stage;
    }

    /**
     * Get created offer (only valid if confirmed)
     */
    public TradeOffer getCreatedOffer() {
        return confirmed ? createdOffer : null;
    }

    /**
     * Show error alert
     */
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
