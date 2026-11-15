package com.dripps.voxyserver.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record LODStreamSessionPayload(ResourceLocation dimension, long streamEpoch,
                                      long manifestRequestId, boolean manifestRequired)
        implements LodPayload {

    public static final Type<LODStreamSessionPayload> TYPE =
            new Type<>(new ResourceLocation("voxyserver:lod_stream_session"));

    public static final PacketCodec<FriendlyByteBuf, LODStreamSessionPayload> CODEC =
            PacketCodec.of(LODStreamSessionPayload::write, LODStreamSessionPayload::read);

    private static void write(FriendlyByteBuf buf, LODStreamSessionPayload payload) {
        buf.writeResourceLocation(payload.dimension);
        buf.writeVarLong(payload.streamEpoch);
        buf.writeVarLong(payload.manifestRequestId);
        buf.writeBoolean(payload.manifestRequired);
    }

    private static LODStreamSessionPayload read(FriendlyByteBuf buf) {
        ResourceLocation dimension = buf.readResourceLocation();
        long streamEpoch = buf.readVarLong();
        long manifestRequestId = buf.readVarLong();
        boolean manifestRequired = buf.readBoolean();
        if (streamEpoch < 0L || manifestRequestId < 0L) {
            throw new IllegalArgumentException("invalid LOD stream session");
        }
        return new LODStreamSessionPayload(dimension, streamEpoch, manifestRequestId, manifestRequired);
    }

    @Override
    public Type<? extends LodPayload> type() {
        return TYPE;
    }
}
