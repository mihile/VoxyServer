package com.dripps.voxyserver;

import com.dripps.voxyserver.config.VoxyServerConfig;
import com.dripps.voxyserver.network.VoxyServerNetworking;
import com.dripps.voxyserver.server.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod(Voxyserver.MOD_ID)
public class Voxyserver {
    public static final String MOD_ID = "voxyserver";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final int CONFIG_CHECK_INTERVAL = 20;
    private static final int STATS_BROADCAST_INTERVAL = 20;
    private static VoxyServerConfig config;
    private final Set<UUID> statsSubscribers = ConcurrentHashMap.newKeySet();
    private int statsBroadcastCounter;
    private ServerLodEngine lodEngine;
    private ChunkVoxelizer chunkVoxelizer;
    private LodStreamingService streamingService;
    private WorldImportCoordinator importCoordinator;
    private DirtyTracker dirtyTracker;
    private long lastConfigModified;
    private int configCheckCounter;

    public static VoxyServerConfig getConfig() { return config; }
    public LodStreamingService getStreamingService() { return streamingService; }
    public WorldImportCoordinator getImportCoordinator() { return importCoordinator; }
    public void addStatsSubscriber(UUID uuid) { statsSubscribers.add(uuid); }
    public boolean removeStatsSubscriber(UUID uuid) { return statsSubscribers.remove(uuid); }

    public List<String> applyConfigFromCommand(VoxyServerConfig candidate) {
        List<String> notes = applyConfigChanges(config, candidate);
        config = candidate;
        candidate.save();
        try { lastConfigModified = Files.getLastModifiedTime(VoxyServerConfig.getConfigPath()).toMillis(); }
        catch (IOException ignored) {}
        return notes;
    }

    public Voxyserver() {
        config = VoxyServerConfig.load();
        VoxyServerNetworking.register();
        MinecraftForge.EVENT_BUS.register(this);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.dripps.voxyserver.client.VoxyserverClient.initialize());
        LOGGER.info("VoxyServer Forge initialized");
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        VoxyServerCommands.register(event.getDispatcher(), this);
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        var server = event.getServer();
        if (!server.isDedicatedServer()) { LOGGER.info("VoxyServer disabled in singleplayer."); return; }
        var worldPath = server.getWorldPath(LevelResource.ROOT);
        lodEngine = new ServerLodEngine(worldPath);
        lodEngine.updateDedicatedThreadsCount(config.workerThreads);
        streamingService = new LodStreamingService(lodEngine, config);
        streamingService.register();
        chunkVoxelizer = new ChunkVoxelizer(lodEngine, streamingService, config);
        importCoordinator = new WorldImportCoordinator(lodEngine, streamingService);
        if (config.dirtyTrackingEnabled) {
            dirtyTracker = new DirtyTracker(chunkVoxelizer, streamingService, config.dirtyTrackingInterval);
            DirtyTracker.INSTANCE = dirtyTracker;
        }
        com.dripps.voxyserver.util.ServerStatsTracker.INSTANCE = new com.dripps.voxyserver.util.ServerStatsTracker(config.debugTrackingInterval);
        try { lastConfigModified = Files.getLastModifiedTime(VoxyServerConfig.getConfigPath()).toMillis(); }
        catch (IOException ignored) {}
        LOGGER.info("VoxyServer engine started for world: {}", worldPath);
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        if (lodEngine == null) return;
        LOGGER.info("shutting down VoxyServer engine");
        DirtyTracker.INSTANCE = null;
        dirtyTracker = null;
        com.dripps.voxyserver.util.ServerStatsTracker.INSTANCE = null;
        statsSubscribers.clear();
        if (importCoordinator != null) importCoordinator.shutdown();
        if (streamingService != null) streamingService.shutdown();
        if (chunkVoxelizer != null) chunkVoxelizer.shutdown();
        lodEngine.shutdown();
        lodEngine = null; chunkVoxelizer = null; streamingService = null; importCoordinator = null;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || lodEngine == null) return;
        var server = event.getServer();
        if (streamingService != null) streamingService.onServerTick(server);
        if (chunkVoxelizer != null) chunkVoxelizer.onServerTick(server);
        if (dirtyTracker != null) dirtyTracker.tick(server);
        if (com.dripps.voxyserver.util.ServerStatsTracker.INSTANCE != null) com.dripps.voxyserver.util.ServerStatsTracker.INSTANCE.tick(server);
        if (++configCheckCounter >= CONFIG_CHECK_INTERVAL) { configCheckCounter = 0; checkConfigReload(); }
        if (!statsSubscribers.isEmpty() && streamingService != null && ++statsBroadcastCounter >= STATS_BROADCAST_INTERVAL) {
            statsBroadcastCounter = 0;
            Component stats = VoxyServerCommands.buildStats(server, this);
            statsSubscribers.removeIf(uuid -> {
                ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                if (player == null) return true;
                player.sendSystemMessage(stats);
                return false;
            });
        }
    }

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        if (chunkVoxelizer != null && event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk)
            chunkVoxelizer.onChunkLoad(level, chunk);
    }
    @SubscribeEvent
    public void onChunkUnload(ChunkEvent.Unload event) {
        if (chunkVoxelizer != null && event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk)
            chunkVoxelizer.onChunkUnload(level, chunk);
    }
    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (streamingService != null && event.getEntity() instanceof ServerPlayer player) streamingService.onPlayerJoin(player);
    }
    @SubscribeEvent
    public void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (streamingService != null && event.getEntity() instanceof ServerPlayer player) streamingService.onPlayerLeave(player);
    }
    @SubscribeEvent
    public void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (streamingService != null && event.getEntity() instanceof ServerPlayer player) streamingService.onDimensionChange(player, player.serverLevel());
    }

    private void checkConfigReload() {
        try {
            var path = VoxyServerConfig.getConfigPath();
            if (!Files.exists(path)) return;
            long currentModified = Files.getLastModifiedTime(path).toMillis();
            if (currentModified == lastConfigModified) return;
            lastConfigModified = currentModified;

            VoxyServerConfig newConfig = VoxyServerConfig.reload();
            if (newConfig == null) return;

            applyConfigChanges(config, newConfig);
            config = newConfig;
            LOGGER.info("config reloaded successfully");
        } catch (IOException ignored) {}
    }

    private List<String> applyConfigChanges(VoxyServerConfig oldCfg, VoxyServerConfig newCfg) {
        List<String> restartNotes = new ArrayList<>();
        if (oldCfg.generateOnChunkLoad != newCfg.generateOnChunkLoad) {
            LOGGER.info("generateonchunkload changed, requires server restart to take effect");
            restartNotes.add("generateOnChunkLoad requires a server restart to take effect");
        }
        if (oldCfg.generateOnChunkUnload != newCfg.generateOnChunkUnload) {
            LOGGER.info("generateonchunkunload changed, requires server restart to take effect");
            restartNotes.add("generateOnChunkUnload requires a server restart to take effect");
        }
        if (oldCfg.dirtyTrackingEnabled != newCfg.dirtyTrackingEnabled) {
            LOGGER.info("dirtytrackingenabled changed, requires server restart to take effect");
            restartNotes.add("dirtyTrackingEnabled requires a server restart to take effect");
        }

        if (streamingService != null) {
            streamingService.updateConfig(
                    newCfg.lodStreamRadius,
                    newCfg.maxSectionsPerTickPerPlayer,
                    newCfg.sectionsPerPacket,
                    newCfg.tickInterval,
                    newCfg.dirtyTrackingInterval,
                    newCfg.hashSyncEnabled
            );
        }

        if (dirtyTracker != null) {
            dirtyTracker.updateFlushInterval(newCfg.dirtyTrackingInterval);
        }

        if (newCfg.workerThreads != oldCfg.workerThreads && lodEngine != null) {
            lodEngine.updateDedicatedThreadsCount(newCfg.workerThreads);
        }

        if (com.dripps.voxyserver.util.ServerStatsTracker.INSTANCE != null) {
            com.dripps.voxyserver.util.ServerStatsTracker.INSTANCE.updateTickInterval(newCfg.debugTrackingInterval);
        }

        return restartNotes;
    }
}
