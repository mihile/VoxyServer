package com.dripps.voxyserver.network;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.util.function.BiConsumer;

public final class ServerLodNetworking {
    public record Context(MinecraftServer server, ServerPlayer player) {}
    public static <T extends LodPayload> void registerGlobalReceiver(LodPayload.Type<T> type, BiConsumer<T, Context> receiver) {
        PacketCodec<FriendlyByteBuf, T> codec = PayloadCodecs.forType(type);
        ServerPlayNetworking.registerGlobalReceiver(type.id(), (server, player, handler, buffer, sender) -> {
            T payload = codec.decode(buffer);
            PayloadCodecs.requireConsumed(buffer);
            server.execute(() -> receiver.accept(payload, new Context(server, player)));
        });
    }
    public static boolean canSend(ServerPlayer player, LodPayload.Type<?> type) {
        return ServerPlayNetworking.canSend(player, type.id());
    }
    public static <T extends LodPayload> void send(ServerPlayer player, T payload) {
        FriendlyByteBuf buffer = PacketByteBufs.create();
        try {
            PayloadCodecs.<T>forType(payload.type()).encode(buffer, payload);
            if (buffer.readableBytes() > 1_048_576) throw new IllegalArgumentException("LOD packet exceeds 1.20.1 clientbound limit");
            ServerPlayNetworking.send(player, payload.type().id(), buffer);
        } catch (RuntimeException | Error e) {
            buffer.release();
            throw e;
        }
        // The custom payload packet owns the buffer until asynchronous encoding.
    }
}
