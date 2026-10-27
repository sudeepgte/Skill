package com.skillhub.repository;

import com.skillhub.model.InstructorSubscription;
import com.skillhub.model.SubscriptionStatus;
import com.skillhub.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface InstructorSubscriptionRepository extends JpaRepository<InstructorSubscription, Long> {

    List<InstructorSubscription> findByInstructorOrderByCreatedAtDesc(User instructor);

    List<InstructorSubscription> findAllByOrderByCreatedAtDesc();

    List<InstructorSubscription> findByStatusOrderByCreatedAtDesc(SubscriptionStatus status);

    long countByStatus(SubscriptionStatus status);

    @Query("SELECT s FROM InstructorSubscription s WHERE s.instructor = :instructor AND s.status = :status AND :now BETWEEN s.startDate AND s.endDate ORDER BY s.endDate DESC")
    List<InstructorSubscription> findActiveSubscriptions(@Param("instructor") User instructor, @Param("status") SubscriptionStatus status, @Param("now") LocalDateTime now);

    Optional<InstructorSubscription> findFirstByInstructorAndStatusOrderByEndDateDesc(User instructor, SubscriptionStatus status);

    @Query("SELECT COUNT(s) FROM InstructorSubscription s WHERE s.status = 'ACTIVE' AND :now BETWEEN s.startDate AND s.endDate")
    long countCurrentlyActive(@Param("now") LocalDateTime now);

    @Query("SELECT SUM(s.monthlyPrice) FROM InstructorSubscription s WHERE s.status = 'ACTIVE' AND :now BETWEEN s.startDate AND s.endDate")
    Double sumMonthlyActiveRevenue(@Param("now") LocalDateTime now);

    @Query("SELECT s FROM InstructorSubscription s WHERE " +
           "(:status IS NULL OR s.status = :status) AND " +
           "(:planName IS NULL OR LOWER(s.planName) LIKE LOWER(CONCAT('%', :planName, '%'))) AND " +
           "(:search IS NULL OR LOWER(s.instructor.name) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(s.instructor.email) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "ORDER BY s.createdAt DESC")
    List<InstructorSubscription> searchSubscriptions(@Param("status") SubscriptionStatus status,
                                                    @Param("planName") String planName,
                                                    @Param("search") String search);
}
