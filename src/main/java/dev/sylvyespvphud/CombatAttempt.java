package dev.sylvyespvphud;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** One immutable attack snapshot shared by health and shield reconciliation. */
final class CombatAttempt {
    final LivingEntity target;
    final ItemStack weapon;
    final DamageWeapon type;
    final Object token;
    final double before, blocks, raw, unblocked, armor, toughness;
    final int protection;
    final boolean calculated, useGear, critical, smash;
    final List<ShieldDamageCalculator.Snapshot> shields;
    private static long nextSequence;
    final long sequence=++nextSequence;
    final long fallGeneration=DamageHud.fallGeneration;
    final long created, retention, settling;
    boolean contact, healthApplied, totem, contaminated;
    AttackHistory.Entry<CombatAttempt> entry;
    private record Position(long time,Vec3 point) {}
    private final List<Position> positions=new ArrayList<>();

    CombatAttempt(LivingEntity target,ItemStack weapon,double raw,double blocks,boolean critical,boolean smash,
                  List<ShieldDamageCalculator.Snapshot> shields,Object token,boolean contact) {
        this.target=target;this.weapon=weapon.copy();this.raw=raw;this.blocks=blocks;this.critical=critical;this.smash=smash;
        this.shields=List.copyOf(shields);this.token=token;this.contact=contact;type=DamageWeapon.of(weapon);before=target.getHealth();
        var config=SylvyesPvPHud.DAMAGE_CONFIG.current();calculated=config.calculatedDamage();useGear=calculated&&config.useEnemyGear();
        armor=target.getArmorValue();toughness=target.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS);
        int protection=0;
        for(var slot:List.of(net.minecraft.world.entity.EquipmentSlot.HEAD,net.minecraft.world.entity.EquipmentSlot.CHEST,
                net.minecraft.world.entity.EquipmentSlot.LEGS,net.minecraft.world.entity.EquipmentSlot.FEET))
            protection+=MaceDamageCalculator.enchantmentLevel(target.getItemBySlot(slot),net.minecraft.world.item.enchantment.Enchantments.PROTECTION);
        this.protection=protection;unblocked=afterGear(raw);
        created=CombatTiming.now();int ping=CombatTiming.ping();retention=CombatTiming.retention(ping);settling=CombatTiming.settling(ping);
        samplePosition(created);
    }
    double afterGear(double damage) {
        return useGear?MaceDamageCalculator.afterGear(damage,(float)armor,(float)toughness,
                MaceDamageCalculator.enchantmentLevel(weapon,net.minecraft.world.item.enchantment.Enchantments.BREACH),protection):damage;
    }
    void samplePosition(long now) {
        positions.removeIf(p->now-p.time()>retention);
        recordPosition(now,target.position());
        recordPosition(now,target.getPositionCodec().getBase());
    }
    private void recordPosition(long now,Vec3 point) {
        if(positions.stream().anyMatch(p->p.time()==now&&p.point().equals(point)))return;
        if(positions.size()>=256)positions.removeFirst();
        positions.add(new Position(now,point));
    }
    boolean near(Vec3 position) {return positions.stream().anyMatch(p->p.point().distanceToSqr(position)<=.25);}
    boolean shieldWeapon(){return type==DamageWeapon.MACE||type==DamageWeapon.SPEAR;}
}
