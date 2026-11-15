package com.dripps.voxyserver.client.mixin;

import com.dripps.voxyserver.client.service.IVoxyServerIngestAccess;
import me.cortex.voxy.client.VoxyClientInstance;
import me.cortex.voxy.commonImpl.WorldIdentifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = VoxyClientInstance.class, remap = false)
public abstract class VoxyClientInstanceMixin {
    @Inject(method = "isIngestEnabled", at = @At("HEAD"), cancellable = true)
    private void voxyserver$disableLocalIngest(WorldIdentifier worldId, CallbackInfoReturnable<Boolean> cir) {
        if (((IVoxyServerIngestAccess) this).voxyserver$isUsingRemoteIngest()) cir.setReturnValue(false);
    }
}
