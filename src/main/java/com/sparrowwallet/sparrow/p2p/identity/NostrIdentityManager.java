package com.sparrowwallet.sparrow.p2p.identity;

import com.google.gson.*;
import com.sparrowwallet.drongo.SecureString;
import com.sparrowwallet.sparrow.io.Storage;
import com.sparrowwallet.sparrow.nostr.NostrCrypto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * Manages Nostr identities for P2P trading.
 *
 * Responsibilities:
 * - Create and store identities (ephemeral and persistent)
 * - Manage active identity selection
 * - Handle identity lifecycle (expiration, deletion)
 * - Persist identities to storage
 * - Generate random display names for privacy
 *
 * Thread-safe implementation using ConcurrentHashMap.
 */
public class NostrIdentityManager {
    private static final Logger log = LoggerFactory.getLogger(NostrIdentityManager.class);

    private static NostrIdentityManager instance;
    private static final String IDENTITIES_FILENAME = "nostr-identities.json";

    // Persistent storage
    private final Map<String, NostrIdentity> identities;
    private NostrIdentity activeIdentity;
    private String activeIdentityId; // Store ID for persistence

    // Identity change listeners
    private final List<BiConsumer<NostrIdentity, NostrIdentity>> identityChangeListeners;

    // Random name generation
    private static final String[] ADJECTIVES = {
        "Anonymous", "Silent", "Swift", "Phantom", "Shadow", "Ghost", "Quiet",
        "Hidden", "Secret", "Private", "Stealth", "Crypto", "Digital", "Anon"
    };

    private static final SecureRandom random = new SecureRandom();

    private NostrIdentityManager() {
        this.identities = new ConcurrentHashMap<>();
        this.identityChangeListeners = new CopyOnWriteArrayList<>();
        loadFromDisk();
    }

    /**
     * Get singleton instance
     */
    public static synchronized NostrIdentityManager getInstance() {
        if (instance == null) {
            instance = new NostrIdentityManager();
        }
        return instance;
    }

    /**
     * Create a new ephemeral identity
     */
    public NostrIdentity createEphemeralIdentity() {
        return createEphemeralIdentity(null);
    }

    /**
     * Create a new ephemeral identity with optional custom name
     */
    public NostrIdentity createEphemeralIdentity(String displayName) {
        try {
            // Generate new keypair
            String nsec = NostrCrypto.generateNostrPrivateKeyHex();
            String npub = NostrCrypto.deriveNostrPublicKeyNpub(nsec);
            String hex = NostrCrypto.deriveNostrPublicKeyHex(nsec);
            String compressedHex = NostrCrypto.deriveNostrPublicKeyCompressed(nsec);

            // Generate random display name if not provided
            if (displayName == null || displayName.trim().isEmpty()) {
                displayName = generateRandomDisplayName();
            }

            NostrIdentity identity = new NostrIdentity(nsec, npub, hex, displayName, IdentityType.EPHEMERAL);
            identity.setCompressedHex(compressedHex);

            // Ephemeral identities expire after trade completion
            // For now, set a default expiration time
            identity.setExpiresAt(LocalDateTime.now().plusDays(7));
            identity.setAutoDelete(true);

            identities.put(identity.getId(), identity);
            saveToDisk();
            log.info("Created ephemeral identity: {} ({})", identity.getDisplayName(), identity.getShortNpub());

            return identity;

        } catch (Exception e) {
            log.error("Failed to create ephemeral identity", e);
            throw new RuntimeException("Failed to create ephemeral identity: " + e.getMessage(), e);
        }
    }

    /**
     * Create a new persistent identity
     */
    public NostrIdentity createPersistentIdentity(String displayName) {
        try {
            // Generate new keypair
            String nsec = NostrCrypto.generateNostrPrivateKeyHex();
            String npub = NostrCrypto.deriveNostrPublicKeyNpub(nsec);
            String hex = NostrCrypto.deriveNostrPublicKeyHex(nsec);
            String compressedHex = NostrCrypto.deriveNostrPublicKeyCompressed(nsec);

            // Persistent identities require a meaningful display name
            if (displayName == null || displayName.trim().isEmpty()) {
                throw new IllegalArgumentException("Persistent identities require a display name");
            }

            NostrIdentity identity = new NostrIdentity(nsec, npub, hex, displayName, IdentityType.PERSISTENT);
            identity.setCompressedHex(compressedHex);
            identity.setAutoDelete(false);

            identities.put(identity.getId(), identity);
            saveToDisk();
            log.info("Created persistent identity: {} ({})", identity.getDisplayName(), identity.getShortNpub());

            return identity;

        } catch (Exception e) {
            log.error("Failed to create persistent identity", e);
            throw new RuntimeException("Failed to create persistent identity: " + e.getMessage(), e);
        }
    }

    /**
     * Import an existing Nostr identity from nsec
     */
    public NostrIdentity importIdentity(String nsec, String displayName, IdentityType type) {
        try {
            String npub = NostrCrypto.deriveNostrPublicKeyNpub(nsec);
            String hex = NostrCrypto.deriveNostrPublicKeyHex(nsec);
            String compressedHex = NostrCrypto.deriveNostrPublicKeyCompressed(nsec);

            if (displayName == null || displayName.trim().isEmpty()) {
                displayName = type == IdentityType.EPHEMERAL ?
                    generateRandomDisplayName() : "Imported_Identity";
            }

            NostrIdentity identity = new NostrIdentity(nsec, npub, hex, displayName, type);
            identity.setCompressedHex(compressedHex);

            identities.put(identity.getId(), identity);
            saveToDisk();
            log.info("Imported {} identity: {} ({})",
                type.name().toLowerCase(), identity.getDisplayName(), identity.getShortNpub());

            return identity;

        } catch (Exception e) {
            log.error("Failed to import identity", e);
            throw new RuntimeException("Failed to import identity: " + e.getMessage(), e);
        }
    }

    /**
     * Get all identities
     */
    public List<NostrIdentity> getAllIdentities() {
        return new ArrayList<>(identities.values());
    }

    /**
     * Get all active identities (not deleted or expired)
     */
    public List<NostrIdentity> getActiveIdentities() {
        return identities.values().stream()
            .filter(id -> id.isActive() && !id.isExpired())
            .collect(Collectors.toList());
    }

    /**
     * Get ephemeral identities
     */
    public List<NostrIdentity> getEphemeralIdentities() {
        return identities.values().stream()
            .filter(NostrIdentity::isEphemeral)
            .collect(Collectors.toList());
    }

    /**
     * Get persistent identities
     */
    public List<NostrIdentity> getPersistentIdentities() {
        return identities.values().stream()
            .filter(NostrIdentity::isPersistent)
            .collect(Collectors.toList());
    }

    /**
     * Get identity by ID
     */
    public NostrIdentity getIdentity(String id) {
        return identities.get(id);
    }

    /**
     * Get identity by npub
     */
    public NostrIdentity getIdentityByNpub(String npub) {
        return identities.values().stream()
            .filter(id -> id.getNpub().equals(npub))
            .findFirst()
            .orElse(null);
    }

    /**
     * Get identity by hex pubkey
     */
    public NostrIdentity getIdentityByHex(String hex) {
        return identities.values().stream()
            .filter(id -> id.getHex().equals(hex))
            .findFirst()
            .orElse(null);
    }

    /**
     * Get or create the active identity
     */
    public NostrIdentity getOrCreateActiveIdentity() {
        if (activeIdentity != null && activeIdentity.isActive() && !activeIdentity.isExpired()) {
            return activeIdentity;
        }

        // Create a new ephemeral identity by default
        activeIdentity = createEphemeralIdentity();
        return activeIdentity;
    }

    /**
     * Get the currently active identity
     */
    public NostrIdentity getActiveIdentity() {
        return activeIdentity;
    }

    /**
     * Add a listener that will be notified when the active identity changes
     * @param listener BiConsumer that receives (newIdentity, oldIdentity)
     */
    public void addIdentityChangeListener(BiConsumer<NostrIdentity, NostrIdentity> listener) {
        if (listener != null) {
            identityChangeListeners.add(listener);
            log.debug("Added identity change listener");
        }
    }

    /**
     * Remove an identity change listener
     * @param listener The listener to remove
     */
    public void removeIdentityChangeListener(BiConsumer<NostrIdentity, NostrIdentity> listener) {
        identityChangeListeners.remove(listener);
        log.debug("Removed identity change listener");
    }

    /**
     * Set the active identity
     */
    public void setActiveIdentity(NostrIdentity identity) {
        if (identity == null) {
            throw new IllegalArgumentException("Identity cannot be null");
        }

        if (!identity.isActive()) {
            throw new IllegalStateException("Cannot activate a non-active identity");
        }

        if (identity.isExpired()) {
            throw new IllegalStateException("Cannot activate an expired identity");
        }

        NostrIdentity oldIdentity = this.activeIdentity;
        this.activeIdentity = identity;
        identity.setLastUsedAt(LocalDateTime.now());
        saveToDisk();
        log.info("Active identity changed to: {} ({})",
            identity.getDisplayName(), identity.getShortNpub());

        // Notify listeners of identity change
        for (BiConsumer<NostrIdentity, NostrIdentity> listener : identityChangeListeners) {
            try {
                listener.accept(identity, oldIdentity);
            } catch (Exception e) {
                log.error("Error notifying identity change listener", e);
            }
        }
    }

    /**
     * Delete an identity
     */
    public void deleteIdentity(String id) {
        NostrIdentity identity = identities.get(id);
        if (identity == null) {
            return;
        }

        identity.delete();
        log.info("Deleted identity: {} ({})", identity.getDisplayName(), identity.getShortNpub());

        // If this was the active identity, clear it
        if (activeIdentity != null && activeIdentity.getId().equals(id)) {
            activeIdentity = null;
            activeIdentityId = null;
        }

        // Remove from storage
        identities.remove(id);
        saveToDisk();
    }

    /**
     * Clean up expired ephemeral identities with auto-delete enabled
     */
    public void cleanupExpiredIdentities() {
        List<NostrIdentity> toDelete = identities.values().stream()
            .filter(NostrIdentity::isEphemeral)
            .filter(NostrIdentity::isAutoDelete)
            .filter(NostrIdentity::isExpired)
            .collect(Collectors.toList());

        for (NostrIdentity identity : toDelete) {
            log.info("Auto-deleting expired ephemeral identity: {} ({})",
                identity.getDisplayName(), identity.getShortNpub());
            identities.remove(identity.getId());
        }

        if (!toDelete.isEmpty()) {
            log.info("Cleaned up {} expired ephemeral identities", toDelete.size());
        }
    }

    /**
     * Generate a random display name for privacy
     */
    private String generateRandomDisplayName() {
        String adjective = ADJECTIVES[random.nextInt(ADJECTIVES.length)];
        String suffix = Integer.toHexString(random.nextInt(0xFFFF));
        return adjective + "_" + suffix;
    }

    /**
     * Get count of identities by type
     */
    public int getIdentityCount(IdentityType type) {
        return (int) identities.values().stream()
            .filter(id -> id.getType() == type)
            .count();
    }

    /**
     * Check if an identity with this npub already exists
     */
    public boolean identityExists(String npub) {
        return getIdentityByNpub(npub) != null;
    }

    /**
     * Export identity keys (for backup)
     */
    public Map<String, String> exportIdentity(String id) {
        NostrIdentity identity = identities.get(id);
        if (identity == null) {
            throw new IllegalArgumentException("Identity not found: " + id);
        }

        Map<String, String> export = new HashMap<>();
        export.put("id", identity.getId());
        export.put("npub", identity.getNpub());
        export.put("nsec", identity.getNsec());
        export.put("hex", identity.getHex());
        export.put("displayName", identity.getDisplayName());
        export.put("type", identity.getType().name());

        log.info("Exported identity: {} ({})", identity.getDisplayName(), identity.getShortNpub());
        return export;
    }

    /**
     * Clear all identities (for testing)
     */
    public void clearAll() {
        identities.clear();
        activeIdentity = null;
        activeIdentityId = null;
        saveToDisk();
        log.warn("Cleared all identities");
    }

    /**
     * Save identities to disk
     */
    private void saveToDisk() {
        try {
            File sparrowDir = Storage.getSparrowDir();
            File identitiesFile = new File(sparrowDir, IDENTITIES_FILENAME);

            if(!identitiesFile.exists()) {
                Storage.createOwnerOnlyFile(identitiesFile);
            }

            // Create JSON object to save
            JsonObject root = new JsonObject();

            // Save active identity ID
            if(activeIdentity != null) {
                root.addProperty("activeIdentityId", activeIdentity.getId());
            }

            // Save all identities
            JsonArray identitiesArray = new JsonArray();
            for(NostrIdentity identity : identities.values()) {
                JsonObject identityJson = new JsonObject();
                identityJson.addProperty("id", identity.getId());
                identityJson.addProperty("npub", identity.getNpub());
                identityJson.addProperty("nsec", identity.getNsec());
                identityJson.addProperty("hex", identity.getHex());
                identityJson.addProperty("displayName", identity.getDisplayName());
                identityJson.addProperty("type", identity.getType().name());
                identityJson.addProperty("isActive", identity.isActive());
                identityJson.addProperty("createdAt", identity.getCreatedAt().toString());

                if(identity.getLastUsedAt() != null) {
                    identityJson.addProperty("lastUsedAt", identity.getLastUsedAt().toString());
                }
                if(identity.getExpiresAt() != null) {
                    identityJson.addProperty("expiresAt", identity.getExpiresAt().toString());
                }

                identitiesArray.add(identityJson);
            }
            root.add("identities", identitiesArray);

            // Write to file
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            try (Writer writer = new FileWriter(identitiesFile)) {
                gson.toJson(root, writer);
                writer.flush();
            }

            log.debug("Saved {} identities to disk", identities.size());

        } catch (IOException e) {
            log.error("Failed to save identities to disk", e);
        }
    }

    /**
     * Load identities from disk
     */
    private void loadFromDisk() {
        try {
            File sparrowDir = Storage.getSparrowDir();
            File identitiesFile = new File(sparrowDir, IDENTITIES_FILENAME);

            if(!identitiesFile.exists()) {
                log.debug("No identities file found, starting fresh");
                return;
            }

            // Read file
            try (Reader reader = new FileReader(identitiesFile)) {
                Gson gson = new Gson();
                JsonObject root = gson.fromJson(reader, JsonObject.class);

                if(root == null) {
                    log.warn("Empty identities file");
                    return;
                }

                // Load active identity ID
                if(root.has("activeIdentityId")) {
                    activeIdentityId = root.get("activeIdentityId").getAsString();
                }

                // Load identities
                if(root.has("identities")) {
                    JsonArray identitiesArray = root.getAsJsonArray("identities");
                    for(JsonElement element : identitiesArray) {
                        JsonObject identityJson = element.getAsJsonObject();

                        try {
                            String id = identityJson.get("id").getAsString();
                            String npub = identityJson.get("npub").getAsString();
                            String nsec = identityJson.get("nsec").getAsString();
                            String hex = identityJson.get("hex").getAsString();
                            String displayName = identityJson.get("displayName").getAsString();
                            IdentityType type = IdentityType.valueOf(identityJson.get("type").getAsString());
                            boolean isActive = identityJson.get("isActive").getAsBoolean();
                            LocalDateTime createdAt = LocalDateTime.parse(identityJson.get("createdAt").getAsString());

                            LocalDateTime lastUsedAt = null;
                            if(identityJson.has("lastUsedAt")) {
                                lastUsedAt = LocalDateTime.parse(identityJson.get("lastUsedAt").getAsString());
                            }

                            LocalDateTime expiresAt = null;
                            if(identityJson.has("expiresAt")) {
                                expiresAt = LocalDateTime.parse(identityJson.get("expiresAt").getAsString());
                            }

                            // Reconstruct identity
                            NostrIdentity identity = new NostrIdentity(
                                id, npub, nsec, hex, displayName, type,
                                createdAt, lastUsedAt, expiresAt, isActive
                            );

                            identities.put(id, identity);

                            // Set as active if it was the active identity
                            if(id.equals(activeIdentityId)) {
                                activeIdentity = identity;
                            }

                        } catch (Exception e) {
                            log.error("Failed to load identity from JSON", e);
                        }
                    }
                }

                log.info("Loaded {} identities from disk (active: {})",
                    identities.size(),
                    activeIdentity != null ? activeIdentity.getDisplayName() : "none");

            }

        } catch (Exception e) {
            log.error("Failed to load identities from disk", e);
        }
    }
}
