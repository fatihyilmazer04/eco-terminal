-- =====================================================
-- V25: Zone Connections (Graph Edges for Pathfinding)
-- =====================================================
-- LLM-tabanlı en kısa yol algoritması (Dijkstra) için
-- zone'lar arası bağlantı tablosu.
-- =====================================================

CREATE TABLE zone_connections (
    id                  BIGSERIAL PRIMARY KEY,
    from_zone_id        BIGINT NOT NULL REFERENCES zones(zone_id) ON DELETE CASCADE,
    to_zone_id          BIGINT NOT NULL REFERENCES zones(zone_id) ON DELETE CASCADE,
    distance_meters     INT NOT NULL,
    walk_time_seconds   INT NOT NULL,
    has_escalator       BOOLEAN DEFAULT FALSE,
    has_elevator        BOOLEAN DEFAULT FALSE,
    has_moving_walkway  BOOLEAN DEFAULT FALSE,
    is_accessible       BOOLEAN DEFAULT TRUE,
    is_active           BOOLEAN DEFAULT TRUE,
    notes               VARCHAR(255),
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_zone_connection UNIQUE (from_zone_id, to_zone_id),
    CONSTRAINT chk_no_self_loop CHECK (from_zone_id <> to_zone_id)
);

CREATE INDEX idx_zone_conn_from ON zone_connections(from_zone_id) WHERE is_active = TRUE;
CREATE INDEX idx_zone_conn_to   ON zone_connections(to_zone_id)   WHERE is_active = TRUE;

-- ---------- CHECKIN -> SECURITY ----------
INSERT INTO zone_connections (from_zone_id, to_zone_id, distance_meters, walk_time_seconds, has_moving_walkway, notes)
SELECT z1.zone_id, z2.zone_id, 100, 75, TRUE, 'Ust koridor - yuruyen bant'
FROM zones z1, zones z2 WHERE z1.zone_name='CheckIn-1' AND z2.zone_name='Security-1';

INSERT INTO zone_connections (from_zone_id, to_zone_id, distance_meters, walk_time_seconds, notes)
SELECT z1.zone_id, z2.zone_id, 145, 110, 'Capraz gecis'
FROM zones z1, zones z2 WHERE z1.zone_name='CheckIn-1' AND z2.zone_name='Security-2';

INSERT INTO zone_connections (from_zone_id, to_zone_id, distance_meters, walk_time_seconds, notes)
SELECT z1.zone_id, z2.zone_id, 115, 86, 'Capraz gecis'
FROM zones z1, zones z2 WHERE z1.zone_name='CheckIn-2' AND z2.zone_name='Security-1';

INSERT INTO zone_connections (from_zone_id, to_zone_id, distance_meters, walk_time_seconds, has_moving_walkway, notes)
SELECT z1.zone_id, z2.zone_id, 110, 82, TRUE, 'Orta koridor - yuruyen bant'
FROM zones z1, zones z2 WHERE z1.zone_name='CheckIn-2' AND z2.zone_name='Security-2';

INSERT INTO zone_connections (from_zone_id, to_zone_id, distance_meters, walk_time_seconds, notes)
SELECT z1.zone_id, z2.zone_id, 150, 113, 'Capraz gecis - uzun'
FROM zones z1, zones z2 WHERE z1.zone_name='CheckIn-3' AND z2.zone_name='Security-1';

INSERT INTO zone_connections (from_zone_id, to_zone_id, distance_meters, walk_time_seconds, notes)
SELECT z1.zone_id, z2.zone_id, 105, 78, 'Alt koridor'
FROM zones z1, zones z2 WHERE z1.zone_name='CheckIn-3' AND z2.zone_name='Security-2';

-- ---------- SECURITY -> LOUNGE ----------
INSERT INTO zone_connections (from_zone_id, to_zone_id, distance_meters, walk_time_seconds, has_escalator, notes)
SELECT z1.zone_id, z2.zone_id, 105, 78, TRUE, 'Ust terminal - kisa yol'
FROM zones z1, zones z2 WHERE z1.zone_name='Security-1' AND z2.zone_name='Lounge-1';

INSERT INTO zone_connections (from_zone_id, to_zone_id, distance_meters, walk_time_seconds, has_elevator, notes)
SELECT z1.zone_id, z2.zone_id, 150, 113, TRUE, 'Capraz - alt lounge'
FROM zones z1, zones z2 WHERE z1.zone_name='Security-1' AND z2.zone_name='Lounge-2';

INSERT INTO zone_connections (from_zone_id, to_zone_id, distance_meters, walk_time_seconds, has_elevator, notes)
SELECT z1.zone_id, z2.zone_id, 160, 120, TRUE, 'Capraz - ust lounge'
FROM zones z1, zones z2 WHERE z1.zone_name='Security-2' AND z2.zone_name='Lounge-1';

INSERT INTO zone_connections (from_zone_id, to_zone_id, distance_meters, walk_time_seconds, has_escalator, notes)
SELECT z1.zone_id, z2.zone_id, 105, 78, TRUE, 'Alt terminal - kisa yol'
FROM zones z1, zones z2 WHERE z1.zone_name='Security-2' AND z2.zone_name='Lounge-2';

-- ---------- LOUNGE-1 -> A CONCOURSE ----------
INSERT INTO zone_connections (from_zone_id, to_zone_id, distance_meters, walk_time_seconds, has_moving_walkway, notes)
SELECT z1.zone_id, z2.zone_id, 95, 71, TRUE, 'A Konkursu - en yakin'
FROM zones z1, zones z2 WHERE z1.zone_name='Lounge-1' AND z2.zone_name='Gate A1';

INSERT INTO zone_connections (from_zone_id, to_zone_id, distance_meters, walk_time_seconds, has_moving_walkway, notes)
SELECT z1.zone_id, z2.zone_id, 155, 117, TRUE, 'A Konkursu orta'
FROM zones z1, zones z2 WHERE z1.zone_name='Lounge-1' AND z2.zone_name='Gate A2';

INSERT INTO zone_connections (from_zone_id, to_zone_id, distance_meters, walk_time_seconds, has_moving_walkway, notes)
SELECT z1.zone_id, z2.zone_id, 215, 162, TRUE, 'A Konkursu en uzak'
FROM zones z1, zones z2 WHERE z1.zone_name='Lounge-1' AND z2.zone_name='Gate A3';

-- ---------- LOUNGE-1 -> B CONCOURSE ----------
INSERT INTO zone_connections (from_zone_id, to_zone_id, distance_meters, walk_time_seconds, notes)
SELECT z1.zone_id, z2.zone_id, 115, 86, 'B Konkursu ust erisim'
FROM zones z1, zones z2 WHERE z1.zone_name='Lounge-1' AND z2.zone_name='Gate B1';

INSERT INTO zone_connections (from_zone_id, to_zone_id, distance_meters, walk_time_seconds, notes)
SELECT z1.zone_id, z2.zone_id, 175, 132, 'B Konkursu orta'
FROM zones z1, zones z2 WHERE z1.zone_name='Lounge-1' AND z2.zone_name='Gate B2';

-- ---------- LOUNGE-1 -> C CONCOURSE (uzak capraz) ----------
INSERT INTO zone_connections (from_zone_id, to_zone_id, distance_meters, walk_time_seconds, has_moving_walkway, notes)
SELECT z1.zone_id, z2.zone_id, 190, 142, TRUE, 'Uzak capraz - C Konkursu'
FROM zones z1, zones z2 WHERE z1.zone_name='Lounge-1' AND z2.zone_name='Gate C1';

INSERT INTO zone_connections (from_zone_id, to_zone_id, distance_meters, walk_time_seconds, has_moving_walkway, notes)
SELECT z1.zone_id, z2.zone_id, 225, 169, TRUE, 'Uzak capraz - C orta'
FROM zones z1, zones z2 WHERE z1.zone_name='Lounge-1' AND z2.zone_name='Gate C2';

INSERT INTO zone_connections (from_zone_id, to_zone_id, distance_meters, walk_time_seconds, has_moving_walkway, notes)
SELECT z1.zone_id, z2.zone_id, 270, 202, TRUE, 'Uzak capraz - C en uzak'
FROM zones z1, zones z2 WHERE z1.zone_name='Lounge-1' AND z2.zone_name='Gate C3';

-- ---------- LOUNGE-2 -> A CONCOURSE (uzak capraz) ----------
INSERT INTO zone_connections (from_zone_id, to_zone_id, distance_meters, walk_time_seconds, has_moving_walkway, notes)
SELECT z1.zone_id, z2.zone_id, 190, 142, TRUE, 'Uzak capraz - A Konkursu'
FROM zones z1, zones z2 WHERE z1.zone_name='Lounge-2' AND z2.zone_name='Gate A1';

INSERT INTO zone_connections (from_zone_id, to_zone_id, distance_meters, walk_time_seconds, has_moving_walkway, notes)
SELECT z1.zone_id, z2.zone_id, 220, 165, TRUE, 'Uzak capraz - A orta'
FROM zones z1, zones z2 WHERE z1.zone_name='Lounge-2' AND z2.zone_name='Gate A2';

INSERT INTO zone_connections (from_zone_id, to_zone_id, distance_meters, walk_time_seconds, has_moving_walkway, notes)
SELECT z1.zone_id, z2.zone_id, 265, 199, TRUE, 'Uzak capraz - A en uzak'
FROM zones z1, zones z2 WHERE z1.zone_name='Lounge-2' AND z2.zone_name='Gate A3';

-- ---------- LOUNGE-2 -> B CONCOURSE ----------
INSERT INTO zone_connections (from_zone_id, to_zone_id, distance_meters, walk_time_seconds, notes)
SELECT z1.zone_id, z2.zone_id, 115, 86, 'B Konkursu alt erisim'
FROM zones z1, zones z2 WHERE z1.zone_name='Lounge-2' AND z2.zone_name='Gate B1';

INSERT INTO zone_connections (from_zone_id, to_zone_id, distance_meters, walk_time_seconds, notes)
SELECT z1.zone_id, z2.zone_id, 175, 132, 'B Konkursu orta'
FROM zones z1, zones z2 WHERE z1.zone_name='Lounge-2' AND z2.zone_name='Gate B2';

-- ---------- LOUNGE-2 -> C CONCOURSE ----------
INSERT INTO zone_connections (from_zone_id, to_zone_id, distance_meters, walk_time_seconds, has_moving_walkway, notes)
SELECT z1.zone_id, z2.zone_id, 95, 71, TRUE, 'C Konkursu - en yakin'
FROM zones z1, zones z2 WHERE z1.zone_name='Lounge-2' AND z2.zone_name='Gate C1';

INSERT INTO zone_connections (from_zone_id, to_zone_id, distance_meters, walk_time_seconds, has_moving_walkway, notes)
SELECT z1.zone_id, z2.zone_id, 155, 117, TRUE, 'C Konkursu orta'
FROM zones z1, zones z2 WHERE z1.zone_name='Lounge-2' AND z2.zone_name='Gate C2';

INSERT INTO zone_connections (from_zone_id, to_zone_id, distance_meters, walk_time_seconds, has_moving_walkway, notes)
SELECT z1.zone_id, z2.zone_id, 215, 162, TRUE, 'C Konkursu en uzak'
FROM zones z1, zones z2 WHERE z1.zone_name='Lounge-2' AND z2.zone_name='Gate C3';

-- ---------- TERS YON EDGE'LER (bidirectional) ----------
INSERT INTO zone_connections (from_zone_id, to_zone_id, distance_meters, walk_time_seconds, has_escalator, has_elevator, has_moving_walkway, is_accessible, notes)
SELECT
    zc.to_zone_id,
    zc.from_zone_id,
    zc.distance_meters,
    zc.walk_time_seconds,
    zc.has_escalator,
    zc.has_elevator,
    zc.has_moving_walkway,
    zc.is_accessible,
    CONCAT('Ters yon: ', zc.notes)
FROM zone_connections zc
WHERE NOT EXISTS (
    SELECT 1 FROM zone_connections zc2
    WHERE zc2.from_zone_id = zc.to_zone_id
      AND zc2.to_zone_id = zc.from_zone_id
);
