package com.dripps.voxyserver.client;

import com.dripps.voxyserver.Voxyserver;
import com.dripps.voxyserver.client.mixin.GameModeConnectionAccessor;
import com.dripps.voxyserver.client.service.IVoxyServerIngestAccess;
import com.dripps.voxyserver.client.service.RemoteIngestService;
import com.dripps.voxyserver.network.*;
import com.dripps.voxyserver.util.ClientCacheKey;
import me.cortex.voxy.client.ClientSessionEvents;
import me.cortex.voxy.client.VoxyClientInstance;
import me.cortex.voxy.client.core.IGetVoxyRenderSystem;
import me.cortex.voxy.commonImpl.VoxyCommon;
import me.cortex.voxy.commonImpl.VoxyInstance;
import me.cortex.voxy.commonImpl.WorldIdentifier;
import me.cortex.voxy.common.world.WorldEngine;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ClientLodReceiver {
    private static final ConnectionInbox<ClientPacketListener> inbox = new ConnectionInbox<>();
    // Everything below is owned by the client thread except completion counters.
    private static ConnectionInbox.Token<ClientPacketListener> joined;
    private static VoxyInstance verifiedInstance;
    private static Path storageBase;
    private static boolean startingVoxy;
    private static boolean handshakeSent;
    private static LODStreamSessionPayload session;
    private static boolean sessionApplied;
    private static ResourceLocation observedDimension;
    private static final ArrayDeque<Inbound> pending = new ArrayDeque<>();
    private static int reservedBatches;
    private static long reservedBytes;
    private static final Object reservationLock = new Object();

    private static final class Inbound {
        final ConnectionInbox.Token<ClientPacketListener> token;
        final ResourceLocation dimension;
        final PreSerializedLodPayload payload;
        final AtomicBoolean released = new AtomicBoolean();
        Inbound(ConnectionInbox.Token<ClientPacketListener> token, ResourceLocation dimension, PreSerializedLodPayload payload) {
            this.token = token; this.dimension = dimension; this.payload = payload;
        }
    }

    public static void register() {
        ClientPlayConnectionEvents.INIT.register((handler, client) -> inbox.begin(handler));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            var token = inbox.tokenFor(handler);
            inbox.end(handler); // Invalidates queued callbacks immediately, even off-thread.
            client.execute(() -> { if (token != null && token == joined) reset(); });
        });
        register(LODProtocolPayload.TYPE);
        register(LODServerSettingsPayload.TYPE);
        register(LODHashSyncSettingsPayload.TYPE);
        register(LODStreamSessionPayload.TYPE);
        register(PreSerializedLodPayload.TYPE);
        register(LODClearPayload.TYPE);
        ClientTickEvents.END_CLIENT_TICK.register(ClientLodReceiver::tick);
    }

    private static <T extends LodPayload> void register(LodPayload.Type<T> type) {
        ClientLodNetworking.registerGlobalReceiver(type, (payload, context) -> {
            var token = inbox.tokenFor(context.handler());
            if (inbox.offer(token, payload)) return;
            if (payload instanceof PreSerializedLodPayload bulk) {
                sendResult(token, new LODBatchResultPayload(bulk.streamEpoch(), bulk.batchId(), LODBatchResultPayload.OVERFLOW));
            } else if (inbox.isCurrent(token)) {
                // Losing a control/session message would corrupt stream ordering.
                // Fail this connection explicitly rather than continue with stale state.
                context.client().execute(() -> {
                    if (inbox.isCurrent(token)) token.handler().getConnection().disconnect(
                            Component.literal("VoxyServer control queue overflow"));
                });
            }
        });
    }

    /** Called at vanilla handleLogin RETURN, after its thread check and world setup. */
    public static void onLogin(ClientPacketListener handler) {
        Minecraft client = Minecraft.getInstance();
        if (!client.isSameThread()) throw new IllegalStateException("Login completion must run on client thread");
        var token = inbox.tokenFor(handler);
        if (token == null) return; // A canceled connection must never become current again.
        if (joined == token) return;
        reset();
        joined = token;
        ClientLodSettings.prepareForCurrentConnection();
    }

    public static boolean allowVoxySessionStart() {
        return Minecraft.getInstance().isSameThread() && startingVoxy;
    }

    public static void onClearLevel() {
        var token = inbox.current();
        if (token != null) inbox.end(token.handler()); // Also cancels INIT before login RETURN.
        reset();
    }

    private static boolean worldReady(Minecraft client) {
        if (!inbox.isCurrent(joined) || !joined.handler().getConnection().isConnected()
                || client.getConnection() != joined.handler() || client.gameMode == null
                || ((GameModeConnectionAccessor) client.gameMode).voxyserver$connection() != joined.handler()
                || client.level == null || client.level != joined.handler().getLevel()
                || client.player == null || client.player.connection != joined.handler()) return false;
        if (client.getSingleplayerServer() != null) return true;
        var info = joined.handler().getServerData();
        return info != null && info.ip != null && !info.ip.isBlank();
    }

    private static Path expectedBase(Minecraft client) {
        if (client.getSingleplayerServer() != null) {
            return client.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("voxy").toAbsolutePath().normalize();
        }
        Path root = client.gameDirectory.toPath().resolve(".voxy").resolve("saves");
        String server = client.isConnectedToRealms() ? "realms" : joined.handler().getServerData().ip.replace(":", "_");
        return root.resolve(server).toAbsolutePath().normalize();
    }

    private static void tick(Minecraft client) {
        if (!worldReady(client)) return;
        if (!ClientSessionEvents.inSession) {
            startingVoxy = true;
            try { ClientSessionEvents.sessionStart(); } finally { startingVoxy = false; }
        }
        VoxyInstance instance = VoxyCommon.getInstance();
        if (!(instance instanceof VoxyClientInstance clientInstance) || !instance.isRunning()) return;
        if (verifiedInstance != instance) {
            Path actual = clientInstance.getStorageBasePath().toAbsolutePath().normalize();
            if (!actual.equals(expectedBase(client))) {
                throw new IllegalStateException("Voxy cache root does not match current connection: " + actual);
            }
            verifiedInstance = instance;
            storageBase = actual;
            // setLevel/allChanged can run before the first client tick. Attach the
            // renderer explicitly once the delayed instance exists.
            var rendering = (IGetVoxyRenderSystem) client.levelRenderer;
            if (rendering.voxy$getRenderSystem() == null) rendering.voxy$createRenderer();
            Voxyserver.LOGGER.info("client cache ready: server={}, root={}, thread={}",
                    joined.handler().getServerData() == null ? "singleplayer" : joined.handler().getServerData().ip,
                    actual, Thread.currentThread().getName());
        }
        if (!handshakeSent && ClientLodNetworking.canSend(LODHandshakePayload.TYPE)) {
            ClientLodNetworking.send(new LODHandshakePayload(VoxyServerNetworking.PROTOCOL_VERSION));
            handshakeSent = true;
        }
        ResourceLocation dimension = client.level.dimension().location();
        if (!Objects.equals(observedDimension, dimension)) {
            observedDimension = dimension;
            sessionApplied = false;
            service().startSession(null, 0L);
            ClientLodSettings.cancelManifest();
        }
        ConnectionInbox.Event<ClientPacketListener> event;
        while ((event = inbox.poll(joined)) != null) handle(event);
        if (session != null && ClientLodSettings.isProtocolOk() && session.dimension().equals(dimension)) {
            if (!sessionApplied) {
                service().startSession(dimension, session.streamEpoch());
                setRemoteIngest(ClientLodSettings.isEnabled());
                ClientLodSettings.applyStreamSession(session);
                sessionApplied = true;
            }
            drain(client);
        }
        ClientLodSettings.onClientTick();
    }

    private static void handle(ConnectionInbox.Event<ClientPacketListener> event) {
        LodPayload payload = event.payload();
        if (payload instanceof LODProtocolPayload protocol) {
            boolean ok = protocol.protocol() == VoxyServerNetworking.PROTOCOL_VERSION;
            ClientLodSettings.setProtocolOk(ok);
            if (!ok) {
                disableRemoteIngest();
                if (protocol.protocol() < VoxyServerNetworking.PROTOCOL_VERSION) tellPlayer(Component.literal("[VoxyServer] Server protocol is out of date; remote LODs disabled.").withStyle(ChatFormatting.RED));
            }
        } else if (payload instanceof LODServerSettingsPayload settings) {
            ClientLodSettings.applyServerSettings(settings.maxLodStreamRadius(), settings.maxSectionsPerTick());
        } else if (payload instanceof LODHashSyncSettingsPayload hashes) {
            ClientLodSettings.applyHashSyncSettings(hashes.enabled());
        } else if (payload instanceof LODStreamSessionPayload next) {
            if (session != null && next.streamEpoch() < session.streamEpoch()) return;
            if (!next.equals(session)) {
                service().startSession(null, 0L);
                ClientLodSettings.cancelManifest();
                sessionApplied = false;
            }
            session = next;
            discardStalePending();
        } else if (payload instanceof PreSerializedLodPayload bulk) {
            if (!ClientLodSettings.isProtocolOk() || session == null || bulk.streamEpoch() != session.streamEpoch()) {
                sendResult(event.token(), new LODBatchResultPayload(bulk.streamEpoch(), bulk.batchId(), LODBatchResultPayload.STALE));
                return;
            }
            Inbound inbound = new Inbound(event.token(), session.dimension(), bulk);
            synchronized (reservationLock) {
                if (reservedBatches >= 64 || reservedBytes + bulk.data().length > 64L * 1024 * 1024) {
                    sendResult(event.token(), new LODBatchResultPayload(bulk.streamEpoch(), bulk.batchId(), LODBatchResultPayload.OVERFLOW));
                    return;
                }
                reservedBatches++; reservedBytes += bulk.data().length;
            }
            pending.addLast(inbound);
        } else if (payload instanceof LODClearPayload) {
            // The following stream session is authoritative; do not create a world here.
        }
    }

    private static void drain(Minecraft client) {
        while (!pending.isEmpty()) {
            Inbound inbound = pending.removeFirst();
            if (!inbox.isCurrent(inbound.token) || session == null
                    || inbound.payload.streamEpoch() != session.streamEpoch()
                    || !inbound.dimension.equals(client.level.dimension().location())) {
                complete(inbound, LODBatchResultPayload.STALE);
                continue;
            }
            WorldIdentifier worldId = WorldIdentifier.of(client.level);
            WorldEngine engine = worldId == null ? null : verifiedInstance.getOrCreate(worldId);
            if (engine == null) { complete(inbound, LODBatchResultPayload.FAILURE); continue; }
            ClientCacheKey key = ClientCacheKey.of(storageBase, worldId.getWorldId());
            if (!service().enqueueIngest(engine, key, inbound.dimension, inbound.payload, client.level.registryAccess(),
                    result -> { release(inbound); sendResult(inbound.token, result); })) {
                complete(inbound, LODBatchResultPayload.OVERFLOW);
            }
        }
    }

    private static void discardStalePending() {
        var it = pending.iterator();
        while (it.hasNext()) {
            Inbound inbound = it.next();
            if (session == null || inbound.payload.streamEpoch() != session.streamEpoch()
                    || !inbound.dimension.equals(session.dimension())) {
                it.remove(); complete(inbound, LODBatchResultPayload.STALE);
            }
        }
    }

    private static void complete(Inbound inbound, int status) {
        release(inbound);
        sendResult(inbound.token, new LODBatchResultPayload(inbound.payload.streamEpoch(), inbound.payload.batchId(), status));
    }
    private static void release(Inbound inbound) {
        if (!inbound.released.compareAndSet(false, true)) return;
        synchronized (reservationLock) { reservedBatches--; reservedBytes -= inbound.payload.data().length; }
    }
    private static void sendResult(ConnectionInbox.Token<ClientPacketListener> token, LODBatchResultPayload result) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            if (!inbox.isCurrent(token) || client.getConnection() != token.handler()) return;
            if (ClientLodNetworking.canSend(LODBatchResultPayload.TYPE)) ClientLodNetworking.send(result);
        });
    }
    private static RemoteIngestService service() {
        return ((IVoxyServerIngestAccess) verifiedInstance).voxyserver$getRemoteIngestService();
    }
    private static void setRemoteIngest(boolean enabled) {
        if (verifiedInstance != null) ((IVoxyServerIngestAccess) verifiedInstance).voxyserver$setUsingRemoteIngest(enabled);
    }
    static void disableRemoteIngest() {
        session = null; sessionApplied = false;
        discardStalePending();
        if (verifiedInstance != null) service().startSession(null, 0L);
        setRemoteIngest(false);
    }
    private static void reset() {
        disableRemoteIngest();
        ClientLodSettings.reset();
        joined = null; verifiedInstance = null; storageBase = null;
        observedDimension = null; handshakeSent = false;
    }
    public static ClientCacheKey cacheKey(WorldIdentifier worldId) {
        if (!Minecraft.getInstance().isSameThread() || storageBase == null || !inbox.isCurrent(joined)) return null;
        return ClientCacheKey.of(storageBase, worldId.getWorldId());
    }
    public static WorldEngine currentEngine(WorldIdentifier worldId) {
        if (cacheKey(worldId) == null || verifiedInstance == null) return null;
        return verifiedInstance.getOrCreate(worldId);
    }
    private static void tellPlayer(Component message) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) client.gui.getChat().addMessage(message);
    }
}
