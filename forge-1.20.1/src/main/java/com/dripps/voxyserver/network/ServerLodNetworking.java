package com.dripps.voxyserver.network;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public final class ServerLodNetworking {
    public record Context(MinecraftServer server, ServerPlayer player) {}
    private static final Map<LodPayload.Type<?>, BiConsumer<LodPayload, Context>> RECEIVERS = new ConcurrentHashMap<>();
    @SuppressWarnings("unchecked")
    public static <T extends LodPayload> void registerGlobalReceiver(LodPayload.Type<T> type, BiConsumer<T, Context> receiver) {
        RECEIVERS.put(type, (BiConsumer<LodPayload, Context>) (BiConsumer<?, ?>) receiver);
    }
    public static void receive(LodPayload payload, ServerPlayer player) {
        var receiver = RECEIVERS.get(payload.type());
        if (receiver != null) receiver.accept(payload, new Context(player.server, player));
    }
    public static boolean canSend(ServerPlayer player, LodPayload.Type<?> type) {
        return VoxyServerNetworking.isRemotePresent(player.connection.connection);
    }
    public static <T extends LodPayload> void send(ServerPlayer player, T payload) {
        if (canSend(player, payload.type())) VoxyServerNetworking.sendToPlayer(player, payload);
    }
}
