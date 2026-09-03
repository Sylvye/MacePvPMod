package dev.macepvpmod;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AttributeSwapConfigStore {
    private static final Logger LOG = LoggerFactory.getLogger("macepvpmod");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path path;
    private AttributeSwapConfig current = AttributeSwapConfig.defaults();
    private boolean writable = true;
    public AttributeSwapConfigStore(Path path) { this.path = path; }
    public AttributeSwapConfig current() { return current; }
    public void load() {
        if (!Files.exists(path)) return;
        try {
            JsonObject saved = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            // Overlay saved fields onto defaults so newly added settings remain usable.
            JsonObject merged = GSON.toJsonTree(AttributeSwapConfig.defaults()).getAsJsonObject();
            for (var entry : saved.entrySet()) {
                if (merged.has(entry.getKey())) {
                    JsonElement value = entry.getValue();
                    JsonPrimitive expected = merged.getAsJsonPrimitive(entry.getKey());
                    if (!value.isJsonPrimitive()) throw new JsonParseException("Invalid " + entry.getKey());
                    JsonPrimitive actual = value.getAsJsonPrimitive();
                    if (expected.isBoolean() != actual.isBoolean() || expected.isNumber() != actual.isNumber())
                        throw new JsonParseException("Invalid type for " + entry.getKey());
                    merged.add(entry.getKey(), value);
                }
            }
            current = GSON.fromJson(merged, AttributeSwapConfig.class).validated();
        } catch (Exception error) {
            LOG.warn("Could not load attribute swap settings; using defaults", error);
            current = AttributeSwapConfig.defaults();
            try {
                Path backup = Files.createTempFile(path.toAbsolutePath().getParent(), "macepvpmod-invalid-", ".json");
                Files.copy(path, backup, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException backupError) {
                writable = false; // Never overwrite a damaged file if its backup failed.
                LOG.error("Could not back up invalid settings; saving disabled", backupError);
            }
        }
    }
    public void save(AttributeSwapConfig next) throws IOException {
        if (!writable) throw new IOException("Original settings could not be backed up. Check config folder permissions.");
        AttributeSwapConfig valid = next.validated();
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
