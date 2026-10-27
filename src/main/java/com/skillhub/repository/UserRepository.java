package com.skillhub.repository;

import com.skillhub.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    User findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<User> findByEmailAndRole(String email, String role);

    long countByLastLoginDateAfter(java.time.LocalDateTime date);

    long countByRole(String role);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdWithPessimisticLock(@org.springframework.data.repository.query.Param("id") Long id);

    @org.springframework.data.jpa.repository.Query("SELECT u FROM User u WHERE u.aadharCardUrl = :path OR u.proofDocUrl = :path OR u.resumeUrl = :path OR u.tenthCertUrl = :path OR u.puCertUrl = :path OR u.ugCertUrl = :path OR u.pgCertUrl = :path")
    Optional<User> findByAnyDocumentUrl(@org.springframework.data.repository.query.Param("path") String path);
}
