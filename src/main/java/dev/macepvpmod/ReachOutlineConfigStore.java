package dev.macepvpmod;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ReachOutlineConfigStore {
    private static final Logger LOG = LoggerFactory.getLogger("macepvpmod");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path path;
    private ReachOutlineConfig current = ReachOutlineConfig.defaults();
    private boolean writable = true;
    public ReachOutlineConfigStore(Path path) { this.path = path; }
    public ReachOutlineConfig current() { return current; }
    public void load() {
        if (!Files.exists(path)) return;
        try { current = GSON.fromJson(Files.readString(path), ReachOutlineConfig.class).validated(); }
        catch (Exception error) {
            LOG.warn("Could not load reach outline settings; using defaults", error);
            current = ReachOutlineConfig.defaults();
            try {
                Path backup = Files.createTempFile(path.toAbsolutePath().getParent(), "macepvpmod-invalid-", ".json");
                Files.copy(path, backup, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException backupError) { writable = false; LOG.error("Could not back up invalid settings; saving disabled", backupError); }
        }
    }
    public void save(ReachOutlineConfig next) throws IOException {
        if (!writable) throw new IOException("Original settings could not be backed up. Check config folder permissions.");
        ReachOutlineConfig valid = next.validated();
        Path parent = path.toAbsolutePath().getParent(); Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, "macepvpmod-", ".tmp");
        try {
            Files.writeString(temporary, GSON.toJson(valid) + "\n", StandardCharsets.UTF_8);
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            current = valid;
        } finally { Files.deleteIfExists(temporary); }
    }
}
