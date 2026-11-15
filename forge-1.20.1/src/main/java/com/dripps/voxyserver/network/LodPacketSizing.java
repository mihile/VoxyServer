package com.dripps.voxyserver.network;

import java.nio.charset.StandardCharsets;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/** Conservative byte bounds for the 1.20.1 custom payload limit. */
public final class LodPacketSizing {
    public static int batchEnd(ResourceLocation dimension, List<LODSectionPayload> sections, int start, int limit) {
        int bytes = 10 + dimension.toString().getBytes(StandardCharsets.UTF_8).length;
        int end = start;
        int count = Math.max(1, Math.min(limit, PreSerializedLodPayload.MAX_SECTIONS));
        while (end < sections.size() && end - start < count) {
            LODSectionPayload section = sections.get(end);
            int bits = Math.max(1, 32 - Integer.numberOfLeadingZeros(Math.max(section.lutBlockStateIds().length - 1, 0)));
            int entriesPerLong = 64 / bits;
            int sectionBytes = 27 + section.lutBlockStateIds().length * 11
                    + ((section.indexArray().length + entriesPerLong - 1) / entriesPerLong) * 8;
            if (bytes + sectionBytes > PreSerializedLodPayload.MAX_ENCODED_BYTES) break;
            bytes += sectionBytes;
            end++;
        }
        if (end == start) throw new IllegalArgumentException("LOD section exceeds packet size limit");
        return end;
    }
}
