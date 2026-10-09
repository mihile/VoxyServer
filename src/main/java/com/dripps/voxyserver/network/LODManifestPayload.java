package com.dripps.voxyserver.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

// client to server manifest of sections it already stores for a dimension
public record LODManifestPayload(
        ResourceLocation dimension,
        long streamEpoch,
        long requestId,
        long[] keys,
        long[] hashes,
        int status
) implements LodPayload {

    public static final int MAX_ENTRIES = 1024;
    public static final int PART = 0;
    public static final int COMPLETE = 1;
    public static final int FAILED = 2;
    public static final int CANCELED = 3;

    public static final Type<LODManifestPayload> TYPE =
            new Type<>(new ResourceLocation("voxyserver:lod_manifest"));

    public static final PacketCodec<FriendlyByteBuf, LODManifestPayload> CODEC =
            PacketCodec.of(LODManifestPayload::write, LODManifestPayload::read);

    private static void write(FriendlyByteBuf buf, LODManifestPayload payload) {
        if (payload.keys.length != payload.hashes.length || payload.keys.length > MAX_ENTRIES) {
            throw new IllegalArgumentException("invalid manifest payload size");
        }
        buf.writeResourceLocation(payload.dimension);
        buf.writeVarLong(payload.streamEpoch);
        buf.writeVarLong(payload.requestId);
        buf.writeByte(payload.status);
        int count = payload.keys.length;
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            buf.writeLong(payload.keys[i]);
            buf.writeLong(payload.hashes[i]);
        }
    }

    private static LODManifestPayload read(FriendlyByteBuf buf) {
        ResourceLocation dimension = buf.readResourceLocation();
        long streamEpoch = buf.readVarLong();
        long requestId = buf.readVarLong();
        int status = buf.readByte() & 0xFF;
        int count = buf.readVarInt();
        if (streamEpoch < 0L || requestId < 0L || status < PART || status > CANCELED
                || count < 0 || count > MAX_ENTRIES) {
            throw new IllegalArgumentException("invalid manifest payload size: " + count);
        }
        long[] keys = new long[count];
        long[] hashes = new long[count];
        for (int i = 0; i < count; i++) {
            keys[i] = buf.readLong();
            hashes[i] = buf.readLong();
        }
        return new LODManifestPayload(dimension, streamEpoch, requestId, keys, hashes, status);
    }

    @Override
    public Type<? extends LodPayload> type() {
        return TYPE;
    }
}
