package com.sparrowwallet.sparrow.p2p.ui;

import com.sparrowwallet.sparrow.p2p.NostrP2PService;
import com.sparrowwallet.sparrow.p2p.identity.NostrIdentity;
import com.sparrowwallet.sparrow.p2p.identity.NostrIdentityManager;
import com.sparrowwallet.sparrow.p2p.trade.*;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class P2PMarketplacePane extends BorderPane {
    private static final Logger log = LoggerFactory.getLogger(P2PMarketplacePane.class);

    private final P2PStatusBar statusBar;
    private final FlowPane offersGrid;
    private final OfferDetailPanel detailPanel;
    private final VBox rightPane;
    private ChatPanel activeChat;

    private final ComboBox<String> typeFilter;
    private final ComboBox<String> currencyFilter;
    private final ComboBox<String> paymentFilter;
    private final TextField searchField;

    private final NostrIdentityManager identityManager;
    private final TradeOfferManager offerManager;
    private final NostrP2PService p2pService;

    private final ObservableList<TradeOffer> allOffers;
    private final List<OfferCard> offerCards;
    private OfferCard selectedCard;

    public P2PMarketplacePane() {
        this.identityManager = NostrIdentityManager.getInstance();
        this.offerManager = TradeOfferManager.getInstance();
        this.p2pService = NostrP2PService.getInstance();
        this.allOffers = FXCollections.observableArrayList();
        this.offerCards = new ArrayList<>();

        getStyleClass().add("p2p-marketplace");

        statusBar = new P2PStatusBar();
        setTop(statusBar);

        VBox leftPane = createLeftPane();
        typeFilter = (ComboBox<String>) leftPane.lookup("#typeFilter");
        currencyFilter = (ComboBox<String>) leftPane.lookup("#currencyFilter");
        paymentFilter = (ComboBox<String>) leftPane.lookup("#paymentFilter");
        searchField = (TextField) leftPane.lookup("#searchField");

        offersGrid = new FlowPane(Orientation.HORIZONTAL, 15, 15);
        offersGrid.setPadding(new Insets(15));
        offersGrid.setAlignment(Pos.TOP_LEFT);

        ScrollPane gridScroll = new ScrollPane(offersGrid);
        gridScroll.setFitToWidth(true);
        gridScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        gridScroll.getStyleClass().add("offers-scroll");
        VBox.setVgrow(gridScroll, Priority.ALWAYS);

        VBox leftContent = new VBox(0);
        leftContent.getChildren().addAll(leftPane, gridScroll);
        VBox.setVgrow(gridScroll, Priority.ALWAYS);

        detailPanel = new OfferDetailPanel();
        detailPanel.setOnContact(this::handleContactOffer);

        rightPane = new VBox(0);
        rightPane.setMinWidth(380);
        rightPane.setPrefWidth(400);
        rightPane.getChildren().add(detailPanel);
        VBox.setVgrow(detailPanel, Priority.ALWAYS);

        SplitPane splitPane = new SplitPane(leftContent, rightPane);
        splitPane.setDividerPositions(0.6);
        SplitPane.setResizableWithParent(rightPane, false);
        setCenter(splitPane);

        HBox bottomBar = createBottomBar();
        setBottom(bottomBar);

        initializeFilters();
        loadOffers();
        startStatusUpdates();
    }

    private VBox createLeftPane() {
        VBox pane = new VBox(10);
        pane.setPadding(new Insets(15));
        pane.setStyle("-fx-background-color: derive(-fx-base, -3%); -fx-border-color: derive(-fx-base, -10%); -fx-border-width: 0 0 1 0;");

        HBox header = new HBox(15);
        header.setAlignment(Pos.CENTER_LEFT);

        Label titleLabel = new Label("Marketplace");
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button createOfferBtn = new Button("+ Create Offer");
        createOfferBtn.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 16;");
        createOfferBtn.setOnAction(e -> handleCreateOffer());

        Button refreshBtn = new Button("Refresh");
        refreshBtn.setOnAction(e -> refreshOffers());

        header.getChildren().addAll(titleLabel, spacer, createOfferBtn, refreshBtn);

        HBox filters = new HBox(10);
        filters.setAlignment(Pos.CENTER_LEFT);

        ComboBox<String> type = new ComboBox<>();
        type.setId("typeFilter");
        type.setPromptText("Type");
        type.setPrefWidth(100);

        ComboBox<String> currency = new ComboBox<>();
        currency.setId("currencyFilter");
        currency.setPromptText("Currency");
        currency.setPrefWidth(100);

        ComboBox<String> payment = new ComboBox<>();
        payment.setId("paymentFilter");
        payment.setPromptText("Payment");
        payment.setPrefWidth(120);

        TextField search = new TextField();
        search.setId("searchField");
        search.setPromptText("Search offers...");
        search.setPrefWidth(200);
        HBox.setHgrow(search, Priority.ALWAYS);

        filters.getChildren().addAll(
            new Label("Filters:"),
            type,
            currency,
            payment,
            search
        );

        pane.getChildren().addAll(header, filters);
        return pane;
    }

    private HBox createBottomBar() {
        HBox bar = new HBox(20);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(10, 15, 10, 15));
        bar.setStyle("-fx-background-color: derive(-fx-base, -5%); -fx-border-color: derive(-fx-base, -15%); -fx-border-width: 1 0 0 0;");

        NostrIdentity identity = identityManager.getActiveIdentity();
        String identityText = identity != null ? identity.getDisplayName() : "No identity";

        Label identityLabel = new Label("Identity: " + identityText);
        identityLabel.setStyle("-fx-font-size: 11px;");

        Button manageIdentityBtn = new Button("Manage Identity");
        manageIdentityBtn.setStyle("-fx-font-size: 11px;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button myOffersBtn = new Button("My Offers");
        myOffersBtn.setStyle("-fx-font-size: 11px;");
        myOffersBtn.setOnAction(e -> handleMyOffers());

        Button chatsBtn = new Button("Chats");
        chatsBtn.setStyle("-fx-font-size: 11px;");
        chatsBtn.setOnAction(e -> handleViewChats());

        bar.getChildren().addAll(identityLabel, manageIdentityBtn, spacer, myOffersBtn, chatsBtn);
        return bar;
    }

    private void initializeFilters() {
        Platform.runLater(() -> {
            ComboBox<String> type = (ComboBox<String>) lookup("#typeFilter");
            ComboBox<String> currency = (ComboBox<String>) lookup("#currencyFilter");
            ComboBox<String> payment = (ComboBox<String>) lookup("#paymentFilter");
            TextField search = (TextField) lookup("#searchField");

            if (type != null) {
                type.getItems().addAll("All", "Buy", "Sell");
                type.setValue("All");
                type.valueProperty().addListener((obs, old, val) -> applyFilters());
            }

            if (currency != null) {
                currency.getItems().addAll("All", "USD", "EUR", "GBP", "JPY", "CNY");
                currency.setValue("All");
                currency.valueProperty().addListener((obs, old, val) -> applyFilters());
            }

            if (payment != null) {
                payment.getItems().add("All");
                for (PaymentMethod pm : PaymentMethod.values()) {
                    payment.getItems().add(pm.getDisplayName());
                }
                payment.setValue("All");
                payment.valueProperty().addListener((obs, old, val) -> applyFilters());
            }

            if (search != null) {
                search.textProperty().addListener((obs, old, val) -> applyFilters());
            }
        });
    }

    private void loadOffers() {
        allOffers.clear();
        allOffers.addAll(offerManager.getAllOffers());
        refreshGrid();
    }

    private void refreshOffers() {
        p2pService.subscribeToOffers();
        loadOffers();
    }

    private void applyFilters() {
        ComboBox<String> type = (ComboBox<String>) lookup("#typeFilter");
        ComboBox<String> currency = (ComboBox<String>) lookup("#currencyFilter");
        ComboBox<String> payment = (ComboBox<String>) lookup("#paymentFilter");
        TextField search = (TextField) lookup("#searchField");

        List<TradeOffer> filtered = allOffers.stream()
            .filter(offer -> {
                if (type != null && !"All".equals(type.getValue())) {
                    String offerType = offer.getType() == TradeOfferType.BUY ? "Buy" : "Sell";
                    if (!offerType.equals(type.getValue())) return false;
                }

                if (currency != null && !"All".equals(currency.getValue())) {
                    if (!offer.getCurrency().equals(currency.getValue())) return false;
                }

                if (payment != null && !"All".equals(payment.getValue())) {
                    if (offer.getPaymentMethod() == null) return false;
                    if (!offer.getPaymentMethod().getDisplayName().equals(payment.getValue())) return false;
                }

                if (search != null && !search.getText().isEmpty()) {
                    String query = search.getText().toLowerCase();
                    String searchable = (offer.getDescription() + " " +
                        offer.getLocation() + " " +
                        offer.getCreatorDisplayName()).toLowerCase();
                    if (!searchable.contains(query)) return false;
                }

                return true;
            })
            .collect(Collectors.toList());

        refreshGrid(filtered);
    }

    private void refreshGrid() {
        refreshGrid(new ArrayList<>(allOffers));
    }

    private void refreshGrid(List<TradeOffer> offers) {
        Platform.runLater(() -> {
            offersGrid.getChildren().clear();
            offerCards.clear();
            selectedCard = null;

            if (offers.isEmpty()) {
                Label empty = new Label("No offers available");
                empty.setStyle("-fx-text-fill: #888; -fx-font-size: 14px;");
                offersGrid.getChildren().add(empty);
                return;
            }

            for (TradeOffer offer : offers) {
                OfferCard card = new OfferCard(offer);
                card.setOnClick(this::handleOfferClick);
                offerCards.add(card);
                offersGrid.getChildren().add(card);
            }
        });
    }

    private void handleOfferClick(TradeOffer offer) {
        for (OfferCard card : offerCards) {
            card.setSelected(card.getOffer().getId().equals(offer.getId()));
            if (card.isSelected()) {
                selectedCard = card;
            }
        }

        detailPanel.setOffer(offer);

        NostrIdentity identity = identityManager.getActiveIdentity();
        boolean isMyOffer = identity != null && offer.getCreatorHex().equals(identity.getHex());
        detailPanel.setDeleteButtonVisible(isMyOffer);

        closeChatIfOpen();
    }

    private void handleContactOffer(TradeOffer offer) {
        closeChatIfOpen();

        activeChat = new ChatPanel(offer);
        activeChat.setOnClose(v -> closeChatIfOpen());

        rightPane.getChildren().clear();

        SplitPane verticalSplit = new SplitPane();
        verticalSplit.setOrientation(Orientation.VERTICAL);
        verticalSplit.setDividerPositions(0.5);
        verticalSplit.getItems().addAll(detailPanel, activeChat);
        VBox.setVgrow(verticalSplit, Priority.ALWAYS);

        rightPane.getChildren().add(verticalSplit);
    }

    private void closeChatIfOpen() {
        if (activeChat != null) {
            rightPane.getChildren().clear();
            rightPane.getChildren().add(detailPanel);
            VBox.setVgrow(detailPanel, Priority.ALWAYS);
            activeChat = null;
        }
    }

    private void handleCreateOffer() {
        log.info("Opening create offer dialog");
    }

    private void handleMyOffers() {
        log.info("Opening my offers view");
    }

    private void handleViewChats() {
        log.info("Opening chats view");
    }

    private void startStatusUpdates() {
        Thread statusThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    updateRelayStatus();
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "P2PStatusUpdater");
        statusThread.setDaemon(true);
        statusThread.start();
    }

    private void updateRelayStatus() {
        try {
            boolean torRunning = com.sparrowwallet.sparrow.AppServices.isTorRunning() ||
                                 com.sparrowwallet.sparrow.io.Config.get().isUseProxy();
            statusBar.updateTorStatus(torRunning, null);

            var relayManager = p2pService.getRelayManager();
            if (relayManager != null) {
                var relays = relayManager.getRelayUrls();
                List<P2PStatusBar.RelayInfo> relayInfos = new ArrayList<>();

                for (int i = 0; i < Math.min(3, relays.size()); i++) {
                    String url = relays.get(i);
                    boolean connected = relayManager.isRelayConnected(url);
                    relayInfos.add(new P2PStatusBar.RelayInfo(url, connected));
                }

                while (relayInfos.size() < 3) {
                    relayInfos.add(new P2PStatusBar.RelayInfo(null, false));
                }

                statusBar.setRelays(relayInfos);
            }
        } catch (Exception e) {
            log.debug("Failed to update relay status", e);
        }
    }

    public P2PStatusBar getStatusBar() {
        return statusBar;
    }

    public OfferDetailPanel getDetailPanel() {
        return detailPanel;
    }

    public void selectOffer(TradeOffer offer) {
        handleOfferClick(offer);
    }
}
