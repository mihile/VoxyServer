package com.dripps.voxyserver.server;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedHashMap;
import java.util.Map;

final class RegionImportCheckpoint {
    record Fingerprint(long size, String modified) {
        static Fingerprint read(Path path) throws IOException {
            var attributes = Files.readAttributes(path, BasicFileAttributes.class);
            if (!attributes.isRegularFile()) throw new IOException("Not a regular file: " + path);
            return new Fingerprint(attributes.size(), attributes.lastModifiedTime().toString());
        }
    }

    private final Path directory;

    RegionImportCheckpoint(Path directory) { this.directory = directory; }

    boolean matches(Path region) {
        try (var input = new DataInputStream(Files.newInputStream(marker(region)))) {
            if (input.readInt() != 1) return false;
            int count = input.readInt();
            if (count < 1 || count > 1025) return false;
            Map<String, Fingerprint> dependencies = new LinkedHashMap<>();
            for (int i = 0; i < count; i++) {
                String name = input.readUTF();
                if (!name.matches("(?:r\\.-?\\d+\\.-?\\d+\\.mca|c\\.-?\\d+\\.-?\\d+\\.mcc)")) return false;
                if (dependencies.put(name, new Fingerprint(input.readLong(), input.readUTF())) != null) return false;
            }
            return input.read() == -1 && dependencies.containsKey(region.getFileName().toString())
                    && unchanged(region.getParent(), dependencies);
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    static boolean unchanged(Path directory, Map<String, Fingerprint> dependencies) throws IOException {
        for (var entry : dependencies.entrySet()) {
            if (!entry.getValue().equals(Fingerprint.read(directory.resolve(entry.getKey())))) return false;
        }
        return true;
    }

    void invalidate(Path region) throws IOException { Files.deleteIfExists(marker(region)); }

    void complete(Path region, Map<String, Fingerprint> dependencies) throws IOException {
        Files.createDirectories(directory);
        Path target = marker(region);
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        try (var output = new DataOutputStream(Files.newOutputStream(temporary))) {
            output.writeInt(1);
            output.writeInt(dependencies.size());
            for (var entry : dependencies.entrySet()) {
                output.writeUTF(entry.getKey());
                output.writeLong(entry.getValue().size());
                output.writeUTF(entry.getValue().modified());
            }
        }
        try (var channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) { channel.force(true); }
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Path marker(Path region) { return directory.resolve(region.getFileName() + ".done"); }
}
