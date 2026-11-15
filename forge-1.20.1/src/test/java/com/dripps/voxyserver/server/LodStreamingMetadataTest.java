package com.dripps.voxyserver.server;

import com.dripps.voxyserver.config.VoxyServerConfig;
import com.dripps.voxyserver.network.LODManifestPayload;
import me.cortex.voxy.common.world.WorldEngine;
import net.minecraft.resources.ResourceLocation;
import org.junit.Test;

import java.util.Arrays;
import java.util.Random;
import java.util.UUID;

import static org.junit.Assert.*;

public class LodStreamingMetadataTest {

    private static long referenceComputeContentHash(int[] lutBlockStateIds, int[] lutBiomeIds, byte[] lutLight, short[] indexArray) {
        int lutLen = lutBlockStateIds.length;
        Integer[] order = new Integer[lutLen];
        for (int i = 0; i < lutLen; i++) order[i] = i;
        Arrays.sort(order, (a, b) -> {
            if (lutBlockStateIds[a] != lutBlockStateIds[b]) return Integer.compare(lutBlockStateIds[a], lutBlockStateIds[b]);
            if (lutBiomeIds[a] != lutBiomeIds[b]) return Integer.compare(lutBiomeIds[a], lutBiomeIds[b]);
            return Integer.compare(lutLight[a] & 0xFF, lutLight[b] & 0xFF);
        });
        int[] remap = new int[lutLen];
        for (int p = 0; p < lutLen; p++) remap[order[p]] = p;

        long h = lutLen;
        for (int p = 0; p < lutLen; p++) {
            int s = order[p];
            h = mix(h, lutBlockStateIds[s] & 0xFFFFFFFFL);
            h = mix(h, lutBiomeIds[s] & 0xFFFFFFFFL);
            h = mix(h, lutLight[s] & 0xFFL);
        }
        for (short slot : indexArray) {
            h = mix(h, remap[slot & 0xFFFF]);
        }
        return fmix64(h ^ ((long) indexArray.length));
    }

    private static long mix(long h, long v) {
        h ^= fmix64(v);
        h = Long.rotateLeft(h, 27) * 0x9E3779B97F4A7C15L + 0x165667B19E3779F9L;
        return h;
    }

    private static long fmix64(long k) {
        k ^= k >>> 33;
        k *= 0xff51afd7ed558ccdL;
        k ^= k >>> 33;
        k *= 0xc4ceb9fe1a85ec53L;
        k ^= k >>> 33;
        return k;
    }

    @Test
    public void primitiveContentHashMatchesBoxedReferenceAcrossAllSizesAndTies() {
        Random rng = new Random(0x5EED1234L);
        for (int lutLen : new int[]{1, 2, 7, 16, 32, 33, 64, 257, 1024}) {
            int[] blocks = new int[lutLen];
            int[] biomes = new int[lutLen];
            byte[] light = new byte[lutLen];
            for (int i = 0; i < lutLen; i++) {
                // Intentionally include duplicate keys to test tie-breaking stability.
                blocks[i] = rng.nextInt(Math.max(1, lutLen / 2));
                biomes[i] = rng.nextInt(8);
                light[i] = (byte) rng.nextInt(256);
            }
            short[] indices = new short[4096];
            for (int i = 0; i < indices.length; i++) {
                indices[i] = (short) rng.nextInt(lutLen);
            }
            long expected = referenceComputeContentHash(blocks, biomes, light, indices);
            long actual = LodStreamingService.computeContentHash(blocks, biomes, light, indices);
            assertEquals("Mismatch for lutLen=" + lutLen, expected, actual);
        }
    }

    @Test
    public void sectionVersionEvictionAdvancesFloorAndNeverMatchesStaleCachedHash() {
        LodStreamingService service = new LodStreamingService(null, new VoxyServerConfig());
        try {
            long keyA = LodStreamingService.composeSectionKey(0, WorldEngine.getWorldSectionId(0, 10, 2, 10));
            int v1 = service.bumpSectionVersion(keyA, 4);
            int v2 = service.bumpSectionVersion(keyA, 4);
            int v3 = service.bumpSectionVersion(keyA, 4);
            assertEquals(1, v1);
            assertEquals(2, v2);
            assertEquals(3, v3);
            service.putHashCacheEntry(keyA, v3, 0xCAFEBABEL, 4);
            assertNotNull(service.getCachedHashEntry(keyA));

            // Insert 10 more keys with maxEntries=4 so keyA is evicted from sectionVersions.
            for (int i = 0; i < 10; i++) {
                long k = LodStreamingService.composeSectionKey(0, WorldEngine.getWorldSectionId(0, 100 + i, 2, 100 + i));
                service.bumpSectionVersion(k, 4);
            }
            var stats = service.snapshotStats(null);
            assertTrue("trackedSections must respect maxEntries=4, got " + stats.trackedSections(),
                    stats.trackedSections() <= 4);

            // Even if keyA was evicted from sectionVersions, its effective version must be >= 3 (never reset to 0),
            // and its stale hashCache entry must have been invalidated.
            assertTrue("Evicted key version must not roll back below 3",
                    service.getCompositeSectionVersion(keyA) >= 3);
            assertNull("Evicted or bumped key must not retain stale hashCache entry",
                    service.getCachedHashEntry(keyA));

            // Bumping keyA again after eviction must produce a version strictly greater than 3.
            int nextVersion = service.bumpSectionVersion(keyA, 4);
            assertTrue("Re-bumped version after eviction must exceed previous version 3, got " + nextVersion,
                    nextVersion > 3);
        } finally {
            service.shutdown();
        }
    }

    @Test
    public void insertionPathsEnforceHardCapAndIncrementalPruningDrainsToHysteresisTarget() {
        ResourceLocation overworld = new ResourceLocation("minecraft:overworld");
        // Configure tracker with maxHashes=100, targetHashes=70, maxStepsPerCall=16.
        PlayerLodTracker tracker = new PlayerLodTracker(100, 70, 16);
        var session = tracker.beginSession(overworld, true, 100L);
        tracker.recordManifestArea(0, 0, 16);

        // 1. Insert 60 out-of-range sections (far X >= 1000) and 60 in-range sections (near X = 0..5) via applyManifestBatch.
        // Hard cap (100) must be enforced immediately during insertion, BEFORE any pruneOutOfRangeAcknowledged call!
        long[] farKeys = new long[60];
        long[] nearKeys = new long[60];
        long[] hashes = new long[60];
        Arrays.fill(hashes, 12345L);
        for (int i = 0; i < 60; i++) {
            farKeys[i] = WorldEngine.getWorldSectionId(0, 1000 + i, 0, 0);
            nearKeys[i] = WorldEngine.getWorldSectionId(0, i % 6, i / 6, 0);
        }
        assertTrue(tracker.applyManifestBatch(overworld, session.streamEpoch(), session.manifestRequestId(), 0, farKeys, hashes));
        assertEquals(60, tracker.sentCount());
        assertTrue(tracker.applyManifestBatch(overworld, session.streamEpoch(), session.manifestRequestId(), 0, nearKeys, hashes));
        assertEquals("applyManifestBatch must enforce hard cap (100) at insertion time", 100, tracker.sentCount());
        assertTrue("Overflow during insertion must activate incremental pruning", tracker.isPruningActive());

        // Incremental calls with maxStepsPerCall=16 drain the remaining 40 out-of-range entries down to targetHashes (70).
        assertEquals(16, tracker.pruneOutOfRangeAcknowledged(0, 0, 0, 32));
        assertEquals(84, tracker.sentCount());
        assertTrue(tracker.isPruningActive());

        assertEquals(14, tracker.pruneOutOfRangeAcknowledged(0, 0, 0, 32));
        assertEquals(70, tracker.sentCount());
        assertFalse("Pruning must deactivate once targetHashes (70) is reached", tracker.isPruningActive());

        // All 60 in-range entries must still be retained!
        for (long nearKey : nearKeys) {
            assertTrue(tracker.hasSent(LodStreamingService.composeSectionKey(0, nearKey), 12345L));
        }

        // 2. Verify completeBatch ALSO enforces the hard cap (100) under continuous interleaved insertions and pruning.
        for (int batchIdx = 0; batchIdx < 6; batchIdx++) {
            long[] batchKeys = new long[12];
            long[] batchHashes = new long[12];
            for (int j = 0; j < 12; j++) {
                int idx = batchIdx * 12 + j;
                // Mix of out-of-range and in-range keys.
                int secX = (j % 2 == 0) ? (2000 + idx) : (idx % 8);
                long rawKey = WorldEngine.getWorldSectionId(0, secX, 1, idx / 8);
                batchKeys[j] = LodStreamingService.composeSectionKey(0, rawKey);
                batchHashes[j] = 777L + idx;
            }
            var delivery = tracker.reserveBatch(overworld, session.streamEpoch(), batchKeys, batchHashes, 1024, 10L + batchIdx);
            assertNotNull(delivery);
            assertTrue(tracker.completeBatch(session.streamEpoch(), delivery.batchId(), true, 10L + batchIdx));
            assertTrue("completeBatch must never exceed maxHashes (100), got " + tracker.sentCount(),
                    tracker.sentCount() <= 100);
            tracker.pruneOutOfRangeAcknowledged(0, 0, 0, 32);
            assertTrue("sentCount must remain <= 100 after interleaved prune", tracker.sentCount() <= 100);
        }

        // 3. When all entries are in-range and size hits maxHashes (100), pruning rotates once and stops without endless rescanning.
        tracker.reset();
        session = tracker.beginSession(overworld, true, 200L);
        long[] allInRange = new long[110];
        long[] allHashes = new long[110];
        Arrays.fill(allHashes, 999L);
        for (int i = 0; i < 110; i++) {
            allInRange[i] = WorldEngine.getWorldSectionId(0, i % 10, i / 10, 0);
        }
        assertTrue(tracker.applyManifestBatch(overworld, session.streamEpoch(), session.manifestRequestId(), 0, allInRange, allHashes));
        assertEquals("Hard cap must hold even when all 110 entries are in-range", 100, tracker.sentCount());
        assertTrue(tracker.isPruningActive());

        int calls = 0;
        while (tracker.isPruningActive()) {
            assertEquals(0, tracker.pruneOutOfRangeAcknowledged(0, 0, 0, 32, 100, 70, 32));
            calls++;
            assertTrue("Must finish single rotation within bounded calls", calls <= 5);
        }
        assertEquals(100, tracker.sentCount());
        assertFalse("Pruning must stop after single rotation when all entries are in-range", tracker.isPruningActive());
    }

    @Test
    public void manifestReceptionAndManifestGatedSnapshotsStillEnforceCapAndRunIncrementalPruning() {
        ResourceLocation overworld = new ResourceLocation("minecraft:overworld");
        LodStreamingService service = new LodStreamingService(null, new VoxyServerConfig());
        try {
            UUID playerId = UUID.randomUUID();
            PlayerLodTracker tracker = new PlayerLodTracker(100, 70, 16);
            tracker.setReady(true);
            tracker.setProtocolOk(true);
            var session = tracker.beginSession(overworld, true, 500L);
            tracker.recordManifestArea(0, 0, 16);
            service.registerTrackerForTest(playerId, tracker);

            // Player is currently manifest-gated (awaiting COMPLETE until tick 500).
            assertTrue(tracker.isManifestGated(overworld, 10L));

            // Send two PART manifest batches: 60 out-of-range keys + 60 in-range keys while manifest-gated.
            long[] farKeys = new long[60];
            long[] nearKeys = new long[60];
            long[] hashes = new long[60];
            Arrays.fill(hashes, 4242L);
            for (int i = 0; i < 60; i++) {
                farKeys[i] = WorldEngine.getWorldSectionId(0, 1500 + i, 0, 0);
                nearKeys[i] = WorldEngine.getWorldSectionId(0, i % 6, i / 6, 0);
            }
            service.offerManifestBatchForTest(playerId, overworld, session.streamEpoch(), session.manifestRequestId(),
                    farKeys, hashes, LODManifestPayload.PART);
            service.offerManifestBatchForTest(playerId, overworld, session.streamEpoch(), session.manifestRequestId(),
                    nearKeys, hashes, LODManifestPayload.PART);

            assertEquals(2, service.drainManifestBatchesForTest(4));
            // During drainManifestBatches, hard cap (100) was enforced AND one 16-step incremental prune ran (100 -> 84)!
            assertEquals(84, tracker.sentCount());
            assertTrue("Tracker is still manifest-gated before COMPLETE arrives", tracker.isManifestGated(overworld, 10L));

            // Even while manifest-gated (and even if backed off), streamForSnapshot runs incremental pruning!
            tracker.deferRetry(10L);
            assertTrue(tracker.isBackedOff(15L));
            service.streamForSnapshotForTest(playerId, overworld, 0, 0);
            assertEquals("streamForSnapshot must run incremental pruning even while manifest-gated and backed off",
                    70, tracker.sentCount());
            assertFalse(tracker.isPruningActive());
        } finally {
            service.shutdown();
        }
    }
}
