package com.dripps.voxyserver.network;

import net.minecraft.resources.ResourceLocation;

public interface LodPayload {
    record Type<T extends LodPayload>(ResourceLocation id) {}
    Type<? extends LodPayload> type();
}
