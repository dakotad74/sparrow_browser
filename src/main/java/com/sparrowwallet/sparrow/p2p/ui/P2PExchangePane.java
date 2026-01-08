package com.sparrowwallet.sparrow.p2p.ui;

import com.sparrowwallet.sparrow.p2p.NostrP2PService;
import com.sparrowwallet.sparrow.p2p.identity.NostrIdentity;
import com.sparrowwallet.sparrow.p2p.identity.NostrIdentityManager;
import com.sparrowwallet.sparrow.p2p.trade.CreateOfferController;
import com.sparrowwallet.sparrow.p2p.trade.MyOffersController;
import com.sparrowwallet.sparrow.p2p.trade.TradeOffer;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class P2PExchangePane extends BorderPane {
    private static final Logger log = LoggerFactory.getLogger(P2PExchangePane.class);

    private final P2PMarketplacePane marketplacePane;
    private final NostrIdentityManager identityManager;
    private final NostrP2PService p2pService;

    public P2PExchangePane() {
        this.identityManager = NostrIdentityManager.getInstance();
        this.p2pService = NostrP2PService.getInstance();

        marketplacePane = new P2PMarketplacePane();

        setCenter(marketplacePane);

        initializeService();
    }

    private void initializeService() {
        new Thread(() -> {
            try {
                Platform.runLater(() -> {
                    try {
                        p2pService.start();
                    } catch (Exception e) {
                        log.error("Failed to start Nostr P2P Service", e);
                    }
                });

                Thread.sleep(2000);

                p2pService.subscribeToOffers();

                com.sparrowwallet.sparrow.p2p.chat.ChatService chatService =
                    com.sparrowwallet.sparrow.p2p.chat.ChatService.getInstance();
                chatService.subscribeToAllMessages();

                log.info("P2P Exchange service initialized");

            } catch (Exception e) {
                log.error("Failed to initialize P2P Exchange service", e);
            }
        }, "P2PExchangeInit").start();
    }

    public void openCreateOfferDialog() {
        NostrIdentity identity = identityManager.getActiveIdentity();
        if (identity == null) {
            log.warn("Cannot create offer: no active identity");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/com/sparrowwallet/sparrow/p2p/trade/create-offer-dialog.fxml"));
            Parent root = loader.load();

            CreateOfferController controller = loader.getController();

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Create New Offer");
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            dialogStage.initOwner(getScene().getWindow());
            dialogStage.setScene(new Scene(root));
            dialogStage.setResizable(false);

            controller.setDialogStage(dialogStage);

            dialogStage.showAndWait();

            TradeOffer newOffer = controller.getCreatedOffer();
            if (newOffer != null) {
                p2pService.publishOffer(newOffer);
                log.info("Offer created and published: {}", newOffer.getId());
            }
        } catch (IOException e) {
            log.error("Failed to open create offer dialog", e);
        }
    }

    public void openMyOffersDialog() {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/com/sparrowwallet/sparrow/p2p/trade/my-offers-dialog.fxml"));
            Parent root = loader.load();

            Stage dialogStage = new Stage();
            dialogStage.setTitle("My Offers");
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            dialogStage.initOwner(getScene().getWindow());
            dialogStage.setScene(new Scene(root));

            dialogStage.showAndWait();
        } catch (IOException e) {
            log.error("Failed to open my offers dialog", e);
        }
    }

    public void openChatsDialog() {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/com/sparrowwallet/sparrow/p2p/chat/chats-list-dialog.fxml"));
            Parent root = loader.load();

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Chats");
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            dialogStage.initOwner(getScene().getWindow());
            dialogStage.setScene(new Scene(root));

            dialogStage.showAndWait();
        } catch (IOException e) {
            log.error("Failed to open chats dialog", e);
        }
    }

    public P2PMarketplacePane getMarketplacePane() {
        return marketplacePane;
    }

    public P2PStatusBar getStatusBar() {
        return marketplacePane.getStatusBar();
    }
}
