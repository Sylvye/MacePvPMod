package dev.sylvyespvphud;

import java.util.function.LongSupplier;
import net.minecraft.client.Minecraft;

/** Deterministic network time, independent of how quickly the client machine runs ticks. */
final class CombatTestClock implements AutoCloseable {
    private final LongSupplier previous=CombatTiming.clock;
    private long now=CombatTiming.now();
    CombatTestClock(){CombatTiming.clock=()->now;}
    void advance(long millis){now+=millis;}
    void tick(Minecraft mc){advance(50);DamageHud.tick(mc);}
    void settle(Minecraft mc){advance(500);DamageHud.tick(mc);}
    @Override public void close(){CombatTiming.clock=previous;}
    static void charge(Minecraft mc) {
        try {
            var ticker=net.minecraft.world.entity.LivingEntity.class.getDeclaredField("attackStrengthTicker");
            ticker.setAccessible(true);ticker.setInt(mc.player,100);
        }catch(ReflectiveOperationException e){throw new AssertionError(e);}
    }
    static void reset(Minecraft mc) {
        DamageHud.clearPending();ShieldDamageHud.reset();DamageHud.clearHealthDisplay();DamageHud.resetTracker();
        AttributeSwaps.endTick();
        if(mc.player==null)return;
        charge(mc);mc.player.setSprinting(true);mc.player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.MACE));
    }
}
