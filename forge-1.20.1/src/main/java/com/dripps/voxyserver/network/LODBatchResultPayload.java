package com.dripps.voxyserver.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public record LODBatchResultPayload(long streamEpoch, long batchId, int status) implements LodPayload {
    public static final int SUCCESS = 0;
    public static final int FAILURE = 1;
    public static final int OVERFLOW = 2;
    public static final int STALE = 3;

    public static final Type<LODBatchResultPayload> TYPE =
            new Type<>(new ResourceLocation("voxyserver:lod_batch_result"));

    public static final PacketCodec<FriendlyByteBuf, LODBatchResultPayload> CODEC =
            PacketCodec.of(LODBatchResultPayload::write, LODBatchResultPayload::read);

    private static void write(FriendlyByteBuf buf, LODBatchResultPayload payload) {
        validate(payload.streamEpoch, payload.batchId, payload.status);
        buf.writeVarLong(payload.streamEpoch);
        buf.writeVarLong(payload.batchId);
        buf.writeByte(payload.status);
    }

    private static LODBatchResultPayload read(FriendlyByteBuf buf) {
        long streamEpoch = buf.readVarLong();
        long batchId = buf.readVarLong();
        int status = buf.readByte() & 0xFF;
        validate(streamEpoch, batchId, status);
        return new LODBatchResultPayload(streamEpoch, batchId, status);
    }

    private static void validate(long streamEpoch, long batchId, int status) {
        if (streamEpoch < 0L || batchId < 0L || status < SUCCESS || status > STALE) {
            throw new IllegalArgumentException("invalid LOD batch result");
        }
    }

    @Override
    public Type<? extends LodPayload> type() {
        return TYPE;
    }
}
