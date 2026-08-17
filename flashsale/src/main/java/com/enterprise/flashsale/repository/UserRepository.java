package com.enterprise.flashsale.repository;

import com.enterprise.flashsale.entity.AppUser;
import com.enterprise.flashsale.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmail(String email);
}