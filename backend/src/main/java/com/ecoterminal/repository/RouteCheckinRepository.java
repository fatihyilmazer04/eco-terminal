package com.ecoterminal.repository;

import com.ecoterminal.model.entity.RouteCheckin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RouteCheckinRepository extends JpaRepository<RouteCheckin, Long> {

    /** Kullanıcının uçuş için tamamladığı adım sayısı */
    int countByUser_UserIdAndFlightId(Long userId, Long flightId);

    /** Belirli adım daha önce check-in yapılmış mı? */
    boolean existsByUser_UserIdAndFlightIdAndStepNumber(Long userId, Long flightId, int stepNumber);

    /** Uçuşun toplam adım sayısını ilk kayıttan al */
    @Query("SELECT r.totalSteps FROM RouteCheckin r WHERE r.user.userId = :userId AND r.flightId = :flightId ORDER BY r.stepNumber ASC")
    java.util.List<Integer> findTotalStepsByUserAndFlight(@Param("userId") Long userId, @Param("flightId") Long flightId);
}
