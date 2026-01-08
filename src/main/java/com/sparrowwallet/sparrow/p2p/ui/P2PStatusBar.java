package com.sparrowwallet.sparrow.p2p.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class P2PStatusBar extends HBox {
    private static final Logger log = LoggerFactory.getLogger(P2PStatusBar.class);

    private final Circle torStatusIndicator;
    private final Label torStatusLabel;
    private final Circle nostrStatusIndicator;
    private final Label nostrStatusLabel;
    private final List<RelayStatusItem> relayItems;
    private final Label connectionCountLabel;

    private boolean torConnected = false;
    private boolean nostrConnected = false;
    private int connectedRelayCount = 0;

    public P2PStatusBar() {
        super(15);
        setAlignment(Pos.CENTER_LEFT);
        setPadding(new Insets(8, 15, 8, 15));
        getStyleClass().add("p2p-status-bar");

        torStatusIndicator = createStatusIndicator();
        torStatusLabel = new Label("Tor");
        torStatusLabel.getStyleClass().add("status-label");
        HBox torBox = createStatusBox(torStatusIndicator, torStatusLabel, "Tor connection status");

        nostrStatusIndicator = createStatusIndicator();
        nostrStatusLabel = new Label("Nostr");
        nostrStatusLabel.getStyleClass().add("status-label");
        HBox nostrBox = createStatusBox(nostrStatusIndicator, nostrStatusLabel, "Nostr network status");

        Label relaysLabel = new Label("Relays:");
        relaysLabel.getStyleClass().add("status-label");

        relayItems = new ArrayList<>();
        HBox relaysBox = new HBox(8);
        relaysBox.setAlignment(Pos.CENTER_LEFT);
        relaysBox.getChildren().add(relaysLabel);

        for (int i = 0; i < 3; i++) {
            RelayStatusItem item = new RelayStatusItem("Relay " + (i + 1));
            relayItems.add(item);
            relaysBox.getChildren().add(item);
        }

        connectionCountLabel = new Label("(0/3)");
        connectionCountLabel.getStyleClass().add("connection-count");
        relaysBox.getChildren().add(connectionCountLabel);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        getChildren().addAll(torBox, nostrBox, spacer, relaysBox);

        updateTorStatus(false, null);
        updateNostrStatus(false);
    }

    private Circle createStatusIndicator() {
        Circle indicator = new Circle(5);
        indicator.setFill(Color.GRAY);
        indicator.getStyleClass().add("status-indicator");
        return indicator;
    }

    private HBox createStatusBox(Circle indicator, Label label, String tooltipText) {
        HBox box = new HBox(5);
        box.setAlignment(Pos.CENTER_LEFT);
        box.getChildren().addAll(indicator, label);
        Tooltip.install(box, new Tooltip(tooltipText));
        return box;
    }

    public void updateTorStatus(boolean connected, String exitNode) {
        Platform.runLater(() -> {
            this.torConnected = connected;
            if (connected) {
                torStatusIndicator.setFill(Color.rgb(76, 175, 80));
                torStatusLabel.setText("Tor");
                if (exitNode != null && !exitNode.isEmpty()) {
                    Tooltip.install(torStatusIndicator.getParent(), 
                        new Tooltip("Connected via Tor\nExit: " + exitNode));
                }
            } else {
                torStatusIndicator.setFill(Color.rgb(244, 67, 54));
                torStatusLabel.setText("Tor (offline)");
                Tooltip.install(torStatusIndicator.getParent(), 
                    new Tooltip("Tor not connected"));
            }
        });
    }

    public void updateNostrStatus(boolean connected) {
        Platform.runLater(() -> {
            this.nostrConnected = connected;
            if (connected) {
                nostrStatusIndicator.setFill(Color.rgb(76, 175, 80));
                nostrStatusLabel.setText("Nostr");
            } else {
                nostrStatusIndicator.setFill(Color.rgb(244, 67, 54));
                nostrStatusLabel.setText("Nostr (offline)");
            }
        });
    }

    public void updateRelayStatus(int index, String relayUrl, boolean connected) {
        if (index < 0 || index >= relayItems.size()) {
            return;
        }

        Platform.runLater(() -> {
            RelayStatusItem item = relayItems.get(index);
            item.update(relayUrl, connected);
            updateConnectionCount();
        });
    }

    public void setRelays(List<RelayInfo> relays) {
        Platform.runLater(() -> {
            for (int i = 0; i < relayItems.size(); i++) {
                if (i < relays.size()) {
                    RelayInfo info = relays.get(i);
                    relayItems.get(i).update(info.url, info.connected);
                } else {
                    relayItems.get(i).update(null, false);
                }
            }
            updateConnectionCount();
        });
    }

    private void updateConnectionCount() {
        connectedRelayCount = 0;
        for (RelayStatusItem item : relayItems) {
            if (item.isConnected()) {
                connectedRelayCount++;
            }
        }
        connectionCountLabel.setText("(" + connectedRelayCount + "/3)");

        if (connectedRelayCount == 3) {
            connectionCountLabel.setStyle("-fx-text-fill: #4CAF50;");
        } else if (connectedRelayCount > 0) {
            connectionCountLabel.setStyle("-fx-text-fill: #FFC107;");
        } else {
            connectionCountLabel.setStyle("-fx-text-fill: #F44336;");
        }

        updateNostrStatus(connectedRelayCount > 0);
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

    public static class RelayInfo {
        public final String url;
        public final boolean connected;
        public final long latencyMs;

        public RelayInfo(String url, boolean connected, long latencyMs) {
            this.url = url;
            this.connected = connected;
            this.latencyMs = latencyMs;
        }

        public RelayInfo(String url, boolean connected) {
            this(url, connected, -1);
        }
    }

    private static class RelayStatusItem extends HBox {
        private final Circle indicator;
        private final Label nameLabel;
        private boolean connected = false;

        public RelayStatusItem(String defaultName) {
            super(3);
            setAlignment(Pos.CENTER_LEFT);

            indicator = new Circle(4);
            indicator.setFill(Color.GRAY);

            nameLabel = new Label(defaultName);
            nameLabel.getStyleClass().add("relay-name");
            nameLabel.setStyle("-fx-font-size: 10px;");

            getChildren().addAll(indicator, nameLabel);
        }

        public void update(String url, boolean connected) {
            this.connected = connected;
            if (url == null || url.isEmpty()) {
                indicator.setFill(Color.GRAY);
                nameLabel.setText("-");
                Tooltip.install(this, new Tooltip("No relay configured"));
            } else {
                String shortName = extractRelayName(url);
                nameLabel.setText(shortName);

                if (connected) {
                    indicator.setFill(Color.rgb(76, 175, 80));
                    Tooltip.install(this, new Tooltip(url + "\nStatus: Connected"));
                } else {
                    indicator.setFill(Color.rgb(244, 67, 54));
                    Tooltip.install(this, new Tooltip(url + "\nStatus: Disconnected"));
                }
            }
        }

        public boolean isConnected() {
            return connected;
        }

        private String extractRelayName(String url) {
            if (url == null) return "-";
            String clean = url.replace("wss://", "").replace("ws://", "");
            int slashIdx = clean.indexOf('/');
            if (slashIdx > 0) {
                clean = clean.substring(0, slashIdx);
            }
            if (clean.length() > 15) {
                clean = clean.substring(0, 12) + "...";
            }
            return clean;
        }
    }
}
