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
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.KineticWeapon;
import net.minecraft.world.phys.Vec3;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public final class DamageHud {
    private static final Map<Integer, Pending> pending = new LinkedHashMap<>();
    private static int displayTicks;
    private static double hitAmount = Double.NaN;
    private static String hit = "";
    private static boolean hitCritical;
    private static Object level;
    private static SpearSnapshot spearSnapshot;
    private DamageHud() {}
    private static final class Pending {
        final LivingEntity target; final float before; final double blocks, amount; final boolean calculated, critical;
        int ticks=40, confirmedTicks; boolean confirmed, totem;
        Pending(LivingEntity target,double blocks,double amount,boolean calculated,boolean critical){this.target=target;before=target.getHealth();this.blocks=blocks;this.amount=amount;this.calculated=calculated;this.critical=critical;}
    }
    public static void attacked(Entity entity) {
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null||!(entity instanceof LivingEntity target))return;
        var config=MacePvPMod.DAMAGE_CONFIG.current();var weapon=mc.player.getMainHandItem().copy();
        if(!config.hitEnabled()||!DamageWeapon.of(weapon).enabled(config))return;
        double amount=MaceDamageCalculator.atAttack(mc.player,target);
        if(config.calculatedDamage()&&config.useEnemyGear())amount=MaceDamageCalculator.afterGear(amount,weapon,target);
        boolean critical=DamageWeapon.of(weapon)!=DamageWeapon.SPEAR
                && MaceDamageCalculator.criticalAtAttack(mc.player,target,AttributeSwaps.attackCooldown(mc));
        pending.put(target.getId(),new Pending(target,mc.player.fallDistance,amount,config.calculatedDamage(),critical));
    }
    private record SpearSnapshot(ItemStack weapon,double attackDamage,int useTicks,double forwardSpeed,int age) {}
    private static void updateSpearSnapshot(Minecraft mc){
        if(mc.player==null)return;
        ItemStack stack=mc.player.getMainHandItem();
        if(DamageWeapon.of(stack)==DamageWeapon.SPEAR){
            Vec3 motion=KineticWeapon.getMotion(mc.player);
            spearSnapshot=new SpearSnapshot(stack.copy(),MaceDamageCalculator.effectiveAttackDamage(mc.player),mc.player.isUsingItem()?mc.player.getTicksUsingItem():0,mc.player.getLookAngle().dot(motion),0);
        }else if(spearSnapshot!=null&&spearSnapshot.age()<6)spearSnapshot=new SpearSnapshot(spearSnapshot.weapon(),spearSnapshot.attackDamage(),spearSnapshot.useTicks(),spearSnapshot.forwardSpeed(),spearSnapshot.age()+1);
        else spearSnapshot=null;
    }
    private static void registerConfirmedSpear(LivingEntity target){
        var mc=Minecraft.getInstance();var config=MacePvPMod.DAMAGE_CONFIG.current();
        if(mc.player==null||!config.hitEnabled()||!config.spearEnabled())return;
        ItemStack weapon=mc.player.getMainHandItem().copy();if(DamageWeapon.of(weapon)!=DamageWeapon.SPEAR&&spearSnapshot==null)return;
        double amount=MaceDamageCalculator.effectiveAttackDamage(mc.player);
        if(spearSnapshot!=null){weapon=spearSnapshot.weapon();amount=spearSnapshot.attackDamage();KineticWeapon kinetic=weapon.get(DataComponents.KINETIC_WEAPON);
            if(kinetic!=null){double targetForward=mc.player.getLookAngle().dot(KineticWeapon.getMotion(target));double relative=Math.max(0,spearSnapshot.forwardSpeed()-targetForward);int duration=spearSnapshot.useTicks()-kinetic.delayTicks();
                if(kinetic.damageConditions().isPresent()&&kinetic.damageConditions().get().test(duration,spearSnapshot.forwardSpeed(),relative,1))amount+=Math.floor(relative*kinetic.damageMultiplier());}}
        amount+=MaceDamageCalculator.enchantmentBonus(weapon,target);
        if(config.calculatedDamage()&&config.useEnemyGear())amount=MaceDamageCalculator.afterGear(amount,weapon,target);
        Pending p=new Pending(target,mc.player.fallDistance,amount,config.calculatedDamage(),false);p.confirmed=true;pending.put(target.getId(),p);
    }
    public static void damageEvent(ClientboundDamageEventPacket packet){var mc=Minecraft.getInstance();if(mc.player==null||packet.sourceCauseId()!=mc.player.getId())return;Pending p=pending.get(packet.entityId());if(p==null&&mc.level!=null&&mc.level.getEntity(packet.entityId()) instanceof LivingEntity living){registerConfirmedSpear(living);p=pending.get(packet.entityId());}if(p!=null)p.confirmed=true;}
    public static void entityEvent(Entity entity,byte event){if(event==35){Pending p=pending.get(entity.getId());if(p!=null)p.totem=true;}}
    public static void tick(Minecraft mc){
        if(mc.level!=level||mc.player==null||!mc.player.isAlive()){level=mc.level;pending.clear();spearSnapshot=null;displayTicks=0;hit="";return;}
        updateSpearSnapshot(mc);
        if(displayTicks>0)displayTicks--;
        if(!MacePvPMod.DAMAGE_CONFIG.current().hitEnabled()){pending.clear();displayTicks=0;return;}
        Iterator<Pending> iterator=pending.values().iterator();
        while(iterator.hasNext()){Pending p=iterator.next();p.ticks--;if(p.confirmed)p.confirmedTicks++;float observed=p.before-p.target.getHealth();
            if(p.confirmed&&p.calculated){show(DamageText.format(MacePvPMod.DAMAGE_CONFIG.current().hitTemplate(),p.blocks,p.amount),p.amount,p.critical);iterator.remove();}
            else if(p.confirmed&&observed>0){show(DamageText.format(MacePvPMod.DAMAGE_CONFIG.current().hitTemplate(),p.blocks,observed),observed,p.critical);iterator.remove();}
            else if(p.confirmed&&(p.totem||p.confirmedTicks>=10)){show(DamageText.format(MacePvPMod.DAMAGE_CONFIG.current().hitTemplate(),p.blocks,p.amount),p.amount,p.critical);iterator.remove();}
            else if(p.ticks<=0)iterator.remove();}
    }
    private static void show(String text,double amount,boolean critical){hit=text;hitAmount=amount;hitCritical=critical;displayTicks=MacePvPMod.DAMAGE_CONFIG.current().hitSeconds()*20;}
    static String visibleHit(){return displayTicks>0?hit:"";}
    static boolean visibleHitCritical(){return displayTicks>0&&hitCritical;}
    static boolean showFall(double distance){return showFall(distance,1.5);}
    static boolean showFall(double distance,double threshold){return Double.isFinite(distance)&&Double.isFinite(threshold)&&distance>threshold;}
    public static void extract(GuiGraphicsExtractor g,DeltaTracker delta){var mc=Minecraft.getInstance();var p=mc.player;if(p==null||mc.level==null||mc.gui.screen()!=null||mc.gui.hud.isHidden()||!p.isAlive()||p.isSpectator())return;var c=MacePvPMod.DAMAGE_CONFIG.current();double fallBlocks=p.fallDistance;if(c.fallEnabled()&&showFall(fallBlocks,c.fallThreshold()))HudRenderer.textColor(g,DamageText.format(c.fallTemplate(),fallBlocks,0),MacePvPMod.HUD_CONFIG.current().fall(),c.fallColors().color(fallBlocks));if(c.hitEnabled()&&displayTicks>0){Component text=Component.literal(hit);if(c.boldCriticalDamage()&&hitCritical)text=text.copy().withStyle(ChatFormatting.BOLD);HudRenderer.textColor(g,text,MacePvPMod.HUD_CONFIG.current().hit(),c.hitColors().color(hitAmount));}}
}
