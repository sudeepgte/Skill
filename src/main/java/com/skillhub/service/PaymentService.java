package com.skillhub.service;

import com.skillhub.exception.BusinessException;
import com.skillhub.model.*;
import com.skillhub.repository.EnrollmentRepository;
import com.skillhub.repository.PaymentRepository;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.annotation.PostConstruct;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private EnrollmentService enrollmentService;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Value("${razorpay.key.id}")
    private String razorpayKeyId;

    @Value("${razorpay.key.secret}")
    private String razorpayKeySecret;

    @Value("${razorpay.webhook.secret:SkillHubWebhookSecret2026}")
    private String razorpayWebhookSecret;

    private RazorpayClient client;

    @PostConstruct
    public void init() {
        try {
            if (razorpayKeyId != null && razorpayKeySecret != null && !razorpayKeyId.isEmpty() && !razorpayKeySecret.isEmpty()) {
                this.client = new RazorpayClient(razorpayKeyId, razorpayKeySecret);
            }
        } catch (Exception e) {
            log.error("Could not initialize Razorpay client: {}", e.getMessage());
        }
    }

    private synchronized RazorpayClient getClient() throws RazorpayException {
        if (this.client == null) {
            this.client = new RazorpayClient(razorpayKeyId, razorpayKeySecret);
        }
        return this.client;
    }

    /**
     * Step 1: Create a Razorpay Order
     */
    @Transactional
    public Payment createOrder(User user, Course course) throws RazorpayException {
        JSONObject orderRequest = new JSONObject();
        double originalPrice = course.getPrice() != null ? course.getPrice() : 0.0;
        double serviceFee = originalPrice * 0.05;
        double totalAmount = originalPrice + serviceFee;

        orderRequest.put("amount", (int) Math.round(totalAmount * 100));
        orderRequest.put("currency", "INR");
        orderRequest.put("receipt", "txn_" + UUID.randomUUID().toString().substring(0, 8));

        Order razorpayOrder = getClient().orders.create(orderRequest);
        String orderId = razorpayOrder.get("id");

        Payment payment = new Payment();
        payment.setTransactionId("PEND-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        payment.setAmount(totalAmount);
        payment.setPaymentMethod("RAZORPAY");
        payment.setStatus(PaymentStatus.PENDING);
        payment.setPaymentDate(LocalDateTime.now());
        payment.setUser(user);
        payment.setCourse(course);
        payment.setRazorpayOrderId(orderId);

        return paymentRepository.save(payment);
    }

    /**
     * Step 2: Verify Payment Signature and finalize with authenticated user ownership and idempotency checks.
     */
    @Transactional
    public boolean verifyPayment(String orderId, String paymentId, String signature, User user) {
        Payment payment = paymentRepository.findByRazorpayOrderId(orderId)
                .orElseThrow(() -> new BusinessException("Payment record not found for order: " + orderId));

        // Requirement 4 & 11: Verify Order Ownership
        if (user != null && !payment.getUser().getId().equals(user.getId())) {
            log.warn("Security Alert: User {} attempted to verify order {} belonging to user {}",
                    user.getEmail(), orderId, payment.getUser().getId());
            throw new SecurityException("Unauthorized: Payment order does not belong to the authenticated user.");
        }

        // Requirement 5: Idempotency Protection
        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            log.info("Payment order {} already successfully verified. Returning success idempotently.", orderId);
            enrollmentService.enrollUser(payment.getUser(), payment.getCourse().getId());
            return true;
        }

        // Requirement 1 & 3: Cryptographic Signature Verification
        boolean isValid = false;
        try {
            JSONObject options = new JSONObject();
            options.put("razorpay_order_id", orderId);
            options.put("razorpay_payment_id", paymentId);
            options.put("razorpay_signature", signature);
            isValid = Utils.verifyPaymentSignature(options, razorpayKeySecret);
        } catch (Exception e) {
            log.error("Razorpay signature verification exception: {}", e.getMessage());
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
            return false;
        }

        if (!isValid) {
            log.warn("Invalid payment signature for order {} and payment {}", orderId, paymentId);
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
            return false;
        }

        // State Machine Transition: PENDING -> SUCCESS
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setRazorpayPaymentId(paymentId);
        payment.setRazorpaySignature(signature);
        payment.setTransactionId(paymentId);
        payment.setPaymentDate(LocalDateTime.now());
        paymentRepository.save(payment);

        // Requirement 8: Activate paid course enrollment only after verified payment
        enrollmentService.enrollUser(payment.getUser(), payment.getCourse().getId());
        log.info("Payment verified and student enrolled in course {} for order {}", payment.getCourse().getId(), orderId);
        return true;
    }

    @Transactional
    public boolean verifyPayment(String orderId, String paymentId, String signature) {
        return verifyPayment(orderId, paymentId, signature, null);
    }

    /**
     * Webhook Processing: payment.captured / order.paid reconciliation
     */
    @Transactional
    public void processWebhookPaymentCaptured(String orderId, String paymentId, Integer amountInPaise) {
        Optional<Payment> optPayment = paymentRepository.findByRazorpayOrderId(orderId);
        if (optPayment.isPresent()) {
            Payment payment = optPayment.get();
            if (payment.getStatus() == PaymentStatus.SUCCESS) {
                log.info("Webhook: Payment order {} already SUCCESS. Idempotent skip.", orderId);
                return;
            }

            if (amountInPaise != null) {
                long expectedPaise = Math.round(payment.getAmount() * 100);
                if (expectedPaise != amountInPaise) {
                    log.error("Webhook amount mismatch for order {}: expected {} paise but got {}", orderId, expectedPaise, amountInPaise);
                    throw new BusinessException("Amount mismatch in webhook for order " + orderId);
                }
            }

            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setRazorpayPaymentId(paymentId);
            payment.setTransactionId(paymentId);
            payment.setPaymentDate(LocalDateTime.now());
            paymentRepository.save(payment);

            enrollmentService.enrollUser(payment.getUser(), payment.getCourse().getId());
            log.info("Webhook: Course payment captured and enrollment activated for order {}", orderId);
        }
    }

    /**
     * Webhook Processing: payment.failed
     */
    @Transactional
    public void processWebhookPaymentFailed(String orderId, String failureReason) {
        paymentRepository.findByRazorpayOrderId(orderId).ifPresent(payment -> {
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
            log.info("Webhook: Payment order {} marked FAILED: {}", orderId, failureReason);
        });
    }

    /**
     * Webhook Processing: refund.processed (Requirement 13)
     */
    @Transactional
    public void processWebhookRefund(String paymentId, Double refundAmount) {
        Optional<Payment> optPayment = paymentRepository.findByRazorpayPaymentId(paymentId);
        if (optPayment.isPresent()) {
            Payment payment = optPayment.get();
            // Revoke course enrollment upon full refund
            enrollmentRepository.deleteByStudentAndCourse(payment.getUser(), payment.getCourse());
            log.info("Webhook: Refund processed for payment {}. Enrollment revoked.", paymentId);
        }
    }

    @Transactional
    public Payment processPayment(User user, Course course, String method) {
        Payment payment = new Payment();
        payment.setTransactionId("TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        double originalPrice = course.getPrice() != null ? course.getPrice() : 0.0;
        double serviceFee = originalPrice * 0.05;
        double totalAmount = originalPrice + serviceFee;
        payment.setAmount(totalAmount);
        payment.setPaymentMethod(method);
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setPaymentDate(LocalDateTime.now());
        payment.setUser(user);
        payment.setCourse(course);

        Payment savedPayment = paymentRepository.save(payment);
        enrollmentService.enrollUser(user, course.getId());
        return savedPayment;
    }

    public Payment getPaymentByTransactionId(String transactionId) {
        return paymentRepository.findByTransactionId(transactionId)
                .or(() -> paymentRepository.findByRazorpayPaymentId(transactionId))
                .orElse(null);
    }
}
