package com.dripps.voxyserver.network;

import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraft.server.level.ServerPlayer;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public final class VoxyServerNetworking {
    public static final int PROTOCOL_VERSION = 2;
    private static final String WIRE_VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("voxyserver", "lod"), () -> WIRE_VERSION,
            VoxyServerNetworking::acceptVersion, VoxyServerNetworking::acceptVersion);
    private static final Map<ResourceLocation, LodPayload.Type<?>> TYPES = Map.ofEntries(
            type(LODHandshakePayload.TYPE), type(LODManifestPayload.TYPE), type(LODBatchResultPayload.TYPE),
            type(LODPreferencesPayload.TYPE), type(LODProtocolPayload.TYPE), type(LODServerSettingsPayload.TYPE),
            type(LODHashSyncSettingsPayload.TYPE), type(LODStreamSessionPayload.TYPE),
            type(PreSerializedLodPayload.TYPE), type(LODClearPayload.TYPE));
    private static final Set<LodPayload.Type<?>> SERVERBOUND = Set.of(LODHandshakePayload.TYPE,
            LODManifestPayload.TYPE, LODBatchResultPayload.TYPE, LODPreferencesPayload.TYPE);
    private static final Set<LodPayload.Type<?>> CLIENTBOUND = Set.of(LODProtocolPayload.TYPE,
            LODServerSettingsPayload.TYPE, LODHashSyncSettingsPayload.TYPE, LODStreamSessionPayload.TYPE,
            PreSerializedLodPayload.TYPE, LODClearPayload.TYPE);

    private static boolean acceptVersion(String version) {
        return WIRE_VERSION.equals(version) || NetworkRegistry.ABSENT.version().equals(version) || NetworkRegistry.ACCEPTVANILLA.equals(version);
    }
    private static Map.Entry<ResourceLocation, LodPayload.Type<?>> type(LodPayload.Type<?> type) {
        return Map.entry(type.id(), type);
    }
    public static void register() {
        CHANNEL.registerMessage(0, Wire.class, VoxyServerNetworking::encode,
                VoxyServerNetworking::decode, VoxyServerNetworking::handle);
    }
    private record Wire(LodPayload payload) {}
    private static void encode(Wire message, FriendlyByteBuf out) {
        out.writeResourceLocation(message.payload.type().id());
        PayloadCodecs.<LodPayload>forType(message.payload.type()).encode(out, message.payload);
    }
    private static Wire decode(FriendlyByteBuf in) {
        var id = in.readResourceLocation();
        var type = TYPES.get(id);
        if (type == null) throw new IllegalArgumentException("Unknown VoxyServer packet " + id);
        LodPayload payload = PayloadCodecs.forType(type).decode(in);
        PayloadCodecs.requireConsumed(in);
        return new Wire(payload);
    }
    private static void handle(Wire wire, Supplier<NetworkEvent.Context> contextSupplier) {
        var context = contextSupplier.get();
        var payload = wire.payload();
        if (context.getDirection().getReceptionSide() == LogicalSide.SERVER) {
            if (!SERVERBOUND.contains(payload.type())) throw new IllegalArgumentException("Invalid serverbound LOD packet");
            var player = context.getSender();
            if (player != null) context.enqueueWork(() -> {
                if (player.connection.connection == context.getNetworkManager()
                        && player.connection.connection.isConnected()
                        && player.server.getPlayerList().getPlayer(player.getUUID()) == player) {
                    ServerLodNetworking.receive(payload, player);
                }
            });
        } else {
            if (!CLIENTBOUND.contains(payload.type())) throw new IllegalArgumentException("Invalid clientbound LOD packet");
            Connection connection = context.getNetworkManager();
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    com.dripps.voxyserver.client.ClientLodNetworking.receive(payload, connection));
        }
        context.setPacketHandled(true);
    }
    public static boolean isRemotePresent(Connection connection) { return connection != null && CHANNEL.isRemotePresent(connection); }
    public static void sendToServer(LodPayload payload) { CHANNEL.sendToServer(new Wire(payload)); }
    public static void sendToPlayer(ServerPlayer player, LodPayload payload) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Wire(payload));
    }
}
