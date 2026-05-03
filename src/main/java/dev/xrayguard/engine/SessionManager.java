package dev.xrayguard.engine;

import dev.xrayguard.data.MiningRecord;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager {

    private final Map<UUID, MiningSession> sessions = new ConcurrentHashMap<>();

    public MiningSession getOrCreate(UUID uuid, String name) {
        return sessions.computeIfAbsent(uuid, id -> new MiningSession(id, name));
    }

    public MiningSession get(UUID uuid)    { return sessions.get(uuid); }
    public void addRecord(MiningRecord r)  { MiningSession s = sessions.get(r.playerUuid()); if (s != null) s.addRecord(r); }
    public Collection<MiningSession> all() { return sessions.values(); }
    public void remove(UUID uuid)          { sessions.remove(uuid); }
    public boolean has(UUID uuid)          { return sessions.containsKey(uuid); }
}
