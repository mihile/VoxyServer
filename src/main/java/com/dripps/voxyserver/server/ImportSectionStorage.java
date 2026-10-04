package com.dripps.voxyserver.server;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import me.cortex.voxy.common.config.section.SectionStorage;
import me.cortex.voxy.common.world.WorldSection;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongConsumer;

final class ImportSectionStorage extends SectionStorage {
    private final SectionStorage delegate;
    private final Object[] saveLocks = new Object[64];
    private final ThreadLocal<Map<Long, WorldSection>> importing = new ThreadLocal<>();

    ImportSectionStorage(SectionStorage delegate) {
        this.delegate = delegate;
        for (int i = 0; i < saveLocks.length; i++) saveLocks[i] = new Object();
    }

    void beginImport() {
        if (importing.get() != null) throw new IllegalStateException("Import already active");
        importing.set(new LinkedHashMap<>());
    }

    void capture(WorldSection section) {
        var sections = importing.get();
        if (sections != null && !sections.containsKey(section.key)) {
            section.acquire();
            sections.put(section.key, section);
        }
    }

    void saveImport(boolean force) {
        var sections = importing.get();
        if (sections == null || (!force && sections.size() < 64)) return;
        try {
            for (WorldSection section : sections.values()) saveSection(section);
        } finally {
            for (WorldSection section : sections.values()) section.release();
            sections.clear();
        }
    }

    void endImport() {
        var sections = importing.get();
        importing.remove();
        if (sections != null) {
            for (WorldSection section : sections.values()) section.release();
        }
    }

    @Override
    public void saveSection(WorldSection section) {
        synchronized (saveLocks[Long.hashCode(section.key) & (saveLocks.length - 1)]) {
            delegate.saveSection(section);
        }
    }

    @Override
    public int loadSection(WorldSection section) { return delegate.loadSection(section); }
    @Override
    public void putIdMapping(int id, ByteBuffer data) { delegate.putIdMapping(id, data); }
    @Override
    public Int2ObjectOpenHashMap<byte[]> getIdMappingsData() { return delegate.getIdMappingsData(); }
    @Override
    public void iteratePositions(int level, LongConsumer consumer) { delegate.iteratePositions(level, consumer); }
    @Override
    public void flush() { delegate.flush(); }
    @Override
    public void close() { delegate.close(); }
}
