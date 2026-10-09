package com.dripps.voxyserver.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class LodProtocolTest {
    private static final ResourceLocation DIM = new ResourceLocation("minecraft:overworld");

    private <T extends LodPayload> T roundTrip(T payload) {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            var codec = PayloadCodecs.<T>forType(payload.type());
            codec.encode(buffer, payload);
            T decoded = codec.decode(buffer);
            PayloadCodecs.requireConsumed(buffer);
            return decoded;
        } finally { buffer.release(); }
    }

    private LODSectionPayload section(int paletteSize) {
        int[] blocks = new int[paletteSize];
        int[] biomes = new int[paletteSize];
        byte[] light = new byte[paletteSize];
        short[] indices = new short[32768];
        for (int i = 0; i < paletteSize; i++) {
            blocks[i] = Integer.MAX_VALUE - i;
            biomes[i] = Integer.MAX_VALUE - i;
            light[i] = (byte)i;
        }
        for (int i = 0; i < indices.length; i++) indices[i] = (short)(i % paletteSize);
        return new LODSectionPayload(DIM, 123L, blocks, biomes, light, indices, -456L);
    }

    @Test public void controlChannelsRoundTrip() {
        List<LodPayload> controls = List.of(new LODHandshakePayload(2), new LODProtocolPayload(2),
                new LODPreferencesPayload(true, 256, 100), new LODBatchResultPayload(42, 7, 0),
                new LODHashSyncSettingsPayload(true), new LODServerSettingsPayload(256, 100),
                new LODStreamSessionPayload(DIM, 42, 7, true), LODClearPayload.clearAll(),
                LODClearPayload.clearDimension(DIM));
        for (LodPayload value : controls) assertEquals(value, roundTrip(value));
    }

    @Test public void packedSectionsRoundTripAtAllPaletteWidths() {
        for (int palette : new int[]{1, 2, 3, 16, 17, 256, 257, 32768}) {
            var original = section(palette);
            var decoded = roundTrip(original);
            assertEquals(original.dimension(), decoded.dimension());
            assertEquals(original.contentHash(), decoded.contentHash());
            assertArrayEquals(original.lutBlockStateIds(), decoded.lutBlockStateIds());
            assertArrayEquals(original.lutBiomeIds(), decoded.lutBiomeIds());
            assertArrayEquals(original.lutLight(), decoded.lutLight());
            assertArrayEquals(original.indexArray(), decoded.indexArray());
        }
    }

    @Test public void fullManifestFitsVanillaServerboundPacket() {
        long[] keys = new long[LODManifestPayload.MAX_ENTRIES];
        long[] hashes = new long[keys.length];
        Arrays.fill(keys, Long.MAX_VALUE); Arrays.fill(hashes, Long.MIN_VALUE);
        var original = new LODManifestPayload(DIM, Long.MAX_VALUE, Long.MAX_VALUE, keys, hashes, LODManifestPayload.PART);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            LODManifestPayload.CODEC.encode(buffer, original);
            assertTrue(buffer.readableBytes() <= 32767);
            var packet = new ServerboundCustomPayloadPacket(LODManifestPayload.TYPE.id(), buffer);
            var wire = new FriendlyByteBuf(Unpooled.buffer());
            try {
                packet.write(wire);
                var decoded = new ServerboundCustomPayloadPacket(wire);
                try {
                    var manifest = LODManifestPayload.CODEC.decode(decoded.getData());
                    assertArrayEquals(keys, manifest.keys()); assertArrayEquals(hashes, manifest.hashes());
                } finally { decoded.getData().release(); }
            } finally { wire.release(); }
        } finally { buffer.release(); }
    }

    @Test public void denseBatchesSplitAndFitVanillaClientboundPackets() {
        var sections = Collections.nCopies(16, section(32768));
        int packets = 0;
        for (int i = 0; i < sections.size();) {
            int end = LodPacketSizing.batchEnd(DIM, sections, i, 16);
            var bulk = new LODBulkPayload(DIM, sections.subList(i, end));
            var payload = PreSerializedLodPayload.fromBulk(bulk, null).withDelivery(42, packets + 1);
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                PreSerializedLodPayload.CODEC.encode(buffer, payload);
                assertTrue(buffer.readableBytes() <= 1048576);
                var packet = new ClientboundCustomPayloadPacket(payload.type().id(), buffer);
                var wire = new FriendlyByteBuf(Unpooled.buffer());
                try {
                    packet.write(wire);
                    var decoded = new ClientboundCustomPayloadPacket(wire).getData();
                    try {
                        var received = PreSerializedLodPayload.CODEC.decode(decoded);
                        assertEquals(end - i, received.decodeBulk(null).sections().size());
                        assertEquals(42, received.streamEpoch());
                    } finally { decoded.release(); }
                } finally { wire.release(); }
            } finally { buffer.release(); }
            i = end; packets++;
        }
        assertTrue(packets > 1);
    }

    @Test public void smallBatchesRespectSectionCountLimit() {
        var sections = Collections.nCopies(20, section(1));
        assertEquals(16, LodPacketSizing.batchEnd(DIM, sections, 0, 100));
        assertEquals(3, LodPacketSizing.batchEnd(DIM, sections, 0, 3));
        assertEquals(20, LodPacketSizing.batchEnd(DIM, sections, 16, 100));
    }

    @Test public void malformedAndTrailingDataRejected() {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeVarLong(0); buffer.writeVarLong(0); buffer.writeVarInt(1);
            buffer.writeVarInt(PreSerializedLodPayload.MAX_ENCODED_BYTES + 1);
            assertThrows(IllegalArgumentException.class, () -> PreSerializedLodPayload.CODEC.decode(buffer));
            buffer.clear(); buffer.writeVarInt(2); buffer.writeByte(99);
            LODHandshakePayload.CODEC.decode(buffer);
            assertThrows(IllegalArgumentException.class, () -> PayloadCodecs.requireConsumed(buffer));
        } finally { buffer.release(); }
        assertThrows(IllegalArgumentException.class, () -> roundTrip(new LODBatchResultPayload(-1, 0, 0)));
    }
}
