package dev.macepvpmod;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class SettingsUpgradeTest {
    @TempDir Path directory;
    @Test void templatesValidateAndFormatWithoutSuffixes() {
        assertEquals("12.5 blocks",DamageText.format("{blocks} blocks",12.45,18));
        assertEquals("18.0 damage from 12.5 blocks",DamageText.format("{damage} damage from {blocks} blocks",12.5,18));
        assertEquals("",DamageText.error("{blocks} blocks",false));
        for(String invalid:new String[]{"", " ","{damage}","{unknown}","{{blocks}}","{blocks","blocks}"})assertFalse(DamageText.error(invalid,false).isEmpty());
        assertEquals("",DamageText.error("Damage: {damage} / {blocks}",true));
    }
    @Test void oldDamageGetsTemplatesAndCustomTemplatesPersist() throws Exception {
        Path path=directory.resolve("damage.json");Files.writeString(path,"{\"fallEnabled\":false}");
        var store=new DamageConfigStore(path);store.load();assertEquals("{blocks} blocks",store.current().fallTemplate());assertEquals("{damage} damage",store.current().hitTemplate());
        var d=store.current();var custom=new DamageConfig(1,d.fallEnabled(),d.fallColor(),d.fallSize(),d.fallX(),d.fallY(),d.hitEnabled(),d.hitColor(),d.hitSize(),d.hitX(),d.hitY(),d.hitSeconds(),true,"Fall: {blocks}","Hit: {damage} ({blocks} blocks)");
        store.save(custom);var loaded=new DamageConfigStore(path);loaded.load();assertEquals(custom,loaded.current());
    }
    @Test void oldAudioMigratesAndExplicitEmptyListStaysEmpty() throws Exception {
        Path path=directory.resolve("survival.json");Files.writeString(path,"{\"lowHealthVolume\":0.3,\"bassPitch\":1.5}");
        var store=new SurvivalConfigStore(path);store.load();assertEquals(SoundEntry.legacy(.3,.3,1,1.5),store.current().sounds());
        Files.writeString(path,"{\"sounds\":[]}");store.load();assertTrue(store.current().sounds().isEmpty());store.save(store.current());store.load();assertTrue(store.current().sounds().isEmpty());
        Files.writeString(path,"{\"sounds\":[{\"sound\":\"missing:event\",\"volume\":2,\"pitch\":0},{\"sound\":\"missing:event\",\"volume\":0.5,\"pitch\":1}]}");store.load();assertEquals(2,store.current().sounds().size());assertEquals(new SoundEntry("missing:event",1,.5),store.current().sounds().getFirst());
    }
    @Test void cursorLoopsResetsAndHandlesEmptyLists() {
        var cursor=new SoundPlaylistCursor();assertEquals(-1,cursor.next(0));
        for(int i=0;i<10;i++)assertEquals(i%3,cursor.next(3));cursor.reset();assertEquals(0,cursor.next(3));assertEquals(-1,cursor.next(0));assertEquals(0,cursor.next(1));assertEquals(0,cursor.next(1));
    }
    @Test void hudMigrationPreservesAnchorsAndAppearanceAndPersists() throws Exception {
        var config=HudConfig.migrate(PitchConfig.defaults(),DamageConfig.defaults(),SurvivalConfig.defaults());
        assertEquals(.5,config.fall().anchorX());assertEquals(14,config.fall().y());assertEquals(0,config.fall().alignY());
        assertEquals(.18,config.retotem().anchorY());assertEquals(.18,config.retotem().alignY());assertEquals(.4,config.pitch().opacity());
        var path=directory.resolve("hud.json");var store=new HudConfigStore(path);store.save(config);var loaded=new HudConfigStore(path);loaded.load();assertEquals(config,loaded.current());
        Files.writeString(path,"{\"schemaVersion\":99}");loaded.load();assertEquals(HudConfig.defaults(),loaded.current());
        try(var files=Files.list(directory)){assertTrue(files.anyMatch(p->p.getFileName().toString().startsWith("macepvpmod-invalid-")));}
    }
    @Test void existingHudFilesGainAttributeSwapWithoutChangingOtherElements() throws Exception {
        var path=directory.resolve("hud.json");var store=new HudConfigStore(path);
        var custom=HudConfig.defaults().with(1,HudConfig.defaults().fall().edit(55,66,2,0x123456,0,0,100,1,1));
        store.save(custom);
        var json=com.google.gson.JsonParser.parseString(Files.readString(path)).getAsJsonObject();json.remove("attributeSwap");Files.writeString(path,json.toString());
        store.load();assertEquals(custom.fall(),store.current().fall());assertEquals(HudConfig.attributeSwapDefault(),store.current().attributeSwap());
        var edited=store.current().with(5,store.current().attributeSwap().edit(25,-80,2,0xffaa00,0,0,100,1,1));
        store.save(edited);store.load();assertEquals(edited,store.current());
    }
    @Test void invalidStylesClampWithoutNonfiniteValues() {
        var s=new HudStyle(Double.NaN,2,-1,0,Double.POSITIVE_INFINITY,-9000,20,-1,0,0,900,0,0).validated();
        assertEquals(.5,s.anchorX());assertEquals(1,s.anchorY());assertEquals(0,s.x());assertEquals(-4000,s.y());assertEquals(4,s.scale());assertEquals(400,s.width());assertEquals(1,s.thickness());assertEquals(.05,s.opacity());
    }
}
