package dev.sylvyespvphud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Owns persisted profiles and the runtime baseline/server/override selection state. */
public final class ProfileManager {
    private static final Logger LOG = LoggerFactory.getLogger("sylvyespvphud");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path path;
    private final Consumer<ProfileSettings> activator;
    private ProfileCatalog catalog;
    private String activeProfileId;
    private String connectedServer;
    private boolean manualOverride;
    private boolean writable = true;

    public ProfileManager(Path path) { this(path, ProfileSettings::activate); }
    ProfileManager(Path path, Consumer<ProfileSettings> activator) { this.path = path; this.activator = activator; }

    public synchronized void load(ProfileSettings legacy) {
        ProfileSettings fallback = legacy.validated();
        if (Files.exists(path)) {
            try {
                catalog = GSON.fromJson(JsonParser.parseString(Files.readString(path)), ProfileCatalog.class).validated();
            } catch (Exception error) {
                LOG.warn("Could not load settings profiles; using recovered Default profile", error);
                try {
                    Path backup = Files.createTempFile(path.toAbsolutePath().getParent(), "sylvyespvphud-profiles-invalid-", ".json");
                    Files.copy(path, backup, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException backupError) {
                    writable = false;
                    LOG.error("Could not back up invalid profile catalog; profile saving disabled", backupError);
                }
            }
        }
        if (catalog == null) {
            SettingsProfile profile = new SettingsProfile(UUID.randomUUID().toString(), "Default", List.of(), fallback);
            catalog = new ProfileCatalog(ProfileCatalog.VERSION, profile.id(), List.of(profile));
            if (writable) try { persist(catalog); } catch (IOException error) {
                writable = false; LOG.error("Could not create profile catalog; profile saving disabled", error);
            }
        }
        activeProfileId = catalog.baselineProfileId();
        activate(activeProfileId);
    }

    public synchronized List<SettingsProfile> profiles() { return catalog.profiles(); }
    public synchronized SettingsProfile active() { return byId(activeProfileId); }
    public synchronized SettingsProfile baseline() { return byId(catalog.baselineProfileId()); }
    public synchronized boolean connected() { return connectedServer != null; }
    public synchronized boolean manualOverride() { return manualOverride; }
    public synchronized String connectedServer() { return connectedServer; }
    public synchronized String selectionDescription() {
        if (manualOverride) return "Manual override until disconnect";
        if (connectedServer != null && ownerOfServer(connectedServer) != null) return "Selected for " + connectedServer;
        return connectedServer == null ? "Baseline profile" : "Baseline profile (no server match)";
    }

    public synchronized void updateActive(ProfileSettings settings) throws IOException {
        ProfileSettings valid = settings.validated();
        List<SettingsProfile> profiles = replace(activeProfileId, active().withSettings(valid));
        ProfileCatalog next = new ProfileCatalog(ProfileCatalog.VERSION, catalog.baselineProfileId(), profiles).validated();
        persist(next); catalog = next; activator.accept(valid);
    }

    public synchronized SettingsProfile create(String requestedName, boolean copyActive) throws IOException {
        String name = availableName(validateName(requestedName));
        ProfileSettings settings = copyActive ? active().settings() : ProfileSettings.defaults();
        SettingsProfile created = new SettingsProfile(UUID.randomUUID().toString(), name, List.of(), settings).validated();
        ArrayList<SettingsProfile> profiles = new ArrayList<>(catalog.profiles()); profiles.add(created);
        saveCatalog(new ProfileCatalog(ProfileCatalog.VERSION, catalog.baselineProfileId(), profiles));
        return created;
    }

    public synchronized SettingsProfile duplicate(String id) throws IOException {
        SettingsProfile source = byId(id);
        String name = availableName(source.name() + " Copy");
        SettingsProfile created = new SettingsProfile(UUID.randomUUID().toString(), name, List.of(), source.settings()).validated();
        ArrayList<SettingsProfile> profiles = new ArrayList<>(catalog.profiles()); profiles.add(created);
        saveCatalog(new ProfileCatalog(ProfileCatalog.VERSION, catalog.baselineProfileId(), profiles));
        return created;
    }

    public synchronized SettingsProfile importProfile(String shared) throws IOException {
        ProfileClipboard.Imported imported = ProfileClipboard.decode(shared);
        SettingsProfile created = new SettingsProfile(UUID.randomUUID().toString(), availableName(imported.name()), List.of(), imported.settings()).validated();
        ArrayList<SettingsProfile> profiles = new ArrayList<>(catalog.profiles()); profiles.add(created);
        saveCatalog(new ProfileCatalog(ProfileCatalog.VERSION, catalog.baselineProfileId(), profiles));
        return created;
    }

    public synchronized String exportProfile(String id) { return ProfileClipboard.encode(byId(id)); }
    public synchronized String exportProfileAscii(String id) { return ProfileClipboard.encodeAscii(byId(id)); }
    public synchronized List<String> exportProfileDiscordChunks(String id) { return ProfileClipboard.discordChunks(byId(id)); }
    public synchronized SettingsProfile ownerOfServer(String address) {
        String server = normalizeServer(address);
        for (SettingsProfile profile : catalog.profiles()) if (profile.servers().contains(server)) return profile;
        return null;
    }

    public synchronized void rename(String id, String requestedName) throws IOException {
        String name = validateName(requestedName);
        for (SettingsProfile profile : catalog.profiles())
            if (!profile.id().equals(id) && profile.name().equalsIgnoreCase(name)) throw new IllegalArgumentException("A profile named '" + name + "' already exists.");
        saveCatalog(new ProfileCatalog(ProfileCatalog.VERSION, catalog.baselineProfileId(), replace(id, byId(id).withName(name))));
    }

    public synchronized void delete(String id) throws IOException {
        if (catalog.profiles().size() == 1) throw new IllegalArgumentException("The final profile cannot be deleted.");
        byId(id);
        boolean deletedActive = activeProfileId.equals(id);
        List<SettingsProfile> profiles = catalog.profiles().stream().filter(p -> !p.id().equals(id)).toList();
        String baseline = catalog.baselineProfileId().equals(id) ? profiles.getFirst().id() : catalog.baselineProfileId();
        String nextActive = deletedActive ? baseline : activeProfileId;
        ProfileCatalog next = new ProfileCatalog(ProfileCatalog.VERSION, baseline, profiles).validated();
        persist(next); catalog = next; activeProfileId = nextActive;
        if (deletedActive) manualOverride = false;
        if (connectedServer != null && !manualOverride) activeProfileId = automaticId();
        activate(activeProfileId);
    }

    public synchronized void assignServer(String id, String address) throws IOException {
        String server = normalizeServer(address); byId(id);
        ArrayList<SettingsProfile> profiles = new ArrayList<>();
        for (SettingsProfile profile : catalog.profiles()) {
            ArrayList<String> servers = new ArrayList<>(profile.servers()); servers.remove(server);
            if (profile.id().equals(id) && !servers.contains(server)) servers.add(server);
            profiles.add(profile.withServers(servers));
        }
        saveCatalog(new ProfileCatalog(ProfileCatalog.VERSION, catalog.baselineProfileId(), profiles));
        reevaluateAfterAssignment();
    }

    public synchronized void removeServer(String id, String address) throws IOException {
        String server = normalizeServer(address); SettingsProfile profile = byId(id);
        ArrayList<String> servers = new ArrayList<>(profile.servers()); servers.remove(server);
        saveCatalog(new ProfileCatalog(ProfileCatalog.VERSION, catalog.baselineProfileId(), replace(id, profile.withServers(servers))));
        reevaluateAfterAssignment();
    }

    public synchronized void switchManual(String nameOrId) throws IOException {
        SettingsProfile target = find(nameOrId);
        if (connectedServer != null) { activeProfileId = target.id(); manualOverride = true; activate(activeProfileId); return; }
        ProfileCatalog next = new ProfileCatalog(ProfileCatalog.VERSION, target.id(), catalog.profiles()).validated();
        persist(next); catalog = next; activeProfileId = target.id(); manualOverride = false; activate(activeProfileId);
    }

    public synchronized void join(String address) {
        connectedServer = normalizeServer(address); manualOverride = false; activeProfileId = automaticId(); activate(activeProfileId);
    }

    public synchronized void disconnect() {
        connectedServer = null; manualOverride = false; activeProfileId = catalog.baselineProfileId(); activate(activeProfileId);
    }

    public synchronized void useAutomatic() {
        manualOverride = false; activeProfileId = connectedServer == null ? catalog.baselineProfileId() : automaticId(); activate(activeProfileId);
    }

    public static String validateName(String value) {
        if (value == null) throw new IllegalArgumentException("Profile name is required.");
        String name = value.trim();
        if (name.isEmpty() || name.length() > 32) throw new IllegalArgumentException("Profile names must contain 1–32 characters.");
        if (name.equalsIgnoreCase("auto")) throw new IllegalArgumentException("'auto' is reserved.");
        if (name.chars().anyMatch(c -> Character.isISOControl(c))) throw new IllegalArgumentException("Profile names cannot contain control characters.");
        return name;
    }

    public static String normalizeServer(String value) {
        if (value == null) throw new IllegalArgumentException("Server address is required.");
        String address = value.trim().toLowerCase(Locale.ROOT);
        if (address.isEmpty() || address.length() > 255 || address.chars().anyMatch(c -> Character.isWhitespace(c) || Character.isISOControl(c))
                || address.contains("/") || address.contains("*")) throw new IllegalArgumentException("Invalid server address.");
        String host; String portText = null;
        if (address.startsWith("[")) {
            int close = address.indexOf(']');
            if (close < 2) throw new IllegalArgumentException("Invalid IPv6 server address.");
            host = address.substring(0, close + 1);
            if (!host.substring(1, host.length() - 1).matches("[0-9a-f:.%_-]+") || !host.contains(":")) throw new IllegalArgumentException("Invalid IPv6 server address.");
            if (close + 1 < address.length()) {
                if (address.charAt(close + 1) != ':') throw new IllegalArgumentException("Invalid server address.");
                portText = address.substring(close + 2);
            }
        } else if (address.chars().filter(c -> c == ':').count() > 1) {
            if (!address.matches("[0-9a-f:.%_-]+") || !address.matches(".*[0-9a-f].*")) throw new IllegalArgumentException("Invalid IPv6 server address.");
            host = "[" + address + "]";
        } else {
            int colon = address.lastIndexOf(':');
            host = colon < 0 ? address : address.substring(0, colon);
            portText = colon < 0 ? null : address.substring(colon + 1);
            while (host.endsWith(".")) host = host.substring(0, host.length() - 1);
            if (!host.matches("[a-z0-9._-]+")) throw new IllegalArgumentException("Invalid server host.");
        }
        if (host.isEmpty()) throw new IllegalArgumentException("Invalid server address.");
        int port = 25565;
        if (portText != null) try { port = Integer.parseInt(portText); } catch (NumberFormatException error) { throw new IllegalArgumentException("Invalid server port."); }
        if (port < 1 || port > 65535) throw new IllegalArgumentException("Server port must be between 1 and 65535.");
        return host + ":" + port;
    }

    private void reevaluateAfterAssignment() { if (connectedServer != null && !manualOverride) { activeProfileId = automaticId(); activate(activeProfileId); } }
    private String automaticId() { for (SettingsProfile p : catalog.profiles()) if (p.servers().contains(connectedServer)) return p.id(); return catalog.baselineProfileId(); }
    private void activate(String id) { activator.accept(byId(id).settings()); }
    private SettingsProfile find(String value) { for (SettingsProfile p : catalog.profiles()) if (p.id().equals(value) || p.name().equalsIgnoreCase(value)) return p; throw new IllegalArgumentException("Unknown profile: " + value); }
    private SettingsProfile byId(String id) { for (SettingsProfile p : catalog.profiles()) if (p.id().equals(id)) return p; throw new IllegalArgumentException("Unknown profile ID."); }
    private List<SettingsProfile> replace(String id, SettingsProfile replacement) { ArrayList<SettingsProfile> result = new ArrayList<>(); for (SettingsProfile p : catalog.profiles()) result.add(p.id().equals(id) ? replacement : p); return result; }
    private String availableName(String requested) { String candidate = requested; int suffix = 2; while (nameExists(candidate)) candidate = requested + " (" + suffix++ + ")"; return candidate; }
    private boolean nameExists(String name) { for (SettingsProfile profile : catalog.profiles()) if (profile.name().equalsIgnoreCase(name)) return true; return false; }
    private void saveCatalog(ProfileCatalog next) throws IOException { next = next.validated(); persist(next); catalog = next; }
    private void persist(ProfileCatalog next) throws IOException {
        if (!writable) throw new IOException("Profile saving is disabled because its recovery backup failed.");
        Path parent = path.toAbsolutePath().getParent(); Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, "sylvyespvphud-profiles-", ".tmp");
        try {
            Files.writeString(temporary, GSON.toJson(next.validated()) + "\n", StandardCharsets.UTF_8);
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }
}
