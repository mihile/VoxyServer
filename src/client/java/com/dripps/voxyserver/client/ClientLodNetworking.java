package com.dripps.voxyserver.client;

import com.dripps.voxyserver.network.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import java.util.function.BiConsumer;

public final class ClientLodNetworking {
    public record Context(Minecraft client) {}
    public static <T extends LodPayload> void registerGlobalReceiver(LodPayload.Type<T> type, BiConsumer<T, Context> receiver) {
        PacketCodec<FriendlyByteBuf, T> codec = PayloadCodecs.forType(type);
        ClientPlayNetworking.registerGlobalReceiver(type.id(), (client, handler, buffer, sender) -> {
            T payload = codec.decode(buffer);
            PayloadCodecs.requireConsumed(buffer);
            receiver.accept(payload, new Context(client));
        });
    }
    public static boolean canSend(LodPayload.Type<?> type) { return ClientPlayNetworking.canSend(type.id()); }
    public static <T extends LodPayload> void send(T payload) {
        FriendlyByteBuf buffer = PacketByteBufs.create();
        try {
            PayloadCodecs.<T>forType(payload.type()).encode(buffer, payload);
            if (buffer.readableBytes() > 32_767) throw new IllegalArgumentException("LOD packet exceeds 1.20.1 serverbound limit");
            ClientPlayNetworking.send(payload.type().id(), buffer);
        } catch (RuntimeException | Error e) {
            buffer.release();
            throw e;
        }
        // The custom payload packet owns the buffer until asynchronous encoding.
    }
}
