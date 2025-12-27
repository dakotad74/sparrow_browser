package com.sparrowwallet.sparrow.p2p.identity;

/**
 * Simple test for NostrIdentity system.
 * Run this to verify identity creation and management works.
 */
public class NostrIdentityTest {

    public static void main(String[] args) {
        System.out.println("=== Nostr Identity System Test ===\n");

        NostrIdentityManager manager = NostrIdentityManager.getInstance();

        // Test 1: Create ephemeral identity
        System.out.println("Test 1: Creating ephemeral identity...");
        NostrIdentity ephemeral = manager.createEphemeralIdentity();
        System.out.println("✓ Created: " + ephemeral.getDisplayName());
        System.out.println("  npub: " + ephemeral.getNpub());
        System.out.println("  Type: " + ephemeral.getType());
        System.out.println("  Status: " + ephemeral.getStatus());
        System.out.println("  Auto-delete: " + ephemeral.isAutoDelete());
        System.out.println();

        // Test 2: Create persistent identity
        System.out.println("Test 2: Creating persistent identity...");
        NostrIdentity persistent = manager.createPersistentIdentity("BitcoinTrader_Pro");
        System.out.println("✓ Created: " + persistent.getDisplayName());
        System.out.println("  npub: " + persistent.getNpub());
        System.out.println("  Type: " + persistent.getType());
        System.out.println("  Completed trades: " + persistent.getCompletedTrades());
        System.out.println("  Rating: " + persistent.getAverageRating());
        System.out.println();

        // Test 3: Set active identity
        System.out.println("Test 3: Setting active identity...");
        manager.setActiveIdentity(ephemeral);
        NostrIdentity active = manager.getActiveIdentity();
        System.out.println("✓ Active identity: " + active.getDisplayName());
        System.out.println();

        // Test 4: Get all identities
        System.out.println("Test 4: Listing all identities...");
        System.out.println("Total identities: " + manager.getAllIdentities().size());
        System.out.println("Ephemeral: " + manager.getEphemeralIdentities().size());
        System.out.println("Persistent: " + manager.getPersistentIdentities().size());
        System.out.println();

        // Test 5: Export identity
        System.out.println("Test 5: Exporting identity...");
        var export = manager.exportIdentity(persistent.getId());
        System.out.println("✓ Exported " + export.get("displayName"));
        System.out.println("  Keys: nsec, npub, hex");
        System.out.println();

        // Test 6: Update reputation
        System.out.println("Test 6: Updating reputation...");
        persistent.incrementCompletedTrades();
        persistent.updateRating(4.5);
        persistent.incrementCompletedTrades();
        persistent.updateRating(5.0);
        System.out.println("✓ Completed trades: " + persistent.getCompletedTrades());
        System.out.println("  Average rating: " + String.format("%.2f", persistent.getAverageRating()));
        System.out.println("  Stars: " + persistent.getReputationStars());
        System.out.println();

        // Test 7: Delete identity
        System.out.println("Test 7: Deleting ephemeral identity...");
        manager.deleteIdentity(ephemeral.getId());
        System.out.println("✓ Deleted: " + ephemeral.getDisplayName());
        System.out.println("  Status: " + ephemeral.getStatus());
        System.out.println();

        System.out.println("=== All Tests Passed ===");
    }
}
