package com.dripps.voxyserver.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

// single level 0 LOD section sent from server to client
// contains: dimension id, section position key, a LUT of vanilla registry ids, and an index array
// contentHash is the canonical content fingerprint the client persists for hash sync
public record LODSectionPayload(
        ResourceLocation dimension,
        long sectionKey,
        int[] lutBlockStateIds,
        int[] lutBiomeIds,
        byte[] lutLight,
        short[] indexArray,
        long contentHash
) implements LodPayload {

    public static final Type<LODSectionPayload> TYPE =
            new Type<>(new ResourceLocation("voxyserver:lod_section"));

    public static final PacketCodec<FriendlyByteBuf, LODSectionPayload> CODEC =
            PacketCodec.of(LODSectionPayload::write, LODSectionPayload::read);

    private static void write(FriendlyByteBuf buf, LODSectionPayload payload) {
        int lutLen = payload.lutBlockStateIds.length;
        if (lutLen < 1 || lutLen > LODBulkPayload.MAX_LUT_ENTRIES
                || payload.lutBiomeIds.length != lutLen || payload.lutLight.length != lutLen
                || payload.indexArray.length < 1
                || payload.indexArray.length > LODBulkPayload.MAX_INDEX_ENTRIES) {
            throw new IllegalArgumentException("invalid section data");
        }
        buf.writeResourceLocation(payload.dimension);
        buf.writeLong(payload.sectionKey);
        buf.writeLong(payload.contentHash);

        buf.writeVarInt(lutLen);
        for (int i = 0; i < lutLen; i++) {
            buf.writeVarInt(payload.lutBlockStateIds[i]);
            buf.writeVarInt(payload.lutBiomeIds[i]);
            buf.writeByte(payload.lutLight[i]);
        }

        // pack indices at minimum bit width for lut size
        int bitsPerEntry = Math.max(1, 32 - Integer.numberOfLeadingZeros(Math.max(lutLen - 1, 0)));
        int entriesPerLong = 64 / bitsPerEntry;
        int longCount = (payload.indexArray.length + entriesPerLong - 1) / entriesPerLong;

        buf.writeVarInt(payload.indexArray.length);
        buf.writeByte(bitsPerEntry);
        for (int li = 0; li < longCount; li++) {
            long packed = 0L;
            int base = li * entriesPerLong;
            for (int ei = 0; ei < entriesPerLong && base + ei < payload.indexArray.length; ei++) {
                packed |= ((long) (payload.indexArray[base + ei] & 0xFFFF)) << (ei * bitsPerEntry);
            }
            buf.writeLong(packed);
        }
    }

    private static LODSectionPayload read(FriendlyByteBuf buf) {
        ResourceLocation dimension = buf.readResourceLocation();
        long sectionKey = buf.readLong();
        long contentHash = buf.readLong();

        int lutLen = buf.readVarInt();
        if (lutLen < 1 || lutLen > LODBulkPayload.MAX_LUT_ENTRIES) {
            throw new IllegalArgumentException("invalid lookup table size: " + lutLen);
        }
        int[] blockStateIds = new int[lutLen];
        int[] biomeIds = new int[lutLen];
        byte[] light = new byte[lutLen];
        for (int i = 0; i < lutLen; i++) {
            blockStateIds[i] = buf.readVarInt();
            biomeIds[i] = buf.readVarInt();
            light[i] = buf.readByte();
        }

        int indexLen = buf.readVarInt();
        int bitsPerEntry = buf.readByte() & 0xFF;
        if (indexLen < 1 || indexLen > LODBulkPayload.MAX_INDEX_ENTRIES
                || bitsPerEntry < 1 || bitsPerEntry > 16) {
            throw new IllegalArgumentException("invalid packed lod index data");
        }
        int entriesPerLong = 64 / bitsPerEntry;
        long mask = (1L << bitsPerEntry) - 1;
        short[] indexArray = new short[indexLen];
        int idx = 0;
        while (idx < indexLen) {
            long packed = buf.readLong();
            for (int ei = 0; ei < entriesPerLong && idx < indexLen; ei++, idx++) {
                indexArray[idx] = (short) ((packed >> (ei * bitsPerEntry)) & mask);
            }
        }

        return new LODSectionPayload(dimension, sectionKey, blockStateIds, biomeIds, light, indexArray, contentHash);
    }

    @Override
    public Type<? extends LodPayload> type() {
        return TYPE;
    }
}
