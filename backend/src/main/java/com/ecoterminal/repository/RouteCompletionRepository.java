package com.ecoterminal.repository;

import com.ecoterminal.model.entity.RouteCompletion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RouteCompletionRepository extends JpaRepository<RouteCompletion, Long> {

    /** Bu kullanıcı bu uçuş için daha önce rota tamamlama puanı aldı mı? */
    boolean existsByUser_UserIdAndFlightId(Long userId, Long flightId);
}
