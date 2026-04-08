package com.ecoterminal.service;

import com.ecoterminal.model.dto.LoungeResponse;
import com.ecoterminal.model.dto.ZoneOccupancyResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LoungeService {

    private final OccupancyService occupancyService;

    /**
     * LOUNGE tipindeki bölgeleri döndürür, density < 0.50 filtresi uygulanır.
     * Doluluk oranına göre artan sırada sıralanır.
     */
    @Transactional(readOnly = true)
    public List<LoungeResponse> getQuietLounges() {
        return occupancyService.getAllZonesWithOccupancy()
                .stream()
                .filter(z -> "LOUNGE".equalsIgnoreCase(z.type()))
                .filter(z -> z.densityPct() != null && z.densityPct() < 0.50f)
                .sorted(Comparator.comparingDouble(z -> (z.densityPct() != null ? z.densityPct() : 1f)))
                .map(LoungeResponse::from)
                .toList();
    }

    /** Tüm aktif LOUNGE bölgeleri (filtre yok) */
    @Transactional(readOnly = true)
    public List<LoungeResponse> getAllLounges() {
        return occupancyService.getAllZonesWithOccupancy()
                .stream()
                .filter(z -> "LOUNGE".equalsIgnoreCase(z.type()))
                .sorted(Comparator.comparingDouble(z -> (z.densityPct() != null ? z.densityPct() : 1f)))
                .map(LoungeResponse::from)
                .toList();
    }

    /** En düşük doluluklu LOUNGE bölgesi */
    @Transactional(readOnly = true)
    public Optional<LoungeResponse> getBestLounge() {
        return occupancyService.getAllZonesWithOccupancy()
                .stream()
                .filter(z -> "LOUNGE".equalsIgnoreCase(z.type()))
                .min(Comparator.comparingDouble(z -> (z.densityPct() != null ? z.densityPct() : 1f)))
                .map(LoungeResponse::from);
    }
}
