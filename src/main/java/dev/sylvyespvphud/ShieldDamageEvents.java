package dev.sylvyespvphud;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Client-thread entry points for the packet mixin. */
public final class ShieldDamageEvents {
    public static void sound(ClientboundSoundPacket packet) {
        ShieldDamageHud.blockSound(packet.getSound(), new Vec3(packet.getX(),packet.getY(),packet.getZ()), null);
    }
    public static void sound(ClientboundSoundEntityPacket packet) {
        ShieldDamageHud.blockSound(packet.getSound(), null, packet.getId());
    }
    public static void equipment(ClientboundSetEquipmentPacket packet) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getEntity(packet.getEntity()) instanceof LivingEntity target)) return;
        for (var update : packet.getSlots())
            ShieldDamageHud.equipment(target,update.getFirst(),target.getItemBySlot(update.getFirst()),update.getSecond());
    }
    private ShieldDamageEvents() {}
}
