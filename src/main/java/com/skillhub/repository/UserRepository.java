package com.skillhub.repository;

import com.skillhub.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    User findByEmail(String email);

    Optional<User> findByEmailAndRole(String email, String role);

    long countByLastLoginDateAfter(java.time.LocalDateTime date);
}
