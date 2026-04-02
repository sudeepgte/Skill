package com.skillhub.repository;

import com.skillhub.model.Creation;
import com.skillhub.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CreationRepository extends JpaRepository<Creation, Long> {
    List<Creation> findByUserOrderByCreatedAtDesc(User user);

    List<Creation> findAllByOrderByCreatedAtDesc();
}
