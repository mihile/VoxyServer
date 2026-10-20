package test;

import com.dripps.voxyserver.client.*;
import me.cortex.voxy.commonImpl.*;
import me.cortex.voxy.client.*;
import me.cortex.voxy.client.core.IGetVoxyRenderSystem;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import java.util.*;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;

public class LifecycleHarness implements ClientModInitializer {
    public static volatile boolean holdIngest;
    private int phase;
    private long since = System.nanoTime();
    private long start = since;
    private Path baseA;
    private String worldA;
    private final List<String> results = new ArrayList<>();
    private double elapsed() { return (System.nanoTime()-since)/1e9; }
    private void next(int p) { phase=p; since=System.nanoTime(); }
    private void log(String s) { results.add(s); System.out.println("LIFECYCLE_TEST "+s); }
    private void require(boolean ok,String why) { if(!ok) throw new AssertionError(why); }
    private Object field(Class<?> type,String name) throws Exception { var f=type.getDeclaredField(name); f.setAccessible(true); return f.get(null); }
    private void cleaned() throws Exception {
        require(VoxyCommon.getInstance()==null && !ClientSessionEvents.inSession,"instance/session left behind");
        var inbox=(com.dripps.voxyserver.network.ConnectionInbox<?>)field(ClientLodReceiver.class,"inbox");
        require(inbox.current()==null && inbox.size()==0,"connection inbox left behind");
        require((Integer)field(ClientLodReceiver.class,"reservedBatches")==0,"ingest batch reservations left behind");
        require((Long)field(ClientLodReceiver.class,"reservedBytes")==0L,"ingest bytes left behind");
        require(((java.util.ArrayDeque<?>)field(ClientLodReceiver.class,"pending")).isEmpty(),"pending LOD left behind");
        require(((java.util.concurrent.atomic.AtomicReference<?>)field(ClientLodSettings.class,"pendingManifestRequest")).get()==null,"pending manifest engine reference left behind");
        require(((java.util.concurrent.BlockingQueue<?>)field(ClientLodSettings.class,"manifestChunks")).isEmpty(),"manifest chunks left behind");
        require(Thread.getAllStackTraces().keySet().stream().noneMatch(t->t.isAlive() && t.getName().equals("VoxyServer Remote Ingest")),"remote worker left behind");
        log("CLEANED phase="+phase);
    }
    private void connect(Minecraft mc,int port) {
        String addr="127.0.0.1:"+port;
        ConnectScreen.startConnecting(new TitleScreen(),mc,ServerAddress.parseString(addr),new ServerData("test",addr,false),false);
        log("CONNECT "+addr);
    }
    private void disconnect(Minecraft mc) {
        if(mc.screen instanceof ConnectScreen) {
            for(var widget:mc.screen.children()) if(widget instanceof net.minecraft.client.gui.components.Button button) { button.onPress(); break; }
        }
        if(mc.getConnection()!=null) mc.getConnection().getConnection().disconnect(net.minecraft.network.chat.Component.literal("test"));
        mc.clearLevel(); mc.setScreen(new TitleScreen());
    }
    private boolean ready(Minecraft mc) { return mc.level!=null && mc.player!=null && ClientLodSettings.isProtocolOk() && VoxyCommon.getInstance() instanceof VoxyClientInstance && ((IGetVoxyRenderSystem)mc.levelRenderer).voxy$getRenderSystem()!=null; }
    private boolean overworld(Minecraft mc) {
        if(mc.level.dimension().location().toString().equals("minecraft:overworld")) return true;
        mc.getConnection().sendCommand("execute in minecraft:overworld run tp @s 0 80 0");
        since=System.nanoTime(); return false;
    }
    private void inspect(Minecraft mc,String label,boolean sameBase) throws Exception {
        require(ready(mc),"not ready: "+label);
        var instance=(VoxyClientInstance)VoxyCommon.getInstance();
        Path base=instance.getStorageBasePath().toAbsolutePath().normalize();
        String world=WorldIdentifier.of(mc.level).getWorldId();
        var key=ClientLodReceiver.cacheKey(WorldIdentifier.of(mc.level));
        int hashes=ClientLodHashStore.get().snapshotAsync(key,()->true).get(5,TimeUnit.SECONDS).size();
        require(hashes>0,"no received hashes: "+label);
        require(!base.toString().contains("UNKNOWN"),"UNKNOWN base");
        require(Files.isDirectory(base.resolve(world).resolve("storage")),"no world storage");
        if(baseA==null) { baseA=base; worldA=world; }
        else if(sameBase) require(base.equals(baseA),"reconnect changed base");
        var renderer=((IGetVoxyRenderSystem)mc.levelRenderer).voxy$getRenderSystem();
        require(renderer.getEngine()==ClientLodReceiver.currentEngine(WorldIdentifier.of(mc.level)),"renderer wrong world engine");
        int[] saved = {0};
        renderer.getEngine().storage.iteratePositions(0,k->saved[0]++);
        require(saved[0]>0,"no stored sections: "+label);
        List<String> debug=new ArrayList<>(); renderer.addDebugInfo(debug);
        log(label+" base="+base+" dimension="+mc.level.dimension().location()+" world="+world+" hashes="+hashes+" stored="+saved[0]+" renderer="+String.join(" | ",debug));
        ClientLodHashStore.get().flush();
    }
    public void onInitializeClient() { log("HARNESS_INITIALIZED"); ClientTickEvents.END_CLIENT_TICK.register(mc->{
        try {
            if((System.nanoTime()-start)/1e9>260) throw new AssertionError("timeout phase="+phase);
            switch(phase) {
                case 0: if(mc.getOverlay()==null && elapsed()>2) { mc.setScreen(new TitleScreen()); boolean stress=Boolean.getBoolean("lifecycle.stressOnly"); connect(mc,25585); next(stress?13:1); } break;
                case 1: if(ready(mc) && elapsed()>14 && overworld(mc)) { inspect(mc,"FIRST",true); mc.getConnection().sendCommand("execute in minecraft:the_nether run tp @s 0 100 0"); next(2); } break;
                case 2: if(ready(mc) && mc.level.dimension().location().toString().equals("minecraft:the_nether") && elapsed()>12) { inspect(mc,"NETHER",true); mc.getConnection().sendCommand("execute in minecraft:overworld run tp @s 0 80 0"); next(3); } break;
                case 3: if(ready(mc) && mc.level.dimension().location().toString().equals("minecraft:overworld") && elapsed()>10) { inspect(mc,"RETURN",true); disconnect(mc); next(4); } break;
                case 4: if(elapsed()>2) { cleaned(); connect(mc,25585); next(5); } break;
                case 5: if(ready(mc) && elapsed()>12 && overworld(mc)) { inspect(mc,"RECONNECT_A",true); disconnect(mc); next(6); } break;
                case 6: if(elapsed()>2) { cleaned(); connect(mc,25586); next(7); } break;
                case 7: if(ready(mc) && elapsed()>14 && overworld(mc)) { inspect(mc,"CLONED_SERVER_B",false); var base=((VoxyClientInstance)VoxyCommon.getInstance()).getStorageBasePath().toAbsolutePath().normalize(); require(!base.equals(baseA),"servers share base"); require(WorldIdentifier.of(mc.level).getWorldId().equals(worldA),"clone world ID different"); disconnect(mc); next(8); } break;
                case 8: if(elapsed()>2) { cleaned(); connect(mc,25585); next(9); } break;
                case 9: if(elapsed()>.15) { disconnect(mc); log("RAPID_DISCONNECT"); next(10); } break;
                case 10: if(elapsed()>3) { cleaned(); connect(mc,25585); next(11); } break;
                case 11: if(ready(mc) && elapsed()>12 && overworld(mc)) { inspect(mc,"AFTER_RAPID_RECONNECT",true); disconnect(mc); next(12); } break;
                case 12: if(elapsed()>3) { cleaned(); connect(mc,25586); next(13); } break;
                case 13: if(ready(mc) && elapsed()>2) { holdIngest=true; mc.getConnection().sendCommand("execute in minecraft:the_nether run tp @s 0 100 0"); next(14); } break;
                case 14: if(ready(mc) && mc.level.dimension().location().toString().equals("minecraft:the_nether") && (Integer)field(ClientLodReceiver.class,"reservedBatches")>0) { log("DISCONNECT_DURING_DIMENSION_STREAM reservations="+field(ClientLodReceiver.class,"reservedBatches")); disconnect(mc); holdIngest=false; next(15); } break;
                case 15: if(elapsed()>3) { cleaned(); connect(mc,25585); next(16); } break;
                case 16: if(ready(mc) && elapsed()>10) { inspect(mc,"AFTER_DIMENSION_DISCONNECT",true); disconnect(mc); next(17); } break;
                case 17: if(elapsed()>3) { cleaned(); log("PASS"); Files.write(mc.gameDirectory.toPath().resolve("lifecycle-result.txt"),results); mc.stop(); next(18); } break;
            }
        } catch(Throwable t) { holdIngest=false; t.printStackTrace(); log("FAIL "+t); try { Files.write(mc.gameDirectory.toPath().resolve("lifecycle-result.txt"),results); } catch(Exception ignored) {} mc.stop(); next(18); }
    }); }
}
