package com.theclub.authservice.repository;

import com.theclub.authservice.model.LoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, UUID> {

    Optional<LoginAttempt> findByEmail(String email);

    @Modifying
    @Query("delete from LoginAttempt a where a.email = :email")
    void deleteByEmail(@Param("email") String email);
}
