package dev.sylvyespvphud;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

/** Ordered, bounded correlation without assuming that every client swing reached the server. */
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
    private final java.util.Map<UUID,Long> quarantine=new java.util.HashMap<>();

    Entry<T> add(UUID target,T value,long now,long lifetime,long settle) {
        expire(now);
        if(quarantine.getOrDefault(target,Long.MIN_VALUE)>now)return null;
        if(entries.size()>=TOTAL_LIMIT||entries.stream().filter(e->e.target.equals(target)).count()>=PER_TARGET_LIMIT) {
            // Dropping one candidate must never turn an ambiguous confirmation into a certain one.
            discard(target);quarantine.put(target,now+lifetime);return null;
        }
        var entry=new Entry<>(target,value,now,lifetime,settle);entries.add(entry);return entry;
    }
    List<Entry<T>> entries() { return List.copyOf(entries); }
    Evidence<T> confirm(UUID target,Kind kind,Predicate<T> eligible,long now) {
        expire(now);
        var candidates=entries.stream().filter(e->e.target.equals(target)&&!e.resolved&&!e.cancelled&&eligible.test(e.value)).toList();
        if(candidates.isEmpty())return null;
        if(evidence.stream().filter(e->e.target.equals(target)).count()>=PER_TARGET_LIMIT) {
            discard(target);return null;
        }
        // A response arriving just before expiry still gets its bounded settling interval.
        for(var entry:candidates)entry.expires=Math.max(entry.expires,Math.min(entry.deadline,now+entry.settle+1));
        var event=new Evidence<>(target,kind,candidates,now);evidence.add(event);return event;
    }
    void discard(UUID target) {
        entries.removeIf(e->{if(!e.target.equals(target))return false;e.cancelled=true;return true;});
        evidence.removeIf(e->{if(!e.target.equals(target))return false;e.cancelled=true;return true;});
    }
    void clear() { for(var e:entries)e.cancelled=true;for(var e:evidence)e.cancelled=true;entries.clear();evidence.clear();quarantine.clear(); }
    private void expire(long now) {
        for(var entry:entries)if(entry.expires<=now)entry.cancelled=true;
        // Expiring a candidate invalidates its whole evidence cohort, not just that candidate.
        for(var event:evidence)if(event.cancelled||event.candidates.isEmpty()||event.candidates.stream().anyMatch(e->e.cancelled)) {
            event.cancelled=true;
            for(var candidate:event.candidates)candidate.cancelled=true;
        }
        // Propagate cancellation through overlapping cohorts.
        boolean changed;
        do {
            changed=false;
            for(var event:evidence)if(!event.cancelled&&event.candidates.stream().anyMatch(e->e.cancelled)) {
                event.cancelled=true;for(var candidate:event.candidates)candidate.cancelled=true;changed=true;
            }
        }while(changed);
        entries.removeIf(e->e.cancelled||e.resolved);
        evidence.removeIf(e->e.cancelled||e.resolved);
        quarantine.values().removeIf(until->until<=now);
    }

    List<Resolution<T>> resolve(long now,Function<T,Object> healthSignature,Function<T,Object> blockSignature) {
        expire(now);
        var results=new ArrayList<Resolution<T>>();
        for(UUID target:new HashSet<>(evidence.stream().map(e->e.target).toList())) {
            var events=evidence.stream().filter(e->e.target.equals(target)).toList();
            // Connected cohorts are independent of later swings and later, disjoint confirmations.
            var remaining=new ArrayList<>(events);
            while(!remaining.isEmpty()) {
                var cohort=new ArrayList<Evidence<T>>();cohort.add(remaining.removeFirst());
                var candidates=new HashSet<>(cohort.getFirst().candidates);
                boolean expanded;
                do {
                    expanded=false;
                    var iterator=remaining.iterator();
                    while(iterator.hasNext()) {
                        var next=iterator.next();
                        if(next.candidates.stream().anyMatch(candidates::contains)) {
                            cohort.add(next);candidates.addAll(next.candidates);iterator.remove();expanded=true;
                        }
                    }
                }while(expanded);
                cohort.sort(java.util.Comparator.comparingInt(events::indexOf));
                var ordered=entries.stream().filter(candidates::contains).toList();
                if(cohort.stream().anyMatch(e->e.readyAt>now))continue;
                int m=cohort.size(),n=ordered.size();
                boolean[][] prefix=new boolean[m+1][n+1],suffix=new boolean[m+1][n+1];
                java.util.Arrays.fill(prefix[0],true);java.util.Arrays.fill(suffix[m],true);
                for(int i=1;i<=m;i++)for(int j=1;j<=n;j++)
                    prefix[i][j]=prefix[i][j-1]||prefix[i-1][j-1]&&cohort.get(i-1).candidates.contains(ordered.get(j-1));
                for(int i=m-1;i>=0;i--)for(int j=n-1;j>=0;j--)
                    suffix[i][j]=suffix[i][j+1]||suffix[i+1][j+1]&&cohort.get(i).candidates.contains(ordered.get(j));
                if(!prefix[m][n]) { // Contradictory server evidence: do not guess a classification.
                    for(var e:cohort)e.cancelled=true;for(var e:ordered)e.cancelled=true;continue;
                }
                boolean certain=true;
                for(int i=0;i<m;i++) {
                    var signature=cohort.get(i).kind==Kind.HEALTH?healthSignature:blockSignature;
                    Object first=null;boolean found=false;
                    for(int j=0;j<n;j++)if(prefix[i][j]&&suffix[i+1][j+1]&&cohort.get(i).candidates.contains(ordered.get(j))) {
                        Object next=signature.apply(ordered.get(j).value);
                        if(found&&!Objects.equals(first,next))certain=false;
                        first=next;found=true;
                    }
                }
                if(!certain)continue;
                int previous=-1;
                for(int i=0;i<m;i++)for(int j=previous+1;j<n;j++)if(cohort.get(i).candidates.contains(ordered.get(j))&&suffix[i+1][j+1]) {
                    var entry=ordered.get(j);entry.resolved=true;cohort.get(i).resolved=true;
                    results.add(new Resolution<>(entry,cohort.get(i)));previous=j;break;
                }
                // Older unmatched swings in a resolved cohort cannot be recycled by a later packet.
                for(int j=0;j<=previous;j++)if(!ordered.get(j).resolved)ordered.get(j).cancelled=true;
            }
        }
        expire(now);
        return results;
    }
}
