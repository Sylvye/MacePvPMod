package dev.macepvpmod;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SurvivalConfigStore {
    private static final Logger LOG = LoggerFactory.getLogger("macepvpmod");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path path;
    private SurvivalConfig current = SurvivalConfig.defaults();
    private boolean writable = true;
    public SurvivalConfigStore(Path path) { this.path = path; }
    public SurvivalConfig current() { return current; }
    public void load() {
        if (!Files.exists(path)) return;
        try {
            JsonObject saved = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            // Preserve the earlier shared volume unless a sound has its own saved value.
            if (saved.has("lowHealthVolume")) {
                if (!saved.has("harpVolume")) saved.add("harpVolume", saved.get("lowHealthVolume"));
                if (!saved.has("bassVolume")) saved.add("bassVolume", saved.get("lowHealthVolume"));
            }
            if (!saved.has("sounds")) saved.add("sounds", GSON.toJsonTree(SoundEntry.legacy(
                    saved.has("harpVolume") ? saved.get("harpVolume").getAsDouble() : .5,
                    saved.has("bassVolume") ? saved.get("bassVolume").getAsDouble() : .5,
                    saved.has("harpPitch") ? saved.get("harpPitch").getAsDouble() : 1,
                    saved.has("bassPitch") ? saved.get("bassPitch").getAsDouble() : 1)));
            // Overlay saved fields onto defaults so newly added settings remain usable.
            JsonObject merged = GSON.toJsonTree(SurvivalConfig.defaults()).getAsJsonObject();
            for (var entry : saved.entrySet()) {
                if (merged.has(entry.getKey())) {
                    JsonElement value = entry.getValue();
                    if (entry.getKey().equals("healingItems") || entry.getKey().equals("saturationItems")) {
                        if (!value.isJsonArray()) throw new JsonParseException("Invalid item list");
                        for (var element : value.getAsJsonArray()) {
                            if (!element.isJsonObject()) throw new JsonParseException("Invalid item rule");
                            var rule = element.getAsJsonObject();
                            if (!rule.has("item") || !rule.get("item").isJsonPrimitive() || !rule.getAsJsonPrimitive("item").isString())
                                throw new JsonParseException("Invalid item ID");
                            if (rule.has("potion") && !rule.get("potion").isJsonNull()
                                    && (!rule.get("potion").isJsonPrimitive() || !rule.getAsJsonPrimitive("potion").isString()))
                                throw new JsonParseException("Invalid potion ID");
                        }
                        merged.add(entry.getKey(), value); continue;
                    }
                    if (entry.getKey().equals("sounds")) {
                        if (!value.isJsonArray()) throw new JsonParseException("Invalid sound list");
                        for (var element : value.getAsJsonArray()) {
                            if (!element.isJsonObject()) throw new JsonParseException("Invalid sound entry");
                            var sound = element.getAsJsonObject();
                            if (!sound.has("sound") || !sound.get("sound").isJsonPrimitive() || !sound.getAsJsonPrimitive("sound").isString()) throw new JsonParseException("Invalid sound ID");
                            for (String field : new String[]{"volume", "pitch"}) {
                                if (!sound.has(field)) sound.addProperty(field, field.equals("volume") ? .5 : 1);
                                if (!sound.get(field).isJsonPrimitive() || !sound.getAsJsonPrimitive(field).isNumber()) throw new JsonParseException("Invalid sound " + field);
                            }
                        }
                        merged.add(entry.getKey(), value); continue;
                    }
                    JsonPrimitive expected = merged.getAsJsonPrimitive(entry.getKey());
                    if (!value.isJsonPrimitive()) throw new JsonParseException("Invalid " + entry.getKey());
                    JsonPrimitive actual = value.getAsJsonPrimitive();
                    if (expected.isBoolean() != actual.isBoolean() || expected.isNumber() != actual.isNumber())
                        throw new JsonParseException("Invalid type for " + entry.getKey());
                    merged.add(entry.getKey(), value);
                }
            }
            current = GSON.fromJson(merged, SurvivalConfig.class).validated();
        } catch (Exception error) {
            LOG.warn("Could not load survival instincts settings; using defaults", error);
            current = SurvivalConfig.defaults();
            try {
                Path backup = Files.createTempFile(path.toAbsolutePath().getParent(), "macepvpmod-survival-invalid-", ".json");
                Files.copy(path, backup, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException backupError) {
                writable = false; // Never overwrite a damaged file if its backup failed.
                LOG.error("Could not back up invalid settings; saving disabled", backupError);
            }
        }
    }
    public void save(SurvivalConfig next) throws IOException {
        if (!writable) throw new IOException("Original settings could not be backed up. Check config folder permissions.");
        SurvivalConfig valid = next.validated();
        Path parent = path.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, "macepvpmod-", ".tmp");
        try {
            Files.writeString(temporary, GSON.toJson(valid) + "\n", StandardCharsets.UTF_8);
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            current = valid;
        } finally { Files.deleteIfExists(temporary); }
    }
}
