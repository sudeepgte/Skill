package com.skillhub.service;

import com.skillhub.model.*;
import com.skillhub.repository.PaymentRepository;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.annotation.PostConstruct;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class PaymentService {

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private EnrollmentService enrollmentService;

    @Value("${razorpay.key.id}")
    private String razorpayKeyId;

    @Value("${razorpay.key.secret}")
    private String razorpayKeySecret;

    private RazorpayClient client;

    @PostConstruct
    public void init() throws RazorpayException {
        this.client = new RazorpayClient(razorpayKeyId, razorpayKeySecret);
    }

    /**
     * Step 1: Create a Razorpay Order
     */
    @Transactional
    public Payment createOrder(User user, Course course) throws RazorpayException {
        JSONObject orderRequest = new JSONObject();
        // Razorpay amount is in paise (1 INR = 100 paise)
        double originalPrice = course.getPrice();
        double serviceFee = originalPrice * 0.05;
        double totalAmount = originalPrice + serviceFee;

        orderRequest.put("amount", (int) (totalAmount * 100));
        orderRequest.put("currency", "INR");
        orderRequest.put("receipt", "txn_" + UUID.randomUUID().toString().substring(0, 8));

        Order razorpayOrder = client.orders.create(orderRequest);
        String orderId = razorpayOrder.get("id");

        // Save a pending payment record
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
     * Step 2: Verify Payment Signature and finalize
     */
    @Transactional
    public boolean verifyPayment(String orderId, String paymentId, String signature) {
        try {
            JSONObject options = new JSONObject();
            options.put("razorpay_order_id", orderId);
            options.put("razorpay_payment_id", paymentId);
            options.put("razorpay_signature", signature);

            boolean isValid = Utils.verifyPaymentSignature(options, razorpayKeySecret);
            
            if (isValid) {
                Payment payment = paymentRepository.findByRazorpayOrderId(orderId)
                        .orElseThrow(() -> new RuntimeException("Payment record not found for order: " + orderId));
                
                payment.setStatus(PaymentStatus.SUCCESS);
                payment.setRazorpayPaymentId(paymentId);
                payment.setRazorpaySignature(signature);
                payment.setTransactionId(paymentId); // Use actual payment ID as txn ID
                paymentRepository.save(payment);

                // Enroll the user
                enrollmentService.enrollUser(payment.getUser(), payment.getCourse().getId());
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    @Transactional
    public Payment processPayment(User user, Course course, String method) {
        // Create payment record
        Payment payment = new Payment();
        payment.setTransactionId("TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        double originalPrice = course.getPrice();
        double serviceFee = originalPrice * 0.05;
        double totalAmount = originalPrice + serviceFee;
        payment.setAmount(totalAmount);
        payment.setPaymentMethod(method);
        payment.setStatus(PaymentStatus.SUCCESS); // Simulating success
        payment.setPaymentDate(LocalDateTime.now());
        payment.setUser(user);
        payment.setCourse(course);

        Payment savedPayment = paymentRepository.save(payment);

        // Enroll user in course
        enrollmentService.enrollUser(user, course.getId());

        return savedPayment;
    }

    public Payment getPaymentByTransactionId(String transactionId) {
        return paymentRepository.findByTransactionId(transactionId).orElse(null);
    }
}
