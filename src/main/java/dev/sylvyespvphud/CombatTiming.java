package dev.sylvyespvphud;

import java.util.function.LongSupplier;
import net.minecraft.client.Minecraft;

/** Milliseconds from a monotonic clock; injectable for delayed-packet client checks. */
final class CombatTiming {
    static LongSupplier clock=()->System.nanoTime()/1_000_000;
    static long now(){return clock.getAsLong();}
    static long retention(int ping){return ping<0?2000:Math.clamp(2L*ping+500,2000,6000);}
    static long settling(int ping){return ping<0?200:Math.clamp(ping/2L+100,150,500);}
    static int ping(){
        var mc=Minecraft.getInstance();
        if(mc.player==null||mc.getConnection()==null)return -1;
        var info=mc.getConnection().getPlayerInfo(mc.player.getUUID());
        return info==null?-1:info.getLatency();
    }
    private CombatTiming(){}
}
