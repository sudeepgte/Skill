package com.skillhub.repository;

import com.skillhub.model.User;
import com.skillhub.model.UserResume;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserResumeRepository extends JpaRepository<UserResume, Long> {
    Optional<UserResume> findByUser(User user);
    Optional<UserResume> findByUserId(Long userId);
}
