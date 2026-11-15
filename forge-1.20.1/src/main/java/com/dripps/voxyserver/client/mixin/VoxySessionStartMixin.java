package com.dripps.voxyserver.client.mixin;

import com.dripps.voxyserver.client.ClientLodReceiver;
import me.cortex.voxy.client.ClientSessionEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClientSessionEvents.class, remap = false)
public abstract class VoxySessionStartMixin {
    @Inject(method = "sessionStart", at = @At("HEAD"), cancellable = true)
    private static void voxyserver$deferEarlySession(CallbackInfo ci) {
        if (!ClientLodReceiver.allowVoxySessionStart()) ci.cancel();
    }
}
