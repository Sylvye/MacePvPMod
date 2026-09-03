package dev.macepvpmod.mixin;

import dev.macepvpmod.DamageHud;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class DamageEventMixin {
    @Inject(method = "handleDamageEvent", at = @At("TAIL"))
    private void macepvpmod$damage(ClientboundDamageEventPacket packet, CallbackInfo ci) {
        DamageHud.damageEvent(packet);
    }
}
