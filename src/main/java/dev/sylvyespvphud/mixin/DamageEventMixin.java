package dev.sylvyespvphud.mixin;

import dev.sylvyespvphud.DamageHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class DamageEventMixin {
    @Inject(method = "handleMovePlayer", at = @At("TAIL"))
    private void sylvyespvphud$positionReset(net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        var player = Minecraft.getInstance().player;
        if (player != null) player.resetFallDistance();
    }
    @Inject(method = "handleDamageEvent", at = @At("TAIL"))
    private void sylvyespvphud$damage(ClientboundDamageEventPacket packet, CallbackInfo ci) {
        DamageHud.damageEvent(packet);
    }
    @Inject(method = "handleEntityEvent", at = @At("TAIL"))
    private void sylvyespvphud$entityEvent(ClientboundEntityEventPacket packet, CallbackInfo ci) {
        var level=Minecraft.getInstance().level;var entity=level==null?null:packet.getEntity(level);
        if(entity!=null)DamageHud.entityEvent(entity,packet.getEventId());
    }
}
