package com.sparrowwallet.sparrow.p2p.ui;

import com.sparrowwallet.sparrow.p2p.trade.TradeOffer;
import com.sparrowwallet.sparrow.p2p.trade.TradeOfferType;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

public class OfferDetailPanel extends VBox {
    private static final Logger log = LoggerFactory.getLogger(OfferDetailPanel.class);
    private static final DecimalFormat BTC_FORMAT = new DecimalFormat("#,##0.########");
    private static final DecimalFormat FIAT_FORMAT = new DecimalFormat("#,##0.00");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private TradeOffer currentOffer;
    private Consumer<TradeOffer> onContactHandler;
    private Consumer<TradeOffer> onDeleteHandler;

    private final Label titleLabel;
    private final Label priceLabel;
    private final Label amountLabel;
    private final Label currencyLabel;
    private final Label paymentLabel;
    private final Label locationLabel;
    private final Label sellerNameLabel;
    private final Label sellerNpubLabel;
    private final Label descriptionLabel;
    private final Label termsLabel;
    private final Label publishedLabel;
    private final Label limitsLabel;
    private final Button contactButton;
    private final Button deleteButton;
    private final VBox contentBox;
    private final VBox emptyState;

    public OfferDetailPanel() {
        setSpacing(0);
        setFillWidth(true);
        getStyleClass().add("offer-detail-panel");
        setMinWidth(350);

        emptyState = createEmptyState();
        contentBox = createContentBox();

        titleLabel = new Label();
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        titleLabel.setWrapText(true);

        priceLabel = new Label();
        priceLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #4CAF50;");

        amountLabel = new Label();
        currencyLabel = new Label();
        paymentLabel = new Label();
        locationLabel = new Label();
        sellerNameLabel = new Label();
        sellerNpubLabel = new Label();
        sellerNpubLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #888;");

        descriptionLabel = new Label();
        descriptionLabel.setWrapText(true);

        termsLabel = new Label();
        termsLabel.setWrapText(true);
        termsLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #888;");

        publishedLabel = new Label();
        publishedLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");

        limitsLabel = new Label();
        limitsLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #888;");

        contactButton = new Button("Contact Seller");
        contactButton.getStyleClass().add("contact-button");
        contactButton.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10 30;");
        contactButton.setMaxWidth(Double.MAX_VALUE);
        contactButton.setOnAction(e -> {
            if (currentOffer != null && onContactHandler != null) {
                onContactHandler.accept(currentOffer);
            }
        });

        deleteButton = new Button("Delete Offer");
        deleteButton.getStyleClass().add("delete-button");
        deleteButton.setStyle("-fx-background-color: #F44336; -fx-text-fill: white; -fx-padding: 8 20;");
        deleteButton.setVisible(false);
        deleteButton.setOnAction(e -> {
            if (currentOffer != null && onDeleteHandler != null) {
                onDeleteHandler.accept(currentOffer);
            }
        });

        getChildren().addAll(emptyState);
    }

    private VBox createEmptyState() {
        VBox empty = new VBox(15);
        empty.setAlignment(Pos.CENTER);
        empty.setPadding(new Insets(50));
        VBox.setVgrow(empty, Priority.ALWAYS);

        Label icon = new Label("👆");
        icon.setStyle("-fx-font-size: 48px;");

        Label text = new Label("Select an offer to see details");
        text.setStyle("-fx-font-size: 14px; -fx-text-fill: #888;");

        Label subtext = new Label("Click on any offer card from the marketplace");
        subtext.setStyle("-fx-font-size: 12px; -fx-text-fill: #666;");

        empty.getChildren().addAll(icon, text, subtext);
        return empty;
    }

    private VBox createContentBox() {
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        content.setFillWidth(true);
        return content;
    }

    public void setOffer(TradeOffer offer) {
        this.currentOffer = offer;
        getChildren().clear();

        if (offer == null) {
            getChildren().add(emptyState);
            return;
        }

        contentBox.getChildren().clear();

        HBox typeHeader = new HBox(10);
        typeHeader.setAlignment(Pos.CENTER_LEFT);

        Label typeBadge = new Label(offer.getType() == TradeOfferType.SELL ? "SELLING" : "BUYING");
        if (offer.getType() == TradeOfferType.SELL) {
            typeBadge.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-padding: 4 12; -fx-background-radius: 4; -fx-font-weight: bold;");
        } else {
            typeBadge.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-padding: 4 12; -fx-background-radius: 4; -fx-font-weight: bold;");
        }

        Label statusBadge = new Label(offer.getStatus().toString());
        statusBadge.setStyle("-fx-background-color: derive(-fx-base, -10%); -fx-text-fill: #888; -fx-padding: 4 8; -fx-background-radius: 4; -fx-font-size: 10px;");

        typeHeader.getChildren().addAll(typeBadge, statusBadge);

        titleLabel.setText(BTC_FORMAT.format(offer.getAmountBtc()) + " BTC");

        if (offer.isUseMarketPrice()) {
            double premium = offer.getPremiumPercent();
            if (premium == 0) {
                priceLabel.setText("Market Price");
            } else if (premium > 0) {
                priceLabel.setText("Market +" + FIAT_FORMAT.format(premium) + "%");
            } else {
                priceLabel.setText("Market " + FIAT_FORMAT.format(premium) + "%");
            }
        } else {
            priceLabel.setText(FIAT_FORMAT.format(offer.getPrice()) + " " + offer.getCurrency());
        }

        VBox amountSection = createDetailSection("Amount",
            BTC_FORMAT.format(offer.getAmountBtc()) + " BTC (" + String.format("%,d", offer.getAmountSats()) + " sats)");

        VBox paymentSection = createDetailSection("Payment Method",
            offer.getPaymentMethod() != null ? offer.getPaymentMethod().getDisplayName() : "Any");

        VBox locationSection = createDetailSection("Location",
            offer.getLocation() != null ? offer.getLocation() : "Any location");

        VBox limitsSection = createDetailSection("Trading Limits",
            "Min: " + BTC_FORMAT.format(offer.getMinTradeBtc()) + " BTC | Max: " + BTC_FORMAT.format(offer.getMaxTradeBtc()) + " BTC");

        VBox sellerSection = new VBox(5);
        Label sellerHeader = new Label("Seller");
        sellerHeader.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #888;");

        HBox sellerInfo = new HBox(10);
        sellerInfo.setAlignment(Pos.CENTER_LEFT);

        Circle avatar = new Circle(20);
        avatar.setFill(Color.rgb(80, 80, 80));

        VBox sellerDetails = new VBox(2);
        sellerNameLabel.setText(offer.getCreatorDisplayName());
        sellerNameLabel.setStyle("-fx-font-weight: bold;");
        sellerNpubLabel.setText(shortenNpub(offer.getCreatorNpub()));

        sellerDetails.getChildren().addAll(sellerNameLabel, sellerNpubLabel);
        sellerInfo.getChildren().addAll(avatar, sellerDetails);
        sellerSection.getChildren().addAll(sellerHeader, sellerInfo);

        VBox descriptionSection = null;
        if (offer.getDescription() != null && !offer.getDescription().isEmpty()) {
            descriptionSection = createDetailSection("Description", offer.getDescription());
        }

        VBox termsSection = null;
        if (offer.getTerms() != null && !offer.getTerms().isEmpty()) {
            termsSection = createDetailSection("Terms", offer.getTerms());
        }

        if (offer.getPublishedAt() != null) {
            publishedLabel.setText("Published: " + offer.getPublishedAt().format(DATE_FORMAT));
        } else if (offer.getCreatedAt() != null) {
            publishedLabel.setText("Created: " + offer.getCreatedAt().format(DATE_FORMAT));
        }

        Separator sep1 = new Separator();
        Separator sep2 = new Separator();

        VBox buttonsBox = new VBox(10);
        buttonsBox.setAlignment(Pos.CENTER);
        contactButton.setText(offer.getType() == TradeOfferType.SELL ? "Contact Seller" : "Contact Buyer");
        buttonsBox.getChildren().add(contactButton);

        if (deleteButton.isVisible()) {
            buttonsBox.getChildren().add(deleteButton);
        }

        contentBox.getChildren().addAll(
            typeHeader,
            titleLabel,
            priceLabel,
            sep1,
            amountSection,
            paymentSection,
            locationSection,
            limitsSection,
            sep2,
            sellerSection
        );

        if (descriptionSection != null) {
            contentBox.getChildren().add(descriptionSection);
        }
        if (termsSection != null) {
            contentBox.getChildren().add(termsSection);
        }

        contentBox.getChildren().addAll(
            publishedLabel,
            buttonsBox
        );

        ScrollPane scrollPane = new ScrollPane(contentBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        getChildren().add(scrollPane);
    }

    private VBox createDetailSection(String title, String value) {
        VBox section = new VBox(4);

        Label titleLbl = new Label(title);
        titleLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 11px; -fx-text-fill: #888;");

        Label valueLbl = new Label(value);
        valueLbl.setWrapText(true);

        section.getChildren().addAll(titleLbl, valueLbl);
        return section;
    }

    private String shortenNpub(String npub) {
        if (npub == null || npub.length() < 20) return npub;
        return npub.substring(0, 12) + "..." + npub.substring(npub.length() - 8);
    }

    public void setOnContact(Consumer<TradeOffer> handler) {
        this.onContactHandler = handler;
    }

    public void setOnDelete(Consumer<TradeOffer> handler) {
        this.onDeleteHandler = handler;
    }

    public void setDeleteButtonVisible(boolean visible) {
        deleteButton.setVisible(visible);
    }

    public TradeOffer getCurrentOffer() {
        return currentOffer;
    }

    public void clear() {
        setOffer(null);
    }
}
