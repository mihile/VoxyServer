package com.dripps.voxyserver.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record LODHandshakePayload(int protocol) implements LodPayload {

    public static final Type<LODHandshakePayload> TYPE =
            new Type<>(new ResourceLocation("voxyserver:lod_handshake"));

    public static final PacketCodec<FriendlyByteBuf, LODHandshakePayload> CODEC =
            PacketCodec.of(LODHandshakePayload::write, LODHandshakePayload::read);

    private static void write(FriendlyByteBuf buf, LODHandshakePayload payload) {
        buf.writeVarInt(payload.protocol);
    }

    private static LODHandshakePayload read(FriendlyByteBuf buf) {
        return new LODHandshakePayload(buf.readVarInt());
    }

    @Override
    public Type<? extends LodPayload> type() {
        return TYPE;
    }
}
