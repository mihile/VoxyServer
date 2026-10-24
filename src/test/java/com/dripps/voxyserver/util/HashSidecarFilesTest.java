package com.dripps.voxyserver.util;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class HashSidecarFilesTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();
    private Path target() throws IOException { return temporary.newFolder().toPath().resolve("hash.bin"); }
    private void noTemps(Path target) throws IOException { try (var files = Files.list(target.getParent())) { assertEquals(1, files.count()); } }
    @Test public void replacesAnExistingCompleteSidecar() throws Exception {
        Path file=target();Files.writeString(file,"old");HashSidecarFiles.write(file,out->out.writeInt(77));
        assertArrayEquals(new byte[]{0,0,0,77},Files.readAllBytes(file));noTemps(file);
    }
    @Test public void windowsAtomicAccessDeniedFallsBack() throws Exception {
        Path file=target();Files.writeString(file,"old");var calls=new AtomicInteger();
        HashSidecarFiles.write(file,out->out.writeInt(91),(a,b,atomic)->{calls.incrementAndGet();if(atomic)throw new AccessDeniedException(b.toString());Files.move(a,b,StandardCopyOption.REPLACE_EXISTING);},millis->fail("unneeded retry"));
        assertEquals(2,calls.get());assertArrayEquals(new byte[]{0,0,0,91},Files.readAllBytes(file));noTemps(file);
    }
    @Test public void unsupportedAtomicMoveFallsBack() throws Exception {
        Path file=target();Files.writeString(file,"old");
        HashSidecarFiles.write(file,out->out.writeInt(3),(a,b,atomic)->{if(atomic)throw new AtomicMoveNotSupportedException(a.toString(),b.toString(),"test");Files.move(a,b,StandardCopyOption.REPLACE_EXISTING);},millis->fail("unneeded retry"));
        assertArrayEquals(new byte[]{0,0,0,3},Files.readAllBytes(file));noTemps(file);
    }
    @Test public void transientMoveFailureRetriesOnTheWorker() throws Exception {
        Path file=target();Files.writeString(file,"old");var attempts=new AtomicInteger();var waits=new AtomicInteger();
        HashSidecarFiles.write(file,out->out.writeInt(42),(a,b,atomic)->{if(attempts.incrementAndGet()<3)throw new IOException("busy");Files.move(a,b,StandardCopyOption.REPLACE_EXISTING);},millis->waits.incrementAndGet());
        assertEquals(3,attempts.get());assertEquals(2,waits.get());noTemps(file);
    }
    @Test public void persistentFailureRetainsTheOldFileAndCleansTheTemporary() throws Exception {
        Path file=target();Files.writeString(file,"old");var waits=new AtomicInteger();
        assertThrows(IOException.class,()->HashSidecarFiles.write(file,out->out.writeInt(4),(a,b,atomic)->{throw new AccessDeniedException(b.toString());},millis->waits.incrementAndGet()));
        assertEquals(2,waits.get());assertEquals("old",Files.readString(file));noTemps(file);
    }
    @Test public void serializationFailureNeverReplacesTheOldFile() throws Exception {
        Path file=target();Files.writeString(file,"old");
        assertThrows(IOException.class,()->HashSidecarFiles.write(file,out->{out.writeInt(1);throw new IOException("serialize");}));
        assertEquals("old",Files.readString(file));noTemps(file);
    }
    @Test public void interruptionStopsRetriesAndRestoresTheFlag() throws Exception {
        Path file=target();Files.writeString(file,"old");
        try {
            assertThrows(IOException.class,()->HashSidecarFiles.write(file,out->out.writeInt(1),(a,b,atomic)->{throw new IOException("busy");},millis->{throw new InterruptedException("cancel");}));
            assertTrue(Thread.currentThread().isInterrupted());assertEquals("old",Files.readString(file));noTemps(file);
        } finally {Thread.interrupted();}
    }
}
