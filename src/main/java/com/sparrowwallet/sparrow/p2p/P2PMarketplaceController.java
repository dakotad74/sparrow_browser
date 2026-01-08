package com.sparrowwallet.sparrow.p2p;

import com.sparrowwallet.sparrow.p2p.ui.P2PMarketplacePane;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.ResourceBundle;

public class P2PMarketplaceController implements Initializable {
    private static final Logger log = LoggerFactory.getLogger(P2PMarketplaceController.class);

    @FXML
    private P2PMarketplacePane marketplacePane;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        log.info("P2P Marketplace Controller initialized");
    }

    public P2PMarketplacePane getMarketplacePane() {
        return marketplacePane;
    }
}
