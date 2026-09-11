package dev.macepvpmod.mixin;

import dev.macepvpmod.DamageHud;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.item.component.PiercingWeapon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public abstract class SpearJabMixin {
    @Inject(method="piercingAttack",at=@At("HEAD"))
    private void macepvpmod$captureSpearJab(PiercingWeapon piercing,CallbackInfo ci){
        DamageHud.spearJab(piercing);
    }
}
