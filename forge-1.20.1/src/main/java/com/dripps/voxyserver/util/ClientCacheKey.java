package com.dripps.voxyserver.util;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Hash sidecars share the exact server namespace of Voxy's storage root. */
public record ClientCacheKey(String serverNamespace, String worldId) {
    public ClientCacheKey {
        if (!serverNamespace.matches("[0-9a-f]{64}") || !worldId.matches("[0-9a-f]{32}")) {
            throw new IllegalArgumentException("Invalid cache identity");
        }
    }
    public static ClientCacheKey of(Path storageBase, String worldId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    storageBase.toAbsolutePath().normalize().toString().getBytes(StandardCharsets.UTF_8));
            return new ClientCacheKey(HexFormat.of().formatHex(digest), worldId);
        } catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }
    public Path fileUnder(Path gameDirectory) {
        return gameDirectory.resolve("voxyserver").resolve("hashes").resolve(serverNamespace).resolve(worldId + ".bin");
    }
}
