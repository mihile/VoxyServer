package com.dripps.voxyserver.network;

import com.dripps.voxyserver.util.ClientCacheKey;
import org.junit.Test;
import java.nio.file.Path;
import static org.junit.Assert.*;

public class ClientCacheKeyTest {
    private static final String WORLD="8935c0b5f7a5360b4880bc14d0ff6c7f";
    @Test public void clonedWorldsOnDifferentServersDoNotShareHashes() {
        var a=ClientCacheKey.of(Path.of(".voxy/saves/localhost_25563"),WORLD);
        var b=ClientCacheKey.of(Path.of(".voxy/saves/localhost_25564"),WORLD);
        assertNotEquals(a,b);assertNotEquals(a.fileUnder(Path.of("game")),b.fileUnder(Path.of("game")));
    }
    @Test public void sameServerReconnectHasStableIdentity() {
        assertEquals(ClientCacheKey.of(Path.of(".voxy/saves/localhost_25563"),WORLD),
                ClientCacheKey.of(Path.of(".voxy/saves/./localhost_25563"),WORLD));
    }
    @Test public void dimensionsUseVoxyWorldIdentifier() {
        var a=ClientCacheKey.of(Path.of("server"),WORLD);
        var b=ClientCacheKey.of(Path.of("server"),"00000000000000000000000000000000");
        assertEquals(a.serverNamespace(),b.serverNamespace());assertNotEquals(a,b);
    }
}
