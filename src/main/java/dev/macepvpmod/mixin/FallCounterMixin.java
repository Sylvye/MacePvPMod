package dev.macepvpmod.mixin;

import dev.macepvpmod.FallCounter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class FallCounterMixin {
    @Unique private double macepvpmod$beforeMoveY;
    @Inject(method = "move", at = @At("HEAD"))
    private void macepvpmod$beforeMove(MoverType type, Vec3 movement, CallbackInfo ci) {
        if ((Object)this instanceof LocalPlayer player && player == Minecraft.getInstance().player)
            macepvpmod$beforeMoveY = player.getY();
    }
    @Inject(method = "move", at = @At("RETURN"))
    private void macepvpmod$afterMove(MoverType type, Vec3 movement, CallbackInfo ci) {
        if ((Object)this instanceof LocalPlayer player && player == Minecraft.getInstance().player)
            FallCounter.moved(player, player.getY() - macepvpmod$beforeMoveY);
    }
}
