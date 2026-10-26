package com.skillhub.repository;

import com.skillhub.model.InstructorSubscription;
import com.skillhub.model.PaymentStatus;
import com.skillhub.model.SubscriptionPayment;
import com.skillhub.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionPaymentRepository extends JpaRepository<SubscriptionPayment, Long> {

    List<SubscriptionPayment> findByInstructorOrderByCreatedAtDesc(User instructor);

    List<SubscriptionPayment> findBySubscriptionOrderByCreatedAtDesc(InstructorSubscription subscription);

    List<SubscriptionPayment> findAllByOrderByCreatedAtDesc();

    Optional<SubscriptionPayment> findByTransactionId(String transactionId);

    Optional<SubscriptionPayment> findByRazorpayOrderId(String razorpayOrderId);

    Optional<SubscriptionPayment> findByRazorpayPaymentId(String razorpayPaymentId);

    @Query("SELECT SUM(p.amount) FROM SubscriptionPayment p WHERE p.paymentStatus = 'SUCCESS'")
    Double sumTotalSuccessfulRevenue();

    @Query("SELECT COUNT(p) FROM SubscriptionPayment p WHERE p.paymentStatus = :status")
    long countByPaymentStatus(@Param("status") PaymentStatus status);
}
