package com.sparrowwallet.sparrow.p2p.ui;

import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.nostr.NostrRelayManager;
import com.sparrowwallet.sparrow.p2p.NostrP2PService;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class P2PStatusIndicator extends HBox {
    private static final Logger log = LoggerFactory.getLogger(P2PStatusIndicator.class);

    private final Circle torIndicator;
    private final Circle nostrIndicator;
    private final Label relayCountLabel;

    private boolean torConnected = false;
    private boolean nostrConnected = false;
    private int connectedRelayCount = 0;

    public P2PStatusIndicator() {
        super(8);
        setAlignment(Pos.CENTER);

        torIndicator = createIndicator();
        Label torLabel = new Label("Tor");
        torLabel.setStyle("-fx-font-size: 10px;");
        HBox torBox = new HBox(3, torIndicator, torLabel);
        torBox.setAlignment(Pos.CENTER);
        Tooltip.install(torBox, new Tooltip("Tor proxy status"));

        nostrIndicator = createIndicator();
        Label nostrLabel = new Label("Nostr");
        nostrLabel.setStyle("-fx-font-size: 10px;");
        HBox nostrBox = new HBox(3, nostrIndicator, nostrLabel);
        nostrBox.setAlignment(Pos.CENTER);
        Tooltip.install(nostrBox, new Tooltip("Nostr network status"));

        relayCountLabel = new Label("(0/3)");
        relayCountLabel.setStyle("-fx-font-size: 10px; -fx-font-weight: bold;");
        HBox relayBox = new HBox(3, relayCountLabel);
        relayBox.setAlignment(Pos.CENTER);
        Tooltip.install(relayBox, new Tooltip("Connected Nostr relays"));

        getChildren().addAll(torBox, new Label("|"), nostrBox, relayBox);
        getStyleClass().add("p2p-status-indicator");

        updateStatus();
        startStatusUpdates();
    }

    private Circle createIndicator() {
        Circle circle = new Circle(4);
        circle.setFill(Color.rgb(150, 150, 150));
        return circle;
    }

    public void updateStatus() {
        Platform.runLater(() -> {
            boolean torRunning = AppServices.isTorRunning() || Config.get().isUseProxy();
            updateTorStatus(torRunning);
            updateNostrStatus();
        });
    }

    private void updateTorStatus(boolean connected) {
        this.torConnected = connected;
        if (connected) {
            torIndicator.setFill(Color.rgb(76, 175, 80));
        } else {
            torIndicator.setFill(Color.rgb(244, 67, 54));
        }
    }

    private void updateNostrStatus() {
        try {
            NostrP2PService p2pService = NostrP2PService.getInstance();
            var relayManager = p2pService.getRelayManager();

            if (relayManager != null) {
                int connected = 0;
                List<String> relays = relayManager.getRelayUrls();

                for (String url : relays) {
                    if (relayManager.isRelayConnected(url)) {
                        connected++;
                    }
                }

                connectedRelayCount = connected;
                nostrConnected = connected > 0;

                if (nostrConnected) {
                    nostrIndicator.setFill(Color.rgb(76, 175, 80));
                } else {
                    nostrIndicator.setFill(Color.rgb(244, 67, 54));
                }

                if (connectedRelayCount == 3) {
                    relayCountLabel.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #4CAF50;");
                } else if (connectedRelayCount > 0) {
                    relayCountLabel.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #FFC107;");
                } else {
                    relayCountLabel.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #F44336;");
                }

                relayCountLabel.setText("(" + connectedRelayCount + "/3)");
            }
        } catch (Exception e) {
            log.debug("Failed to update Nostr status", e);
        }
    }

    private void startStatusUpdates() {
        Thread statusThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    updateStatus();
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "P2PStatusIndicatorUpdater");
        statusThread.setDaemon(true);
        statusThread.start();
    }

    public boolean isTorConnected() {
        return torConnected;
    }

    public boolean isNostrConnected() {
        return nostrConnected;
    }

    public int getConnectedRelayCount() {
        return connectedRelayCount;
    }
}
