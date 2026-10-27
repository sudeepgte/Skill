package com.skillhub.security;

import com.razorpay.Utils;
import com.skillhub.model.*;
import com.skillhub.repository.CourseRepository;
import com.skillhub.repository.EnrollmentRepository;
import com.skillhub.repository.PaymentRepository;
import com.skillhub.repository.UserRepository;
import com.skillhub.service.EnrollmentService;
import com.skillhub.service.PaymentService;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class RazorpayPaymentHardeningIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private PaymentService paymentService;

    @Value("${razorpay.key.secret}")
    private String keySecret;

    @Value("${razorpay.webhook.secret:SkillHubWebhookSecret2026}")
    private String webhookSecret;

    private User studentA;
    private User studentB;
    private Course paidCourse;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        studentA = new User();
        studentA.setName("Buyer Student A");
        studentA.setEmail("studentA_" + suffix + "@skillhub.com");
        studentA.setPassword("Password123!");
        studentA.setRole("STUDENT");
        studentA.setApproved(true);
        studentA = userRepository.save(studentA);

        studentB = new User();
        studentB.setName("Attacker Student B");
        studentB.setEmail("studentB_" + suffix + "@skillhub.com");
        studentB.setPassword("Password123!");
        studentB.setRole("STUDENT");
        studentB.setApproved(true);
        studentB = userRepository.save(studentB);

        paidCourse = new Course();
        paidCourse.setTitle("Razorpay Secure Course " + suffix);
        paidCourse.setPrice(499.0);
        paidCourse.setPublished(true);
        paidCourse.setStatus(CourseStatus.PUBLISHED);
        paidCourse = courseRepository.save(paidCourse);
    }

    private String calculateHmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(rawHmac);
        } catch (Exception e) {
            throw new RuntimeException("HMAC computation failed", e);
        }
    }

    @Test
    @DisplayName("Verify valid Razorpay signature succeeds and enrolls student")
    void testValidSignatureVerificationSuccess() {
        String orderId = "order_" + UUID.randomUUID().toString().substring(0, 10);
        String paymentId = "pay_" + UUID.randomUUID().toString().substring(0, 10);

        Payment payment = new Payment();
        payment.setTransactionId("PEND-" + UUID.randomUUID().toString().substring(0, 8));
        payment.setAmount(499.0);
        payment.setPaymentMethod("RAZORPAY");
        payment.setStatus(PaymentStatus.PENDING);
        payment.setPaymentDate(LocalDateTime.now());
        payment.setUser(studentA);
        payment.setCourse(paidCourse);
        payment.setRazorpayOrderId(orderId);
        paymentRepository.save(payment);

        // Compute valid signature: HMAC_SHA256(orderId + "|" + paymentId, secret)
        String payload = orderId + "|" + paymentId;
        String validSignature = calculateHmacSha256(payload, keySecret);

        boolean verified = paymentService.verifyPayment(orderId, paymentId, validSignature, studentA);
        assertTrue(verified, "Valid signature must verify successfully");

        Payment updated = paymentRepository.findByRazorpayOrderId(orderId).orElseThrow();
        assertEquals(PaymentStatus.SUCCESS, updated.getStatus());
        assertTrue(enrollmentRepository.findByStudentAndCourse(studentA, paidCourse).isPresent(),
                "Student must be enrolled upon verified payment");
    }

    @Test
    @DisplayName("Verify forged/invalid Razorpay signature is rejected and marks payment FAILED")
    void testForgedSignatureRejection() {
        String orderId = "order_" + UUID.randomUUID().toString().substring(0, 10);
        String paymentId = "pay_" + UUID.randomUUID().toString().substring(0, 10);

        Payment payment = new Payment();
        payment.setTransactionId("PEND-" + UUID.randomUUID().toString().substring(0, 8));
        payment.setAmount(499.0);
        payment.setPaymentMethod("RAZORPAY");
        payment.setStatus(PaymentStatus.PENDING);
        payment.setUser(studentA);
        payment.setCourse(paidCourse);
        payment.setRazorpayOrderId(orderId);
        paymentRepository.save(payment);

        String forgedSignature = "0000000000000000000000000000000000000000000000000000000000000000";
        boolean verified = paymentService.verifyPayment(orderId, paymentId, forgedSignature, studentA);
        assertFalse(verified, "Forged signature must fail verification");

        Payment updated = paymentRepository.findByRazorpayOrderId(orderId).orElseThrow();
        assertEquals(PaymentStatus.FAILED, updated.getStatus(), "Payment status must transition to FAILED");
        assertFalse(enrollmentRepository.findByStudentAndCourse(studentA, paidCourse).isPresent(),
                "Student must not be enrolled on failed signature");
    }

    @Test
    @DisplayName("Verify cross-user order ownership rejection (Student B cannot verify Student A's order)")
    void testCrossUserOrderOwnershipRejection() {
        String orderId = "order_" + UUID.randomUUID().toString().substring(0, 10);
        String paymentId = "pay_" + UUID.randomUUID().toString().substring(0, 10);

        Payment payment = new Payment();
        payment.setTransactionId("PEND-" + UUID.randomUUID().toString().substring(0, 8));
        payment.setAmount(499.0);
        payment.setPaymentMethod("RAZORPAY");
        payment.setStatus(PaymentStatus.PENDING);
        payment.setUser(studentA); // Order belongs to Student A
        payment.setCourse(paidCourse);
        payment.setRazorpayOrderId(orderId);
        paymentRepository.save(payment);

        String payload = orderId + "|" + paymentId;
        String signature = calculateHmacSha256(payload, keySecret);

        // Student B attempts to verify Student A's order
        assertThrows(SecurityException.class, () -> {
            paymentService.verifyPayment(orderId, paymentId, signature, studentB);
        }, "Unauthorized verification of another user's order must throw SecurityException");
    }

    @Test
    @DisplayName("Verify idempotency: repeated callback execution does not duplicate enrollment")
    void testIdempotentRepeatedCallback() {
        String orderId = "order_" + UUID.randomUUID().toString().substring(0, 10);
        String paymentId = "pay_" + UUID.randomUUID().toString().substring(0, 10);

        Payment payment = new Payment();
        payment.setTransactionId("PEND-" + UUID.randomUUID().toString().substring(0, 8));
        payment.setAmount(499.0);
        payment.setPaymentMethod("RAZORPAY");
        payment.setStatus(PaymentStatus.PENDING);
        payment.setUser(studentA);
        payment.setCourse(paidCourse);
        payment.setRazorpayOrderId(orderId);
        paymentRepository.save(payment);

        String payload = orderId + "|" + paymentId;
        String validSignature = calculateHmacSha256(payload, keySecret);

        // First execution
        boolean firstCall = paymentService.verifyPayment(orderId, paymentId, validSignature, studentA);
        assertTrue(firstCall);

        // Second repeated execution
        boolean secondCall = paymentService.verifyPayment(orderId, paymentId, validSignature, studentA);
        assertTrue(secondCall, "Second callback must return true idempotently");

        assertEquals(1, enrollmentRepository.findByStudent(studentA).size(),
                "Must strictly have 1 enrollment record without duplication");
    }

    @Test
    @DisplayName("Verify webhook rejection when X-Razorpay-Signature is invalid or missing")
    void testWebhookMissingOrInvalidSignatureRejection() throws Exception {
        String sampleJson = "{\"event\":\"payment.captured\"}";

        // Missing signature -> 400
        mockMvc.perform(post("/api/payment/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sampleJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"));

        // Invalid signature -> 400
        mockMvc.perform(post("/api/payment/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Razorpay-Signature", "invalid_signature_hash")
                        .content(sampleJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    @DisplayName("Verify valid webhook payment.captured event processes payment and enrolls student")
    void testWebhookValidSignaturePaymentCaptured() throws Exception {
        String orderId = "order_" + UUID.randomUUID().toString().substring(0, 10);
        String paymentId = "pay_" + UUID.randomUUID().toString().substring(0, 10);

        Payment payment = new Payment();
        payment.setTransactionId("PEND-" + UUID.randomUUID().toString().substring(0, 8));
        payment.setAmount(499.0);
        payment.setPaymentMethod("RAZORPAY");
        payment.setStatus(PaymentStatus.PENDING);
        payment.setUser(studentA);
        payment.setCourse(paidCourse);
        payment.setRazorpayOrderId(orderId);
        paymentRepository.save(payment);

        JSONObject entity = new JSONObject();
        entity.put("id", paymentId);
        entity.put("order_id", orderId);
        entity.put("amount", 49900);
        entity.put("status", "captured");

        JSONObject paymentWrapper = new JSONObject();
        paymentWrapper.put("entity", entity);

        JSONObject payloadWrapper = new JSONObject();
        payloadWrapper.put("payment", paymentWrapper);

        JSONObject event = new JSONObject();
        event.put("event", "payment.captured");
        event.put("payload", payloadWrapper);

        String rawJson = event.toString();
        String validWebhookSignature = calculateHmacSha256(rawJson, webhookSecret);

        mockMvc.perform(post("/api/payment/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Razorpay-Signature", validWebhookSignature)
                        .content(rawJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        Payment updated = paymentRepository.findByRazorpayOrderId(orderId).orElseThrow();
        assertEquals(PaymentStatus.SUCCESS, updated.getStatus());
        assertTrue(enrollmentRepository.findByStudentAndCourse(studentA, paidCourse).isPresent());
    }
}
