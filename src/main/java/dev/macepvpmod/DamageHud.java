package dev.macepvpmod;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public final class DamageHud {
    private static final Map<Integer, Pending> pending = new LinkedHashMap<>();
    private static int displayTicks;
    private static double hitAmount = Double.NaN;
    private static String hit = "";
    private static Object level;
    private DamageHud() {}
    private static final class Pending {
        final LivingEntity target; final float before; final double blocks, amount; final boolean calculated;
        int ticks=20; boolean confirmed;
        Pending(LivingEntity target,double blocks,double amount,boolean calculated){this.target=target;before=target.getHealth();this.blocks=blocks;this.amount=amount;this.calculated=calculated;}
    }
    public static void attacked(Entity entity) {
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null||!(entity instanceof LivingEntity target))return;
        var config=MacePvPMod.DAMAGE_CONFIG.current();var weapon=mc.player.getMainHandItem().copy();
        if(!config.hitEnabled()||!DamageWeapon.of(weapon).enabled(config))return;
        double amount=config.calculatedDamage()?MaceDamageCalculator.atAttack(mc.player,target):0;
        if(config.calculatedDamage()&&config.useEnemyGear())amount=MaceDamageCalculator.afterGear(amount,weapon,target);
        pending.put(target.getId(),new Pending(target,mc.player.fallDistance,amount,config.calculatedDamage()));
    }
    public static void spearAttacked(LivingEntity attacker,Entity entity,float kineticAmount){
        Minecraft mc=Minecraft.getInstance();var config=MacePvPMod.DAMAGE_CONFIG.current();
        if(attacker!=mc.player||!(entity instanceof LivingEntity target)||!config.hitEnabled()||!config.spearEnabled())return;
        ItemStack weapon=attacker.getMainHandItem().copy();double amount=kineticAmount+MaceDamageCalculator.enchantmentBonus(weapon,target);
        if(config.calculatedDamage()&&config.useEnemyGear())amount=MaceDamageCalculator.afterGear(amount,weapon,target);
        pending.put(target.getId(),new Pending(target,attacker.fallDistance,amount,config.calculatedDamage()));
    }
    public static void damageEvent(ClientboundDamageEventPacket packet){var mc=Minecraft.getInstance();Pending p=pending.get(packet.entityId());if(p!=null&&mc.player!=null&&packet.sourceCauseId()==mc.player.getId())p.confirmed=true;}
    public static void tick(Minecraft mc){
        if(mc.level!=level||mc.player==null||!mc.player.isAlive()){level=mc.level;pending.clear();displayTicks=0;hit="";return;}
        if(displayTicks>0)displayTicks--;
        if(!MacePvPMod.DAMAGE_CONFIG.current().hitEnabled()){pending.clear();displayTicks=0;return;}
        Iterator<Pending> iterator=pending.values().iterator();
        while(iterator.hasNext()){Pending p=iterator.next();p.ticks--;float observed=p.before-p.target.getHealth();
            if(p.confirmed&&p.calculated){show(DamageText.format(MacePvPMod.DAMAGE_CONFIG.current().hitTemplate(),p.blocks,p.amount),p.amount);iterator.remove();}
            else if(p.confirmed&&observed>0){show(DamageText.format(MacePvPMod.DAMAGE_CONFIG.current().hitTemplate(),p.blocks,observed),observed);iterator.remove();}
            else if(p.ticks<=0){if(p.confirmed)show("Damage unavailable",Double.NaN);iterator.remove();}}
    }
    private static void show(String text,double amount){hit=text;hitAmount=amount;displayTicks=MacePvPMod.DAMAGE_CONFIG.current().hitSeconds()*20;}
    static String visibleHit(){return displayTicks>0?hit:"";}
    static boolean showFall(double distance){return showFall(distance,1.5);}
    static boolean showFall(double distance,double threshold){return Double.isFinite(distance)&&Double.isFinite(threshold)&&distance>threshold;}
    public static void extract(GuiGraphicsExtractor g,DeltaTracker delta){var mc=Minecraft.getInstance();var p=mc.player;if(p==null||mc.level==null||mc.gui.screen()!=null||mc.gui.hud.isHidden()||!p.isAlive()||p.isSpectator())return;var c=MacePvPMod.DAMAGE_CONFIG.current();double fallBlocks=p.fallDistance;if(c.fallEnabled()&&showFall(fallBlocks,c.fallThreshold()))HudRenderer.textColor(g,DamageText.format(c.fallTemplate(),fallBlocks,0),MacePvPMod.HUD_CONFIG.current().fall(),c.fallColors().color(fallBlocks));if(c.hitEnabled()&&displayTicks>0)HudRenderer.textColor(g,hit,MacePvPMod.HUD_CONFIG.current().hit(),c.hitColors().color(hitAmount));}
}
