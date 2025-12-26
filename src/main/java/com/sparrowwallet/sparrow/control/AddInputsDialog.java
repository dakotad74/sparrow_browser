package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import com.sparrowwallet.sparrow.wallet.Entry;
import com.sparrowwallet.sparrow.wallet.UtxoEntry;
import com.sparrowwallet.sparrow.wallet.WalletUtxosEntry;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.controlsfx.glyphfont.Glyph;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Dialog for selecting UTXOs from a wallet to add as inputs to a PSBT
 */
public class AddInputsDialog extends Dialog<List<UtxoEntry>> {
    private final UtxosTreeTable utxosTable;
    private final Wallet wallet;

    public AddInputsDialog(Wallet wallet) {
        this.wallet = wallet;

        final DialogPane dialogPane = getDialogPane();
        AppServices.setStageIcon(dialogPane.getScene().getWindow());

        setTitle("Add Inputs from Wallet");
        dialogPane.setHeaderText("Select UTXOs to add as inputs to the transaction:");
        dialogPane.getStylesheets().add(AppServices.class.getResource("general.css").toExternalForm());
        dialogPane.getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
        dialogPane.setPrefWidth(700);
        dialogPane.setPrefHeight(500);
        AppServices.moveToActiveWindowScreen(this);

        Glyph walletGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.COINS);
        walletGlyph.setFontSize(50);
        dialogPane.setGraphic(walletGlyph);

        final VBox content = new VBox(10);

        // Create WalletUtxosEntry for the wallet
        WalletUtxosEntry walletUtxosEntry = new WalletUtxosEntry(wallet);

        // Initialize UtxosTreeTable
        utxosTable = new UtxosTreeTable();
        utxosTable.setPrefHeight(350);
        utxosTable.initialize(walletUtxosEntry);

        // Enable multi-selection
        utxosTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        content.getChildren().add(utxosTable);

        dialogPane.setContent(content);

        // Disable OK button when no selection
        final ButtonType okButtonType = ButtonType.OK;
        Button okButton = (Button) dialogPane.lookupButton(okButtonType);
        okButton.setDisable(true);

        // Enable/disable OK button based on selection
        utxosTable.getSelectionModel().getSelectedItems().addListener(
            (javafx.collections.ListChangeListener.Change<? extends TreeItem<Entry>> c) -> {
                okButton.setDisable(utxosTable.getSelectionModel().getSelectedItems().isEmpty());
            }
        );

        // Set result converter
        setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                return utxosTable.getSelectionModel().getSelectedItems().stream()
                    .map(item -> item.getValue())
                    .filter(entry -> entry instanceof UtxoEntry)
                    .map(entry -> (UtxoEntry) entry)
                    .collect(Collectors.toList());
            }
            return null;
        });
    }

    public Wallet getWallet() {
        return wallet;
    }
}
