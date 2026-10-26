package dev.sylvyespvphud;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Independent per-target accumulation and display lifetime for either shield HUD. */
final class ShieldDamageTotals {
    private record Entry(String name, double damage, int ticks, Object session) {}
    private final Map<UUID, Entry> entries = new LinkedHashMap<>();
    private final int fadeDuration;
    private UUID visibleTarget;
    private Object visibleSession;
    private String name;
    private double damage;
    private int displayTicks, fadeTicks;
    private boolean always;

    ShieldDamageTotals(int fadeDuration) { this.fadeDuration = fadeDuration; }

    Object record(UUID id, String targetName, double amount, int storageTicks, int hudTicks, boolean alwaysVisible) {
        if (!Double.isFinite(amount) || amount <= 0) return null;
        Entry previous = entries.get(id);
        damage = (previous == null ? 0 : previous.damage()) + amount;
        Object session=previous==null?new Object():previous.session();
        entries.put(id, new Entry(targetName, damage, storageTicks, session));
        visibleSession=session;
        visibleTarget = id;
        name = targetName;
        displayTicks = hudTicks;
        fadeTicks = 0;
        always = alwaysVisible;
        return session;
    }

    /** Correct a provisional amount without extending storage/display life or reviving old streaks. */
    void adjust(UUID id,Object session,double delta) {
        if(session==null||!Double.isFinite(delta))return;
        Entry entry=entries.get(id);
        if(entry!=null&&entry.session()==session)
            entries.put(id,new Entry(entry.name(),Math.max(0,entry.damage()+delta),entry.ticks(),session));
        if(id.equals(visibleTarget)&&visibleSession==session&&name!=null)damage=Math.max(0,damage+delta);
    }
    boolean retains(UUID id,Object session) {
        Entry entry=entries.get(id);
        return session!=null&&(entry!=null&&entry.session()==session||id.equals(visibleTarget)&&visibleSession==session&&name!=null);
    }

    void tick() {
        boolean expired = false;
        var iterator = entries.entrySet().iterator();
        while (iterator.hasNext()) {
            var next = iterator.next();
            Entry entry = next.getValue();
            if (entry.ticks() <= 1) {
                if (next.getKey().equals(visibleTarget)) expired = true;
                iterator.remove();
            } else next.setValue(new Entry(entry.name(), entry.damage(), entry.ticks() - 1, entry.session()));
        }
        if (expired) { hide(); return; }
        if (fadeTicks > 0) { if (--fadeTicks == 0) clearDisplay(); }
        else if (!always && displayTicks > 0 && --displayTicks == 0) hide();
    }

    // A broken shield's final result remains visible, but cannot seed the next sum.
    void broken(UUID id) { entries.remove(id); }
    void reset(UUID id) {
        entries.remove(id);
        if (id.equals(visibleTarget)) hide();
    }
    void resetAll() { entries.clear(); clearDisplay(); }
    void dismiss() { clearDisplay(); }
    private void hide() {
        displayTicks = 0;
        always = false;
        if (name != null && fadeDuration > 0 && fadeTicks == 0) fadeTicks = fadeDuration;
        else if (fadeDuration == 0) clearDisplay();
    }
    private void clearDisplay() { visibleTarget = null; visibleSession=null; name = null; damage = 0; displayTicks = fadeTicks = 0; always = false; }
    double total(UUID id) { Entry entry = entries.get(id); return entry == null ? 0 : entry.damage(); }
    double amount() { return name == null ? Double.NaN : damage; }
    UUID active() { return visibleTarget; }
    String visible(String template) { return name == null ? "" : CumulativeDamageText.format(template, name, damage); }
    double opacity() { return name == null ? 0 : fadeTicks > 0 ? fadeTicks / (double) fadeDuration : 1; }
}
