package com.dripps.voxyserver.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record LODHashSyncSettingsPayload(
        boolean enabled
) implements LodPayload {

    public static final Type<LODHashSyncSettingsPayload> TYPE =
            new Type<>(new ResourceLocation("voxyserver:lod_hash_sync_settings"));

    public static final PacketCodec<FriendlyByteBuf, LODHashSyncSettingsPayload> CODEC =
            PacketCodec.of(LODHashSyncSettingsPayload::write, LODHashSyncSettingsPayload::read);

    private static void write(FriendlyByteBuf buf, LODHashSyncSettingsPayload payload) {
        buf.writeBoolean(payload.enabled);
    }

    private static LODHashSyncSettingsPayload read(FriendlyByteBuf buf) {
        return new LODHashSyncSettingsPayload(buf.readBoolean());
    }

    @Override
    public Type<? extends LodPayload> type() {
        return TYPE;
    }
}
