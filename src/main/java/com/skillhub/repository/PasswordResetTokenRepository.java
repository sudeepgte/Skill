package com.skillhub.repository;

import com.skillhub.model.PasswordResetToken;
import com.skillhub.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;
import java.util.stream.Stream;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByToken(String token);

    PasswordResetToken findByUser(User user);

    Optional<PasswordResetToken> findByUserAndOtp(User user, String otp);

    Optional<PasswordResetToken> findByOtp(String otp);

    Stream<PasswordResetToken> findAllByExpiryDateLessThan(java.time.LocalDateTime now);

    void deleteByExpiryDateLessThan(java.time.LocalDateTime now);

    @Modifying
    @Query("delete from PasswordResetToken t where t.expiryDate <= ?1")
    void deleteAllExpiredSince(java.time.LocalDateTime now);
}
