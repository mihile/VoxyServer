package com.dripps.voxyserver.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

// tells client to clear cached lods for a dimension or all dimensions
public record LODClearPayload(
        Optional<ResourceLocation> dimension
) implements LodPayload {

    public static final Type<LODClearPayload> TYPE =
            new Type<>(new ResourceLocation("voxyserver:lod_clear"));

    public static final PacketCodec<FriendlyByteBuf, LODClearPayload> CODEC =
            PacketCodec.of(LODClearPayload::write, LODClearPayload::read);

    // clear all dimensions
    public static LODClearPayload clearAll() {
        return new LODClearPayload(Optional.empty());
    }

    // clear specific dimension
    public static LODClearPayload clearDimension(ResourceLocation dimension) {
        return new LODClearPayload(Optional.of(dimension));
    }

    private static void write(FriendlyByteBuf buf, LODClearPayload payload) {
        buf.writeBoolean(payload.dimension.isPresent());
        payload.dimension.ifPresent(buf::writeResourceLocation);
    }

    private static LODClearPayload read(FriendlyByteBuf buf) {
        boolean hasDimension = buf.readBoolean();
        Optional<ResourceLocation> dimension = hasDimension
                ? Optional.of(buf.readResourceLocation())
                : Optional.empty();
        return new LODClearPayload(dimension);
    }

    @Override
    public Type<? extends LodPayload> type() {
        return TYPE;
    }
}
