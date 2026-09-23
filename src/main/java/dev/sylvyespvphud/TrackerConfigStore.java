package dev.sylvyespvphud;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TrackerConfigStore {
    private static final Logger LOG=LoggerFactory.getLogger("sylvyespvphud");
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    private final Path path;private TrackerConfig current=TrackerConfig.defaults();private boolean writable=true;
    public TrackerConfigStore(Path path){this.path=path;} public TrackerConfig current(){return current;} void activate(TrackerConfig next){current=next.validated();}
    public void load(){if(!Files.exists(path))return;try{JsonObject saved=JsonParser.parseString(Files.readString(path)).getAsJsonObject();int version=saved.has("schemaVersion")?saved.get("schemaVersion").getAsInt():1;
            if(version==1){var defaults=TrackerConfig.defaults();saved.addProperty("distanceScalingStrength",defaults.distanceScalingStrength());saved.addProperty("showDistance",defaults.showDistance());saved.addProperty("hideDistantPlayers",false);saved.addProperty("hideStartDistance",defaults.hideStartDistance());}
            if(version==1||version==2){saved.addProperty("schemaVersion",3);saved.addProperty("maxVisiblePlayers",0);}
            current=GSON.fromJson(saved,TrackerConfig.class).validated();}
        catch(Exception error){LOG.warn("Could not load Player Tracker settings; using defaults",error);current=TrackerConfig.defaults();
            try{Path backup=Files.createTempFile(path.toAbsolutePath().getParent(),"sylvyespvphud-invalid-",".json");Files.copy(path,backup,StandardCopyOption.REPLACE_EXISTING);}
            catch(IOException backupError){writable=false;LOG.error("Could not back up invalid settings; saving disabled",backupError);}}}
    public void save(TrackerConfig next)throws IOException{if(!writable)throw new IOException("Original settings could not be backed up. Check config folder permissions.");
        TrackerConfig valid=next.validated();Path parent=path.toAbsolutePath().getParent();Files.createDirectories(parent);Path temporary=Files.createTempFile(parent,"sylvyespvphud-",".tmp");
        try{Files.writeString(temporary,GSON.toJson(valid)+"\n",StandardCharsets.UTF_8);Files.move(temporary,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);current=valid;}
        finally{Files.deleteIfExists(temporary);}}
}
