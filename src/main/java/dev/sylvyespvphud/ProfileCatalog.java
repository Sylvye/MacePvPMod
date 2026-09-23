package dev.sylvyespvphud;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public record ProfileCatalog(int schemaVersion, String baselineProfileId, List<SettingsProfile> profiles) {
    public static final int VERSION = 1;

    public ProfileCatalog validated() {
        if (schemaVersion != VERSION) throw new IllegalArgumentException("Unsupported profile catalog version: " + schemaVersion);
        if (profiles == null || profiles.isEmpty()) throw new IllegalArgumentException("At least one profile is required.");
        List<SettingsProfile> clean = profiles.stream().map(SettingsProfile::validated).toList();
        Set<String> ids = new HashSet<>(), names = new HashSet<>(), servers = new HashSet<>();
        for (SettingsProfile profile : clean) {
            if (!ids.add(profile.id())) throw new IllegalArgumentException("Duplicate profile ID.");
            if (!names.add(profile.name().toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("Duplicate profile name: " + profile.name());
            for (String server : profile.servers())
                if (!servers.add(server)) throw new IllegalArgumentException("Server is assigned more than once: " + server);
        }
        if (!ids.contains(baselineProfileId)) throw new IllegalArgumentException("The baseline profile does not exist.");
        return new ProfileCatalog(VERSION, baselineProfileId, List.copyOf(clean));
    }
}
