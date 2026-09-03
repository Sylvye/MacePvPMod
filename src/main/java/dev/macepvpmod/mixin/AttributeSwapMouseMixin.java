package dev.macepvpmod.mixin;

import dev.macepvpmod.AttributeSwaps;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class AttributeSwapMouseMixin {
    @Inject(method = "onButton", at = @At("HEAD"))
    private void clicked(long window, MouseButtonInfo button, int action, CallbackInfo ci) {
        if (action == 1 && (button.button() == 0 || button.button() == 1)) AttributeSwaps.click();
    }
}
