package dev.sylvyespvphud;

import java.util.List;
import java.util.UUID;

public record SettingsProfile(String id, String name, List<String> servers, ProfileSettings settings) {
    public SettingsProfile validated() {
        try { UUID.fromString(id); } catch (Exception error) { throw new IllegalArgumentException("Invalid profile ID."); }
        String cleanName = ProfileManager.validateName(name);
        if (servers == null) throw new IllegalArgumentException("A profile is missing its server list.");
        List<String> cleanServers = servers.stream().map(ProfileManager::normalizeServer).distinct().toList();
        return new SettingsProfile(id, cleanName, List.copyOf(cleanServers), settings.validated());
    }

    SettingsProfile withName(String value) { return new SettingsProfile(id, value, servers, settings); }
    SettingsProfile withServers(List<String> value) { return new SettingsProfile(id, name, List.copyOf(value), settings); }
    SettingsProfile withSettings(ProfileSettings value) { return new SettingsProfile(id, name, servers, value); }
}
