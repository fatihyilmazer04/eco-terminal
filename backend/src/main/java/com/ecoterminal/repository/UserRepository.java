package com.ecoterminal.repository;

import com.ecoterminal.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Login sonrası last_login alanını günceller — SELECT + UPDATE yerine tek sorgu */
    @Modifying
    @Query("UPDATE User u SET u.lastLogin = :loginTime WHERE u.userId = :userId")
    void updateLastLogin(@Param("userId") Long userId, @Param("loginTime") Instant loginTime);
}
