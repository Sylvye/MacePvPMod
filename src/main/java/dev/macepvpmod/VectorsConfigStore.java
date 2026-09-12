package dev.macepvpmod;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class VectorsConfigStore {
    private static final Logger LOG = LoggerFactory.getLogger("macepvpmod");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path path;
    private VectorsConfig current = VectorsConfig.defaults();
    private boolean writable = true;
    public VectorsConfigStore(Path path) { this.path = path; }
    public VectorsConfig current() { return current; }
    public void load() {
        if (!Files.exists(path)) return;
        try {
            JsonObject saved=JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            int version=saved.has("schemaVersion")?saved.get("schemaVersion").getAsInt():1;
            if(version==1){saved.addProperty("schemaVersion",2);saved.addProperty("elytraOnly",true);saved.addProperty("spearOnly",true);saved.remove("radius");}
            current=GSON.fromJson(saved,VectorsConfig.class).validated();
        }
        catch (Exception error) {
            LOG.warn("Could not load Vectors settings; using defaults", error); current = VectorsConfig.defaults();
            try { Path backup=Files.createTempFile(path.toAbsolutePath().getParent(),"macepvpmod-invalid-",".json");Files.copy(path,backup,StandardCopyOption.REPLACE_EXISTING); }
            catch (IOException backupError) { writable=false;LOG.error("Could not back up invalid settings; saving disabled",backupError); }
        }
    }
    public void save(VectorsConfig next) throws IOException {
        if (!writable) throw new IOException("Original settings could not be backed up. Check config folder permissions.");
        VectorsConfig valid=next.validated();Path parent=path.toAbsolutePath().getParent();Files.createDirectories(parent);
        Path temporary=Files.createTempFile(parent,"macepvpmod-",".tmp");
        try { Files.writeString(temporary,GSON.toJson(valid)+"\n",StandardCharsets.UTF_8);Files.move(temporary,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);current=valid; }
        finally { Files.deleteIfExists(temporary); }
    }
}
