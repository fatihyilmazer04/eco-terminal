package com.ecoterminal.service.pathfinding;

import com.ecoterminal.model.entity.Zone;
import com.ecoterminal.model.entity.ZoneType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DijkstraService Unit Tests")
class DijkstraServiceTest {

    @Mock private GraphService graphService;

    @InjectMocks
    private DijkstraService dijkstraService;

    // ── findPath Tests ────────────────────────────────────────────────────────

    @Test
    @DisplayName("findPath_sameStartAndEnd_returnsTrivialSingleSegmentPath")
    void findPath_sameStartAndEnd_returnsTrivialSingleSegmentPath() {
        // given — başlangıç ve hedef aynı node
        Zone zone = buildZone(1L, "Gate A1");
        when(graphService.getZone(1L)).thenReturn(zone);

        // when
        DijkstraService.PathResult result = dijkstraService.findPath(
                1L, 1L, GraphService.WeightStrategy.BALANCED);

        // then
        assertThat(result.reachable()).isTrue();
        assertThat(result.segments()).hasSize(1);
        assertThat(result.segments().get(0).zoneId()).isEqualTo(1L);
        assertThat(result.totalWalkTimeSeconds()).isEqualTo(0);
    }

    @Test
    @DisplayName("findPath_unreachableGoal_returnsUnreachableResult")
    void findPath_unreachableGoal_returnsUnreachableResult() {
        // given — graph'ta sadece node 1 var, 2 yok / bağlı değil
        when(graphService.getAllZoneIds()).thenReturn(Set.of(1L, 2L));
        when(graphService.getNeighbors(1L)).thenReturn(List.of()); // kenar yok
        when(graphService.getNeighbors(2L)).thenReturn(List.of());

        // when
        DijkstraService.PathResult result = dijkstraService.findPath(
                1L, 2L, GraphService.WeightStrategy.SHORTEST);

        // then
        assertThat(result.reachable()).isFalse();
        assertThat(result.segments()).isEmpty();
    }

    @Test
    @DisplayName("findPath_withDirectEdge_returnsOneHopPath")
    void findPath_withDirectEdge_returnsOneHopPath() {
        // given — 1 → 2 doğrudan bağlı
        Zone zone1 = buildZone(1L, "CheckIn-1");
        Zone zone2 = buildZone(2L, "Gate A1");
        GraphService.EdgeInfo edge = buildEdge(1L, 2L, 60, 50);

        when(graphService.getAllZoneIds()).thenReturn(Set.of(1L, 2L));
        when(graphService.getNeighbors(1L)).thenReturn(List.of(edge));
        when(graphService.getNeighbors(2L)).thenReturn(List.of());
        when(graphService.calculateEdgeWeight(eq(edge), any())).thenReturn(1.0);
        when(graphService.getZone(1L)).thenReturn(zone1);
        when(graphService.getZone(2L)).thenReturn(zone2);

        // when
        DijkstraService.PathResult result = dijkstraService.findPath(
                1L, 2L, GraphService.WeightStrategy.BALANCED);

        // then
        assertThat(result.reachable()).isTrue();
        assertThat(result.segments()).hasSize(2); // başlangıç + hedef
        assertThat(result.segments().get(0).zoneId()).isEqualTo(1L);
        assertThat(result.segments().get(1).zoneId()).isEqualTo(2L);
        assertThat(result.totalWalkTimeSeconds()).isEqualTo(60);
        assertThat(result.totalDistanceMeters()).isEqualTo(50);
    }

    @Test
    @DisplayName("findPath_withTwoHops_prefersShortestPath")
    void findPath_withTwoHops_prefersShortestPath() {
        // given — 1 → 2 (doğrudan, ağırlık 10) | 1 → 3 → 2 (2 adım, ağırlık 3 + 3 = 6)
        Zone zone1 = buildZone(1L, "Start");
        Zone zone2 = buildZone(2L, "End");
        Zone zone3 = buildZone(3L, "Middle");

        GraphService.EdgeInfo direct   = buildEdge(1L, 2L, 200, 500); // ağır
        GraphService.EdgeInfo hop1     = buildEdge(1L, 3L, 60, 100);
        GraphService.EdgeInfo hop2     = buildEdge(3L, 2L, 60, 100);

        when(graphService.getAllZoneIds()).thenReturn(Set.of(1L, 2L, 3L));
        when(graphService.getNeighbors(1L)).thenReturn(List.of(direct, hop1));
        when(graphService.getNeighbors(3L)).thenReturn(List.of(hop2));
        when(graphService.getNeighbors(2L)).thenReturn(List.of());
        when(graphService.calculateEdgeWeight(eq(direct), any())).thenReturn(10.0);
        when(graphService.calculateEdgeWeight(eq(hop1), any())).thenReturn(3.0);
        when(graphService.calculateEdgeWeight(eq(hop2), any())).thenReturn(3.0);
        when(graphService.getZone(1L)).thenReturn(zone1);
        when(graphService.getZone(2L)).thenReturn(zone2);
        when(graphService.getZone(3L)).thenReturn(zone3);

        // when
        DijkstraService.PathResult result = dijkstraService.findPath(
                1L, 2L, GraphService.WeightStrategy.BALANCED);

        // then — daha düşük ağırlıklı 2-hop yol seçilmeli
        assertThat(result.reachable()).isTrue();
        assertThat(result.segments()).hasSize(3); // 1 → 3 → 2
        assertThat(result.segments().get(1).zoneId()).isEqualTo(3L);
    }

    // ── Yardımcı ──────────────────────────────────────────────────────────────

    private Zone buildZone(Long id, String name) {
        return Zone.builder()
                .zoneId(id)
                .zoneName(name)
                .type(ZoneType.GATE)
                .maxCapacity(200)
                .build();
    }

    private GraphService.EdgeInfo buildEdge(Long from, Long to, int walkSec, int distMeters) {
        return new GraphService.EdgeInfo(
                from * 100 + to,  // edgeId
                from, to,
                distMeters, walkSec,
                false, false, false, true, null
        );
    }
}
