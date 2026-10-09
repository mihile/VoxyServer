package com.dripps.voxyserver.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record LODServerSettingsPayload(
        int maxLodStreamRadius,
        int maxSectionsPerTick
) implements LodPayload {

    public static final Type<LODServerSettingsPayload> TYPE =
            new Type<>(new ResourceLocation("voxyserver:lod_server_settings"));

    public static final PacketCodec<FriendlyByteBuf, LODServerSettingsPayload> CODEC =
            PacketCodec.of(LODServerSettingsPayload::write, LODServerSettingsPayload::read);

    private static void write(FriendlyByteBuf buf, LODServerSettingsPayload payload) {
        buf.writeVarInt(payload.maxLodStreamRadius);
        buf.writeVarInt(payload.maxSectionsPerTick);
    }

    private static LODServerSettingsPayload read(FriendlyByteBuf buf) {
        int maxLodStreamRadius = buf.readVarInt();
        int maxSectionsPerTick = buf.readVarInt();
        return new LODServerSettingsPayload(maxLodStreamRadius, maxSectionsPerTick);
    }

    @Override
    public Type<? extends LodPayload> type() {
        return TYPE;
    }
}
