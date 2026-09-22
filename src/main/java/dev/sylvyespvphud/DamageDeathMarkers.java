package dev.sylvyespvphud;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Session-local player deaths retained until a living instance is attacked. */
final class DamageDeathMarkers {
    private final Set<UUID> dead=new HashSet<>();
    void mark(UUID id){dead.add(id);}
    void revive(UUID id){dead.remove(id);}
    boolean contains(UUID id){return dead.contains(id);}
    Set<UUID> ids(){return Set.copyOf(dead);}
    void clear(){dead.clear();}
}
