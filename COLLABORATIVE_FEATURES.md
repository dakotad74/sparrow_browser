# Sparrow Browser - Collaborative Transaction Features

This fork of Sparrow Wallet adds collaborative transaction capabilities for multi-party Bitcoin transactions.

## New Features

### 1. Allow Incomplete Transactions

Create transactions where outputs exceed available inputs, enabling collaborative multi-party transactions via PSBT.

**How to use:**
1. Go to the **Send** tab
2. Add your desired outputs (payments)
3. Check the **"Allow Incomplete Transaction"** checkbox
4. Click **"Create Transaction"**
5. The transaction will be created even if your inputs are insufficient
6. Export the PSBT to share with other participants

**Use case:** When multiple parties need to contribute inputs to a single transaction, the first party can create an incomplete transaction specifying all outputs, then other parties can add their inputs.

### 2. Add Inputs to Imported PSBT

Add inputs from any open wallet to an imported PSBT.

**How to use:**
1. Load a PSBT file in the **Transaction** tab (File → Open Transaction)
2. Click **"Finalize Transaction for Signing"** to view signing options
3. Click the **"Add Inputs"** button
4. Select which wallet to add inputs from
5. Select the UTXOs you want to add
6. Click **"Add Inputs"**
7. The transaction diagram will update showing the new inputs

**Use case:** Enables second (or third, fourth, etc.) party to contribute inputs to a collaborative transaction without needing to reconstruct the entire transaction.

### 3. Manual UTXO Selection with Incomplete Transactions

When "Allow Incomplete Transaction" is enabled, manually selected UTXOs are respected.

**How to use:**
1. In the **Send** tab, click the **"UTXOs"** button
2. Manually select specific UTXOs you want to use
3. Check **"Allow Incomplete Transaction"**
4. Create the transaction - only your selected UTXOs will be used

**Use case:** Precise control over which UTXOs to contribute in a collaborative transaction.

## Collaborative Transaction Workflow

### Example: Two-Party Transaction

**Party A (Transaction Creator):**
1. Opens Sparrow and goes to Send tab
2. Adds all outputs (including Party B's payment)
3. Selects their UTXOs to contribute
4. Checks "Allow Incomplete Transaction"
5. Creates the transaction
6. Exports PSBT and sends to Party B

**Party B (Input Contributor):**
1. Receives PSBT file from Party A
2. Opens PSBT in Sparrow (File → Open Transaction)
3. Clicks "Finalize Transaction for Signing"
4. Clicks "Add Inputs"
5. Selects their wallet
6. Selects UTXOs to contribute
7. Adds inputs to the PSBT
8. Signs the transaction
9. Exports signed PSBT back to Party A (or broadcasts if complete)

**Party A (Final Steps):**
1. Receives signed PSBT from Party B
2. Opens it in Sparrow
3. Signs with their keys
4. Broadcasts the fully signed transaction

## Technical Details

### Modified Components

**Core (drongo):**
- `TransactionParameters.java`: Added `allowInsufficientInputs` parameter
- `Wallet.java`: Modified transaction creation to support insufficient inputs and respect PresetUtxoSelector

**UI (sparrow):**
- `send.fxml` / `SendController.java`: Added "Allow Incomplete Transaction" checkbox
- `headers.fxml` / `HeadersController.java`: Added "Add Inputs" button and logic
- `AddInputsDialog.java`: Dialog for selecting UTXOs to add
- `PSBTReconstructedEvent.java`: Event for PSBT reconstruction notifications

### Building

```bash
git clone --recursive https://github.com/dakotad74/sparrow_browser.git
cd sparrow_browser
./gradlew jpackage
```

### Running

```bash
./sparrow
```

Or use the packaged binary:
```bash
./build/jpackage/Sparrow/bin/Sparrow
```

## Future Development

### Planned: Nostr-Based P2P Coordination Layer

A decentralized coordination system using the Nostr protocol to enable automatic collaborative transaction construction:

- **Pre-transaction coordination sessions** where parties share:
  - Desired outputs (destinations and amounts)
  - Fee preferences
  - Available UTXOs (optionally)

- **Authentication** using multisig wallet public keys

- **Automatic transaction construction** once all parties have coordinated

- **Fully decentralized** - no central coordination server required

This will eliminate the manual back-and-forth of PSBT files and enable seamless collaborative transactions.

## Contributing

This is an experimental fork exploring collaborative transaction features. Contributions and feedback are welcome!

## License

Same as upstream Sparrow Wallet (Apache 2.0)

## Original Project

This is a fork of [Sparrow Wallet](https://github.com/sparrowwallet/sparrow) by Craig Raw.

Original project website: https://sparrowwallet.com
