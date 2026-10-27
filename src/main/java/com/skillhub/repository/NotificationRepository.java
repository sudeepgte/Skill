package com.skillhub.repository;

import com.skillhub.model.Notification;
import com.skillhub.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByUserOrderByCreatedAtDesc(User user);

    long countByUserAndIsReadFalse(User user);

    List<Notification> findByUserAndTypeAndIsReadFalseOrderByCreatedAtDesc(User user, String type);

    Optional<Notification> findFirstByUserAndTypeAndIsReadFalseOrderByCreatedAtDesc(User user, String type);
}
