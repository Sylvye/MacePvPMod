package dev.macepvpmod;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HudConfigStore {
    private static final Logger LOG = LoggerFactory.getLogger("macepvpmod");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path path;
    private HudConfig current = HudConfig.defaults();
    private boolean writable = true;
    public HudConfigStore(Path path) { this.path = path; }
    public HudConfig current() { return current; }
    public void load() {
        if (!Files.exists(path)) { current = HudConfig.migrate(MacePvPMod.CONFIG.current(), MacePvPMod.DAMAGE_CONFIG.current(), MacePvPMod.SURVIVAL_CONFIG.current()); return; }
        try {
            JsonObject saved = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            current = GSON.fromJson(saved, HudConfig.class).validated();
        } catch (Exception error) {
            LOG.warn("Could not load HUD settings; using defaults", error);
            current = HudConfig.defaults();
            try {
                Path backup = Files.createTempFile(path.toAbsolutePath().getParent(), "macepvpmod-invalid-", ".json");
                Files.copy(path, backup, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException backupError) {
                writable = false; // Never overwrite a damaged file if its backup failed.
                LOG.error("Could not back up invalid settings; saving disabled", backupError);
            }
        }
    }
    public void save(HudConfig next) throws IOException {
        if (!writable) throw new IOException("Original settings could not be backed up. Check config folder permissions.");
        HudConfig valid = next.validated();
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
