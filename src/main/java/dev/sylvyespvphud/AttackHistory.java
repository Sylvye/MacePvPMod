package dev.sylvyespvphud;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/** Server observations consume saved attacks in order; each observation has its own deadline. */
final class AttackHistory<T> {
    enum Kind { HEALTH, BLOCK }
    static final int PER_TARGET_LIMIT = 32, TOTAL_LIMIT = 256;
    static final class Entry<T> {
        final UUID target;
        final T value;
        final long created, deadline, settle;
        long expires;
        boolean resolved, cancelled;
        Entry(UUID target,T value,long now,long lifetime,long settle) {
            this.target=target;this.value=value;created=now;expires=now+lifetime;deadline=expires+settle+1;this.settle=settle;
        }
    }
    static final class Evidence<T> {
        final UUID target;
        final Kind kind;
        final List<Entry<T>> candidates;
        final long received,readyAt;
        boolean cancelled, resolved;
        Evidence(UUID target,Kind kind,List<Entry<T>> candidates,long now) {
            this.target=target;this.kind=kind;this.candidates=new ArrayList<>(candidates);received=now;
            readyAt=now+candidates.stream().mapToLong(e->e.settle).max().orElse(200);
        }
    }
    record Resolution<T>(Entry<T> entry,Evidence<T> evidence) {}
    private final List<Entry<T>> entries=new ArrayList<>();
    private final List<Evidence<T>> evidence=new ArrayList<>();

    Entry<T> add(UUID target,T value,long now,long lifetime,long settle) {
        expire(now);
        boolean targetFull=entries.stream().filter(e->e.target.equals(target)).count()>=PER_TARGET_LIMIT;
        if(targetFull||entries.size()>=TOTAL_LIMIT) {
            // Reserve the ordered candidates for observations already received. Evict only an
            // unconfirmed old swing, rather than blacking out this target for another retention window.
            var reserved=new HashSet<Entry<T>>();
            for(var observation:evidence)if(!observation.cancelled&&!observation.resolved)
                observation.candidates.stream().filter(e->!e.cancelled&&!e.resolved&&!reserved.contains(e)).findFirst().ifPresent(reserved::add);
            var oldest=entries.stream().filter(e->(!targetFull||e.target.equals(target))&&!reserved.contains(e)).findFirst();
            if(oldest.isEmpty())return null;
            oldest.get().cancelled=true;expire(now);
        }
        var entry=new Entry<>(target,value,now,lifetime,settle);entries.add(entry);return entry;
    }
    List<Entry<T>> entries() { return List.copyOf(entries); }
    Evidence<T> confirm(UUID target,Kind kind,Predicate<T> eligible,long now) {
        expire(now);
        var candidates=entries.stream().filter(e->e.target.equals(target)&&!e.resolved&&!e.cancelled&&eligible.test(e.value)).toList();
        if(candidates.isEmpty())return null;
        if(evidence.stream().filter(e->e.target.equals(target)).count()>=PER_TARGET_LIMIT) {
            return null;
        }
        // A response arriving just before expiry still gets its bounded settling interval.
        for(var entry:candidates)entry.expires=Math.max(entry.expires,Math.min(entry.deadline,now+entry.settle+1));
        var event=new Evidence<>(target,kind,candidates,now);evidence.add(event);return event;
    }
    void discard(UUID target) {
        entries.removeIf(e->{if(!e.target.equals(target))return false;e.cancelled=true;return true;});
        evidence.removeIf(e->{if(!e.target.equals(target))return false;e.cancelled=true;return true;});
    }
    void clear() { for(var e:entries)e.cancelled=true;for(var e:evidence)e.cancelled=true;entries.clear();evidence.clear(); }
    void expire(long now) {
        for(var entry:entries)if(entry.expires<=now)entry.cancelled=true;
        for(var event:evidence)if(!event.resolved&&!event.cancelled) {
            // Losing an old swing does not invalidate more recent swings or their confirmations.
            event.candidates.removeIf(e->e.cancelled||e.resolved);
            if(event.candidates.isEmpty())event.cancelled=true;
        }
        entries.removeIf(e->e.cancelled||e.resolved);
        evidence.removeIf(e->e.cancelled||e.resolved);
    }
    private List<Entry<T>> available(Evidence<T> event) {
        return event.candidates.stream().filter(e->!e.cancelled&&!e.resolved).toList();
    }

    List<Resolution<T>> resolve(long now) {
        expire(now);
        var results=new ArrayList<Resolution<T>>();
        var waiting=new HashSet<UUID>();
        for(var event:evidence) {
            if(event.cancelled||event.resolved||waiting.contains(event.target))continue;
            if(event.readyAt>now){waiting.add(event.target);continue;}
            var candidates=available(event);
            if(candidates.isEmpty()){event.cancelled=true;continue;}
            var entry=candidates.getFirst();
            if(candidates.size()==1) {
                // Both observations can only describe this one attack. Reject the conflict locally,
                // including an opposite-kind observation that arrived during this settling window.
                var conflicts=evidence.stream().filter(other->other!=event&&!other.cancelled&&!other.resolved
                        &&other.target.equals(event.target)&&other.kind!=event.kind&&other.received<=event.readyAt)
                        .filter(other->{var possible=available(other);return possible.size()==1&&possible.getFirst()==entry;}).toList();
                if(!conflicts.isEmpty()) {
                    entry.cancelled=true;event.cancelled=true;
                    for(var conflict:conflicts)conflict.cancelled=true;
                    continue;
                }
            }
            // Damage estimates are snapshots, not server swing IDs. Differing estimates must not
            // suppress proof that a hit/block happened, nor let a weak follow-up replace a smash.
            entry.resolved=true;event.resolved=true;
            results.add(new Resolution<>(entry,event));
        }
        expire(now);
        return results;
    }
}
