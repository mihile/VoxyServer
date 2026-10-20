package com.dripps.voxyserver.client.mixin;

import com.dripps.voxyserver.client.ClientLodReceiver;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientLoginMixin {
    @Inject(method = "handleLogin", at = @At("RETURN"))
    private void voxyserver$loginCompleted(ClientboundLoginPacket packet, CallbackInfo ci) {
        ClientLodReceiver.onLogin((ClientPacketListener) (Object) this);
    }
}
