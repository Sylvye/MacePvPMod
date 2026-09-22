package dev.sylvyespvphud;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

final class CumulativeDamageTracker {
    static final int FADE_TICKS=20;
    private static final Map<UUID,Entry> entries=new LinkedHashMap<>();
    private static UUID active;
    private static String displayName;
    private static double displayDamage;
    private static int displayTicks,fadeTicks;
    private static boolean alwaysVisible;
    private CumulativeDamageTracker() {}

    private static final class Entry {
        String name;double damage;int ticks;
        Entry(String name,double damage,int ticks){this.name=name;this.damage=damage;this.ticks=ticks;}
    }

    static void record(UUID id,String name,double damage,int durationTicks,int hudTicks,boolean always) {
        if(!Double.isFinite(damage)||damage<=0)return;
        Entry entry=entries.computeIfAbsent(id,key->new Entry(name,0,durationTicks));
        entry.name=name;entry.damage+=damage;entry.ticks=durationTicks;active=id;
        displayName=entry.name;displayDamage=entry.damage;displayTicks=hudTicks;fadeTicks=0;alwaysVisible=always;
    }

    static void tick() {
        boolean startedFade=false;
        Iterator<Map.Entry<UUID,Entry>> iterator=entries.entrySet().iterator();
        while(iterator.hasNext()){
            var next=iterator.next();
            if(--next.getValue().ticks<=0){if(next.getKey().equals(active)){startFade();startedFade=true;active=null;}iterator.remove();}
        }
        if(displayName==null||startedFade)return;
        if(fadeTicks>0){if(--fadeTicks<=0)clearDisplay();return;}
        if(!alwaysVisible&&displayTicks>0&&--displayTicks<=0)startFade();
    }

    static void reset(UUID id){entries.remove(id);if(id.equals(active)){startFade();active=null;}}
    static void fade(UUID id){if(id.equals(active))startFade();}
    static void resetAll(boolean fade){entries.clear();active=null;if(fade)startFade();else clearDisplay();}
    static void resetAll(){resetAll(false);}
    private static void startFade(){if(displayName!=null)fadeTicks=FADE_TICKS;displayTicks=0;alwaysVisible=false;}
    private static void clearDisplay(){displayName=null;displayDamage=0;displayTicks=0;fadeTicks=0;alwaysVisible=false;}
    static String visible(String template){return displayName==null?"":CumulativeDamageText.format(template,displayName,displayDamage);}
    static double opacity(){return displayName==null?0:fadeTicks>0?fadeTicks/(double)FADE_TICKS:1;}
    static double total(UUID id){Entry entry=entries.get(id);return entry==null?0:entry.damage;}
    static UUID active(){return active;}
}
