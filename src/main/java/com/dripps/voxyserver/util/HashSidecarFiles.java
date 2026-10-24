package com.dripps.voxyserver.util;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Background-only, replaceable hash metadata; never writes the actual LOD store. */
public final class HashSidecarFiles {
    private HashSidecarFiles() {}
    @FunctionalInterface public interface Writer { void write(DataOutputStream output) throws IOException; }
    @FunctionalInterface interface Mover { void move(Path source, Path target, boolean atomic) throws IOException; }
    @FunctionalInterface interface Sleeper { void sleep(long millis) throws InterruptedException; }

    public static void write(Path target, Writer writer) throws IOException {
        write(target, writer, (source, destination, atomic) -> {
            if (atomic) Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            else Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }, Thread::sleep);
    }

    static void write(Path target, Writer writer, Mover mover, Sleeper sleeper) throws IOException {
        Files.createDirectories(target.getParent());
        // A fixed .tmp name can collide with another client writing this cache.
        Path temporary = Files.createTempFile(target.getParent(), target.getFileName() + ".", ".tmp");
        Throwable failure = null;
        try {
            try (var out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(temporary)))) {
                writer.write(out);
            }
            IOException last = null;
            for (int attempt = 0; attempt < 3; attempt++) {
                try {
                    try {
                        mover.move(temporary, target, true);
                    } catch (AtomicMoveNotSupportedException | FileAlreadyExistsException | AccessDeniedException atomicFailure) {
                        // Windows/providers can reject replacing an existing file atomically.
                        try { mover.move(temporary, target, false); }
                        catch (IOException replacementFailure) { replacementFailure.addSuppressed(atomicFailure); throw replacementFailure; }
                    }
                    return;
                } catch (IOException ex) {
                    last = ex;
                    if (attempt == 2) break;
                    try { sleeper.sleep(25L << attempt); }
                    catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        var cancelled = new IOException("Interrupted while persisting LOD hash metadata", interrupted);
                        cancelled.addSuppressed(ex);
                        throw cancelled;
                    }
                }
            }
            throw last;
        } catch (IOException | RuntimeException | Error ex) {
            failure = ex;
            throw ex;
        } finally {
            try { Files.deleteIfExists(temporary); }
            catch (IOException cleanup) { if (failure != null) failure.addSuppressed(cleanup); else throw cleanup; }
        }
    }
}
