package test;

import com.dripps.voxyserver.client.ClientLodHashStore;
import com.dripps.voxyserver.client.ClientLodReceiver;
import com.dripps.voxyserver.client.ClientLodSettings;
import me.cortex.voxy.client.ClientSessionEvents;
import me.cortex.voxy.client.VoxyClientInstance;
import me.cortex.voxy.client.core.IGetVoxyRenderSystem;
import me.cortex.voxy.common.world.WorldEngine;
import me.cortex.voxy.commonImpl.VoxyCommon;
import me.cortex.voxy.commonImpl.WorldIdentifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.client.ConfigScreenHandler;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Mod("voxyserver_verify")
public class ForgeClientHarness {
    private final long start = System.nanoTime();
    private long since = start;
    private int phase;
    private java.nio.file.Path firstBase;
    private long changedSection;
    private long originalHash;
    private final List<String> results = new ArrayList<>();
    public ForgeClientHarness() { MinecraftForge.EVENT_BUS.register(this); log("INITIALIZED"); }
    private double elapsed() { return (System.nanoTime() - since) / 1e9; }
    private void next(int value) { phase = value; since = System.nanoTime(); }
    private void log(String value) { results.add(value); System.out.println("FORGE_VERIFY " + value); }
    private void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private void connect(Minecraft mc) {
        String address = "127.0.0.1:25585";
        ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString(address), new ServerData("Forge test", address, false), false);
        log("CONNECT");
    }
    private void disconnect(Minecraft mc) {
        if (mc.getConnection() != null) mc.getConnection().getConnection().disconnect(Component.literal("verification"));
        mc.clearLevel(); mc.setScreen(new TitleScreen());
    }
    private boolean inspect(Minecraft mc, String label) throws Exception {
        if (mc.level == null || mc.player == null || !ClientLodSettings.isProtocolOk()
                || !(VoxyCommon.getInstance() instanceof VoxyClientInstance instance)) return false;
        var renderer = ((IGetVoxyRenderSystem) mc.levelRenderer).voxy$getRenderSystem();
        var worldId = WorldIdentifier.of(mc.level);
        var cacheKey = ClientLodReceiver.cacheKey(worldId);
        if (renderer == null || cacheKey == null) return false;
        int hashes = ClientLodHashStore.get().snapshotAsync(cacheKey, () -> true).get(3, TimeUnit.SECONDS).size();
        if (hashes == 0) return false;
        int[] stored = {0}; renderer.getEngine().storage.iteratePositions(0, k -> stored[0]++);
        if (stored[0] == 0) return false;
        var base = instance.getStorageBasePath().toAbsolutePath().normalize();
        require(!base.toString().contains("UNKNOWN"), "incorrect cache path");
        require(renderer.getEngine() == ClientLodReceiver.currentEngine(worldId), "renderer engine mismatch");
        if (firstBase == null) firstBase = base; else require(firstBase.equals(base), "reconnect cache changed");
        ClientLodHashStore.get().flush();
        log(label + " hashes=" + hashes + " stored=" + stored[0] + " cache=" + base);
        return true;
    }
    private void checkSettingsScreen(Minecraft mc) {
        var factory = ModList.get().getModContainerById("voxyserver").orElseThrow()
                .getCustomExtension(ConfigScreenHandler.ConfigScreenFactory.class).orElseThrow();
        var screen = factory.screenFunction().apply(mc, new TitleScreen());
        mc.setScreen(screen);
        var buttons = screen.children().stream().filter(w -> w instanceof net.minecraft.client.gui.components.Button)
                .map(w -> (net.minecraft.client.gui.components.Button) w).toList();
        var toggle = buttons.stream().filter(b -> b.getMessage().getString().startsWith("LOD streaming:")).findFirst().orElseThrow();
        var save = buttons.stream().filter(b -> b.getMessage().getString().equals("Save")).findFirst().orElseThrow();
        require(toggle.active && save.active, "settings screen is unavailable while connected");
        boolean enabled = ClientLodSettings.isEnabled();
        toggle.onPress(); toggle.onPress(); save.onPress();
        require(ClientLodSettings.isEnabled() == enabled, "settings screen changed the saved preference");
        mc.setScreen(null);
        log("CONFIG_SCREEN_OK");
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || phase == 9) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if ((System.nanoTime() - start) / 1e9 > 240) throw new AssertionError("timeout at phase " + phase + "; screen=" + mc.screen);
            switch (phase) {
                case 0 -> { if (mc.getOverlay() == null && elapsed() > 5) { mc.setScreen(new TitleScreen()); connect(mc); next(1); } }
                case 1 -> {
                    if (elapsed() > 8 && inspect(mc, "FIRST")) {
                        checkSettingsScreen(mc);
                        var pos = mc.player.blockPosition().below();
                        changedSection = WorldEngine.getWorldSectionId(0, pos.getX() >> 5, pos.getY() >> 5, pos.getZ() >> 5);
                        var key = ClientLodReceiver.cacheKey(WorldIdentifier.of(mc.level));
                        var hashes = ClientLodHashStore.get().snapshotAsync(key, () -> true).get(3, TimeUnit.SECONDS);
                        require(hashes.containsKey(changedSection), "test block section has not been received");
                        originalHash = hashes.get(changedSection);
                        String block = mc.level.getBlockState(pos).is(Blocks.DIAMOND_BLOCK) ? "gold_block" : "diamond_block";
                        mc.getConnection().sendCommand("gamemode creative");
                        mc.getConnection().sendCommand("setblock " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " minecraft:" + block);
                        log("BLOCK_CHANGE_REQUEST " + pos);
                        next(2);
                    }
                }
                case 2 -> {
                    if (elapsed() > 5 && mc.level != null) {
                        var key = ClientLodReceiver.cacheKey(WorldIdentifier.of(mc.level));
                        var hashes = ClientLodHashStore.get().snapshotAsync(key, () -> true).get(3, TimeUnit.SECONDS);
                        if (hashes.containsKey(changedSection) && hashes.get(changedSection) != originalHash) {
                            log("BLOCK_CHANGE_PROPAGATED");
                            mc.getConnection().sendCommand("execute in minecraft:the_nether run tp @s 0 90 0");
                            next(3);
                        }
                    }
                }
                case 3 -> {
                    if (elapsed() > 10 && mc.level != null && mc.level.dimension().location().toString().equals("minecraft:the_nether") && inspect(mc, "NETHER")) {
                        mc.getConnection().sendCommand("execute in minecraft:overworld run tp @s 0 -58 0");
                        next(4);
                    }
                }
                case 4 -> {
                    if (elapsed() > 8 && mc.level != null && mc.level.dimension().location().toString().equals("minecraft:overworld") && inspect(mc, "RETURN")) {
                        disconnect(mc); next(5);
                    }
                }
                case 5 -> { if (elapsed() > 3) { require(VoxyCommon.getInstance() == null && !ClientSessionEvents.inSession, "session remained active"); connect(mc); next(6); } }
                case 6 -> { if (elapsed() > 8 && inspect(mc, "RECONNECT")) { disconnect(mc); next(7); } }
                case 7 -> { if (elapsed() > 3) { require(VoxyCommon.getInstance() == null && !ClientSessionEvents.inSession, "session remained active"); log("PASS"); finish(mc); } }
            }
        } catch (Throwable failure) {
            failure.printStackTrace(); log("FAIL " + failure); finish(mc);
        }
    }
    private void finish(Minecraft mc) {
        try { Files.write(mc.gameDirectory.toPath().resolve("forge-verification-result.txt"), results); }
        catch (Exception failure) { failure.printStackTrace(); }
        phase = 9; mc.stop();
    }
}
