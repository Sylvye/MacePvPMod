package dev.sylvyespvphud;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class RepotAttentionTest {
    private static final String SPEED="minecraft:speed", STRENGTH="minecraft:strength";
    private static final RepotState.Effect LONG=new RepotState.Effect(1200,false,false);
    private static final RepotConfig CONFIG=RepotConfig.defaults();
    private static final Set<String> POTIONS=Set.of(SPEED,STRENGTH);
    private final RepotState state=new RepotState();

    @Test void pulseSpeedsUpSmoothlyAndStaysWithinVisibleOpacityRange() {
        var a=RepotAttention.defaults();
        assertEquals(1.5,a.period(300,15),1e-9);
        assertEquals(.925,a.period(150,15),1e-9);
        assertEquals(.35,a.period(0,15),1e-9);
        assertEquals(.35,a.period(0,0),1e-9);
        assertEquals(1.5,a.period(600,15),1e-9);
        assertEquals(.35,a.period(-5,15),1e-9);
        for(int i=0;i<1000;i++) assertTrue(a.opacity(i/100.0)>=.35&&a.opacity(i/100.0)<=1);
        assertEquals(1,a.opacity(0),1e-9); assertEquals(.35,a.opacity(.5),1e-9);
        var disabled=new RepotAttention(false,1.5,.35,false,0x123456);
        assertEquals(1,disabled.opacity(.5));
        assertEquals(0xabcdef,disabled.color(true,0xabcdef));
        assertEquals(0xff3333,a.color(true,0xabcdef)); assertEquals(0xabcdef,a.color(false,0xabcdef));
    }

    @Test void intervalsValidateAndColorsClamp() {
        assertThrows(IllegalArgumentException.class,()->new RepotAttention(true,.5,1,true,0).validated());
        var valid=new RepotAttention(true,10,-1,true,-1).validated();
        assertEquals(5,valid.slowInterval()); assertEquals(.1,valid.fastInterval()); assertEquals(0xffffff,valid.endedColor());
        assertEquals(RepotAttention.defaults(),new RepotAttention(true,Double.NaN,Double.POSITIVE_INFINITY,true,0xff3333).validated());
    }

    @Test void removalBeforePopCreatesEndedWarningForLongEffect() {
        state.tick(CONFIG,Map.of(SPEED,LONG),POTIONS);
        state.removed(SPEED,LONG);
        assertTrue(state.tick(CONFIG,Map.of(),POTIONS).isEmpty());
        state.totemPopped();
        assertEquals(List.of(SPEED),state.tick(CONFIG,Map.of(),POTIONS));
        assertEquals(List.of(new RepotState.Warning(SPEED,0,true)),state.details());
    }

    @Test void popBeforeRemovalAndMixedWarningsPreserveOrder() {
        state.totemPopped(); state.tick(CONFIG,Map.of(SPEED,LONG),POTIONS);
        state.removed(SPEED,LONG);
        var active=Map.of(STRENGTH,new RepotState.Effect(75,false,false));
        assertEquals(List.of(SPEED,STRENGTH),state.tick(CONFIG,active,POTIONS));
        assertTrue(state.details().get(0).ended()); assertFalse(state.details().get(1).ended());
        assertEquals(75,state.details().get(1).remainingTicks());
    }

    @Test void correlationIncludesTwoTicksButExpiresAfterThat() {
        state.removed(SPEED,LONG);
        state.tick(CONFIG,Map.of(),POTIONS); state.tick(CONFIG,Map.of(),POTIONS);
        state.totemPopped(); assertEquals(List.of(SPEED),state.tick(CONFIG,Map.of(),POTIONS));
        state.clear(); state.removed(SPEED,LONG);
        for(int i=0;i<3;i++)state.tick(CONFIG,Map.of(),POTIONS);
        state.totemPopped(); assertTrue(state.tick(CONFIG,Map.of(),POTIONS).isEmpty());
        state.clear(); state.totemPopped();
        for(int i=0;i<3;i++)state.tick(CONFIG,Map.of(),POTIONS);
        state.removed(SPEED,LONG); assertTrue(state.tick(CONFIG,Map.of(),POTIONS).isEmpty());
    }

    @Test void ordinaryRemovalAndPopWithoutRemovalDoNotWarn() {
        state.removed(SPEED,LONG);
        assertTrue(state.tick(CONFIG,Map.of(),POTIONS).isEmpty());
        state.clear(); state.totemPopped();
        assertTrue(state.tick(CONFIG,Map.of(SPEED,LONG),POTIONS).isEmpty());
        assertTrue(state.tick(CONFIG,Map.of(),POTIONS).isEmpty());
    }

    @Test void reappliedAmbientAndInfiniteEffectsCancelTotemGrace() {
        for(var effect:List.of(LONG,new RepotState.Effect(1,true,false),new RepotState.Effect(-1,false,true))) {
            state.clear(); state.removed(SPEED,LONG); state.totemPopped();
            assertTrue(state.tick(CONFIG,Map.of(SPEED,effect),POTIONS).isEmpty());
            assertTrue(state.tick(CONFIG,Map.of(),POTIONS).isEmpty());
        }
        for(var effect:List.of(new RepotState.Effect(1,true,false),new RepotState.Effect(-1,false,true))) {
            state.clear(); state.removed(SPEED,effect); state.totemPopped();
            assertTrue(state.tick(CONFIG,Map.of(),POTIONS).isEmpty());
        }
    }

    @Test void graceUsesExistingDurationAndInventoryGateWithoutRestarting() {
        var config=new RepotConfig(true,List.of(SPEED),15,1,true,4,"Repot {effect}");
        state.removed(SPEED,LONG);state.totemPopped();
        assertTrue(state.tick(config,Map.of(),Set.of()).isEmpty());
        for(int i=0;i<19;i++)assertEquals(List.of(SPEED),state.tick(config,Map.of(),POTIONS));
        assertTrue(state.tick(config,Map.of(),POTIONS).isEmpty());
        state.totemPopped();assertTrue(state.tick(config,Map.of(),POTIONS).isEmpty());
    }

    @Test void zeroGraceDeselectionDisableAndResetClearPendingPops() {
        state.removed(SPEED,LONG);state.totemPopped();
        assertTrue(state.tick(new RepotConfig(true,List.of(SPEED),15,0,true,4,"{effect}"),Map.of(),POTIONS).isEmpty());
        for(var config:List.of(new RepotConfig(false,List.of(SPEED),15,10,true,4,"{effect}"),
                new RepotConfig(true,List.of(),15,10,true,4,"{effect}"))) {
            state.clear();state.removed(SPEED,LONG);state.totemPopped();
            state.tick(config,Map.of(),POTIONS);
            assertTrue(state.tick(CONFIG,Map.of(),POTIONS).isEmpty());
        }
        state.removed(SPEED,LONG);state.totemPopped();state.clear();
        assertTrue(state.tick(CONFIG,Map.of(),POTIONS).isEmpty());
    }

    @TempDir Path directory;
    @Test void legacyNestedSettingsAndProfilesMigrateWithoutLosingCustomizations() throws Exception {
        var gson=new Gson();var root=gson.toJsonTree(ProfileSettings.defaults()).getAsJsonObject();
        var repot=root.getAsJsonObject("survival").getAsJsonObject("repot");
        repot.remove("attention");repot.addProperty("graceSeconds",24);repot.addProperty("icons",false);
        var old=gson.fromJson(root,ProfileSettings.class).validated();
        assertEquals(RepotAttention.defaults(),old.survival().repot().attention());
        assertEquals(24,old.survival().repot().graceSeconds());assertFalse(old.survival().repot().icons());
        var file=directory.resolve("survival.json");Files.writeString(file,root.get("survival").toString());
        var store=new SurvivalConfigStore(file);store.load();assertEquals(old.survival(),store.current());
        repot.add("attention",JsonParser.parseString("{\"pulsing\":false,\"endedColor\":1193046}"));
        Files.writeString(file,root.get("survival").toString());store.load();store.save(store.current());
        assertFalse(store.current().repot().attention().pulsing());
        assertTrue(store.current().repot().attention().endedColorEnabled());
        assertEquals(0x123456,store.current().repot().attention().endedColor());
        var loaded=new SurvivalConfigStore(file);loaded.load();assertEquals(store.current(),loaded.current());
    }

    @Test void customAttentionRoundTripsThroughProfilesAndBothClipboardFormats() throws Exception {
        var gson=new Gson();var root=gson.toJsonTree(ProfileSettings.defaults()).getAsJsonObject();
        var attention=new RepotAttention(false,2,.2,false,0x123456);
        root.getAsJsonObject("survival").getAsJsonObject("repot").add("attention",gson.toJsonTree(attention));
        var settings=gson.fromJson(root,ProfileSettings.class).validated();
        var manager=new ProfileManager(directory.resolve("profiles.json"),ignored->{});manager.load(settings);
        manager.setModuleEnabled(ProfileManager.Module.SURVIVAL,false);manager.setModuleEnabled(ProfileManager.Module.SURVIVAL,true);
        var reloaded=new ProfileManager(directory.resolve("profiles.json"),ignored->{});reloaded.load(ProfileSettings.defaults());
        assertEquals(settings,reloaded.active().settings());
        assertEquals(settings,ProfileClipboard.decode(ProfileClipboard.encode(manager.active())).settings());
        assertEquals(settings,ProfileClipboard.decode(ProfileClipboard.encodeAscii(manager.active())).settings());
    }
}
