package com.sparrowwallet.sparrow.p2p.ui;

import com.sparrowwallet.sparrow.p2p.trade.PaymentMethod;
import com.sparrowwallet.sparrow.p2p.trade.TradeOffer;
import com.sparrowwallet.sparrow.p2p.trade.TradeOfferType;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import java.text.DecimalFormat;
import java.util.function.Consumer;

public class OfferCard extends VBox {

    private static final DecimalFormat BTC_FORMAT = new DecimalFormat("#,##0.########");
    private static final DecimalFormat FIAT_FORMAT = new DecimalFormat("#,##0.00");

    private final TradeOffer offer;
    private boolean selected = false;
    private Consumer<TradeOffer> onClickHandler;
    private Consumer<TradeOffer> onContactHandler;

    private final Rectangle imagePlaceholder;
    private final Label titleLabel;
    private final Label priceLabel;
    private final Label amountLabel;
    private final Label sellerLabel;
    private final Label paymentLabel;
    private final Label locationLabel;

    public OfferCard(TradeOffer offer) {
        this.offer = offer;

        setSpacing(8);
        setPadding(new Insets(12));
        setMinWidth(200);
        setMaxWidth(280);
        setPrefWidth(240);
        getStyleClass().add("offer-card");

        imagePlaceholder = new Rectangle(216, 120);
        imagePlaceholder.setFill(Color.rgb(60, 60, 60));
        imagePlaceholder.setArcWidth(8);
        imagePlaceholder.setArcHeight(8);

        Label btcIcon = new Label("₿");
        btcIcon.setStyle("-fx-font-size: 48px; -fx-text-fill: #F7931A;");

        VBox imageContainer = new VBox(btcIcon);
        imageContainer.setAlignment(Pos.CENTER);
        imageContainer.setMinHeight(120);
        imageContainer.setMaxHeight(120);
        imageContainer.setStyle("-fx-background-color: #2a2a2a; -fx-background-radius: 8;");

        HBox typeAndPrice = new HBox(8);
        typeAndPrice.setAlignment(Pos.CENTER_LEFT);

        Label typeLabel = new Label(offer.getType() == TradeOfferType.SELL ? "SELL" : "BUY");
        typeLabel.getStyleClass().add("offer-type-badge");
        if (offer.getType() == TradeOfferType.SELL) {
            typeLabel.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-padding: 2 8; -fx-background-radius: 4; -fx-font-size: 10px; -fx-font-weight: bold;");
        } else {
            typeLabel.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-padding: 2 8; -fx-background-radius: 4; -fx-font-size: 10px; -fx-font-weight: bold;");
        }

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        priceLabel = new Label(formatPrice(offer));
        priceLabel.getStyleClass().add("offer-price");
        priceLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        typeAndPrice.getChildren().addAll(typeLabel, spacer, priceLabel);

        titleLabel = new Label(createTitle(offer));
        titleLabel.getStyleClass().add("offer-title");
        titleLabel.setWrapText(true);
        titleLabel.setMaxWidth(216);
        titleLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");

        amountLabel = new Label(formatAmount(offer));
        amountLabel.getStyleClass().add("offer-amount");
        amountLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #888;");

        HBox detailsRow = new HBox(10);
        detailsRow.setAlignment(Pos.CENTER_LEFT);

        paymentLabel = new Label(formatPaymentMethod(offer.getPaymentMethod()));
        paymentLabel.getStyleClass().add("offer-payment");
        paymentLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #aaa;");

        locationLabel = new Label(offer.getLocation() != null ? offer.getLocation() : "Any");
        locationLabel.getStyleClass().add("offer-location");
        locationLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #aaa;");

        detailsRow.getChildren().addAll(paymentLabel, new Label("•"), locationLabel);

        sellerLabel = new Label(offer.getCreatorDisplayName());
        sellerLabel.getStyleClass().add("offer-seller");
        sellerLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");

        if (offer.getCreatorNpub() != null) {
            Tooltip.install(sellerLabel, new Tooltip(offer.getCreatorNpub()));
        }

        getChildren().addAll(
            imageContainer,
            typeAndPrice,
            titleLabel,
            amountLabel,
            detailsRow,
            sellerLabel
        );

        setOnMouseClicked(event -> {
            if (onClickHandler != null) {
                onClickHandler.accept(offer);
            }
        });

        setOnMouseEntered(event -> {
            if (!selected) {
                setStyle("-fx-background-color: derive(-fx-base, -5%); -fx-background-radius: 8; -fx-border-color: #555; -fx-border-radius: 8;");
            }
        });

        setOnMouseExited(event -> {
            if (!selected) {
                setStyle("-fx-background-color: derive(-fx-base, 5%); -fx-background-radius: 8; -fx-border-color: transparent; -fx-border-radius: 8;");
            }
        });

        setStyle("-fx-background-color: derive(-fx-base, 5%); -fx-background-radius: 8; -fx-border-color: transparent; -fx-border-radius: 8;");
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
        if (selected) {
            setStyle("-fx-background-color: derive(-fx-base, -8%); -fx-background-radius: 8; -fx-border-color: #2196F3; -fx-border-width: 2; -fx-border-radius: 8;");
        } else {
            setStyle("-fx-background-color: derive(-fx-base, 5%); -fx-background-radius: 8; -fx-border-color: transparent; -fx-border-radius: 8;");
        }
    }

    public void setOnClick(Consumer<TradeOffer> handler) {
        this.onClickHandler = handler;
    }

    public void setOnContact(Consumer<TradeOffer> handler) {
        this.onContactHandler = handler;
    }

    public TradeOffer getOffer() {
        return offer;
    }

    public boolean isSelected() {
        return selected;
    }

    private String createTitle(TradeOffer offer) {
        String action = offer.getType() == TradeOfferType.SELL ? "Selling" : "Buying";
        return action + " " + BTC_FORMAT.format(offer.getAmountBtc()) + " BTC";
    }

    private String formatPrice(TradeOffer offer) {
        if (offer.isUseMarketPrice()) {
            double premium = offer.getPremiumPercent();
            if (premium == 0) {
                return "Market Price";
            } else if (premium > 0) {
                return "+" + FIAT_FORMAT.format(premium) + "%";
            } else {
                return FIAT_FORMAT.format(premium) + "%";
            }
        }
        return FIAT_FORMAT.format(offer.getPrice()) + " " + offer.getCurrency();
    }

    private String formatAmount(TradeOffer offer) {
        double btc = offer.getAmountBtc();
        long sats = offer.getAmountSats();
        if (sats < 100_000_000) {
            return String.format("%,d sats", sats);
        }
        return BTC_FORMAT.format(btc) + " BTC";
    }

    private String formatPaymentMethod(PaymentMethod method) {
        if (method == null) return "Any";
        return method.getDisplayName();
    }
}
