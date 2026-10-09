package com.dripps.voxyserver.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record LODPreferencesPayload(
        boolean enabled,
        int lodStreamRadius,
        int maxSectionsPerTick
) implements LodPayload {

    public static final Type<LODPreferencesPayload> TYPE =
            new Type<>(new ResourceLocation("voxyserver:lod_preferences"));

    public static final PacketCodec<FriendlyByteBuf, LODPreferencesPayload> CODEC =
            PacketCodec.of(LODPreferencesPayload::write, LODPreferencesPayload::read);

    private static void write(FriendlyByteBuf buf, LODPreferencesPayload payload) {
        buf.writeBoolean(payload.enabled);
        buf.writeVarInt(payload.lodStreamRadius);
        buf.writeVarInt(payload.maxSectionsPerTick);
    }

    private static LODPreferencesPayload read(FriendlyByteBuf buf) {
        boolean enabled = buf.readBoolean();
        int lodStreamRadius = buf.readVarInt();
        int maxSectionsPerTick = buf.readVarInt();
        return new LODPreferencesPayload(enabled, lodStreamRadius, maxSectionsPerTick);
    }

    @Override
    public Type<? extends LodPayload> type() {
        return TYPE;
    }
}
