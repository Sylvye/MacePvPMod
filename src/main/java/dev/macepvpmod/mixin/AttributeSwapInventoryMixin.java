package dev.macepvpmod.mixin;

import dev.macepvpmod.AttributeSwaps;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Inventory.class)
public abstract class AttributeSwapInventoryMixin {
    @Inject(method = "setSelectedSlot", at = @At("RETURN"))
    private void selected(int slot, CallbackInfo ci) {
        AttributeSwaps.selected((Inventory)(Object)this, slot);
    }
}
