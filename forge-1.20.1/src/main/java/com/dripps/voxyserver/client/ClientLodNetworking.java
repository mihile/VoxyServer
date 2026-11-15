package com.dripps.voxyserver.client;

import com.dripps.voxyserver.network.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public final class ClientLodNetworking {
    public record Context(Minecraft client, ClientPacketListener handler) {}
    private static final Map<LodPayload.Type<?>, BiConsumer<LodPayload, Context>> RECEIVERS = new ConcurrentHashMap<>();
    @SuppressWarnings("unchecked")
    public static <T extends LodPayload> void registerGlobalReceiver(LodPayload.Type<T> type, BiConsumer<T, Context> receiver) {
        RECEIVERS.put(type, (BiConsumer<LodPayload, Context>) (BiConsumer<?, ?>) receiver);
    }
    public static void receive(LodPayload payload, Connection connection) {
        Minecraft client = Minecraft.getInstance();
        ClientPacketListener handler = client.getConnection();
        if (handler == null || handler.getConnection() != connection || !connection.isConnected()) return;
        var receiver = RECEIVERS.get(payload.type());
        if (receiver != null) receiver.accept(payload, new Context(client, handler));
    }
    public static boolean canSend(LodPayload.Type<?> type) {
        var handler = Minecraft.getInstance().getConnection();
        return handler != null && VoxyServerNetworking.isRemotePresent(handler.getConnection());
    }
    public static <T extends LodPayload> void send(T payload) {
        if (canSend(payload.type())) VoxyServerNetworking.sendToServer(payload);
    }
}
