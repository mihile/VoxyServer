package com.dripps.voxyserver.client.mixin;

import com.dripps.voxyserver.client.ClientLodReceiver;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class ClientDisconnectMixin {
    @Inject(method = "clearLevel()V", at = @At("HEAD"))
    private void voxyserver$cancelBeforeVoxyShutdown(CallbackInfo ci) {
        ClientLodReceiver.onClearLevel();
    }
}
