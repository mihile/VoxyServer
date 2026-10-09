package com.dripps.voxyserver.network;

import net.minecraft.network.FriendlyByteBuf;
import java.util.Map;

public final class PayloadCodecs {
    private static final Map<LodPayload.Type<?>, PacketCodec<FriendlyByteBuf, ?>> CODECS = Map.ofEntries(
            Map.entry(LODBatchResultPayload.TYPE, LODBatchResultPayload.CODEC),
            Map.entry(LODBulkPayload.TYPE, LODBulkPayload.CODEC),
            Map.entry(LODClearPayload.TYPE, LODClearPayload.CODEC),
            Map.entry(LODHandshakePayload.TYPE, LODHandshakePayload.CODEC),
            Map.entry(LODHashSyncSettingsPayload.TYPE, LODHashSyncSettingsPayload.CODEC),
            Map.entry(LODManifestPayload.TYPE, LODManifestPayload.CODEC),
            Map.entry(LODPreferencesPayload.TYPE, LODPreferencesPayload.CODEC),
            Map.entry(LODProtocolPayload.TYPE, LODProtocolPayload.CODEC),
            Map.entry(LODSectionPayload.TYPE, LODSectionPayload.CODEC),
            Map.entry(LODServerSettingsPayload.TYPE, LODServerSettingsPayload.CODEC),
            Map.entry(LODStreamSessionPayload.TYPE, LODStreamSessionPayload.CODEC),
            Map.entry(PreSerializedLodPayload.TYPE, PreSerializedLodPayload.CODEC)
    );
    @SuppressWarnings("unchecked")
    public static <T extends LodPayload> PacketCodec<FriendlyByteBuf, T> forType(LodPayload.Type<?> type) {
        var codec = CODECS.get(type);
        if (codec == null) throw new IllegalArgumentException("Unknown LOD channel: " + type.id());
        return (PacketCodec<FriendlyByteBuf, T>) codec;
    }
    public static void requireConsumed(FriendlyByteBuf buffer) {
        if (buffer.isReadable()) throw new IllegalArgumentException("Trailing LOD packet bytes");
    }
}
