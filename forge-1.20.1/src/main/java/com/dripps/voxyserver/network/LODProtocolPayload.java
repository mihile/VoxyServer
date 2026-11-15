package com.dripps.voxyserver.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record LODProtocolPayload(int protocol) implements LodPayload {

    public static final Type<LODProtocolPayload> TYPE =
            new Type<>(new ResourceLocation("voxyserver:lod_protocol"));

    public static final PacketCodec<FriendlyByteBuf, LODProtocolPayload> CODEC =
            PacketCodec.of(LODProtocolPayload::write, LODProtocolPayload::read);

    private static void write(FriendlyByteBuf buf, LODProtocolPayload payload) {
        buf.writeVarInt(payload.protocol);
    }

    private static LODProtocolPayload read(FriendlyByteBuf buf) {
        return new LODProtocolPayload(buf.readVarInt());
    }

    @Override
    public Type<? extends LodPayload> type() {
        return TYPE;
    }
}
