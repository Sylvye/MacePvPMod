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
    @Inject(method = "handleRemoveMobEffect", at = @At("HEAD"))
    private void sylvyespvphud$repotRemoval(net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket packet, CallbackInfo ci) {
        // HEAD also runs on the network thread before vanilla schedules packet processing.
        if (Minecraft.getInstance().isSameThread()) dev.sylvyespvphud.RepotHud.removingEffect(packet);
    }
    @Inject(method = "handleSoundEvent", at = @At("TAIL"))
    private void sylvyespvphud$blockSound(net.minecraft.network.protocol.game.ClientboundSoundPacket packet, CallbackInfo ci) {
        dev.sylvyespvphud.ShieldDamageEvents.sound(packet);
    }
    @Inject(method = "handleSoundEntityEvent", at = @At("TAIL"))
    private void sylvyespvphud$entityBlockSound(net.minecraft.network.protocol.game.ClientboundSoundEntityPacket packet, CallbackInfo ci) {
        dev.sylvyespvphud.ShieldDamageEvents.sound(packet);
    }
    @Inject(method = "handleSetEquipment", at = @At("HEAD"))
    private void sylvyespvphud$shieldEquipment(net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket packet, CallbackInfo ci) {
        // This handler may first be called on the network thread. Only inspect world state on the client thread.
        if(Minecraft.getInstance().isSameThread())dev.sylvyespvphud.ShieldDamageEvents.equipment(packet);
    }
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
        if(entity!=null) {
            DamageHud.entityEvent(entity,packet.getEventId());
            dev.sylvyespvphud.RepotHud.entityEvent(entity,packet.getEventId());
        }
    }
}
