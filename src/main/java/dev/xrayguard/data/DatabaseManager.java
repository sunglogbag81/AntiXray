package dev.xrayguard.data;

import dev.xrayguard.XrayGuardPlugin;

import java.io.File;
import java.sql.*;
import java.util.*;

public class DatabaseManager {

    private final XrayGuardPlugin plugin;
    private Connection connection;

    public DatabaseManager(XrayGuardPlugin plugin) {
        this.plugin = plugin;
    }

    public void init() throws SQLException {
        File dbFile = new File(plugin.getDataFolder(), "xrayguard.db");
        String url  = "jdbc:sqlite:" + dbFile.getAbsolutePath()
                    + "?busy_timeout=10000&journal_mode=WAL&synchronous=NORMAL&cache_size=-8000";

        try { Class.forName("org.sqlite.JDBC"); } catch (ClassNotFoundException e) {
            throw new SQLException("SQLite JDBC driver not found", e);
        }

        SQLException lastEx = null;
        for (int attempt = 1; attempt <= 5; attempt++) {
            try {
                connection = DriverManager.getConnection(url);
                connection.setAutoCommit(true);
                try (Statement st = connection.createStatement()) {
                    st.execute("PRAGMA busy_timeout = 10000");
                    st.execute("PRAGMA journal_mode = WAL");
                    st.execute("PRAGMA synchronous  = NORMAL");
                    st.execute("PRAGMA cache_size   = -8000");
                    st.execute("PRAGMA foreign_keys = ON");
                }
                createTables();
                return;
            } catch (SQLException e) {
                lastEx = e;
                plugin.getLogger().warning("[XrayGuard] DB init attempt " + attempt + "/5 failed: " + e.getMessage());
                if (connection != null) { try { connection.close(); } catch (SQLException ignored) {} }
                try { Thread.sleep(500L * attempt); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            }
        }
        throw lastEx;
    }

    private void createTables() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS mining_records (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    player_uuid TEXT NOT NULL,
                    timestamp   INTEGER NOT NULL,
                    material    TEXT NOT NULL,
                    x INTEGER, y INTEGER, z INTEGER,
                    world TEXT,
                    player_x REAL, player_y REAL, player_z REAL,
                    is_ore INTEGER NOT NULL
                )""");
            st.execute("""
                CREATE TABLE IF NOT EXISTS player_stats (
                    player_uuid TEXT PRIMARY KEY,
                    player_name TEXT,
                    total_blocks INTEGER DEFAULT 0,
                    total_ores   INTEGER DEFAULT 0,
                    ore_rate     REAL    DEFAULT 0,
                    suspicion_score INTEGER DEFAULT 0,
                    last_updated INTEGER
                )""");
            st.execute("""
                CREATE TABLE IF NOT EXISTS evidence_snapshots (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    player_uuid  TEXT NOT NULL,
                    player_name  TEXT,
                    timestamp    INTEGER,
                    score        INTEGER,
                    total_blocks INTEGER,
                    total_ores   INTEGER,
                    ore_rate     REAL,
                    ore_coords   TEXT,
                    inventory    TEXT
                )""");
            st.execute("CREATE INDEX IF NOT EXISTS idx_mining_uuid   ON mining_records(player_uuid)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_evidence_uuid ON evidence_snapshots(player_uuid)");
        }
    }

    public synchronized void insertRecord(MiningRecord r) throws SQLException {
        String sql = "INSERT INTO mining_records(player_uuid,timestamp,material,x,y,z,world,player_x,player_y,player_z,is_ore) VALUES(?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1,  r.playerUuid().toString());
            ps.setLong(2,    r.timestamp());
            ps.setString(3,  r.material().name());
            ps.setInt(4,     r.x()); ps.setInt(5, r.y()); ps.setInt(6, r.z());
            ps.setString(7,  r.world());
            ps.setDouble(8,  r.playerX()); ps.setDouble(9, r.playerY()); ps.setDouble(10, r.playerZ());
            ps.setInt(11,    r.isOre() ? 1 : 0);
            ps.executeUpdate();
        }
    }

    public synchronized void upsertScore(PlayerStats s) throws SQLException {
        String sql = """
            INSERT INTO player_stats(player_uuid,player_name,total_blocks,total_ores,ore_rate,suspicion_score,last_updated)
            VALUES(?,?,?,?,?,?,?)
            ON CONFLICT(player_uuid) DO UPDATE SET
              player_name=excluded.player_name, total_blocks=excluded.total_blocks,
              total_ores=excluded.total_ores,   ore_rate=excluded.ore_rate,
              suspicion_score=excluded.suspicion_score, last_updated=excluded.last_updated""";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, s.playerUuid().toString());
            ps.setString(2, s.playerName());
            ps.setInt(3,    s.totalBlocks());
            ps.setInt(4,    s.totalOres());
            ps.setDouble(5, s.oreRate());
            ps.setInt(6,    s.suspicionScore());
            ps.setLong(7,   s.lastUpdated());
            ps.executeUpdate();
        }
    }

    /**
     * player_stats 에 데이터가 있으면 그대로 반환.
     * 없으면 mining_records 에서 실시간 집계해서 반환 (suspicion_score = -1 로 "미분석" 표시).
     */
    public synchronized Optional<PlayerStats> getScore(UUID uuid) throws SQLException {
        // 1) player_stats 우선 조회
        String sql = "SELECT * FROM player_stats WHERE player_uuid=?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return Optional.of(new PlayerStats(
                        UUID.fromString(rs.getString("player_uuid")),
                        rs.getString("player_name"),
                        rs.getInt("total_blocks"),
                        rs.getInt("total_ores"),
                        rs.getDouble("ore_rate"),
                        rs.getInt("suspicion_score"),
                        rs.getLong("last_updated")));
            }
        }

        // 2) player_stats 없으면 mining_records 에서 실시간 집계
        String liveSql = """
            SELECT COUNT(*) AS total_blocks,
                   SUM(is_ore) AS total_ores,
                   MAX(timestamp) AS last_ts
            FROM mining_records
            WHERE player_uuid = ?
            """;
        try (PreparedStatement ps = connection.prepareStatement(liveSql)) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (!rs.next() || rs.getInt("total_blocks") == 0) return Optional.empty();

            int totalBlocks = rs.getInt("total_blocks");
            int totalOres   = rs.getInt("total_ores");
            long lastTs     = rs.getLong("last_ts");
            double oreRate  = totalBlocks > 0 ? (double) totalOres / totalBlocks : 0;

            // 이름은 mining_records 에 없으므로 uuid 문자열 앞 8자리로 대체
            String fallbackName = uuid.toString().substring(0, 8) + "...";

            // suspicion_score = -1 → 아직 분석 전임을 커맨드에서 표시
            return Optional.of(new PlayerStats(
                    uuid, fallbackName, totalBlocks, totalOres, oreRate, -1, lastTs));
        }
    }

    public synchronized List<PlayerStats> getTopSuspects(int limit) throws SQLException {
        List<PlayerStats> list = new ArrayList<>();
        String sql = "SELECT * FROM player_stats ORDER BY suspicion_score DESC LIMIT ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(new PlayerStats(
                    UUID.fromString(rs.getString("player_uuid")),
                    rs.getString("player_name"),
                    rs.getInt("total_blocks"),
                    rs.getInt("total_ores"),
                    rs.getDouble("ore_rate"),
                    rs.getInt("suspicion_score"),
                    rs.getLong("last_updated")));
        }
        return list;
    }

    public synchronized void insertEvidence(EvidenceSnapshot snap, String coordsJson) throws SQLException {
        String sql = "INSERT INTO evidence_snapshots(player_uuid,player_name,timestamp,score,total_blocks,total_ores,ore_rate,ore_coords,inventory) VALUES(?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, snap.playerUuid().toString());
            ps.setString(2, snap.playerName());
            ps.setLong(3,   snap.timestamp());
            ps.setInt(4,    snap.score());
            ps.setInt(5,    snap.totalBlocks());
            ps.setInt(6,    snap.totalOres());
            ps.setDouble(7, snap.oreRate());
            ps.setString(8, coordsJson);
            ps.setString(9, snap.inventorySummary());
            ps.executeUpdate();
        }
    }

    public synchronized List<Map<String, Object>> getEvidenceList(UUID uuid) throws SQLException {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT id,timestamp,score,ore_rate FROM evidence_snapshots WHERE player_uuid=? ORDER BY timestamp DESC LIMIT 20";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id",        rs.getInt("id"));
                row.put("timestamp", rs.getLong("timestamp"));
                row.put("score",     rs.getInt("score"));
                row.put("ore_rate",  rs.getDouble("ore_rate"));
                list.add(row);
            }
        }
        return list;
    }

    public synchronized List<int[]> getRecentOreCoords(UUID uuid, int limit) throws SQLException {
        List<int[]> list = new ArrayList<>();
        String sql = "SELECT x,y,z FROM mining_records WHERE player_uuid=? AND is_ore=1 ORDER BY timestamp DESC LIMIT ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(new int[]{rs.getInt("x"), rs.getInt("y"), rs.getInt("z")});
        }
        return list;
    }

    public synchronized void resetPlayer(UUID uuid) throws SQLException {
        for (String tbl : List.of("mining_records", "player_stats", "evidence_snapshots")) {
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM " + tbl + " WHERE player_uuid=?")) {
                ps.setString(1, uuid.toString()); ps.executeUpdate();
            }
        }
    }

    public synchronized int getPlayerCount() throws SQLException {
        ResultSet rs = connection.createStatement().executeQuery("SELECT COUNT(*) FROM player_stats");
        return rs.next() ? rs.getInt(1) : 0;
    }

    public synchronized double getGlobalAverageOreRate() throws SQLException {
        ResultSet rs = connection.createStatement().executeQuery("SELECT AVG(ore_rate) FROM player_stats WHERE total_blocks >= 64");
        return rs.next() ? rs.getDouble(1) : 0;
    }

    public void close() {
        try { if (connection != null && !connection.isClosed()) connection.close(); }
        catch (SQLException ignored) {}
    }
}
