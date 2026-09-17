package dev.macepvpmod.mixin;

import dev.macepvpmod.MacePvPMod;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.contextualbar.LocatorBar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocatorBar.class)
public abstract class LocatorBarMixin {
    @Inject(method="extractBackground",at=@At("HEAD"),cancellable=true)
    private void macepvpmod$hideBackground(GuiGraphicsExtractor graphics,DeltaTracker delta,CallbackInfo ci){if(hide())ci.cancel();}
    @Inject(method="extractRenderState",at=@At("HEAD"),cancellable=true)
    private void macepvpmod$hideMarkers(GuiGraphicsExtractor graphics,DeltaTracker delta,CallbackInfo ci){if(hide())ci.cancel();}
    private static boolean hide(){var config=MacePvPMod.TRACKER_CONFIG.current();return config.enabled()&&config.hideLocatorBar();}
}
