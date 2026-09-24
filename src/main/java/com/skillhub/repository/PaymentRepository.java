package com.skillhub.repository;

import com.skillhub.model.Payment;
import com.skillhub.model.User;
import com.skillhub.model.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByTransactionId(String transactionId);
    Optional<Payment> findByRazorpayOrderId(String razorpayOrderId);
    List<Payment> findByUserAndStatusOrderByPaymentDateDesc(User user, PaymentStatus status);
}
