package dev.macepvpmod.mixin;

import dev.macepvpmod.DamageHud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.KineticWeapon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(KineticWeapon.class)
public abstract class SpearDamageMixin {
    @Redirect(method="damageEntities",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;stabAttack(Lnet/minecraft/world/entity/EquipmentSlot;Lnet/minecraft/world/entity/Entity;FZZZ)Z"))
    private boolean macepvpmod$captureSpear(LivingEntity attacker,EquipmentSlot slot,Entity target,float amount,boolean damage,boolean knockback,boolean dismount){
        if(damage)DamageHud.spearAttacked(attacker,target,amount);
        return attacker.stabAttack(slot,target,amount,damage,knockback,dismount);
    }
}
