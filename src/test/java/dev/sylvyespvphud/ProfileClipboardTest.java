package dev.sylvyespvphud;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;

class ProfileClipboardTest {
    private static final Gson GSON=new Gson();
    private static SettingsProfile profile(String name,ProfileSettings settings){return new SettingsProfile(UUID.randomUUID().toString(),name,List.of("private.example:25565"),settings);}

    @Test void defaultIsTinyAndBothAlphabetsRoundTrip(){var p=profile("Default",ProfileSettings.defaults());String compact=ProfileClipboard.encode(p),ascii=ProfileClipboard.encodeAscii(p);assertTrue(compact.length()<100,compact.length()+" characters");assertTrue(compact.length()<ascii.length());assertEquals(p.settings(),ProfileClipboard.decode(compact).settings());assertEquals(p.settings(),ProfileClipboard.decode(ascii).settings());assertFalse(compact.contains("private.example"));}

    @Test void nonDefaultValuesAcrossEverySectionRoundTripExactly(){JsonObject root=GSON.toJsonTree(ProfileSettings.defaults()).getAsJsonObject();root.getAsJsonObject("pitch").addProperty("targetPitch",12.25);root.getAsJsonObject("damage").addProperty("fallTemplate","落下 {blocks} ✓");root.getAsJsonObject("attributeSwaps").addProperty("soundEnabled",false);root.getAsJsonObject("survival").addProperty("healthText","Heal ♥ now");root.getAsJsonObject("hud").getAsJsonObject("pitch").addProperty("x",137);root.getAsJsonObject("reachOutlines").addProperty("intensity",.37);root.getAsJsonObject("vectors").addProperty("size",19);root.getAsJsonObject("playerTracker").addProperty("radius",93);ProfileSettings settings=GSON.fromJson(root,ProfileSettings.class).validated();String encoded=ProfileClipboard.encode(profile("PvP 日本語",settings));var decoded=ProfileClipboard.decode(encoded);assertTrue(encoded.length()<2000);assertEquals(settings,decoded.settings());assertEquals("PvP 日本語",decoded.name());}

    @Test void stableSchemaFieldIdsDoNotDrift(){assertEquals(130,ProfileClipboard.schemaFieldCount());assertEquals(0,ProfileClipboard.schemaFieldId("alignX"));assertEquals(15,ProfileClipboard.schemaFieldId("damage"));assertEquals(40,ProfileClipboard.schemaFieldId("healingItems"));assertEquals(93,ProfileClipboard.schemaFieldId("schemaVersion"));assertEquals(101,ProfileClipboard.schemaFieldId("sounds"));assertEquals(118,ProfileClipboard.schemaFieldId("vectors"));assertEquals(129,ProfileClipboard.schemaFieldId("y"));}

    @Test void legacySph1StillImports()throws Exception{var p=profile("Legacy",ProfileSettings.defaults());String json=GSON.toJson(Map.of("schemaVersion",1,"name",p.name(),"settings",p.settings()));ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(GZIPOutputStream gzip=new GZIPOutputStream(bytes)){gzip.write(json.getBytes(StandardCharsets.UTF_8));}String old="SPH1:"+Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());assertEquals(p.settings(),ProfileClipboard.decode(old).settings());}

    @Test void checksumAndAlphabetDamageAreRejected(){String s=ProfileClipboard.encode(profile("Default",ProfileSettings.defaults()));char last=s.charAt(s.length()-1);String damaged=s.substring(0,s.length()-1)+(char)(last==0x4e00?0x4e01:last-1);assertThrows(IllegalArgumentException.class,()->ProfileClipboard.decode(damaged));assertThrows(IllegalArgumentException.class,()->ProfileClipboard.decode("SPH2:abc"));}

    @Test void largeProfilesChunkAndReassembleInAnyOrder(){JsonObject root=GSON.toJsonTree(ProfileSettings.defaults()).getAsJsonObject();JsonArray sounds=new JsonArray();Random random=new Random(7);for(int i=0;i<10000;i++){JsonObject sound=new JsonObject();sound.addProperty("sound","test:"+i+"_"+Long.toUnsignedString(random.nextLong(),36));sound.addProperty("volume",.5);sound.addProperty("pitch",1);sounds.add(sound);}root.getAsJsonObject("survival").add("sounds",sounds);ProfileSettings settings=GSON.fromJson(root,ProfileSettings.class).validated();var p=profile("Large",settings);List<String> chunks=new ArrayList<>(ProfileClipboard.discordChunks(p));assertTrue(chunks.size()>1,"encoded="+ProfileClipboard.encode(p).length());assertTrue(chunks.stream().allMatch(x->x.length()<1900),"max="+chunks.stream().mapToInt(String::length).max().orElse(0));Collections.reverse(chunks);assertEquals(settings,ProfileClipboard.decode(String.join("\n",chunks)).settings());assertThrows(IllegalArgumentException.class,()->ProfileClipboard.decode(String.join("\n",chunks.subList(1,chunks.size()))));}
}
